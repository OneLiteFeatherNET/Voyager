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
| D10 | `io.airlift:guice:10` for dependency injection, annotations confined to the composition roots | Upstream Guice runs on Java 25 but is unmaintained; the fork drops ASM and `Unsafe` entirely |
| D11 | No legacy data import; the rebuild starts with an empty database | Greenfield means greenfield — player history from the 2023 build is not carried over |
| D12 | `CLAUDE.md` is superseded by this specification | It describes a tree that no longer matches reality; the design rules live here now |

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

## Design principles

The ten mandatory rules in `CLAUDE.md` govern shape — what a factory looks like, where an adapter
lives. They say nothing about whether the decomposition underneath is sound. This section states the
principles that decide that, and — where a principle needs a mechanism to be more than an intention —
names it.

**Single responsibility** is the strongest lever in this design and shows up in three places: the
split between the tick layer and the event boundary, the velocity exit as exactly one class, and
`CollisionSpace` separating "know the world" from "compute movement". The counter-example is in the
tree being replaced: `HudComponent` renders itself, and the accepted ADR that would have split
renderer from state was never implemented.

**Open/closed** carries a real tension worth naming rather than glossing. `sealed … permits`
deliberately *closes* a hierarchy. What is open is the `non-sealed Base*` class — the controlled
extension point — not the interface. Genuinely open for extension: scoring strategies, ring effects,
and the tick step list, where a future version delta becomes a different list rather than an edit to
existing steps. Deliberately not open: the module graph.

**Liskov substitution** needs a mechanism or it is prose. Each `sealed` hierarchy has an **abstract
contract test class** that every implementation must pass. "A `ScoringStrategy` may not throw for
inputs a sibling accepts" then fails as a red test rather than as a review opinion. ArchUnit cannot
check behavioural substitutability; contract tests can, and they are the reason the hierarchies are
allowed to exist at all.

**Interface segregation** has its model in `CollisionSpace`: one method, defined from the consumer's
need rather than the provider's capability. That is the rule for every interface in the rebuild.

**Dependency inversion** is what the module graph is for. `voyager-physics` depends on the
`CollisionSpace` abstraction, not on Minestom; game logic depends on `voyager-api` interfaces;
persistence sits behind ports. `voyager-server` is the composition root and the only place that knows
every concrete type.

**Patterns deliberately used:** Strategy (scoring), Adapter (platform), Abstract Factory (rule 2),
Registry/Provider (rule 3), State Machine (race progression), Template Method (`Base*` step
decomposition), Decorator (the recording simulator that produces trace snapshots), Composition Root.

**Patterns deliberately avoided:** Singleton and static mutable state — precisely what the current
static factories are, and why they cannot be substituted in tests; Service Locator, which hides
dependencies that dependency inversion exists to expose; and generic `*Manager` classes, which are a
name for "everything landed here". Patterns are means. Where a method suffices, no interface is
introduced.

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

### Package root

Every module of the rebuild places its packages under `net.elytrarace.voyager..` — `voyager-api`
at `net.elytrarace.voyager.api`, `voyager-platform` at `net.elytrarace.voyager.platform`, and so
on. The base package stays `net.elytrarace`.

The sub-root is not cosmetic. The tree being replaced already owns `net.elytrarace.api`
(`shared/conversation-api`, `shared/database`), `net.elytrarace.server` (`server/`) and
`net.elytrarace.setup` (`plugins/setup`). A rule written as `resideInAPackage("net.elytrarace.api..")`
would silently span both trees the moment they share a classpath, and the old side violates several
of these rules. Scoping the rebuild to its own sub-root keeps every fitness rule meaning what it
says until E7 deletes the old tree.

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

### Dependency injection

`io.airlift:guice:10`, pinned exactly. Package names are unchanged (`com.google.inject.*`); the fork
is a coordinate move, not an API change.

Upstream `com.google.inject:guice:7.0.0` was evaluated and rejected — but not for the reason usually
given. It **does** run on Java 25: the `Unsupported class file major version 69` that ASM raises is
caught inside `LineNumbers` and logged once as a warning, so only source locations in error messages
degrade. The same holds for JDK 26 and `Unsafe`: `UnsafeClassDefiner` catches the failure and falls
back to `ChildClassDefiner`, costing the fast hidden-class definer rather than the process. The
rejection is a maintenance judgement: no release since 2023-05-12, two open Java-25 issues whose
every comment is from a non-maintainer, and a master branch still pinning ASM 9.5.

The fork removes both failure modes at the root — line numbers come from the JDK's own
`java.lang.classfile` API, and there is no `HiddenClassDefiner` because there is no bytecode
generation. It is exercised in production by Trino on JDK 25. The cost is that **AOP is removed**;
`bindInterceptor` throws. Voyager does not intercept — ECS systems are registered explicitly — so
this is a non-cost here, and a fitness rule keeps it that way.

**DI annotations do not appear outside the composition roots.** Domain classes in `voyager-physics`,
`voyager-race` and `voyager-persistence` have ordinary constructors and no `@Inject`, no
`@Singleton`, no `jakarta.inject` import at all. Wiring happens in explicit `@Provides` methods in
Guice modules that live in `voyager-server` and `voyager-setup`. This keeps every domain class
constructible with `new` in a test, keeps the container swappable, and means a decision to drop DI
later touches two modules rather than eight.

`Stage.PRODUCTION` is mandatory: eager singletons and upfront error checking mean a wiring mistake
fails at boot rather than lazily initialising something inside the tick loop.

`Multibinder` is **not** used for the ECS system pipeline. Its iteration order is documented as
consistent only within a single module, and system order in a fixed-step simulation is a correctness
property, not a detail. The pipeline is bound as an explicit ordered `List`.

Fitness rules: no class outside the composition roots may reference `com.google.inject..` or
`jakarta.inject..`; no code may call `bindInterceptor`; `voyager-api` references neither.

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

Acceptance thresholds, **measured** against all nine recorded profiles in E2b Task 7
(the `1e-6` per tick and `0.01` cumulative this section previously carried were stated
assumptions, and both were too loose — by six and two orders of magnitude):

- per-tick position deviation **exactly `0`** blocks
- cumulative drift over a whole trace **exactly `0`** blocks

