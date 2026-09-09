# Voyager Greenfield Rebuild — Design

Date: 2026-09-09
Status: Approved (design); implementation plan pending

## Context

Voyager is a Minecraft elytra racing minigame. A Kotlin implementation from 2023
(commit `85d77c6a8309fa1f2848229c9b8f8e634b6b2c69`) still runs in production and is
stable. Since then the project was rewritten in Java as a multi-module Gradle build and
partially migrated from Paper to Minestom.

The current tree does not meet the standard the project set for itself. An audit of the
`server` module against the ten mandatory design rules in `CLAUDE.md` found **zero rules
fully satisfied** (three partial, seven not met). Beyond style, the module carries a
complete dead parallel implementation of its own gameplay, and shared logic exists in up
to three copies. See "Evidence" below.

Production still runs the 2023 Kotlin build. Neither the current Java tree nor the
rebuild is under production pressure, and the cut-over schedule is therefore driven by
readiness rather than by an outage risk.

This document specifies a greenfield rebuild: a new module tree inside the existing
repository, replacing `server`, `plugins/game`, `plugins/setup` and all four `shared/*`
modules.

## Decisions

These were decided with the project owner before this document was written.

| # | Decision | Rationale |
|---|---|---|
| D1 | Greenfield rebuild, not incremental hardening | Structural debt is in the module boundaries themselves; refactoring in place preserves them |
| D2 | Same repository, new module tree alongside the old | Keeps git history, CI, Release Please and Renovate; `main` stays buildable throughout |
| D3 | Physics core first, then race loop | Vanilla parity is the project's central risk; it must fail fast if it fails |
| D4 | Parity proven by trace comparison against real Vanilla flights | Proves observed behaviour, not just the transcribed formula |
| D5 | Client keeps movement authority; server validates plausibility | Matches Vanilla and Minestom; avoids rubber-banding in a flight game |
| D6 | Setup server moves to Minestom; Paper is dropped entirely | Paper is the only blocker for Java 25 across all modules |
| D7 | Target Minecraft 26.2 now, follow to 26.3 later | Minestom `2026.08.28-26.2` is released; 26.3 has no Minestom build yet |
| D8 | Domain-oriented module cut (eight modules) | Makes the physics core independently testable and confines Minestom to one module |
| D9 | Existing ADRs are not binding for the new stack | They describe the old design; several are accepted but never implemented |

### Non-goals

- Feature parity with the current Java tree before cut-over. The bar is D3: physics
  parity plus a playable race loop (E4). Persistence, setup and polish follow after.
- Elytra durability simulation. Racing does not consume elytras.
- A general anti-cheat. The plausibility check validates runs; it does not police players.
- Virtual threads in the tick path. A fixed-budget 20 TPS loop does not benefit.

## Target platform