Every comparable tick of every profile reproduces the recorded position, velocity and
`onGround` flag bit-for-bit, both as a one-step residual and across a free-running replay
of the whole trace. Per-profile figures, the two derived scope rules and the one recording
defect that still limits the velocity comparison are in
[docs/reference/elytra-physics-26.2.md](../../reference/elytra-physics-26.2.md#measured-parity-e2b-task-7).

Calibrate only against the **one-step residual** — from the recorded state at tick *k*,
advance exactly one tick, compare against the recorded state at *k+1*. A free-running
replay measures the seeding and the formula at once and turns a single slip into a long
decaying tail: a fixture that is bit-exact step by step still showed `8.2e-02` of
free-running drift while the recorder's first-tick transient was unfixed. Keep the
free-running replay under its own bound as well, never as the only measurement.

Flight profiles to cover: steady glide; climb into stall; **sustained turn**; dive and
pull-out; single firework boost; chained firework boosts; pitch at ±90°; glancing wall
collision; landing. Nine, not eight: the sustained turn was added in E2a because the other
eight all fly at yaw `0`, where `lookAngle.x` vanishes and the whole x axis drops out of
the tick — a sign flip worth `1.9e-02` blocks per tick survived the module's entire suite
until a non-zero-yaw fixture existed.

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

Hibernate ORM 7 with HikariCP over MariaDB, schema owned by Flyway. The module depends on
`voyager-api` and nothing else. It contains no race types, no Minestom types, and it exports no
Hibernate types.

The current tree does not have this boundary. `shared/database` compiles against `shared:common`
and its `GameResultEntity` imports `net.elytrarace.common.game.mode.GameMode` directly, so the
persistence module knows the game. Its repositories return `CompletableFuture` from
`ForkJoinPool.commonPool()` (no executor is passed to `supplyAsync`), timestamps are written with
`LocalDateTime.now()` in server-local time, and `MapRecordRepositoryImpl.saveOrUpdateRecord` reads
the current record and writes it back in two statements — a lost update whenever two players finish
within the same transaction window. None of that is carried over.

### The governing decision: no persistence context, no associations

Two rules decide almost everything else in this module.

**The module never opens a stateful `Session`.** All work goes through `StatelessSession`. There is
no persistence context, no dirty checking, no first-level cache and no proxy generation. A Hibernate
proxy cannot leak into the game loop because the module never creates one.

**No entity declares an association.** No `@ManyToOne`, `@OneToMany`, `@OneToOne` or `@ManyToMany`
anywhere. Entities hold raw foreign-key columns (`long mapId`, `UUID playerId`) and every join is
written explicitly in HQL. N+1 is not mitigated in this design; it is structurally impossible,
because there is no association to walk lazily.

Both are enforceable exactly:

| Rule | ArchUnit form |
|---|---|
| No stateful sessions | no method calls `SessionFactory.openSession/inSession/fromSession/createEntityManager` |
| No associations | no field in `..persistence.entity..` carries any of the four association annotations |
| No entity escapes | no public method outside `..persistence.entity..` declares a parameter or return type from it |
| No JPA in the API | `voyager-api` must not reference `jakarta.persistence..` or `org.hibernate..` |

### Entity model

Cups and maps stay file-defined; `voyager-api` owns their format and `voyager-race` loads them. The
database holds a *registry* of map and cup identities, not their geometry, so results carry a
compact `INT` foreign key instead of repeating map names in every row as `game_results` does today.
Renaming a map becomes a display-name change rather than a mass update.

| Table | Purpose | PK | Notable columns |
|---|---|---|---|
| `player` | Identity only, near-static after first join | `player_id UUID` | `last_known_name`, `first_seen`, `last_seen` |
| `player_statistics` | Lifetime counters, write-hot | `player_id UUID` (shared PK) | `races_played`, `races_won`, `rings_passed`, `cups_won`, `total_flight_ms` |
| `cup` | Registry of cup identities | `cup_id SMALLINT` | `cup_key UNIQUE`, `display_name` |
| `map` | Registry of map identities plus layout generation | `map_id INT` | `map_key UNIQUE`, `cup_id`, `display_name`, `layout_version` |
| `cup_session` | One run of a cup | `cup_session_id BIGINT` | `cup_id`, `game_mode`, `started_at`, `finished_at` |
| `race_session` | One run of one map | `race_session_id BIGINT` | `map_id`, `layout_version`, `cup_session_id NULL`, `game_mode`, timestamps, `player_count` |
| `race_result` | One player's outcome in one race | `race_result_id BIGINT` | `race_session_id`, `player_id`, `finish_position`, `finish_time_ms NULL`, `ring_points`, `position_bonus`, `total_points`, `rings_passed`, `medal_tier NULL`, `validation_state`, `plausibility_error NULL` |
| `cup_result` | One player's outcome in one cup | `cup_result_id BIGINT` | `cup_session_id`, `player_id`, `final_position`, `total_points`, `maps_finished`, `best_time_ms NULL` |
| `map_personal_best` | One row per (map, layout, mode, player) — the leaderboard | `map_personal_best_id BIGINT` | `map_id`, `layout_version`, `game_mode`, `player_id`, `best_time_ms`, `race_result_id`, `achieved_at` |

Four points deserve justification.

**There is no `map_records` table.** The current V4 stores the single fastest time per map in its
own table. That value is `ORDER BY best_time_ms LIMIT 1` against an index that already exists in
exactly that order. A second table holding a derived value is denormalisation without a measured
need — and it is the table carrying today's lost-update bug. The map record is a query.

**Records are scoped by `layout_version`.** If a map's ring layout changes, previously set times are
no longer comparable. `map.layout_version` derives from the map configuration's content hash and is
bumped when it changes; the leaderboard filters on it. Old rows are retained and simply stop
appearing. Without this, the first balance pass on a map silently corrupts every record it touches.

**`validation_state` and `plausibility_error` are persisted from day one.** The plausibility check
ships in log-only mode and can only be armed on measured distributions — those distributions have to
live somewhere queryable. `validation_state` is `VALID` for every row in v1; the accumulated window
error is recorded regardless. Threshold calibration then becomes a percentile query rather than a
log-scraping exercise.

**`player` and `player_statistics` split 1:1 on a shared primary key.** The profile row is read on
every join and written almost never; the statistics row is written at the end of every race.
Splitting keeps the row-lock path off the identity row.

Timestamps are `DATETIME(6)` holding UTC — not `TIMESTAMP` (2038) and not server-local time (DST).
Durations are `INT UNSIGNED` milliseconds, capping at 49 days and halving the leaderboard index key
against a `BIGINT`.

Two indexes on `map_personal_best` are both required and are different orderings of the same
columns: a unique index on `(map_id, layout_version, game_mode, player_id)` enforces "one best per
player per map per mode" and drives the upsert, while `(map_id, layout_version, game_mode,
best_time_ms)` drives top-N, rank-by-range-count and the map record. Further indexes serve
"my last N races" (`race_result (player_id, created_at DESC)`), the race scoreboard, the legs of one
cup in play order, and cup standings.

### The ORM/domain boundary

Hibernate entities are mutable classes with a no-argument constructor; domain types are immutable
records. The boundary between them is not a mapping layer sprinkled through the repositories — it is
a property of which code path is taken.

**The read path never materialises an entity.** Reads are HQL constructor expressions that
instantiate a `voyager-api` record directly:

```java
static final String TOP_BY_MAP = """
    select new net.elytrarace.voyager.api.persistence.LeaderboardEntry(
        pb.playerId, p.lastKnownName, pb.bestTimeMs, pb.achievedAt)
    from MapPersonalBestEntity pb
      join PlayerEntity p on p.playerId = pb.playerId
    where pb.mapId = :mapId
      and pb.layoutVersion = :layout
      and pb.gameMode = :mode
    order by pb.bestTimeMs asc
    """;
```

The result is a list of records, fully populated, with no session affinity. There is no entity to
detach, no proxy to initialise and no `LazyInitializationException` to be had.

**The write path uses entities, and they never leave the transaction.** A repository receives an
immutable command record, constructs entities inside the transaction, inserts them, and returns.
Entity classes are package-private to `..persistence.entity`.

**Domain records are projection-shaped.** HQL constructor expressions require exact parameter types,
so a record's canonical components use primitives that map straight from columns; richer accessors
are derived (`bestTimeMillis` as `int`, with a `bestTime()` returning `Duration`). This is the one
place where persistence needs shape a domain type, and it is a cheap price for eliminating the
mapping layer entirely.

**Entities are the documented exception to rule 4.** Components and DTOs are records without
exception; entities cannot be, because Hibernate requires mutability and a no-argument constructor.
`voyager-fitness` scopes the record rule to `..component..` and adds a positive rule instead: every
class in `..persistence.entity..` is `@Entity`, non-final, has a no-argument constructor and
declares no association. Stating the exception precisely beats an unscoped rule that quietly does
not apply.

### Repositories

Ports live in `voyager-api` and are **not sealed**. This corrects the "repositories follow the
sealed-interface pattern" assumption: a sealed interface's permitted subtypes must live in the same
module or package, and `voyager-api` and `voyager-persistence` are neither — `sealed … permits
DefaultPlayerRepository` across two Gradle modules does not compile. More fundamentally, a port
exists to be reimplemented: `voyager-race` tests run against in-memory fakes, and a caching decorator
is plausible later. Sealing would forbid exactly what the port is for.

Rule 3 applies where it was written to apply — the provider:

```java
public sealed interface PersistenceProvider permits DefaultPersistenceProvider {
    @Contract(pure = true)
    static PersistenceProvider create(PersistenceSettings settings) {
        return new DefaultPersistenceProvider(settings);
    }
    PlayerProfileStore players();
    RaceArchive races();
    CupArchive cups();
    LeaderboardQuery leaderboards();
    void shutdown(Duration drainTimeout);
}
```

Every write method returns `void`; see tick safety below.

**Jakarta Data is not used.** Hibernate 7 implements Jakarta Data 1.0 through its annotation
processor over `StatelessSession` — the session type this design already chose — so the temptation
is real. Hand-written repositories win for five reasons in descending weight. Jakarta Data 1.0 has
no asynchronous return type, and the entire tick-safety design rests on futures and fire-and-forget
writes, so every generated method would need a hand-written wrapper. The generated class is named
`<Repository>_` and can be neither a `final class Default*` nor a `permits` target, so rule 3 would
need a blanket exemption for generated code. The write path is deliberately native DML that
`@Insert`/`@Update` cannot express. The value scales with method count and ours is about ten. And it
puts an annotation processor on the critical path of a module compiled with `--release 25`.

The genuine advantage it offers — compile-time query validation — is recovered by other means: every
HQL string is a `@NamedQuery`, so a malformed query fails at `SessionFactory` bootstrap rather than
on first use, and a Testcontainers test executes each named query against a Flyway-migrated schema.
That validates against the real schema, which is strictly more than the processor checks.

**Atomic writes.** Both hot mutations are single statements — no read-modify-write, no lost updates:

```sql
INSERT INTO map_personal_best
       (map_id, layout_version, game_mode, player_id, best_time_ms, race_result_id, achieved_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
    race_result_id = IF(VALUES(best_time_ms) < best_time_ms, VALUES(race_result_id), race_result_id),
    achieved_at    = IF(VALUES(best_time_ms) < best_time_ms, VALUES(achieved_at),    achieved_at),
    best_time_ms   = LEAST(best_time_ms, VALUES(best_time_ms));
```

A finished race is **one transaction**: close `race_session`, batch-insert every `race_result`, one
upsert per finisher, one statistics increment per participant. All or nothing.
`hibernate.jdbc.batch_size=50` plus `rewriteBatchedStatements=true` on the JDBC URL turns fifty
inserts into one multi-row statement — without that URL parameter the driver sends fifty round trips
and Hibernate's batching setting has no visible effect.

### Tick safety

The tick thread never touches JDBC and never waits on a future. `voyager-fitness` enforces the
second half directly: no class in `..race..` or `..platform..system..` may call
`CompletableFuture.get`, `join` or `Future.get`.

**Threading.** Two executors, both platform threads, named by `DefaultPersistenceProvider`:
`voyager-db-read` (fixed, 4) and `voyager-db-write` (single). The single writer gives per-player
write ordering for free — the result insert always precedes the statistics increment — without
partitioning or locks. Nothing uses `ForkJoinPool.commonPool()`.

**Virtual threads are allowed on the IO path but bring nothing here, so they are not used.** The
non-goal above is scoped to the tick path, and the IO path is nominally what virtual threads are
for. Concurrency here is capped by the connection pool, not by threads: a virtual thread parked in
`getConnection()` is not meaningfully cheaper than a platform thread parked there, it just moves the
queue somewhere less observable. Carrier pinning is no longer the argument it once was (JEP 491
removed the `synchronized` case), so this is purely a question of value, and there is none below a
few hundred concurrent IO operations.

**Reads never block the game.** The profile load starts on join and completes whenever it completes.
Absence is modelled in the type system rather than as a null or a future the tick polls:

```java
public sealed interface ProfileSlot {
    record Pending(long requestedTick)   implements ProfileSlot {}
    record Loaded(PlayerProfile profile) implements ProfileSlot {}
    record Failed(String reason)         implements ProfileSlot {}
}
```

Systems pattern-match on it. A player whose profile is still `Pending` sits in the lobby with default
cosmetics; nothing stalls. The profile is not needed until the player leaves the lobby, which is why
this works without a timeout hack.

**Completions re-enter through the tick, not from the IO thread.** An IO callback never mutates ECS
state; it offers a `PersistenceEvent` onto a bounded MPSC queue that the game loop drains at a fixed
point at the start of the tick, under a budget. The budget is what keeps a burst of fifty
completions from consuming the 50 ms tick. A backlog costs latency, never a dropped tick.

**Writes are fire-and-forget with an outbox.** At race end the race service already holds every value
it needs in memory. It builds an immutable `RaceCompletion` and calls `races().submit(it)`, which
returns `void` — persistence failing does not change what happened in the race. The submit is an
`offer()` on a bounded queue; a failed offer is a logged incident, never a block. Failed transactions
go to a retry queue with backoff, and `shutdown(Duration)` drains the queue before the
`SessionFactory` closes. Without that drain, the last race of every restart is lost.

### Query design

There are no associations, so accidental N+1 cannot occur. Two shapes could still go wrong by hand.

**Leaderboard with player names** would be ten rows plus one name lookup each. `TOP_BY_MAP` above is
one query with an explicit ad-hoc join. Expected plan: `pb` as `ref` on the leaderboard index with
`Using index` and no `filesort`, `p` as `eq_ref` on `PRIMARY` for ten rows.

**Cup standings with every map leg** is N+1 nested inside N+1, solved by not traversing: one flat
query returns every result row for the cup ordered by leg and finish position, and the grouping
happens in Java over at most eight maps by fifty players.

**Profile on join is deliberately three queries** in one transaction — identity plus statistics,
recent results, personal bests — not one query with two joined collections, which would produce a
cartesian product.

**Reference data is not queried at runtime.** `cup` and `map` are read once at boot into an in-memory
registry (rule 3) and upserted from the configuration files at the same time. No second-level cache
is configured anywhere in the module.

The four critical queries carry an `EXPLAIN` assertion in the integration suite, with a mechanical
acceptance criterion that cannot drift: **no `Using filesort`, no `Using temporary`, and no
`type: ALL` on any table above a thousand rows.**

### Flyway

**One migration, `V1__baseline.sql`, and no baseline stamping.** The new schema has no deployment and
therefore no history worth preserving. It stays a single script until the first real environment runs
it; from then on it is append-only and applied scripts are never edited.

The existing `V1`–`V4` under `shared/database` are **not carried forward**. They describe different
table names, mixed-case columns, a records design this specification removes, and in `V3` a repair of
a Hibernate-7 UUID mapping mistake the new schema does not make. They stay with `shared/database` and
are deleted at E7.

| Flyway setting | Value | Why |
|---|---|---|
| `baselineOnMigrate` | `false` | The current code sets `true` with `baselineVersion=1`, silently skipping `V1` against any non-empty database |
| `validateOnMigrate` | `true` | Checksum drift on an applied script is a deployment error |
| `cleanDisabled` | `true` | `clean` is enabled by default and drops the schema |

Flyway runs to completion before the `SessionFactory` is built, and `hbm2ddl.auto=validate` in
**every** environment including local development. The current `DatabaseConfig.DEFAULT_HBM2DDL =
"update"` contradicts ADR-0011 in its own javadoc and is the reason `V3` had to exist.

The new schema also gets its own database name (`voyager`), so old and new can run side by side
through E5–E7 and a rollback is a configuration change rather than a restore.

The single highest-value test in the module: start a MariaDB Testcontainer, run `migrate()`, build
the `SessionFactory` in `validate` mode, and execute every named query. That one test catches
entity/schema drift, malformed HQL and missing indexes before CI goes green.

### HikariCP

The workload is not what pool-sizing intuition suggests. Fifty concurrent players do not produce
fifty concurrent transactions — a finished race is *one* transaction carrying all fifty results, and
the design permits at most five concurrent database operations by construction.

| Setting | Value | Rationale |
|---|---|---|
| `maximumPoolSize` | `10` | 4 readers + 1 writer + headroom; the application cannot demand more |
| `minimumIdle` | `10` | Fixed-size pool; connection establishment must not appear in the race-end burst |
| `connectionTimeout` | `5000` | Fail fast; a 30 s stall would fill the bounded write queue before surfacing |
| `maxLifetime` | `1500000` | Below MariaDB `wait_timeout` and any load-balancer idle cut |
| `keepaliveTime` | `120000` | Prevents NAT silently killing idle connections between quiet periods |
| `leakDetectionThreshold` | `10000` dev/staging, `0` production | Catches an unclosed `StatelessSession` |
| `transactionIsolation` | `READ_COMMITTED` | MariaDB defaults to `REPEATABLE_READ`, whose gap locks on the personal-best unique-key range serialise concurrent finishers for no benefit |

JDBC URL parameters are part of the configuration, not an afterthought:
`rewriteBatchedStatements=true`, `cachePrepStmts=true`, `useServerPrepStmts=true`,
`prepStmtCacheSize=250`, and `connectionTimeZone=UTC` with `forceConnectionTimeZoneToSession=true`,
so no session-local timezone can reinterpret a stored `DATETIME`.

Hikari's `pendingThreads` gauge is exported alongside the tick-budget metrics from E4. It should be
zero; a non-zero value means the design's own concurrency assumption is wrong, not that the pool is
too small.

### Design rules in this module

Rule 7 gets two concrete uses: a `SessionFactoryCreator` so tests substitute a Testcontainers
factory, and a `PersistenceClock` (`Instant now()`) so timestamps are deterministic instead of
`LocalDateTime.now()` scattered through repositories. Rule 9's exceptions are
`PersistenceUnavailableException`, `SchemaValidationException` and `SubmissionRejectedException` —
note the deliberate avoidance of `PersistenceException`, which collides with
`jakarta.persistence.PersistenceException`.

**One convention amendment is required and must be approved, not assumed:** rule 8's `*Adapter`
naming currently covers Gson deserializers only. This module extends it to row-to-record adapters in
`..persistence.adapter`. That is a one-line change to `CLAUDE.md`.

### Defects in the current layer that this section fixes

Lost update on map records; `shared/database` importing `shared/common` game types in violation of a
documented rule that no ArchUnit test enforces; blocking JDBC on `ForkJoinPool.commonPool()`;
`hbm2ddl.auto` defaulting to `update` against ADR-0011; `baselineOnMigrate=true` silently skipping
`V1`; server-local timestamps with no timezone forced on the JDBC session; and a dangling
`@OneToMany` on `ElytraPlayerEntity.gameResults` that is mapped, never fetched, and is a
`LazyInitializationException` waiting for its first caller.

## Configuration

Configuration is currently the least-defined part of the project. In the tree being replaced there is
no configuration layer at all: values are read wherever they are needed, mostly from system
properties in static initialisers — `MapProvider.MAPS_FOLDER`, `CupProvider.CUPS_FOLDER`,
`GuidePointStore`, `RunMode.getRunModeFromProperty()`, `GameServiceImpl.DEV_MODE`. A value read in a
static initialiser is frozen at class-load time and cannot be varied by a test, which is why none of
these have one. `DatabaseConfig.fromEnvironment()` is the only place that resolves in layers, and it
does so with its own private helper, silently swallowing a malformed integer as a fallback.

`grep -ri cloudnet` over the whole source tree returns **zero hits**, although `CLAUDE.md` names
CloudNet v4 as the primary deployment target. Everything in the CloudNet subsection below is new
work, not a description of something that exists.

### There is no such thing as "the config"

Five different things get called configuration, with five different lifecycles, owners and failure
modes. Behind one mechanism, the slowest-changing one dictates the ergonomics of the fastest.

| # | Kind | Examples | Changes when | Owner | Travels as |
|---|---|---|---|---|---|
| a | Runtime parameters | bind host/port, data and world paths, view distance, log level, run profile | per service start | operator | env + system properties, non-secret file |
| b | Secrets | database user and password | on rotation | operator | environment only |
| c | Gameplay tuning | boost burn ticks, boost cooldown, ring points, medal brackets, plausibility tolerances | per balancing pass | game designer | versioned JSON in the repo |
| d | Content data | map and cup definitions, worlds | per map release | map builder | JSON + Anvil directories from `voyager-setup` |
| e | i18n | `elytrarace_en_US.properties` and siblings | per translation update | translators | classpath resources, optional override directory |

Two boundaries in that table carry most of the weight.

**(c) is source, not configuration.** A boost cooldown and a bracket threshold are part of what the
game *is*. They belong in git, they are reviewed, they are tagged with the release, and a record set
under one set of values is not comparable to a record set under another. They are separated from
Java source only so a designer can change them without a compiler — not so an operator can vary them
per environment. There is no `VOYAGER_BOOST_COOLDOWN_MS` variable and there will not be one; a
per-environment override of a gameplay constant means the staging leaderboard silently measures a
different game.

**(b) has no file layer.** Secrets come from the environment and nowhere else. They are never passed
as `-Dvoyager.db.password=…`: the JVM command line is readable by any local user through
`/proc/<pid>/cmdline`, and CloudNet's own service listings surface the started command. The single
file-shaped exception is the container convention `VOYAGER_DB_PASSWORD_FILE`, naming a path whose
contents are the secret — how Docker secrets and Kubernetes projected volumes deliver values, at the
cost of one branch.

**`voyager-physics` has no configuration of any kind.** Not a tuning file, not an environment
variable, not a constructor parameter beyond `FlightState`, `FlightInput` and `CollisionSpace`. Its
constants are Vanilla's, and a knob on any of them would let a deployment silently break the parity
the trace suite exists to prove. If a value there ever needs to be configurable, that is a defect in
the port, not a missing feature.

### Layering and precedence

Precedence applies to kinds (a) and (b) only. Kinds (c), (d) and (e) load from exactly one place — a
tuning value that could come from four sources is a tuning value nobody can reason about.

The rule: **the narrower the scope of a mechanism, the higher its precedence.**

| Rank | Layer | Scope | Set by |
|---|---|---|---|
| 1 (lowest) | Record constants in code | the artifact | developers |
| 2 | `config/server.json` | the template / image | release pipeline |
| 3 | Environment variables `VOYAGER_*` | the container / node / pod | operator |
| 4 (highest) | System properties `voyager.*`, plus CloudNet's `service.bind.*` | this one process | orchestrator |

A CloudNet template is shared by every service of a task, so it is the broadest non-code layer.
Environment is set once per node or container. System properties are what the orchestrator injects
for this specific process. The same ordering holds under Kubernetes, where layer 4 is simply unused.

Keys map mechanically between layers 2–4: `/db/url` ↔ `VOYAGER_DB_URL` ↔ `voyager.db.url`
(`VOYAGER_A_B` → `voyager.a.b`). The only exceptions are the two keys CloudNet defines and we do not
control.

| Concern | Types | Loader |
|---|---|---|
| (a) runtime | `voyager-api` — `ServerConfig`, `PathsConfig`, `RunProfile` | `voyager-platform` |
| (b) secrets | `voyager-api` — `DatabaseConfig`, `Secret` | `voyager-platform`, consumed by `voyager-persistence` |
| (c) tuning | `voyager-api` — `TuningConfig`, `BoostTuning`, `ScoringTuning`, `PlausibilityTuning` | `voyager-race` |
| (d) content | `voyager-api` — `MapDefinition`, `CupDefinition`, `RingDefinition` | `voyager-race` |
| (e) i18n | — (Adventure) | `voyager-platform` |

`voyager-api` holds every configuration *type* and no loading whatsoever, consistent with its
charter. It must not reference `java.nio.file.Files`.

The layered resolver lives in `voyager-platform`, not `voyager-server`: `voyager-setup` is a second
application with the same CloudNet contract, and duplicating resolution across two composition roots
is how the two drift apart.

`voyager-race` loads (c) and (d) but resolves nothing — it is handed a `Path`, or a `Reader` in
tests, by the composition root. It contains no `System.getenv` and no `System.getProperty`. That is
what keeps a full race runnable in a JUnit run.

The resolver never touches the JVM either. It reads through a functional source, so environment and
system properties are two implementations among several and tests supply a third:

```java
@FunctionalInterface
public interface ConfigSource {
    Optional<String> lookup(String key);
}
```

```java
ConfigResolver resolver = ConfigResolver.create(List.of(
        ConfigSources.defaults(),                              // rank 1
        ConfigSources.json(configDir.resolve("server.json")),  // rank 2
        ConfigSources.environment(),                           // rank 3
        ConfigSources.systemProperties()                       // rank 4
));
```

`ConfigResolver` is `sealed … permits DefaultConfigResolver` with a static `create()` (rule 3);
`ConfigSources` is an `abstract` utility with a private constructor and `@ApiStatus.Internal`
(rule 2). This matters beyond style: Java has no supported way to set an environment variable
in-process, so code calling `System.getenv` directly is untestable — and if it caches in a static
initialiser, untestable even across JVM forks.

### CloudNet v4

CloudNet's wrapper starts the shaded jar as an ordinary `java -jar` process and talks to it in
exactly two ways. Both are easy to miss, and each one missed produces a service that appears to work.

**The bind address arrives as system properties** — not an environment variable, not the command
line, not an API:

```java
String bindHost = System.getProperty("service.bind.host", "0.0.0.0");
int bindPort = Integer.getInteger("service.bind.port", 25565);
```

The fallbacks exist for standalone runs only. A hardcoded bind address yields a service that starts,
reports healthy, and that the node can never route a player to.

**Shutdown arrives as the literal line `stop` on stdin.** CloudNet sends no signal. If nothing reads
stdin, the node has no clean way to ask the process to exit and kills it after a timeout — which in
a racing game means an in-flight run is lost rather than recorded. A daemon thread reads lines from
`System.in` into Minestom's `CommandManager`, and `stop` hands off to a fresh thread:

```java
setDefaultExecutor((sender, context) ->
        Thread.ofPlatform().name("voyager-stop").start(() -> {
            MinecraftServer.stopCleanly();
            System.exit(0);
        }));
```

The separate thread is not defensive style. `stopCleanly()` tears down the very thread reading
console input, so calling it inline from the stdin reader deadlocks instead of exiting and the node
falls back to the kill path anyway. Any non-`Player` sender — that is, CloudNet — may always run it;
players need an explicit permission node.

**Extensions must be bootstrapped explicitly.** CloudNet's Minestom integration ships as a Minestom
*extension* (`CloudNet_Bridge`) with its own classloader, and upstream Minestom no longer has an
extension system. Without `net.onelitefeather:minestom-extensions` on the classpath the bridge never
loads, with no error pointing at the cause. Bootstrapping happens before binding:

```java
ExtensionBootstrap bootstrap = ExtensionBootstrap.bootstrap();
// instances, listeners, commands, configuration already resolved and validated
bootstrap.start(bindHost, bindPort);
```

That library pulls `com.github.Minestom:DependencyGetter` from JitPack transitively, so
`settings.gradle.kts` needs the organisation's proxy alongside the existing repositories, or
resolution fails on a transitive dependency with no visible relationship to CloudNet.

**What stays out of the shadow jar.** `voyager-server` depends on **no** `eu.cloudnetservice.*`
artifact at all. It reads two system properties and one stdin stream; that is the entire contract,
expressible in plain JDK types. Bundling CloudNet's driver or bridge classes produces duplicates
across classloaders, not a working service.

If Voyager later needs to *call* CloudNet — routing a player to a lobby task after a cup, resolving
permissions across the network, reading service snapshots — that is a separate ninth module,
`voyager-cloudnet-bridge`, packaged as its own extension jar and published unshaded. It is the only
module permitted to reference `eu.cloudnetservice..`, every such dependency in it is `compileOnly`,
and its `extension.json` declares `"dependencies": ["CloudNet_Bridge"]` so it loads after the bridge
and shares its classloader hierarchy. The application and the extension exchange only JDK types
declared in `voyager-api`. This module is **not** part of E4 and is deliberately deferred; it is
specified here so the `compileOnly` boundary is decided before someone reaches for it in a hurry.

**Template cut**, following the lifecycle boundary from the first table:

```
Global/default     — network-wide shutdown scripts, nothing Voyager
Minigame/default   — shared minigame tooling
Voyager/default    — voyager-server.jar
                     config/server.json      (a, non-secret only)
                     config/tuning.json      (c)
Voyager/maps       — data/maps/**, data/cups/**   (d)
                     worlds/**
```

Two templates because the jar plus its tuning ships on the release cadence and maps ship on the
content cadence. Merging them means every map fix reuploads the binary and every binary release
risks a stale map set. Templates layer in declared order, so `maps` comes second.

The task runs `staticServices: false` and `autoDeleteOnStop: true` — a race server holds per-match
state and must never inherit the previous match's directory. `environment` is `MINECRAFT_SERVER`;
CloudNet has no `MINESTOM` type, and teaching it one is a node module, not a game project.

### Secrets

Database credentials never enter the repository, the jar, a template, or a task JSON.

**Locally**, `docker/compose.yml` currently carries `voyager-project` as literal username, password
and database name. Tolerable for a compose file bound to localhost provisioning a throwaway
database — but it must not be mirrored as a default in code. Today `DatabaseConfig.DEFAULT_USERNAME`
is `"voyager-project"` and the password default is `""`, so a production deployment that forgets
`VOYAGER_DB_PASSWORD` connects as the development user with an empty password and reports the
failure as a database problem. In the rebuild the compose values move to a gitignored `.env` with a
committed `.env.example`, and `DatabaseConfig` carries **no credential defaults at all**.

**Under CloudNet**, credentials are environment variables on the *node* process, inherited by the
wrapper and passed to each service — a mode-`0600` `EnvironmentFile=` on a systemd node, compose's
`env_file` on a containerised one. Never in `local/tasks/Voyager.json` and never in a template: both
are plain files on the node's disk, readable by anyone with node access and by every operator who
runs `template download`. Task JSON is configuration, not a secret store.

**Under Kubernetes**, a `Secret` via `envFrom.secretRef`, later External Secrets or Vault. Variable
names are identical to the CloudNet ones, so the migration is a manifest change with no code change —
the point of naming them at the application layer rather than the platform layer.

**On a missing secret the server refuses to start.** No degradation, no in-memory fallback that looks
like it works until someone asks where the records went. The distinction that matters is between
absent by decision and absent by accident:

- `VOYAGER_PERSISTENCE=off` is an explicit operator decision. A no-op repository is bound and a
  `WARN` line states that no records will be persisted, on every start rather than once. This is what
  E4 uses before `voyager-persistence` exists.
- `VOYAGER_PERSISTENCE` unset or `required` with a missing or unusable credential is fatal. The
  process exits non-zero before `bootstrap.start(...)`, so it never binds and no player connects to a
  server that cannot record their run.

Falling back to no-op because a *configured* database failed to connect is the failure mode this
distinction forbids.

To make accidental disclosure a type error rather than a review finding, secrets are wrapped, in the
same spirit as `Vec3` refusing to hold NaN:

```java
public record Secret(String value) {
    public Secret {
        Objects.requireNonNull(value, "secret value must not be null");
    }

    @Override
    public String toString() {
        return "***";
    }
}
```

`DatabaseConfig.password()` returns `Secret`. It cannot be interpolated into a log line, a stack
trace, a build-scan tag or an enclosing record's `toString()` by accident; extracting it requires
`.value()`, which is a grep-able and ArchUnit-enforceable act.

### Validation

Invalid configuration must fail at startup, in one place, with a message an operator can act on.
Rule 4 gives the mechanism and rule 9 the exception shape, but a compact constructor alone fails on
the first bad field and hands the operator a stack trace. The two levels are complementary.

```java
public record BoostTuning(int burnDurationTicks, double maxSpeedBlocksPerTick, long cooldownMs) {

    public BoostTuning {
        if (burnDurationTicks <= 0) {
            throw new InvalidConfigurationException(
                    "boost.burnDurationTicks must be > 0, was " + burnDurationTicks);
        }
        if (!Double.isFinite(maxSpeedBlocksPerTick) || maxSpeedBlocksPerTick <= 0.0) {
            throw new InvalidConfigurationException(
                    "boost.maxSpeedBlocksPerTick must be finite and > 0, was " + maxSpeedBlocksPerTick);
        }
        if (cooldownMs < 0) {
            throw new InvalidConfigurationException(
                    "boost.cooldownMs must be >= 0, was " + cooldownMs);
        }
    }
}
```

The compact constructor is the unconditional guarantee: the type cannot exist in an invalid state,
whether it came from a file, a test, or a future admin command. `InvalidConfigurationException`
extends `RuntimeException` and lives in `voyager-api`.

The **loader** does not construct-and-die. It parses each field into a staging structure, accumulates
every failure, and throws once, carrying `ConfigProblem(String key, String source, String message)`.

`source` is what makes a four-layer precedence chain debuggable: `env:VOYAGER_DB_URL`,
`file:config/tuning.json#/boost/cooldownMs`, `sysprop:voyager.bind.port`. An operator who sets
`VOYAGER_BIND_PORT` and sees it ignored because a `-D` overrides it has no other way to find out.

```
Configuration is invalid (3 problems):
  boost.cooldownMs        file:config/tuning.json#/boost/cooldownMs   must be >= 0, was -1
  db.poolSize             env:VOYAGER_DB_POOL_SIZE                    not an integer: "ten"
  db.password             <unset>                                     required when persistence=required
```

One restart per error is an unacceptable operator loop. Note also that `"ten"` is *reported*, not
silently replaced by the default — the current `DatabaseConfig.parseInt` swallows the
`NumberFormatException` and returns the fallback, turning a typo into a production pool size the
operator believes they changed.

Enums parsed from external text follow rule 6 — cached `VALUES`, `byName()` returning `@Nullable` —
and the loader turns a `null` into a `ConfigProblem` listing the accepted values. It does not map to
an `UNKNOWN` member. `RunMode.getRunModeFromProperty()` in the current tree returns `UNKNOWN` for a
misspelled value, so a typo'd run mode runs in neither mode and reports nothing.

Startup ordering is fixed: resolve and validate (a) through (e) **completely**, then initialise
persistence, then `bootstrap.start(...)`. Nothing binds a socket before the configuration is known
good.

Two operator-facing outputs fall out cheaply and are both worth having. `-Dvoyager.config.check=true`
resolves, validates, prints the effective configuration with `Secret` fields rendered as `***` and
each value annotated with its winning source, then exits 0 or 1 without starting a server; it runs in
CI against the compose environment block and the CloudNet task JSON, and as the last step of the
template-push pipeline, so a broken template never reaches a node. The same dump is logged at `INFO`
on every real start — in a four-layer system, "what is this process actually running with" must not
require reasoning about precedence.

### Gameplay tuning and hot reload

Designers want to change balancing without a restart. The naive form — a mutable global that systems
read each tick — is rejected for a reason specific to this game rather than a general distaste for
mutable state.

A race is a measurement. A time, a ring count and a score are meaningful only relative to the boost
strength, cooldown and ring values in force for the whole run. If tuning changes at tick 900 of a
1200-tick race, the record is not slightly wrong, it is uninterpretable — and the plausibility check
is comparing a client trajectory against a prediction whose boost parameters changed underneath it,
which reads as a breach.

The invariant is therefore not "tuning is immutable" but **"tuning is immutable for the duration of a
run"**. That is weaker, more useful, and makes reload safe almost for free:

```java
private final AtomicReference<TuningConfig> active = new AtomicReference<>(initial);

// at race creation, on the tick thread:
TuningConfig snapshot = active.get();
entity.set(new TuningComponent(snapshot));
```

Systems read `TuningComponent` off the race entity; they never read `active`. The only tick-thread
interaction with the reference is one `get()` at race creation — a plain volatile read, no lock, no
torn read, no possibility of two systems in the same tick seeing different values. Races in progress
finish under the snapshot they started with; only the next race picks up the new one.

Reload becomes: parse off the tick thread, construct the full `TuningConfig` — which validates
completely, because the compact constructors do — and on success `active.set(newConfig)`. On failure,
report the accumulated problems to the command sender and leave `active` untouched. A broken tuning
file cannot take down a running server, and a half-parsed one can never be published because the
record cannot be constructed.

Two deliberate restrictions. **No file watching:** reload is an explicit, permission-gated command. A
`WatchService` fires on partial writes and editor temp files, producing a parse error at a moment
nobody chose, and it turns "what tuning is this server running" into a question about filesystem
timing. **Every persisted record carries the tuning revision:** `TuningConfig` has an explicit
`revision` field and the loader computes a content hash; both are written alongside every record and
cup result. Without this, reload is quietly destructive — the leaderboard silently mixes runs from
before and after a balancing change with no way to separate them afterwards.

Scope: **under CloudNet, reload is not the production path.** Services are dynamic,
`autoDeleteOnStop: true`, and typically live one cup. The production way to change tuning is to push
a new `Voyager/default` template and let the next service pick it up, reaching full fleet coverage
within one match cycle without touching a running race. The reload command exists for the designer's
local server and for a static staging service, where the iteration loop is the point.

### Testability

Because the resolver reads through `ConfigSource` rather than from the JVM, precedence is a pure
function over an ordered list and is tested as one — a `Map`-backed source per layer, no
`System.setProperty`, no test-ordering hazards, parallel-safe. This matters because environment
variables are process-global and cannot be set from Java at all.

| Layer | What it asserts |
|---|---|
| Precedence | For a given stack of sources, the winning value and the reported `source` string are correct, including "higher layer overrides lower" and "blank is not a value" |
| Validation | Each record's compact constructor rejects each documented invariant violation; the loader accumulates rather than short-circuits and reports the originating source per problem |
| Deployment golden tests | For each deployment shape — local compose, CloudNet task, Kubernetes manifest — a fixture of exactly the variables that shape sets, asserting the resulting effective configuration |

The third catches the failure that actually happens: someone renames `VOYAGER_DB_URL`, updates the
code, and forgets `docker/compose.yml`. Fixtures are generated from the real descriptors, not
hand-copied, so they cannot drift, and `-Dvoyager.config.check=true` runs against them in CI.

`voyager-fitness` enforces the boundaries, with `allowEmptyShould(false)` like every other rule:

```java
@ArchTest
static final ArchRule environmentAccessIsConfinedToTheEdge =
        noClasses()
            .that().resideOutsideOfPackages(
                    "net.elytrarace.voyager.platform.config..",
                    "net.elytrarace.voyager.server..",
                    "net.elytrarace.voyager.setup..")
            .should().callMethod(System.class, "getenv", String.class)
            .orShould().callMethod(System.class, "getProperty", String.class)
            .orShould().callMethod(Integer.class, "getInteger", String.class, int.class)
            .orShould().callMethod(Boolean.class, "getBoolean", String.class);
```

`Integer.getInteger`, `Long.getLong` and `Boolean.getBoolean` are on the list because they read
system properties despite reading like parsers, and they are the standard way this rule gets
circumvented without anyone intending to.

The remaining rules: no module references `eu.cloudnetservice..` (when `voyager-cloudnet-bridge`
exists, that module becomes the sole exception and the rule is narrowed rather than deleted); no
class outside `net.elytrarace.voyager.persistence..` and the composition roots calls `Secret.value()`; every
type in a `..config..` package is a record with only final fields; no `static final String` in a
`..config..` package has a name containing `PASSWORD`, `SECRET` or `TOKEN`; `voyager-api` does not
depend on `java.nio.file.Files`; and `voyager-physics` does not depend on any type in a `..config..`
package, restating at the fitness level that the physics core has no knobs.

Applied to the current tree, the first rule alone fails in six places — `MapProvider`, `CupProvider`,
`GuidePointStore`, `RunMode`, `GameServiceImpl`, `DatabaseConfig` — five of them in static
initialisers. That is the measurement of the gap this section closes.

### Configuration open questions

| Question | Impact | Resolution |
|---|---|---|
| Does the CloudNet wrapper propagate node environment variables to every service, or only to those declared in the task? | Determines whether database credentials can live on the node at all | Verify against the pinned RC on a staging node before E5 |
| Which CloudNet RC does the organisation actually pin? | Wrong coordinates fail to resolve; snapshot repositories may be needed | Confirm against the `aonyx-bom` / `manis-bom` contents before E4 |
| Is `voyager-cloudnet-bridge` needed for post-cup lobby routing, or does the existing bridge handle it without our code? | Decides whether a ninth module enters the tree | Answer during E4 planning; the `compileOnly` boundary is specified either way |
| Are tuning revisions worth segmenting leaderboards by, or only worth recording? | Product decision | Record from E5 regardless; segmentation is a later query-side concern |

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

`CLAUDE.md` is rewritten rather than patched, and the rewrite lands in **E1** rather than at
cut-over: it is the file every contributor and every agent reads first, and leaving it describing a
tree that is being deleted is worse than having no file. Until it is rewritten, this specification is
the authority — including for the ten design rules, which it carries forward in full.

CI, Release Please and Renovate are untouched.

## Risks and open questions

| Risk | Impact | Resolution |
|---|---|---|
| FAWE has no Minestom equivalent | Blocks E6 | Own research epic before E6 planning |
| `air_drag_modifier` may alter the 26.2 elytra drag path | Drag constants wrong, all tracking drifts | Decompile check of `LivingEntity.travel()` in 26.2, before E2 completes |
| ~~Trace tolerances set too tight or too loose~~ | ~~False failures, or parity claimed without proof~~ | **Closed in E2b Task 7.** Calibrated against all nine real traces; both bounds are exactly `0`, so there is no slack left to be wrong about. The residual risk moved: `Math.cos` carries 1 ulp of platform latitude, so a CI runner on another architecture could show a last-bit residual. The response is to measure and record it, not to widen the bound pre-emptively |
| Plausibility thresholds reject legitimate fast pilots | Valid records discarded | Log-only in v1; arm only on measured distributions |
| Minecraft 26.3 ships during the rebuild | Possible double migration | Minestom is confined to `voyager-platform`; re-check `releases.atom` at E4 |
| Vanilla 26.2 recording setup is more work than estimated | E2 slips, and E2 gates everything | Prototype the recorder before committing to E2 scope |
| Server-side recording lacks the client's internal velocity | Some divergence classes invisible | Accepted: position sequence is what production measures too |
| `io.airlift:guice` is a single-vendor fork aligned to Trino's needs | Abandonment would force a DI migration | Annotations confined to two composition roots, so a swap touches two modules; pin the exact version and re-check before a JDK 26 migration |

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