| Item | Value | Source |
|---|---|---|
| Minecraft | 26.2 "Chaos Cubed", released 2026-06-16, protocol 776, data 4903 | minecraft.wiki |
| Java | 25 — required by Vanilla itself since 26.1 | minecraft.wiki |
| Minestom | `net.minestom:minestom:2026.08.28-26.2` | Maven Central, GitHub releases |
| GC | ZGC (Vanilla's own default since 26.1) | minecraft.wiki |

Mojang moved to calendar versioning (`YY.N`) in 2026. There is no 1.22; it became 26.1.

The Vanilla elytra flight formula is **unchanged** from 1.21.11 through 26.3. Four
independent sources show no elytra or gliding entry after 1.21.11. The constants in
`docs/elytra-physics-reference.md` (§1–§4) remain valid. Only §6.1 (packet IDs) is
version-bound, and Minestom encapsulates those anyway.

26.2 adds three living-entity attributes: `air_drag_modifier`, `friction_modifier`,
`bounciness`. At default values behaviour is identical to 1.21.11. Whether the elytra
drag path (hardcoded `0.99` / `0.98`) routes through `air_drag_modifier` is **not
verified** and must be checked against a 26.2 decompile before E2 completes.

## Module architecture

Eight modules. Dependencies point strictly left; no cycles, no back-references.

```
voyager-api          JDK + annotations + adventure-api      (interfaces, records, enums, exceptions only)
voyager-physics      -> api
voyager-race         -> api, physics
voyager-persistence  -> api
voyager-platform     -> api, physics, race            + minestom 26.2
voyager-server       -> all
voyager-setup        -> api, platform
voyager-fitness      -> all                            (test-only, ArchUnit)
```

`voyager-api` contains no implementation. `voyager-physics` depends only on `api` and is
free of Minestom, of the game, and of the database.

### The vector type

`voyager-api` defines its own `record Vec3(double x, double y, double z)`. Conversion to
`net.minestom.server.coordinate.Vec` happens only in `voyager-platform`.

`Vec3`'s compact constructor **rejects NaN and infinity**. This closes Minestom issue
\#1335 at the type level: a single NaN passed to `setVelocity` permanently freezes a
player's velocity channel, and every subsequent valid call is silently ignored. A value
that cannot be constructed cannot be sent.

### voyager-fitness

ArchUnit today lives in the `server` test source set and therefore imports only what
`server` depends on — `shared/common` and `shared/database`. `shared/conversation-api`,
`shared/spline`, `plugins/game` and `plugins/setup` are never scanned, so the isolation
rules `CLAUDE.md` documents for them have no effect.

A dedicated test-only module depending on every other module is the only construction
that imports the whole tree. `allowEmptyShould(true)` is removed: a rule that passes
because it found nothing is worse than no rule.

Rules to enforce, at minimum:

- `voyager-api`, `voyager-physics`, `voyager-race`, `voyager-persistence` must not
  reference `net.minestom..`
- No module may reference `org.bukkit..`
- `voyager-persistence` must not reference race or platform packages
- The ten `CLAUDE.md` design rules, as far as ArchUnit can express them: sealed domain
  interfaces, abstract factories with private constructors, `Default*` classes final,
  `*ServiceImpl` implements a `*Service`, `*Exception` extends `RuntimeException`,
  components in `..component..` are records, systems named `*System` and located in
  `..system..`, every package has a `package-info.java`

### Build

`buildSrc` holds three convention plugins: `voyager.java-conventions`,
`voyager.library-conventions`, `voyager.application-conventions`. Toolchain and
`--release 25` are declared once. The current tree repeats these blocks in seven files,
two of which still use `sourceCompatibility` instead of a toolchain.

The version catalog stays programmatic in `settings.gradle.kts`, per project convention.
`gradle/libs.versions.toml` is not used.

### Java 25 usage

Sealed hierarchies with `permits` for domain interfaces; records for all components and
DTOs; pattern matching in `switch` over ring types and phase transitions instead of `if`
chains.

## Physics core (`voyager-physics`)

### Scope

The **full** Vanilla tick, not the subset the current code implements. Today steps 2–8
are computed and steps 1, 9 and 10 (durability, position integration, block collision)
are omitted. That was sufficient while the simulation was a passive tracker. Under D5 it
is not: validating a client requires predicting where it should be, which is steps 9 and
10.

Step 1 (durability) stays out by decision, and is documented as such.

### Collision access

Step 10 needs block access, and `voyager-physics` must not know Minestom. `voyager-api`
declares a narrow query interface passed into the simulation:

```java
public interface CollisionSpace {
    List<Aabb> boxesIntersecting(Aabb region);
}
```

`voyager-platform` implements it against the Minestom instance. Tests implement it from
the world slice recorded in the trace fixture. The physics stays a pure function: the
world is handed to it, never looked up.

### Signature

```java
FlightState tick(FlightState previous, FlightInput input, CollisionSpace space);
```

`FlightState` and `FlightInput` are records. No hidden state, no field mutation.

### Fidelity rules

**Numeric types mirror Vanilla exactly.** Vanilla holds rotation as `float`, position and
velocity as `double`. Computing angles in `double` because it is "more accurate" produces
reproducible drift. Every place Vanilla narrows to `float` the port narrows too.

**Vanilla's edge behaviour is replicated, including its missing guards.** The current
implementation adds an `hLook > 1e-8` guard at step 6 that Vanilla does not have —
Vanilla divides unguarded. Well-intentioned, and exactly the class of divergence trace
tests exist to catch. Where Vanilla yields NaN, the port yields NaN; the platform layer
refuses to send it. Safety belongs at the boundary, not inside the formula.

### Step decomposition

Steps 2-10 are `@FunctionalInterface` units in an enum-defined order, with an optional
snapshot after each. The purpose is diagnostic: a trace mismatch then reports
"drag step diverges from tick 412" rather than "position wrong at tick 412". Without it,
debugging means bisecting a nine-stage nested formula by hand.

### Trace acceptance

Recording is **server-side on a real Vanilla 26.2 server**, not via a client mod. A
recorder logs, per tick, the incoming movement and rotation packets, the relevant world
slice, and firework events. This is sufficient because the client's position sequence is
exactly the quantity the plausibility check later measures against — the test targets the
reality that matters in production. A client mod would cost several times more.

Fixtures live in `voyager-physics/src/test/resources/traces/`, each with metadata for
Minecraft version and initial state.

Acceptance thresholds (initial assumptions, to be confirmed or corrected against the
first real traces):

- per-tick position deviation < `1e-6` blocks
- cumulative drift over 200 ticks < `0.01` blocks

Flight profiles to cover: steady glide; climb into stall; dive and pull-out; single
firework boost; chained firework boosts; pitch at ±90°; glancing wall collision; landing.

### Explicitly not here

Ring collision is game geometry, not Vanilla replication, and belongs to `voyager-race`.
Mixing them would dilute "this module is Vanilla-conformant" into "partly conformant".

## Race core (`voyager-race`)

### ECS at the tick layer only

The current tree never settled this: cup progression exists both as an ECS component
(`CupProgressComponent`) and as a service (`CupFlowServiceImpl`), the latter never
instantiated and kept alive only by its own ten tests.

The rule for the rebuild:

- **ECS handles what happens every tick** — advancing flight state, detecting ring
  passes, updating HUD values, counting down boost cooldowns.
- **Everything at event boundaries is a plain service** — cup advancement, scoring,
  persistence, ranking. A cup does not advance twenty times per second.

### Components are records

Without exception. Components are replaced, not mutated. Cost: roughly 50 players × 6
components × 20 TPS = 6,000 small objects per second, which is negligible for ZGC. If
profiling later identifies a specific component as hot, that one becomes a mutable class
with justification in an ADR — not preemptively.

### Phases

Xerus is Minestom-bound and therefore may not appear in `voyager-race`.

`voyager-race` owns the state machine as pure domain: which transitions exist, when a
race ends, what happens on completion, how a practice retry loops back.
`voyager-platform` drives it with Xerus for ticking, timers and delays.

Cost: one thin adapter. Benefit: a complete race can be played through in a JUnit run
with no server. The Lobby→Game→End end-to-end test is missing today precisely because it
was impossible without one.

### Ring collision

Segment-plane intersection against the ring disc, hand-written, tested, **once**. Ten
lines of vector arithmetic do not justify a dependency. The current tree has it twice —
`plugins/game` via commons-geometry, `server` hand-written — and the two disagree at the
edges.

### One format, one model, one loader

`voyager-api` defines map, cup, ring and boost configuration. `voyager-race` loads them.
Today `BoostConfig` (server) and `BoostConfigDTO` (`shared/common`) carry the same three
fields, boxed in one and primitive in the other, and the DTO's javadoc links backwards to
a server class.

### Design rules, concretely

- Domain interfaces are `sealed … permits BaseX` with a `non-sealed abstract` base:
  `ScoringStrategy`, `RaceRule`, `RingEffect`.
- Registries are `sealed` with a static `create()` and exactly one `Default*`
  implementation over `ConcurrentHashMap`; returned collections are `@Unmodifiable`.
- Factories are `abstract` with a private constructor, `@ApiStatus.Internal`, and
  `@Contract` on every factory method.
- Every package has `package-info.java` with `@NotNullByDefault`. The current `server`
  module has none across sixteen packages.
- Enums with an external representation cache `VALUES` and provide `byName()`. This
  applies immediately to `RingType`, which is parsed from JSON today with no lookup.
- Errors throw domain exceptions. The current `server` module defines none, which is why
  its ArchUnit exception rule passes without checking anything.
- The entity factory is injectable through a `@FunctionalInterface` creator so tests can
  substitute it. Today `GameEntityFactory` and `GamePhaseFactory` are called statically
  and cannot be replaced; `GamePhaseFactory` has five overloads of `createGamePhases`,
  i.e. telescoping instead of a builder.

## Platform layer (`voyager-platform`)

The only module importing `net.minestom.*`. It contains the `Vec3`↔`Vec` conversion, the
`CollisionSpace` implementation, event registration, the Xerus tick driver, world
management via the Anvil loader, HUD output and the velocity exit. It makes no game
decisions; it translates.

### Velocity authority

Nobody sets velocity during normal flight. The client flies; the server simulates
silently alongside. Velocity is emitted only for external forces: firework boost, ring
BOOST/SLOW, out-of-bounds reset. This matches Minestom, which deliberately discards the
physics result for players and leaves the client authoritative.

The velocity exit is a **single class**. `Vec3` cannot hold NaN, and the boundary
conversion additionally asserts finiteness before anything reaches Minestom.

### Flight detection

Use the real signals: `entityMeta.isFlyingWithElytra()`, `PlayerStopFlyingWithElytraEvent`
and the client action `START_FLYING_WITH_ELYTRA`. The current code uses `isOnGround()` as
a landing proxy, with a comment claiming Minestom exposes no `isGliding()`; that is not
accurate.

26.2 removes `PlayerStartSneakingEvent` and `PlayerStopSneakingEvent`. Any flight-entry
logic depending on sneak breaks hard.

### Plausibility check

Each tick yields a prediction; the client reports a position. Comparison is not per-tick
and absolute but against an error budget over a window — short-term deviation is normal
(packet batching, latency, rotation quantisation), but accumulated error over the window
must stay below threshold.

On breach the run is **invalidated, the player is not moved**: record and cup points are
discarded, the event is logged, the flight continues undisturbed. Rubber-banding in a
flight game destroys the feel the entire physics effort exists to protect.

**For v1 the check runs in log-only mode.** It is armed only once real runs show how the
error actually distributes for clean players. GrimAC — the strongest existing server-side
Vanilla movement replication — is known to drift on high-speed elytra manoeuvres, which
is exactly the regime a racer occupies. Thresholds set theoretically would invalidate the
best pilots' runs.

### Known Minestom hazards to design around

- Issue \#2017 — players fall below the map on instance switch. Directly affects
  map-to-map cup transitions.
- Issue \#2267 — a periodic auto-sync tick resets velocity. PR \#1426 introduced a
  configurable velocity update interval as the mitigation.
- Issue \#1335 — NaN velocity poisoning, addressed by the `Vec3` invariant.

## Persistence (`voyager-persistence`)

Hibernate ORM with HikariCP over MariaDB, schema managed by Flyway. Repositories follow
the sealed-interface pattern. The module knows `voyager-api` only; it must not reference
race or platform types.

## Setup (`voyager-setup`)

The setup server becomes a Minestom application, removing Paper from the project and
unblocking Java 25 everywhere.

A side effect worth stating: research flagged Anvil format compatibility between a Paper
1.21.5 setup server and a Minestom 26.2 game server (data version 4903) as the single
largest unverified risk of the version upgrade. With setup on Minestom that boundary
disappears — both sides read and write the same format with the same library.

**Open risk:** FastAsyncWorldEdit has no Minestom equivalent. Map editing must be
redesigned. This requires its own research epic before E6 is planned and is deliberately
not resolved in this document.

## Testing strategy

| Layer | Approach |
|---|---|
| Physics | Trace replay against recorded Vanilla flights; plain JUnit, no server instance |
| Race | Ordinary unit tests; a full race runs without a server |
| Platform | Integration tests with Cyano, the organisation's JUnit 5 Minestom extension |
| Architecture | `voyager-fitness`, ArchUnit, no `allowEmptyShould` |
| Load | Tick-budget measurement from E4 onward, not at the end |

MockBukkit is dropped along with Paper.

## Delivery plan

Both module trees coexist; CI builds both; `main` stays buildable throughout.

| Stage | Content | Done when |
|---|---|---|
| E1 | `buildSrc` conventions, `voyager-api`, `voyager-fitness` | ArchUnit imports every module; no rule runs empty |
| E2 | Vanilla recorder, trace fixtures, `voyager-physics` | Trace suite green within tolerance |
| E3 | `voyager-race`: state machine, rings, scoring | Full race playable without a server |
| E4 | `voyager-platform` + `voyager-server` | **First flyable build** |
| E5 | `voyager-persistence` | Records and profiles survive restart |
| E6 | `voyager-setup` on Minestom | A map is configurable without Paper |
| E7 | Cut-over | Old tree removed; Java 25 everywhere |

E2 precedes anything playable by design. If Vanilla parity turns out to be unreachable,
that must surface in weeks, not after a cup system exists.

**Cut-over gate:** E4 reached and the trace suite green. Everything from E5 onward may
follow the cut. The rebuild does not need to catch up with the current Java tree before
replacing it.

**Removed at cut-over:** `server/`, `plugins/game/`, `plugins/setup/`, all four
`shared/*` modules, and with them roughly 34 tests that exclusively cover dead code
(`GameLoopSystemTest`, `GameSessionTest`, `CupFlowServiceTest`, `CupScoringTest`).

## Documentation

A fresh ADR series starting at `0001` for the new stack; existing ADRs move to
`docs/decisions/archive/` as historical context. `docs/migration/status.md` is replaced,
not patched — it has not been updated since the commit that created it and is
substantially inaccurate.

CI, Release Please and Renovate are untouched.

## Risks and open questions

| Risk | Impact | Resolution |
|---|---|---|
| FAWE has no Minestom equivalent | Blocks E6 | Own research epic before E6 planning |
| `air_drag_modifier` may alter the 26.2 elytra drag path | Drag constants wrong, all tracking drifts | Decompile check of `LivingEntity.travel()` in 26.2, before E2 completes |
| Trace tolerances set too tight or too loose | False failures, or parity claimed without proof | Calibrate against the first real traces; thresholds above are assumptions |
| Plausibility thresholds reject legitimate fast pilots | Valid records discarded | Log-only in v1; arm only on measured distributions |
| Minecraft 26.3 ships during the rebuild | Possible double migration | Minestom is confined to `voyager-platform`; re-check `releases.atom` at E4 |
| Vanilla 26.2 recording setup is more work than estimated | E2 slips, and E2 gates everything | Prototype the recorder before committing to E2 scope |
| Server-side recording lacks the client's internal velocity | Some divergence classes invisible | Accepted: position sequence is what production measures too |

## Evidence

Findings from the audit of the current tree that motivated D1.

**Design rules:** zero of the ten mandatory rules in `CLAUDE.md` are fully satisfied in
the `server` module; three are partial, seven unmet. No sealed interfaces, no
`package-info.java` in sixteen packages, no `@FunctionalInterface`, no domain exceptions,
no provider or registry, no `@ApiStatus.Internal`.

**Dead parallel stack:** `GameLoopSystem`, `GameSession`, `CupFlowServiceImpl` and
`CupScoring` are never instantiated outside their own tests. `GameLoopSystem` says so in
its own javadoc. The gameplay exists twice.

**Triplicated spline logic:** `shared/spline/SplineGenerator` (whose javadoc claims to be
the only place that knows the algorithm), `shared/common/utils/SplineAPI`, and
`SplineVisualizationSystem`. `server` does not even depend on `:shared:spline`.

**Duplicated `Simple*` pattern** inside `plugins/game`: `CupSystem`/`SimpleCupSystem`,
`GameStateSystem`/`SimpleGameStateSystem`, `SplineSystem`/`SimpleSplineSystem`,
`WorldComponent`/`SimpleWorldComponent`, and more.

**Ineffective architecture tests:** ArchUnit scans only the `server` test classpath.
Rules documented for `shared/conversation-api`, `shared/spline` and both plugins never
run. `allowEmptyShould(true)` lets several rules pass vacuously. A `DependencyInjectionTest`
was lost when the former `fitness` module was dropped from `settings.gradle.kts`.

**Stale documentation:** `docs/migration/status.md` has one commit in its history and
misstates the module layout, the test count (49 claimed, 212 actual) and the status of
several milestones. `CLAUDE.md` references a `shared/phase` module that does not exist,
a Minestom version that does not match `settings.gradle.kts`, and build commands for
tests that do not exist. Two files link to
`docs/decisions/0002-elytra-flight-client-authority.md`, which was never written.

**Unimplemented accepted ADRs:** practice mode exists only as an enum value —
`GamePhaseFactory` produces the same series for RACE and PRACTICE; `CyclicPhaseSeries`
(ADR-0008) appears nowhere; the HUD renderer split (ADR-0009) was never done.
