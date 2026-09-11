# Voyager Greenfield Rebuild — Epics E1–E7

Date: 2026-09-09
Status: Draft, ready for transfer to GitHub Issues (pending owner approval)
Source: `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` (design spec, Approved)

## Purpose and scope of this document

This file turns the seven delivery stages (E1–E7) from the design spec's "Delivery plan"
section into ticket-ready text: one epic issue per stage, plus the sub-tickets where the
actual work happens. **Nothing in this file has been created on GitHub.** It is written so
that each section below can be copy-pasted into `gh issue create` (or the GitHub web UI)
once the project owner signs off, with epic issues created first and sub-tickets linked
to them via "part of #<epic-issue-number>".

Two things are carried forward from the design spec without being allowed to disappear:

- The **four open configuration questions** (spec, "Configuration open questions") each
  appear as a named ticket or a named precondition below — see E4.1, E4.12, E4.13, and the
  note under E5.1.
- The **risk register** (spec, "Risks and open questions") is threaded into the epic that
  owns each risk, either as a dedicated spike ticket or as an explicit acceptance
  criterion / Definition of Ready item. A cross-reference table is at the end of this
  document.

Ticket numbering is `E<epic>.<n>` for planning purposes only; GitHub will assign real
issue numbers on creation. Each ticket follows the standard Compass template
(Description / Acceptance Criteria / Technical Details / Dependencies / Estimate).

Estimate scale: S (≤1 day), M (2–4 days), L (roughly one to two weeks), XL (needs its own
breakdown before work starts — used only where that breakdown is itself a ticket).

---

## Epic overview

| Epic | Goal | Done when (per spec) | Depends on |
|---|---|---|---|
| E1 | Module skeleton, build conventions, ArchUnit fitness harness, `CLAUDE.md` rewrite | ArchUnit imports every module; no rule runs empty | — |
| E2 | Vanilla parity: recorder, trace fixtures, `voyager-physics` | Trace suite green within tolerance | E1 |
| E3 | `voyager-race`: state machine, rings, scoring | Full race playable without a server | E1, E2 |
| E4 | `voyager-platform` + `voyager-server` | First flyable build | E1, E2, E3 |
| E5 | `voyager-persistence` | Records and profiles survive restart | E1, E4 (persistence is bolted onto a working server) |
| E6 | `voyager-setup` on Minestom | A map is configurable without Paper | E1, E4; blocked on FAWE research (E6.0) |
| E7 | Cut-over | Old tree removed; Java 25 everywhere | E4 reached **and** trace suite green (cut-over gate) — see note below |

**Cut-over gate, stated precisely (per spec, "Delivery plan"):** the gate for starting E7
is **E4 reached plus a green trace suite from E2**, not feature parity with the current
Java tree. E5 and E6 may still be in flight when E7 begins; they are not gate conditions.
This must not be softened to "when everything is done" when these tickets are transferred.

---

## E1 — Foundation: build conventions, `voyager-api`, `voyager-fitness`, `CLAUDE.md`

### Epic ticket: [E1] Foundation — build conventions, voyager-api, voyager-fitness, CLAUDE.md rewrite

**Description**
Establish the eight-module Gradle tree (`voyager-api`, `voyager-physics`, `voyager-race`,
`voyager-persistence`, `voyager-platform`, `voyager-server`, `voyager-setup`,
`voyager-fitness`), the shared build conventions, the core platform-agnostic types in
`voyager-api`, and the ArchUnit fitness suite that will police every later epic. Rewrite
`CLAUDE.md` per decision D12 — this is a foundation-epic deliverable, not a cut-over
deliverable.

**Acceptance Criteria**
- [ ] `settings.gradle.kts` declares all eight modules with the programmatic version
      catalog (no `gradle/libs.versions.toml`); `./gradlew build` succeeds for the new
      tree alongside the existing tree without touching `server`, `plugins/*`, `shared/*`
- [ ] `buildSrc` contains exactly three convention plugins (`voyager.java-conventions`,
      `voyager.library-conventions`, `voyager.application-conventions`); Java toolchain and
      `--release 25` are declared once, not per-module
- [ ] `voyager-fitness` has a test source set that imports all eight modules (verified by
      an ArchUnit `Architectures`/`JavaClasses` import assertion covering every module's
      main source set) and zero `@ArchTest` rules use `allowEmptyShould(true)`
- [ ] Running the fitness suite against the (still mostly empty) new tree produces zero
      **vacuous** passes — every rule that runs, runs against at least one class
- [ ] `CLAUDE.md` is rewritten: module table matches the eight-module tree, build commands
      match real Gradle tasks, the ten design rules are restated as currently authoritative,
      and every stale reference the spec's Evidence section lists (`shared/phase`,
      mismatched Minestom version, dead links to `docs/decisions/0002-...`, wrong test
      count) is corrected or removed
- [ ] `CI` (existing GitHub Actions) builds both the old and the new module tree; a broken
      new-tree build fails CI the same way a broken old-tree build does

**Technical Details**
- Module dependency graph (strict, no cycles): `api` ← `physics`, `race`, `persistence`;
  `platform` ← `api, physics, race` + Minestom 2026.08.28-26.2; `server` ← all;
  `setup` ← `api, platform`; `fitness` ← all (test-only)
- `voyager-api` core types for this epic: `Vec3` record (compact constructor rejects NaN
  and infinity — closes Minestom #1335 at the type level), `CollisionSpace` interface
  (`List<Aabb> boxesIntersecting(Aabb region)`), the base `RuntimeException` exception
  hierarchy convention (rule 9), `Secret` record with `toString()` returning `"***"`
- `CLAUDE.md` rewrite must fold in decision D12 exactly: the design spec (this document's
  parent) is the authority until this ticket lands, after which `CLAUDE.md` is authoritative
  again — no gap where neither file is current
- ArchUnit minimum rule set for this ticket (spec, "voyager-fitness"): no `net.minestom..`
  reference from `api`/`physics`/`race`/`persistence`; no `org.bukkit..` reference
  anywhere; `persistence` must not reference race or platform packages; sealed domain
  interface / `Base*` / factory / `Default*` / `*ServiceImpl` / `*Exception` / component
  record / `*System` naming / `package-info.java` rules, as far as ArchUnit can express
  them at this stage (most will be empty of *subjects* until later epics add classes —
  that is acceptable; empty of *effect* i.e. `allowEmptyShould(true)` is not)

**Dependencies**
None. This is the first epic.

**Estimate:** L

---

### E1.1 — buildSrc convention plugins

**Description**
Create `voyager.java-conventions`, `voyager.library-conventions`, and
`voyager.application-conventions` in `buildSrc`, replacing the seven repeated blocks in
the current tree (two of which use `sourceCompatibility` instead of a toolchain).

**Acceptance Criteria**
- [ ] Java 25 toolchain and `--release 25` declared exactly once, in `java-conventions`
- [ ] `library-conventions` applies to `api`, `physics`, `race`, `persistence`; no
      `application` plugin, no `mainClass`
- [ ] `application-conventions` applies to `server` and `setup`; declares `mainClass` and
      the ShadowJar fat-jar task
- [ ] Every one of the eight new modules' `build.gradle.kts` applies exactly one of the
      three convention plugins and declares no toolchain/`--release` of its own

**Technical Details**
Reference: spec section "Build". Programmatic version catalog stays in
`settings.gradle.kts`; convention plugins consume it via `dependencyResolutionManagement`.

**Dependencies:** None.
**Estimate:** S

---

### E1.2 — Eight-module Gradle skeleton and settings wiring

**Description**
Add the eight new modules to `settings.gradle.kts` with correct `project()` dependency
declarations matching the strict left-pointing graph, each with an empty `src/main/java`
and a `package-info.java` stub so the tree compiles from day one.

**Acceptance Criteria**
- [ ] `./gradlew build` succeeds for the whole multi-project build (old + new trees)
- [ ] `voyager-api` declares zero project dependencies (JDK + annotations + adventure-api
      only)
- [ ] Dependency declarations match the graph in the design spec exactly; an intentional
      violation (e.g. `race` depending on `platform`) fails Gradle configuration, not just
      ArchUnit
- [ ] Each module has at least one `package-info.java` with `@NotNullByDefault`

**Technical Details**
Graph: `api`; `physics -> api`; `race -> api, physics`; `persistence -> api`;
`platform -> api, physics, race` + minestom; `server -> all`; `setup -> api, platform`;
`fitness -> all` (test-only, add via `testImplementation`, not `implementation`).

**Dependencies:** E1.1
**Estimate:** S

---

### E1.3 — `voyager-api` core types: Vec3, CollisionSpace, Secret, exception base

**Description**
Implement the handful of `voyager-api` types every later epic needs immediately:
`Vec3`, `CollisionSpace`, `Secret`, and the domain exception naming convention.

**Acceptance Criteria**
- [ ] `Vec3(double x, double y, double z)` — compact constructor throws for any NaN or
      infinite component; unit test asserts construction fails for all three components
      individually
- [ ] `CollisionSpace` is a one-method interface (`boxesIntersecting`), no default methods,
      no unrelated members (rule of interface segregation, per spec's `CollisionSpace`
      example)
- [ ] `Secret(String value)` — `toString()` returns `"***"`; `Objects.requireNonNull` in
      compact constructor; unit test asserts a `Secret` embedded in another record's
      `toString()` never leaks `value()`
- [ ] At least one example domain exception (e.g. `InvalidConfigurationException`) extends
      `RuntimeException`, named with the `Exception` suffix, documented as the pattern
      every later module-specific exception follows

**Technical Details**
Direct code from spec sections "The vector type", "Collision access", and "Secrets".

**Dependencies:** E1.2
**Estimate:** S

---

### E1.4 — `voyager-fitness`: ArchUnit rule suite

**Description**
Build the test-only module that imports all eight modules and enforces module boundaries
and the ten `CLAUDE.md` design rules as far as ArchUnit can express them.

**Acceptance Criteria**
- [ ] `voyager-fitness` test source set has a single ArchUnit `ClassFileImporter` /
      `@AnalyzeClasses` configuration that scans all eight modules' `build/classes`
      output, verified by a rule that asserts at least N classes were imported (fails if
      accidentally scoped to one module)
- [ ] Every `@ArchTest` rule uses `allowEmptyShould(false)` (the module default, not a
      per-rule opt-in) — a grep/config check enforces this so a future rule cannot
      silently regress to vacuous
- [ ] Module boundary rules implemented: no `net.minestom..` from `api`/`physics`/`race`/
      `persistence`; no `org.bukkit..` anywhere; `persistence` excludes race/platform
      packages
- [ ] Design-rule scaffolding implemented (rules will gain subjects as later epics add
      classes): sealed-interface-with-`Base*`-permits check, abstract-factory-with-
      private-constructor check, `Default*`-is-final check, `*ServiceImpl`-implements-
      `*Service` check, `*Exception`-extends-`RuntimeException` check, `..component..`-
      classes-are-records check (scoped to exclude `..persistence.entity..`, per the
      entity exception this design documents), `*System`-in-`..system..` check,
      `package-info.java`-present-per-package check

**Technical Details**
Reference: spec section "voyager-fitness" and the exception noted under "Persistence" for
the record rule (entities are excluded, with a positive counter-rule instead).

**Dependencies:** E1.2, E1.3
**Estimate:** M

---

### E1.5 — `CLAUDE.md` rewrite (decision D12)

**Description**
Replace `CLAUDE.md` with a version describing the eight-module greenfield tree, correcting
every stale reference the spec's Evidence section documents, and restating the ten design
rules as currently binding.

**Acceptance Criteria**
- [ ] Module Structure section lists the eight new modules with one-line descriptions;
      old modules are listed as "removed at E7 cut-over, present during migration" rather
      than as the primary tree
- [ ] Build Commands section contains only commands that succeed against the current repo
      state (no aspirational commands for tests that don't exist yet)
- [ ] No reference to `shared/phase` remains anywhere in the file
- [ ] Minestom version reference matches `settings.gradle.kts` exactly
  (`2026.08.28-26.2`)
- [ ] Both broken doc links the spec identifies are either fixed or the target file is
      created (`docs/decisions/0002-elytra-flight-client-authority.md` status is resolved
      one way or the other, not left dangling)
- [ ] The Agent Team Workflow section is preserved verbatim (out of scope for this ticket)
- [ ] Diff reviewed and approved by the project owner before merge (this file rewrites a
      document every agent reads first — see Human in the Loop checkpoint)

**Technical Details**
This is D12 executed, landing in E1 per explicit spec instruction ("the rewrite lands in
E1 rather than at cut-over"). Until this ticket merges, the design spec remains the
authority per the spec's own statement.

**Dependencies:** E1.2 (module list must be real before it's documented)
**Agents:** Scribe (voyager-tech-writer) leads; Compass (voyager-product-manager) and
Atlas (voyager-architect) review; **requires explicit user approval before merge**
(Human in the Loop checkpoint — "architecture decisions", "restructuring existing docs").
**Estimate:** M

---

### E1.6 — CI wiring for the dual tree

**Description**
Ensure existing GitHub Actions, Release Please, and Renovate all continue to function
with the new module tree added alongside the old one, per spec ("CI, Release Please and
Renovate are untouched").

**Acceptance Criteria**
- [ ] `./gradlew build` job builds all eight new modules plus the existing tree in one CI
      run; a failure in either fails the workflow
- [ ] `./gradlew :voyager-fitness:test` runs in CI as its own step (fast fail on
      architecture violations, separate from the full build)
- [ ] Release Please config requires no changes (verified by a dry run) — new modules
      don't need independent versioning yet
- [ ] Renovate continues to pick up the programmatic version catalog entries for new
      dependencies (Minestom 26.2, `io.airlift:guice`, etc.) without a config change

**Technical Details:** No CloudNet, no deployment changes in this ticket — CI build/test only.

**Dependencies:** E1.2
**Agents:** Hangar (voyager-devops-expert)
**Estimate:** S

---

## E2 — Vanilla parity: recorder, trace fixtures, `voyager-physics`

### Epic ticket: [E2] Vanilla parity — recorder, trace fixtures, voyager-physics

**Description**
This is the risk-bearing epic and precedes anything playable by design (D3): if Vanilla
parity is unreachable, that must surface here, in weeks, not after a cup system exists.
Build a server-side recorder against a real Vanilla 26.2 server, capture trace fixtures
across eight flight profiles, and implement the full ten-step Vanilla tick (steps 2–10;
step 1, durability, stays out by decision) in `voyager-physics` as a pure function proven
against those traces.

**Acceptance Criteria**
- [ ] The trace suite (JUnit, no server instance) is green for all eight flight profiles
      at the calibrated tolerance — this is the literal spec Done condition and the single
      gate for E3 to start
- [ ] Per-tick position deviation and cumulative 200-tick drift thresholds are either
      confirmed at the spec's initial assumptions (`1e-6` blocks per tick, `0.01` blocks
      cumulative) or replaced with measured values from real traces, with the replacement
      documented and justified, not silently adjusted until tests pass
- [ ] `voyager-physics` has zero dependency on Minestom, the game, or the database
      (enforced by the E1.4 ArchUnit rule, now with real subjects to check)
- [ ] The 26.2 decompile check of `air_drag_modifier` / `LivingEntity.travel()` is
      completed and its finding (drag path routes through the new attribute or it doesn't)
      is recorded before this epic is marked done — this is an explicit spec blocking
      condition ("must be checked... before E2 completes"), not an optional nice-to-have
- [ ] `Vec3` invariant (E1.3) is exercised at the trace-comparison boundary: a trace that
      would produce NaN is asserted to produce NaN inside the pure function and rejected
      only at the (future) platform boundary, never inside `voyager-physics`

**Technical Details**
- Signature: `FlightState tick(FlightState previous, FlightInput input, CollisionSpace space)`
  — both `FlightState` and `FlightInput` are records, no hidden state
- Numeric fidelity: rotation stays `float`, position/velocity stay `double`, exactly
  mirroring Vanilla's own types — no "more accurate" `double` upgrades
- Vanilla's missing guards are replicated, not patched (example given in spec: no
  `hLook > 1e-8` guard at step 6 — Vanilla divides unguarded and so does the port)
- Step decomposition: steps 2–10 as `@FunctionalInterface` units in an enum-defined order,
  optional snapshot after each step, for diagnosable trace mismatches ("drag step diverges
  at tick 412" instead of bisecting by hand)
- Fixtures live in `voyager-physics/src/test/resources/traces/`, each with Minecraft
  version + initial state metadata
- Flight profiles required: steady glide; climb into stall; dive and pull-out; single
  firework boost; chained firework boosts; pitch at ±90°; glancing wall collision; landing

**Dependencies:** E1 (module skeleton, `Vec3`, `CollisionSpace`, fitness rules)

**Estimate:** XL — breaks down into the sub-tickets below; do not start without the E2.1
prototype spike completing first, per the risk register.

---

### E2.1 — Recorder prototype spike (time-boxed, de-risking)

**Description**
Before committing to the full recorder build, time-box a spike that proves the recording
approach works end-to-end against a real Vanilla 26.2 server: capture one flight profile's
movement/rotation packets, world slice, and firework events, and replay them through a
throwaway harness.

**Acceptance Criteria**
- [ ] One complete trace (steady glide) is captured from a real Vanilla 26.2 server and
      saved in a format that could plausibly become the fixture format
- [ ] The spike produces a written estimate (in the ticket, not a separate doc) for how
      long the full recorder (E2.2) and full fixture capture (E2.4) will take, based on
      what the spike learned
- [ ] Explicit go/no-go note: does the server-side approach (vs. a client mod) hold up in
      practice, confirming or revising the spec's cost argument for why server-side was
      chosen
- [ ] Time-boxed: if the spike is not complete within its box, that itself is reported as
      a finding (the risk register item "Vanilla 26.2 recording setup is more work than
      estimated" firing), not silently extended

**Technical Details**
Risk register: "Vanilla 26.2 recording setup is more work than estimated" — Impact: "E2
slips, and E2 gates everything". Resolution given in spec: "Prototype the recorder before
committing to E2 scope." This ticket **is** that prototype.

**Dependencies:** E1 module skeleton (for where fixtures will eventually live); otherwise
independent infrastructure work against a standalone Vanilla server.
**Agents:** Bedrock (voyager-minecraft-expert) leads; Scout (voyager-researcher) for
protocol/decompile groundwork; Lumen (voyager-scientist) records methodology.
**Estimate:** M (time-boxed explicitly, see AC)

---

### E2.2 — Vanilla recorder, production build

**Description**
Build the full server-side recorder: per-tick logging of incoming movement and rotation
packets, the relevant world slice, and firework events, against a real Vanilla 26.2
server, producing fixtures in the format `voyager-physics` tests will consume.

**Acceptance Criteria**
- [ ] Recorder captures, per tick: movement packet, rotation packet, relevant world block
      slice, firework ignition/detonation events
- [ ] Output format includes metadata (Minecraft version, initial state) as specified for
      `voyager-physics/src/test/resources/traces/`
- [ ] Recorder is independently runnable (documented command) without any Voyager server
      code — it is a standalone tool against a Vanilla server, not a plugin
- [ ] Recorded output for the steady-glide profile from E2.1 is re-captured with the
      production recorder and the two captures agree within the not-yet-final tolerance
      (this is a self-consistency check, not the parity check itself)

**Technical Details**
Reference: spec section "Trace acceptance" — "This is sufficient because the client's
position sequence is exactly the quantity the plausibility check later measures against
— the test targets the reality that matters in production." Accepted limitation, do not
attempt to work around: server-side recording cannot see the client's internal velocity;
this is listed as an accepted risk, not a defect to fix here.

**Dependencies:** E2.1 (prototype must confirm the approach first)
**Agents:** Bedrock (voyager-minecraft-expert), Helix (voyager-minestom-expert) for
tooling, Quench (voyager-senior-testing) for fixture harness conventions.
**Estimate:** L

---

### E2.3 — 26.2 decompile check: `air_drag_modifier` risk

**Description**
Decompile-check whether 26.2's new `air_drag_modifier` living-entity attribute alters the
hardcoded elytra drag path (`0.99` / `0.98`) that the physics port relies on.

**Acceptance Criteria**
- [ ] `LivingEntity.travel()` (or the 26.2 equivalent) is inspected against a 26.2
      decompile; the finding — "elytra drag path routes through `air_drag_modifier`" or
      "elytra drag path is unaffected" — is written down explicitly in the ticket, with
      the decompiled method reference
- [ ] If the drag path *is* affected: a follow-up ticket is filed against `voyager-physics`
      (E2.5) to account for it before the trace suite is trusted; if unaffected: the
      ticket closes with that as the recorded, checkable finding
- [ ] This ticket must close before the E2 epic is marked done (explicit spec blocking
      condition)

**Technical Details**
Risk register: "`air_drag_modifier` may alter the 26.2 elytra drag path" — Impact: "Drag
constants wrong, all tracking drifts". At default attribute values the spec states
behaviour is identical to 1.21.11, but this is explicitly "not verified" in the spec text.

**Dependencies:** None (can run in parallel with E2.1/E2.2)
**Agents:** Bedrock (voyager-minecraft-expert), Scout (voyager-researcher)
**Estimate:** S

---

### E2.4 — Trace fixture capture: eight flight profiles

**Description**
Using the production recorder (E2.2), capture and commit fixtures for all eight required
flight profiles.

**Acceptance Criteria**
- [ ] Fixtures exist for: steady glide; climb into stall; dive and pull-out; single
      firework boost; chained firework boosts; pitch at ±90°; glancing wall collision;
      landing — eight fixture files/directories under
      `voyager-physics/src/test/resources/traces/`
- [ ] Each fixture is reviewed for plausibility (does the raw capture look like the named
      profile, e.g. the stall profile actually shows a velocity collapse) before being
      committed as a ground truth
- [ ] Fixture metadata (Minecraft version, initial state) is present and consistent across
      all eight

**Technical Details:** Depends on the recorder tool from E2.2, not a stub.

**Dependencies:** E2.2
**Agents:** Bedrock (voyager-minecraft-expert), Lumen (voyager-scientist) for methodology
documentation in `docs/research/`.
**Estimate:** M

---

### E2.5 — `voyager-physics` core implementation

**Description**
Implement the pure-function Vanilla tick (steps 2–10) in `voyager-physics`: `FlightState`,
`FlightInput`, the step-decomposition enum, and the `tick(...)` entry point.

**Acceptance Criteria**
- [ ] `tick(FlightState previous, FlightInput input, CollisionSpace space)` compiles with
      both state types as records and no field mutation anywhere in the module
- [ ] Steps 2–10 exist as separately named `@FunctionalInterface` units composed via an
      enum-defined order; a diagnostic mode reports per-step snapshots
- [ ] Step 1 (durability) is absent and documented as absent by decision (a comment or
      package doc states this, so it reads as intentional, not incomplete)
- [ ] Numeric types match Vanilla exactly: rotation as `float`, position/velocity as
      `double`, checked by an ArchUnit or unit-test assertion on method signatures where
      practical
- [ ] The current tree's `hLook > 1e-8` guard (a known divergence from Vanilla) is **not**
      present in the port; a unit test constructs the exact edge case and asserts the
      unguarded (Vanilla) behaviour, including NaN where Vanilla produces NaN
- [ ] If E2.3 found `air_drag_modifier` affects the drag path, that finding is incorporated
      here before this ticket is considered complete

**Technical Details:** Full spec section "Physics core" applies verbatim; this ticket is
its implementation.

**Dependencies:** E1.3 (`Vec3`, `CollisionSpace`), E2.3 (drag-path finding)
**Agents:** Vector (voyager-math-physics) leads, Bedrock (voyager-minecraft-expert)
reviews for Vanilla fidelity, Thrust (voyager-game-developer) for implementation.
**Estimate:** L

---

### E2.6 — Trace comparison harness and threshold calibration

**Description**
Build the JUnit harness that replays a fixture through `voyager-physics` and compares the
resulting position sequence against the recorded trace, then calibrate the acceptance
thresholds against the first real fixtures.

**Acceptance Criteria**
- [ ] Harness reports per-step divergence (which of steps 2–10 first diverges), not just a
      final position mismatch, using the E2.5 diagnostic snapshot mode
- [ ] Thresholds (`1e-6` per-tick, `0.01` cumulative over 200 ticks) are re-evaluated
      against the E2.4 fixtures; the ticket records whether they were confirmed as-is or
      replaced, with the replacement values and the reasoning
- [ ] All eight fixtures pass at the calibrated thresholds — this is the literal E2 Done
      condition
- [ ] Harness runs as plain JUnit with no server instance, confirmed by a CI run with no
      Minestom/Vanilla process alive during the test

**Technical Details:** Risk register item "Trace tolerances set too tight or too loose" is
resolved by this ticket's calibration step, not assumed away.

**Dependencies:** E2.4, E2.5
**Agents:** Quench (voyager-senior-testing) leads, Vector (voyager-math-physics) for
divergence analysis, Lumen (voyager-scientist) documents calibration methodology.
**Estimate:** M

---

### E2.7 — Contract tests for the step-decomposition sealed hierarchy

**Description**
Add the abstract contract-test mechanism (per the design principles section: "Liskov
substitution needs a mechanism or it is prose") for any sealed hierarchy introduced in
`voyager-physics`.

**Acceptance Criteria**
- [ ] At least one abstract contract test class exists that every implementation of a
      sealed physics-related interface must pass (if E2.5's step decomposition introduces
      a sealed interface rather than only an enum, this covers it; if it stays
      enum-only, this ticket documents that decision and closes as not-applicable with
      the reasoning recorded)
- [ ] Contract test fails red, not as a review comment, for a hypothetical implementation
      that violates the stated invariant (demonstrated with a deliberately broken test
      double)

**Technical Details:** Spec, design principles, "Liskov substitution" paragraph.

**Dependencies:** E2.5
**Agents:** Quench (voyager-senior-testing), Vector (voyager-math-physics)
**Estimate:** S

---

## E3 — `voyager-race`: state machine, rings, scoring

### Epic ticket: [E3] voyager-race — state machine, rings, scoring

**Description**
Implement the race domain as pure, server-free logic: the race state machine, ring
collision, scoring strategies, and the map/cup/ring/boost configuration model and loader.
ECS is confined to the tick layer (advancing flight state, ring passes, HUD values, boost
cooldowns); everything at event boundaries (cup advancement, scoring, persistence,
ranking) is a plain service.

**Acceptance Criteria**
- [ ] A complete race (Lobby → Preparation → Game → End, including a practice-mode retry
      loop) runs to completion in a single JUnit test with no Minestom instance and no
      network I/O
- [ ] Ring collision (segment-plane intersection against the ring disc) is implemented
      exactly once in the codebase, hand-written, with no `commons-geometry` dependency in
      `voyager-race`
- [ ] `ScoringStrategy`, `RaceRule`, `RingEffect` are `sealed … permits BaseX` interfaces
      with `non-sealed abstract Base*` implementations, each with an abstract contract
      test class
- [ ] `voyager-race` contains zero `net.minestom..` imports and zero references to Xerus
      (verified by the E1.4 ArchUnit rule now populated with real subjects)
- [ ] Map/cup/ring/boost configuration has exactly one format, one model (in
      `voyager-api`), one loader (in `voyager-race`) — no second `BoostConfig`/
      `BoostConfigDTO`-style duplication
- [ ] `RingType` (or any enum parsed from JSON) caches `VALUES` and exposes a `byName()`
      returning `@Nullable`, per rule 6

**Technical Details**
- Components in the ECS tick layer are records without exception; cost is accepted as
  negligible for ZGC (spec's own estimate: ~6,000 small objects/sec at 50 players)
- Xerus stays out of `voyager-race` entirely — it is Minestom-bound and lives in
  `voyager-platform` (E4), which drives the pure state machine via a thin adapter
- Entity factory is injectable through a `@FunctionalInterface` creator (rule 7),
  replacing the current tree's static `GameEntityFactory`/`GamePhaseFactory` and its
  five-overload telescoping constructor problem

**Dependencies:** E1 (module skeleton, fitness rules), E2 (physics core — race consumes
`FlightState`/`FlightInput` types for the tick-layer ECS, though it does not compute
physics itself)

**Estimate:** XL — see sub-tickets.

---

### E3.1 — Race state machine (pure domain)

**Description**
Implement the race progression state machine as pure domain logic: which transitions
exist, when a race ends, what happens on completion, how a practice retry loops back.

**Acceptance Criteria**
- [ ] State machine is a plain Java type with no Minestom, no Xerus, no I/O dependency
- [ ] Practice-mode retry loop is implemented as a real cyclic transition (the design
      spec's audit found the equivalent `CyclicPhaseSeries` from ADR-0008 "appears
      nowhere" in the current tree — this ticket is where it actually gets built)
- [ ] A JUnit test drives Lobby → Preparation → Game → End end-to-end with no server
      (the exact test the spec says "is missing today precisely because it was impossible
      without one")
- [ ] Race and Practice modes produce genuinely different phase series (not the current
      tree's bug where `GamePhaseFactory` produces the same series for both)

**Technical Details:** Spec section "Phases" under `voyager-race`.

**Dependencies:** E1.4 (fitness rules to check against as this lands)
**Agents:** Lattice (voyager-senior-ecs) leads, Drift (voyager-game-designer) for
transition rules, Thrust (voyager-game-developer)
**Estimate:** L

---

### E3.2 — Ring collision (segment-plane intersection)

**Description**
Hand-write segment-plane intersection against the ring disc, once, replacing the two
disagreeing implementations in the current tree (`plugins/game` via commons-geometry,
`server` hand-written).

**Acceptance Criteria**
- [ ] Single implementation in `voyager-race`, no `commons-geometry` dependency added to
      this module
- [ ] Unit tests cover center pass, edge pass (boundary case), miss, and the specific edge
      disagreement the two old implementations had (documented in the ticket once found)
- [ ] Function signature takes pure geometric inputs (segment start/end, ring center,
      normal, radius) and returns a boolean/hit result — no ECS or entity coupling

**Technical Details:** Spec: "Ten lines of vector arithmetic do not justify a dependency."

**Dependencies:** E1.3 (`Vec3`)
**Agents:** Vector (voyager-math-physics) leads, Quench (voyager-senior-testing)
**Estimate:** M

---

### E3.3 — Sealed strategy hierarchies: `ScoringStrategy`, `RaceRule`, `RingEffect`

**Description**
Implement the three sealed domain-interface hierarchies with `Base*` non-sealed
implementations and abstract contract tests, per design rule 1 and the Liskov mechanism.

**Acceptance Criteria**
- [ ] `ScoringStrategy`, `RaceRule`, `RingEffect` are each `sealed … permits BaseX`
- [ ] Each has a `non-sealed abstract BaseX` implementation as the controlled extension
      point
- [ ] Each has an abstract contract test class; at least one concrete strategy per
      hierarchy passes it
- [ ] Registries for these (where applicable) follow rule 3: `sealed` interface, static
      `create()`, single `Default*` implementation over `ConcurrentHashMap`,
      `@Unmodifiable` returned collections

**Technical Details:** Design principles section, "Liskov substitution" and "Interface
segregation" paragraphs; rule 1 and rule 3 from `CLAUDE.md`.

**Dependencies:** E3.1
**Agents:** Lattice (voyager-senior-ecs), Drift (voyager-game-designer) for scoring rules,
Quench (voyager-senior-testing) for contract tests.
**Estimate:** L

---

### E3.4 — Map/cup/ring/boost configuration model and loader

**Description**
Define the configuration types in `voyager-api` (map, cup, ring, boost) and the single
loader in `voyager-race`, eliminating the current `BoostConfig`/`BoostConfigDTO`
duplication.

**Acceptance Criteria**
- [ ] Exactly one boost configuration type exists in the whole tree (searched, not
      assumed)
- [ ] Loader accepts a `Path` or `Reader` (test-injectable), calls no `System.getenv`/
      `System.getProperty` directly — resolution happens upstream in the composition root
      (this ticket only loads and validates, per the Configuration section's layering
      rule for kind (c)/(d))
- [ ] Invalid configuration fails via the loader's accumulate-and-report pattern
      (`ConfigProblem(key, source, message)`), not construct-and-die on the first bad
      field
- [ ] `RingType` enum caches `VALUES` and exposes `byName()` returning `@Nullable`, tested
      against a misspelled value producing a reported problem, not a silent `UNKNOWN`

**Technical Details:** Spec section "One format, one model, one loader" plus the
Configuration section's kind (c)/(d) row and the "Validation" section's loader pattern.

**Dependencies:** E1.3
**Agents:** Forge (voyager-senior-backend), Drift (voyager-game-designer) for the tuning
schema itself (`BoostTuning`, `ScoringTuning`, `PlausibilityTuning`).
**Estimate:** M

---

### E3.5 — `TuningConfig` and per-race immutability snapshot

**Description**
Implement `TuningConfig`/`BoostTuning`/`ScoringTuning`/`PlausibilityTuning` as validating
records, plus the "immutable for the duration of a run" snapshot mechanism (`AtomicReference`
read once at race creation into a `TuningComponent`).

**Acceptance Criteria**
- [ ] `BoostTuning` compact constructor rejects `burnDurationTicks <= 0`, non-finite or
      non-positive `maxSpeedBlocksPerTick`, and negative `cooldownMs`, each with a
      dedicated unit test and an `InvalidConfigurationException` message naming the field
      and the bad value
- [ ] `TuningConfig` carries a `revision` field and a content hash; both are threaded
      through to any persisted record (verified once E5 exists; this ticket only needs to
      expose the fields)
- [ ] A race entity reads `active.get()` exactly once, at creation, into a
      `TuningComponent`; a unit test proves that mutating `active` mid-race does not
      change the snapshot the running race sees
- [ ] No `WatchService`/file-watching exists anywhere in this ticket's code — reload is
      explicit only (deferred to E4's command surface, but the config type itself must not
      assume a watcher)

**Technical Details:** Spec section "Gameplay tuning and hot reload" — note the open
question "Are tuning revisions worth segmenting leaderboards by, or only worth recording?"
is answered for E3/E5 purposes as "record from E5 regardless" (spec's own resolution);
segmentation is out of scope here.

**Dependencies:** E3.4
**Agents:** Forge (voyager-senior-backend), Drift (voyager-game-designer)
**Estimate:** M

---

### E3.6 — Injectable entity factory (`@FunctionalInterface` creator)

**Description**
Replace the static `GameEntityFactory`/`GamePhaseFactory` pattern with an injectable
`@FunctionalInterface` creator per rule 7, eliminating the five-overload telescoping
`createGamePhases` problem.

**Acceptance Criteria**
- [ ] Entity/phase creation goes through a `@FunctionalInterface` type substitutable in
      tests (a test supplies an alternate creator and asserts it was used, not the
      production one)
- [ ] No overload set exists with more than two parameter-list variants for the same
      creation operation — telescoping is replaced with a builder or a parameter record
- [ ] `voyager-race` unit tests construct race/phase objects using a test creator with no
      static factory call anywhere in the test

**Technical Details:** Spec's design-rule section under `voyager-race`, final bullet.

**Dependencies:** E3.1, E3.3
**Agents:** Lattice (voyager-senior-ecs)
**Estimate:** M

---

### E3.7 — End-to-end race JUnit test and fitness-rule population

**Description**
Close out E3 with the full Lobby→Game→End JUnit test (already required as an AC on
E3.1) elevated to a proper regression suite, and confirm the `voyager-fitness` rules for
`voyager-race` now have real subjects and pass non-vacuously.

**Acceptance Criteria**
- [ ] Full race playthrough test covers at least: normal finish, practice retry loop,
      a mid-race disconnect/reconnect domain event (if modeled at this layer), and a
      scoring computation with at least two `ScoringStrategy` implementations producing
      different results for the same input
- [ ] `voyager-fitness` rules scoped to `voyager-race` (sealed hierarchies, `Default*`
      final, `*ServiceImpl`/`*Service`, exceptions, `package-info.java`) all report real
      classes checked, not zero
- [ ] Epic-level Done condition confirmed: "Full race playable without a server"

**Dependencies:** E3.1–E3.6
**Agents:** Quench (voyager-senior-testing), Compass (voyager-product-manager) validates
against the epic AC.
**Estimate:** M

---

## E4 — `voyager-platform` + `voyager-server` — first flyable build

### Epic ticket: [E4] voyager-platform + voyager-server — first flyable build

**Description**
Wire the pure `voyager-physics`/`voyager-race` domains to Minestom: velocity exit, flight
detection, plausibility checking (log-only in v1), Xerus tick driver, world management,
HUD, DI composition root, configuration resolver, and the CloudNet v4 contract. This is
the cut-over gate epic (together with E2's green trace suite) — not a feature-parity
milestone.

**Acceptance Criteria**
- [ ] A player can fly a real race on the new Minestom server end-to-end: join, fly
      through rings, finish, see a score — "first flyable build" taken literally, verified
      by a manual playtest recorded in the ticket plus at least one Cyano integration test
      covering the same path
- [ ] `voyager-platform` is the **only** module importing `net.minestom.*` (ArchUnit rule
      from E1.4, now checked against real code)
- [ ] Velocity is set on the client **only** for firework boost, ring BOOST/SLOW, and
      out-of-bounds reset — never during normal flight — verified by a test asserting
      `setVelocity` is called from exactly one class (the velocity exit) and only from
      those three call sites
- [ ] Plausibility check runs in log-only mode: a deliberately implausible synthetic run
      is logged, not corrected, and the player's flight is visibly undisturbed
- [ ] `-Dvoyager.config.check=true` resolves and validates configuration, prints the
      effective configuration with `Secret` fields as `***`, and exits 0/1 without binding
      a socket
- [ ] Server binds using `service.bind.host`/`service.bind.port` system properties with
      documented standalone fallbacks, confirmed by a test run both with and without those
      properties set
- [ ] `stop` on stdin cleanly shuts down via a separate thread (not inline from the stdin
      reader) — confirmed by a test or documented manual verification that inline
      execution would deadlock, matching the spec's stated reasoning
- [ ] Minestom issues #2017 (instance-switch fall) and #2267 (auto-sync velocity reset)
      have documented, implemented mitigations, each with a regression test or a manual
      verification note
- [ ] The two CloudNet open questions scoped to before-E4/before-E5 (see E4.1 and E4.12
      below) are answered, not deferred silently

**Technical Details**
- `Vec3`↔`Vec` conversion, `CollisionSpace` Minestom implementation, event registration,
  Xerus tick driver, Anvil world loader, HUD output, and the velocity exit all live here,
  per spec section "Platform layer"
- Flight detection uses real signals: `entityMeta.isFlyingWithElytra()`,
  `PlayerStopFlyingWithElytraEvent`, `START_FLYING_WITH_ELYTRA` client action — not
  `isOnGround()` as a landing proxy
- 26.2 removes `PlayerStartSneakingEvent`/`PlayerStopSneakingEvent` — any flight-entry
  logic must not depend on sneak events
- DI: `io.airlift:guice:10` pinned exactly, `Stage.PRODUCTION`, no `Multibinder` for the
  ECS system pipeline (bound as an explicit ordered `List` instead), annotations confined
  to `voyager-server`/`voyager-setup` composition roots only
- CloudNet: bind via system properties, `stop` via stdin + `ExtensionBootstrap`,
  `net.onelitefeather:minestom-extensions` on the classpath, JitPack proxy repository for
  `DependencyGetter`, no `eu.cloudnetservice.*` dependency in the shaded jar

**Dependencies:** E1, E2 (green trace suite feeds the plausibility predictor), E3 (race
domain to drive)

**Estimate:** XL — see sub-tickets.

---

### E4.0 — Spike: confirm pinned CloudNet RC and BOM coordinates

**Description**
Resolve open configuration question: "Which CloudNet RC does the organisation actually
pin?" before any CloudNet-dependent code in this epic is written against guessed
coordinates.

**Acceptance Criteria**
- [ ] Exact CloudNet v4 RC version and `aonyx-bom`/`manis-bom` coordinates confirmed and
      recorded in this ticket
- [ ] `settings.gradle.kts` repository list updated if a snapshot repository is needed
- [ ] This ticket blocks E4.10 (CloudNet integration) from starting with wrong coordinates

**Technical Details:** Configuration open-questions table, row 2: "Wrong coordinates fail
to resolve; snapshot repositories may be needed." Resolution: "Confirm against the
`aonyx-bom`/`manis-bom` contents before E4."

**Dependencies:** None
**Agents:** Scout (voyager-researcher), Hangar (voyager-devops-expert)
**Estimate:** S

---

### E4.1 — `Vec3`↔`Vec` conversion and `CollisionSpace` Minestom implementation

**Description**
Implement the only conversion point between `voyager-api`'s `Vec3` and Minestom's
`Vec`, and the Minestom-backed `CollisionSpace` for the physics core.

**Acceptance Criteria**
- [ ] Exactly one class performs `Vec3`↔`Vec` conversion; no other class in
      `voyager-platform` or `voyager-server` constructs a Minestom `Vec` from raw doubles
      bypassing it
- [ ] `CollisionSpace` implementation queries the live Minestom instance for
      `boxesIntersecting`, tested against a constructed test instance with known blocks
- [ ] Conversion asserts finiteness before handing a value to Minestom (belt-and-braces
      over the `Vec3` compact constructor)

**Dependencies:** E1.3, E2.5
**Agents:** Helix (voyager-minestom-expert)
**Estimate:** M

---

### E4.2 — Velocity exit (single class) and NaN boundary

**Description**
Implement the single class that is the only place `setVelocity` is ever called from,
covering firework boost, ring BOOST/SLOW, and out-of-bounds reset.

**Acceptance Criteria**
- [ ] `setVelocity` call sites: exactly one class, exactly three logical callers (boost
      burn, ring effect, out-of-bounds reset) — enforced by an ArchUnit rule added to
      `voyager-fitness`
- [ ] Normal elytra flight never calls `setVelocity` — a test flies a player through a
      full lap with no boost/ring-effect/OOB trigger and asserts zero `setVelocity` calls
- [ ] Addresses Minestom #1335 (NaN velocity poisoning) structurally: the exit cannot be
      called with a `Vec3` that failed construction, by type

**Dependencies:** E4.1
**Agents:** Helix (voyager-minestom-expert), Thrust (voyager-game-developer)
**Estimate:** M

---

### E4.3 — Flight detection via real Minestom signals

**Description**
Detect elytra flight start/stop using `entityMeta.isFlyingWithElytra()`,
`PlayerStopFlyingWithElytraEvent`, and the `START_FLYING_WITH_ELYTRA` client action —
replacing the current tree's `isOnGround()` landing proxy.

**Acceptance Criteria**
- [ ] No code path uses `isOnGround()` to infer landing
- [ ] No code path depends on `PlayerStartSneakingEvent`/`PlayerStopSneakingEvent` (both
      removed in 26.2) — a search of the module confirms zero references
- [ ] Flight start/stop is tested with a Cyano integration test simulating the real event
      sequence

**Dependencies:** E4.1
**Agents:** Helix (voyager-minestom-expert), Bedrock (voyager-minecraft-expert)
**Estimate:** M

---

### E4.4 — Plausibility check (log-only v1)

**Description**
Implement the error-budget-over-a-window comparator between the physics prediction and
the client-reported position, running in log-only mode: breaches are logged, the player's
flight is never corrected, and record/points from a breaching run are discarded.

**Acceptance Criteria**
- [ ] Comparison is windowed (not per-tick absolute); short-term deviation within the
      window does not log a breach
- [ ] On breach: run invalidated (points/record discarded), player movement completely
      unaffected, event logged — a test simulates a breach and asserts the player's
      position was never touched
- [ ] No threshold is "armed" to reject/correct a player in this ticket — log-only is
      structurally enforced (no code path calls the velocity exit in response to a
      plausibility breach)
- [ ] `validation_state`/`plausibility_error`-shaped output is produced even though
      persistence doesn't exist yet (E5 will consume it) — the value is at least logged
      with enough structure to be persisted later without redesign

**Technical Details:** Spec section "Plausibility check" — explicitly not armed in v1
because GrimAC-class replication is known to drift on high-speed elytra manoeuvres, and
arming on theoretical thresholds would invalidate the best pilots' runs. This is the
resolution to the risk register item "Plausibility thresholds reject legitimate fast
pilots".

**Dependencies:** E2 (trace-validated predictor), E4.1, E4.2
**Agents:** Bedrock (voyager-minecraft-expert), Vector (voyager-math-physics)
**Estimate:** L

---

### E4.5 — Xerus tick driver for the race state machine

**Description**
Implement the thin Minestom/Xerus adapter that drives the pure `voyager-race` state
machine (E3.1) with real ticking, timers, and delays.

**Acceptance Criteria**
- [ ] Xerus code exists only in `voyager-platform`, never in `voyager-race` (ArchUnit-
      checked)
- [ ] The adapter is provably thin: a test constructs the pure state machine, drives it
      manually through the same transitions the adapter would trigger, and both paths
      produce identical race outcomes for the same input sequence

**Dependencies:** E3.1, E4.1
**Agents:** Helix (voyager-minestom-expert), Lattice (voyager-senior-ecs)
**Estimate:** M

---

### E4.6 — World management via Anvil loader

**Description**
Load and manage Minestom instances from Anvil-format worlds, handling map-to-map cup
transitions.

**Acceptance Criteria**
- [ ] Maps load from the same Anvil directories the setup side produces (format
      compatibility target — full cross-check happens in E6, but this ticket's loader must
      not assume Paper-specific quirks)
- [ ] Instance switch between cup legs does not reproduce Minestom #2017 (player falls
      below the map) — regression test or documented manual verification
- [ ] Loader failure (missing/corrupt world) fails the map load with a domain exception,
      not a silent empty instance

**Dependencies:** E4.1
**Agents:** Helix (voyager-minestom-expert)
**Estimate:** M

---

### E4.7 — HUD output

**Description**
Render race HUD values (score, ring count, timer, boost cooldown) from ECS tick-layer
components, split render from state per the ADR-0009 intent the current tree never
finished.

**Acceptance Criteria**
- [ ] HUD component holds only data (record); a separate renderer class reads it and
      produces Minestom-visible output — no `HudComponent.render()`-style self-rendering
- [ ] HUD updates read boost cooldown, ring count, and timer from ECS components populated
      by E3's tick-layer systems, not from ad hoc queries

**Dependencies:** E3.7, E4.1
**Agents:** Glint (voyager-junior-frontend)
**Estimate:** M

---

### E4.8 — Guice DI composition root

**Description**
Wire `voyager-server`'s composition root with `io.airlift:guice:10`, `Stage.PRODUCTION`,
explicit `@Provides` methods, and the ECS pipeline as an explicit ordered `List` (no
`Multibinder`).

**Acceptance Criteria**
- [ ] `io.airlift:guice:10` pinned to the exact version in the version catalog; a
      fitness-rule comment or ticket note records the re-check trigger before any future
      JDK 26 migration (risk register: single-vendor fork abandonment risk)
- [ ] `Stage.PRODUCTION` used at boot — a deliberately broken binding fails fast at
      startup in a test, not lazily inside the tick loop
- [ ] No `@Inject`/`@Singleton`/`jakarta.inject` import exists outside `voyager-server`/
      `voyager-setup` (ArchUnit rule, checked against real code now)
- [ ] No code calls `bindInterceptor` (ArchUnit rule)
- [ ] ECS system pipeline is bound as an explicit `List<System>` with documented order,
      not via `Multibinder`

**Dependencies:** E1.4 (fitness rules), E3 (domain types to wire), E4.1–E4.7 (platform
adapters to wire)
**Agents:** Forge (voyager-senior-backend), Atlas (voyager-architect)
**Estimate:** M

---

### E4.9 — Configuration resolver in `voyager-platform`

**Description**
Implement the layered `ConfigResolver`/`ConfigSource` mechanism (defaults → JSON template
→ environment → system properties), the validation/accumulation loader, and the
`-Dvoyager.config.check=true` operator command.

**Acceptance Criteria**
- [ ] `ConfigSource` is a `@FunctionalInterface`; environment and system-property
      implementations exist alongside a `Map`-backed test implementation — no
      `System.setProperty` anywhere in the resolver's own tests
- [ ] Precedence is proven by a parameterized test: for a given stack of sources, the
      winning value and its reported `source` string (`env:...`, `file:...#/...`,
      `sysprop:...`) are both correct
- [ ] Validation accumulates all problems and reports them together in one exception,
      never construct-and-die on the first bad field (matches E3.5's per-record behavior
      one layer up)
- [ ] `-Dvoyager.config.check=true` resolves, validates, prints the effective config with
      `Secret` fields as `***` plus per-value source annotation, then exits 0/1 without
      binding a socket
- [ ] `voyager-race` (already built in E3) still calls no `System.getenv`/
      `System.getProperty` — this ticket's resolver lives entirely in `voyager-platform`
      and hands already-resolved values down
- [ ] ArchUnit rule confines `System.getenv`/`getProperty`/`Integer.getInteger`/
      `Boolean.getBoolean` calls to `..platform.config..`, `..server..`, `..setup..`

**Technical Details:** Spec sections "Layering and precedence", "Validation",
"Testability" in full.

**Dependencies:** E1.3, E3.4, E3.5
**Agents:** Forge (voyager-senior-backend), Quench (voyager-senior-testing) for the
deployment golden tests
**Estimate:** L

---

### E4.10 — CloudNet v4 integration

**Description**
Implement the full CloudNet contract: bind via system properties, `stop` via stdin plus
`ExtensionBootstrap`, and the template cut (`Voyager/default` + `Voyager/maps`).

**Acceptance Criteria**
- [ ] Bind host/port read exclusively from `service.bind.host`/`service.bind.port` system
      properties, with standalone-only fallbacks (`0.0.0.0`/`25565`)
- [ ] `stop` on stdin hands off to a fresh thread calling `MinecraftServer.stopCleanly()` +
      `System.exit(0)` — never executed inline from the stdin-reading thread (test or
      documented rationale confirming the deadlock the spec describes is avoided)
- [ ] `net.onelitefeather:minestom-extensions` present; `ExtensionBootstrap.bootstrap()`
      runs and binds before any player can connect
- [ ] `settings.gradle.kts` includes the JitPack proxy repository for
      `com.github.Minestom:DependencyGetter`
- [ ] Shaded `voyager-server` jar contains **zero** `eu.cloudnetservice.*` classes
      (verified by inspecting the jar contents in CI)
- [ ] `local/tasks/Voyager.json` (or equivalent) and both CloudNet templates
      (`Voyager/default`, `Voyager/maps`) exist with the described content split; task
      runs `staticServices: false`, `autoDeleteOnStop: true`, `environment: MINECRAFT_SERVER`

**Dependencies:** E4.0, E4.9 (config resolution feeds the bind values)
**Agents:** Hangar (voyager-devops-expert), Helix (voyager-minestom-expert)
**Estimate:** L

---

### E4.11 — Minestom hazard mitigations (#2017, #2267)

**Description**
Implement and test mitigations for the two known Minestom hazards relevant to this epic:
instance-switch fall (#2017) and periodic auto-sync velocity reset (#2267).

**Acceptance Criteria**
- [ ] #2017: cup map-to-map transition does not drop a player below the new map — test or
      documented manual verification against a real multi-map cup transition
- [ ] #2267: velocity update interval is configured (per PR #1426's mitigation) so the
      auto-sync tick does not reset an in-flight boost/ring-effect velocity — test
      captures velocity immediately before and after an auto-sync tick during an active
      boost and asserts no unwanted reset

**Dependencies:** E4.2, E4.6
**Agents:** Helix (voyager-minestom-expert)
**Estimate:** M

---

### E4.12 — Spike: CloudNet node→service environment variable propagation

**Description**
Resolve open configuration question: "Does the CloudNet wrapper propagate node
environment variables to every service, or only to those declared in the task?" — this
determines whether database credentials (needed in E5) can live on the node at all.

**Acceptance Criteria**
- [ ] Verified against the pinned RC (from E4.0) on a staging node, with the finding
      written down explicitly: "propagates to all services" or "only declared ones," with
      the evidence (log excerpt, task JSON diff, or equivalent)
- [ ] If only declared variables propagate: E4.10's task JSON is updated to declare the
      needed variable names (not values) explicitly
- [ ] This ticket's finding is a stated precondition for E5's secrets handling (E5's
      secrets ticket references this ticket's outcome, not the other way around)

**Technical Details:** Configuration open-questions table, row 1. Resolution: "Verify
against the pinned RC on a staging node before E5."

**Dependencies:** E4.0, E4.10 (needs a running CloudNet task to test against)
**Agents:** Hangar (voyager-devops-expert)
**Estimate:** S

---

### E4.13 — Decision: is `voyager-cloudnet-bridge` needed for post-cup lobby routing?

**Description**
Decide, during E4 planning, whether post-cup lobby routing / permission resolution /
service snapshot reading requires the deferred ninth module (`voyager-cloudnet-bridge`),
or whether the existing bridge extension handles it without Voyager-specific code.

**Acceptance Criteria**
- [ ] Decision recorded explicitly (module needed now / needed later / not needed) with
      reasoning
- [ ] If "needed now": this ticket is superseded by a new epic-scoped ticket to build
      `voyager-cloudnet-bridge` as its own extension jar, `compileOnly` on
      `eu.cloudnetservice.*`, with `extension.json` declaring `"dependencies":
      ["CloudNet_Bridge"]`
- [ ] If "needed later" or "not needed": this ticket documents the `compileOnly` boundary
      for future reference (per spec: "specified here so the boundary is decided before
      someone reaches for it in a hurry") without building the module
- [ ] Confirmed either way: `voyager-server` (built in this epic) depends on **zero**
      `eu.cloudnetservice.*` artifacts, regardless of this decision's outcome

**Technical Details:** Configuration open-questions table, row 3. Explicitly out of E4
scope to build; this ticket is the decision only.

**Dependencies:** E4.10
**Agents:** Atlas (voyager-architect), Compass (voyager-product-manager) — **requires
user decision** (architecture/new-module checkpoint)
**Estimate:** S

---

### E4.14 — Secrets bootstrap and `VOYAGER_PERSISTENCE=off` no-op path

**Description**
Implement the secret-handling contract (`Secret` usage, `VOYAGER_DB_PASSWORD_FILE`
convention, fail-fast on missing required secret) and the `VOYAGER_PERSISTENCE=off`
no-op repository path this epic needs before `voyager-persistence` exists in E5.

**Acceptance Criteria**
- [ ] `VOYAGER_PERSISTENCE=off` binds a no-op repository and logs a `WARN` line on every
      start (not once) stating no records will be persisted — matches spec's explicit
      "This is what E4 uses before `voyager-persistence` exists"
- [ ] `VOYAGER_PERSISTENCE` unset or `required` with a missing/unusable credential exits
      non-zero before `bootstrap.start(...)` — no socket bound, no player can connect
- [ ] `VOYAGER_DB_PASSWORD_FILE` convention implemented (path-to-secret-file), with a test
      covering both the direct-env and file-based paths
- [ ] No credential is ever passed as a `-D` system property (ArchUnit or code-review
      check: no `voyager.db.password`-shaped system property read)
- [ ] `DatabaseConfig` (even as a stub type at this stage) carries no credential defaults
      — no repeat of the current tree's `DEFAULT_USERNAME = "voyager-project"` /
      empty-password default

**Dependencies:** E4.9
**Agents:** Forge (voyager-senior-backend), Hangar (voyager-devops-expert)
**Estimate:** M

---

## E5 — `voyager-persistence` — records and profiles survive restart

### Epic ticket: [E5] voyager-persistence — records and profiles survive restart

**Description**
Implement the Hibernate/HikariCP/MariaDB persistence layer behind ports, with no
persistence context (`StatelessSession` only), no entity associations, atomic writes, and
a tick-safe fire-and-forget submission path with an outbox.

**Acceptance Criteria**
- [ ] A player finishes a race, the server restarts, and the record and profile are
      readable afterward — literal spec Done condition, verified with a restart test
      against a Testcontainers MariaDB instance
- [ ] No class in the module calls `SessionFactory.openSession`/`inSession`/
      `fromSession`/`createEntityManager` (ArchUnit rule with real subjects now)
- [ ] No field in `..persistence.entity..` carries `@ManyToOne`/`@OneToMany`/
      `@OneToOne`/`@ManyToMany` (ArchUnit rule)
- [ ] No entity type is a parameter or return type of any public method outside
      `..persistence.entity..` (ArchUnit rule)
- [ ] `voyager-api` references neither `jakarta.persistence..` nor `org.hibernate..`
      (ArchUnit rule)
- [ ] No class in `..race..` or `..platform..system..` calls
      `CompletableFuture.get`/`join`/`Future.get` (tick-safety ArchUnit rule)
- [ ] The four critical queries (leaderboard, cup standings, profile-on-join × 3, map
      record) each carry an `EXPLAIN` assertion in the integration suite: no `Using
      filesort`, no `Using temporary`, no `type: ALL` on any table above a thousand rows
- [ ] A single `V1__baseline.sql` Flyway migration exists; `baselineOnMigrate=false`,
      `validateOnMigrate=true`, `cleanDisabled=true`; `hbm2ddl.auto=validate` in every
      environment including local dev

**Technical Details**
- Nine tables per spec's Entity model table: `player`, `player_statistics`, `cup`, `map`,
  `cup_session`, `race_session`, `race_result`, `cup_result`, `map_personal_best`
- `map_personal_best` upsert via `ON DUPLICATE KEY UPDATE` with `LEAST`/conditional
  `IF(...)` — single statement, no read-modify-write
- Two executors: `voyager-db-read` (fixed, 4), `voyager-db-write` (single); nothing uses
  `ForkJoinPool.commonPool()`
- `ProfileSlot` sealed (`Pending`/`Loaded`/`Failed`) models join-time profile absence in
  the type system, no null, no polled future
- Completions re-enter through a bounded MPSC queue drained at a fixed point at tick
  start, under budget
- Reads: HQL constructor expressions directly into `voyager-api` records, never an entity
- Writes: entities constructed and inserted inside the transaction, never leave it;
  `@NamedQuery` for every HQL string so malformed queries fail at `SessionFactory`
  bootstrap
- **Open question resolution embedded here:** "Are tuning revisions worth segmenting
  leaderboards by, or only worth recording?" — per spec's own resolution, record the
  revision from E5 regardless (see E5.1 AC); segmentation is a deliberately deferred
  product decision, not built in this epic

**Dependencies:** E1 (fitness rules), E4 (a server to persist from; E4.14's no-op path is
what this epic replaces with a real implementation)

**Estimate:** XL — see sub-tickets.

---

### E5.1 — Entity model and `V1__baseline.sql`

**Description**
Implement all nine entity classes and the single baseline Flyway migration.

**Acceptance Criteria**
- [ ] All nine tables from the spec's Entity model table exist in `V1__baseline.sql` with
      the specified PKs and notable columns
- [ ] `race_result` includes `validation_state` and `plausibility_error` columns, written
      for every row from day one (`validation_state = VALID` for all v1 rows per spec)
- [ ] `map_personal_best` is scoped by `layout_version`; `map.layout_version` derives from
      a content hash of the map configuration
- [ ] **Tuning revision is recorded**: `race_result` (and/or `cup_result`) carries the
      `TuningConfig` revision/hash from E3.5 alongside every row — this closes the fourth
      configuration open question's "record regardless" resolution; a follow-up ticket
      (not in this epic) is filed to the backlog for leaderboard segmentation as a
      separate product decision, referenced but not built here
- [ ] Timestamps are `DATETIME(6)` UTC; durations are `INT UNSIGNED` milliseconds
- [ ] Two indexes on `map_personal_best`: unique `(map_id, layout_version, game_mode,
      player_id)` and `(map_id, layout_version, game_mode, best_time_ms)`
- [ ] Every entity class is `@Entity`, non-final, has a no-arg constructor, declares no
      association (ArchUnit rule from the fitness module's entity exception)

**Dependencies:** E1.4, E3.5 (tuning revision field), E4.6 (map layout source)
**Agents:** Vault (voyager-database-expert)
**Estimate:** L

---

### E5.2 — Repository ports and `StatelessSession`-only implementation

**Description**
Define repository ports in `voyager-api` (not sealed) and implement them in
`voyager-persistence` using `StatelessSession` exclusively.

**Acceptance Criteria**
- [ ] `PlayerProfileStore`, `RaceArchive`, `CupArchive`, `LeaderboardQuery` ports live in
      `voyager-api`, are plain interfaces (not `sealed`), reimplementable by an in-memory
      fake for `voyager-race` tests
- [ ] `PersistenceProvider` is `sealed … permits DefaultPersistenceProvider` with a static
      `create(PersistenceSettings settings)` factory
- [ ] Every write method on the ports returns `void`
- [ ] No method anywhere in the implementation opens a stateful `Session`

**Dependencies:** E5.1
**Agents:** Vault (voyager-database-expert), Forge (voyager-senior-backend)
**Estimate:** L

---

### E5.3 — Read path: HQL constructor expressions

**Description**
Implement every read query as a `@NamedQuery` HQL constructor expression producing
`voyager-api` records directly, per the `TOP_BY_MAP` example in the spec.

**Acceptance Criteria**
- [ ] Leaderboard, cup standings, and profile-on-join queries all return records, never an
      entity, never a detached-entity pattern
- [ ] All HQL strings are `@NamedQuery`; a Testcontainers test executes every named query
      against a Flyway-migrated schema and asserts it does not fail at bootstrap
- [ ] Cup standings is one flat query (no N+1-inside-N+1); grouping by leg happens in Java
- [ ] Profile-on-join is exactly three queries in one transaction (identity+statistics,
      recent results, personal bests) — no joined-collection cartesian product

**Dependencies:** E5.2
**Agents:** Vault (voyager-database-expert)
**Estimate:** M

---

### E5.4 — Write path: atomic upsert and one-transaction race completion

**Description**
Implement the `map_personal_best` atomic upsert and the single-transaction race
completion (close `race_session`, batch-insert `race_result`s, upsert personal bests,
increment statistics).

**Acceptance Criteria**
- [ ] `map_personal_best` upsert is the exact single `INSERT ... ON DUPLICATE KEY UPDATE`
      statement from the spec (or an equivalent single-statement form) — no read then
      write
- [ ] A finished race with N finishers commits as one transaction; a forced failure
      mid-transaction (test injects one) leaves zero partial rows
- [ ] `hibernate.jdbc.batch_size=50` configured; JDBC URL includes
      `rewriteBatchedStatements=true`; a test confirms N inserts produce fewer round trips
      than N (batching verified, not just configured)
- [ ] Concurrent-finisher test: two simulated finishers in overlapping transactions never
      lose an update to the personal best (the exact bug this design eliminates
      structurally)

**Dependencies:** E5.1, E5.2
**Agents:** Vault (voyager-database-expert), Piston (voyager-java-performance) for batch
verification
**Estimate:** L

---

### E5.5 — Tick-safe submission: executors, bounded queue, budgeted drain

**Description**
Implement the two named executors, the bounded MPSC completion queue drained at tick
start under budget, and the outbox/retry path for failed writes.

**Acceptance Criteria**
- [ ] `voyager-db-read` (fixed, 4 platform threads) and `voyager-db-write` (single
      platform thread) are the only executors used; `ForkJoinPool.commonPool()` never
      appears (ArchUnit or grep check)
- [ ] A race-end `submit()` call returns `void` immediately; a load test bursts 50
      simultaneous completions and confirms the tick budget is never exceeded (backlog
      grows, no tick is dropped)
- [ ] A failed `offer()` is logged as an incident, never blocks
- [ ] Failed transactions land in a retry queue with backoff; `shutdown(Duration)` drains
      the queue before `SessionFactory` closes — a test kills the process mid-backlog and
      confirms no data loss on a clean `shutdown(Duration)` call
- [ ] IO callbacks never mutate ECS state directly — verified by an ArchUnit rule that no
      class in the executor/callback path calls into `..race..system..` types except via
      the `PersistenceEvent` queue

**Dependencies:** E5.2, E4.8 (composition root to wire executors into)
**Agents:** Forge (voyager-senior-backend), Piston (voyager-java-performance), Lattice
(voyager-senior-ecs) for the tick-side drain integration
**Estimate:** L

---

### E5.6 — `ProfileSlot` and join-time profile load

**Description**
Implement the sealed `ProfileSlot` (`Pending`/`Loaded`/`Failed`) and wire profile loading
to start on join without stalling the lobby.

**Acceptance Criteria**
- [ ] A player whose profile is still `Pending` renders default cosmetics in the lobby;
      no code path blocks waiting for `Loaded`
- [ ] Systems pattern-match on `ProfileSlot` via `switch`; no `null` profile reference
      exists anywhere in the tick path
- [ ] A simulated slow database (artificial delay) does not delay lobby join at all,
      confirmed by a timed test

**Dependencies:** E5.3, E5.5
**Agents:** Lattice (voyager-senior-ecs), Forge (voyager-senior-backend)
**Estimate:** M

---

### E5.7 — HikariCP configuration

**Description**
Configure the connection pool per the spec's table (size, timeouts, isolation level, JDBC
URL parameters).

**Acceptance Criteria**
- [ ] `maximumPoolSize=10`, `minimumIdle=10`, `connectionTimeout=5000`,
      `maxLifetime=1500000`, `keepaliveTime=120000` all set as documented
- [ ] `leakDetectionThreshold=10000` in dev/staging profile, `0` in production profile
      (profile-conditional, not hardcoded to one value)
- [ ] `transactionIsolation=READ_COMMITTED` explicitly set (not MariaDB's
      `REPEATABLE_READ` default) — a test demonstrates the gap-lock serialization this
      avoids, or documents why it could not be demonstrated in a unit/integration test
- [ ] JDBC URL includes `cachePrepStmts=true`, `useServerPrepStmts=true`,
      `prepStmtCacheSize=250`, `connectionTimeZone=UTC`,
      `forceConnectionTimeZoneToSession=true`
- [ ] Hikari `pendingThreads` gauge is exported (ties into E4's tick-budget metrics
      infrastructure)

**Dependencies:** E5.2
**Agents:** Vault (voyager-database-expert), Piston (voyager-java-performance)
**Estimate:** M

---

### E5.8 — Testcontainers integration suite

**Description**
Build the single highest-value test the spec names: start a MariaDB Testcontainer, run
`migrate()`, build the `SessionFactory` in `validate` mode, execute every named query.

**Acceptance Criteria**
- [ ] One test class: Testcontainers MariaDB → Flyway `migrate()` → `SessionFactory` in
      `hbm2ddl.auto=validate` mode → every `@NamedQuery` executed at least once
- [ ] Test catches entity/schema drift (deliberately break one entity field in a draft PR
      during ticket work and confirm the test fails) — this is a self-check, not part of
      the shipped test
- [ ] `EXPLAIN` assertions for the four critical queries (leaderboard, cup standings,
      profile-on-join, map record) are part of this suite, not a separate one
- [ ] Suite runs in CI on every PR touching `voyager-persistence`

**Dependencies:** E5.1–E5.4
**Agents:** Quench (voyager-senior-testing), Vault (voyager-database-expert)
**Estimate:** M

---

### E5.9 — Rule 7 injectables: `SessionFactoryCreator`, `PersistenceClock`

**Description**
Implement the two rule-7 functional-interface creators this module needs:
`SessionFactoryCreator` (test substitutes a Testcontainers factory) and `PersistenceClock`
(`Instant now()`, replacing scattered `LocalDateTime.now()` calls).

**Acceptance Criteria**
- [ ] No class in `voyager-persistence` calls `LocalDateTime.now()`/`Instant.now()`
      directly outside the `PersistenceClock` default implementation (ArchUnit rule)
- [ ] A test substitutes a fixed `PersistenceClock` and asserts deterministic timestamps
      in a written entity
- [ ] `SessionFactoryCreator` is used by E5.8's Testcontainers suite as the substitution
      point

**Dependencies:** E5.1, E5.8
**Agents:** Vault (voyager-database-expert)
**Estimate:** S

---

### E5.10 — `CLAUDE.md` amendment: rule 8 `*Adapter` scope extension

**Description**
Extend rule 8's `*Adapter` naming convention to cover row-to-record adapters in
`..persistence.adapter`, as the spec explicitly flags as requiring approval rather than
assumption.

**Acceptance Criteria**
- [ ] A one-line amendment to `CLAUDE.md` rule 8 is drafted, explicitly presented to the
      project owner for approval before merge (per spec: "must be approved, not assumed")
- [ ] Once approved, any row-to-record adapter classes in the module follow the amended
      convention

**Dependencies:** E1.5 (the rewritten `CLAUDE.md` this amends), E5.3
**Agents:** Scribe (voyager-tech-writer) drafts; **requires explicit user approval**
(Human in the Loop checkpoint — CLAUDE.md changes are named explicitly as requiring
approval in the agent workflow rules)
**Estimate:** S

---

## E6 — `voyager-setup` on Minestom

### Epic ticket: [E6] voyager-setup on Minestom

**Description**
Move the setup server from Paper to Minestom, removing Paper from the project entirely
and unblocking Java 25 everywhere. **This epic is blocked on an unresolved research
question and must not be scheduled for implementation until E6.0 closes.**

**Acceptance Criteria**
- [ ] A map is configurable (created, edited, saved) on the Minestom setup server with no
      Paper dependency anywhere in `voyager-setup` — literal spec Done condition
- [ ] `voyager-setup` depends only on `api, platform` per the module graph; zero
      `org.bukkit..` references (ArchUnit rule)
- [ ] The Anvil format compatibility question between setup and game server is verified
      as resolved by construction: both sides now use the same Minestom/Anvil loader, so
      no separate cross-compatibility test is needed beyond the loader's own tests
      (already covered by E4.6) — this ticket records that the risk is closed, not newly
      tested
- [ ] Map-editing functionality has a concrete replacement for whatever FastAsyncWorldEdit
      provided, chosen and justified by E6.0's findings — not "TBD" left in the shipped
      epic

**Technical Details**
Spec, "Setup" section: "**Open risk:** FastAsyncWorldEdit has no Minestom equivalent. Map
editing must be redesigned. This requires its own research epic before E6 is planned and
is deliberately not resolved in this document." This epic ticket is written now so the
work is ready to transfer, but **E6.0 must complete and be reviewed with the project
owner before any E6.1+ sub-ticket is scheduled.**

**Dependencies:** E1, E4 (platform layer to build on); **E6.0 (blocking, must close
first)**

**Estimate:** XL — see sub-tickets. Do not size the post-E6.0 sub-tickets precisely until
E6.0's findings are known; estimates below are placeholders subject to revision.

---

### E6.0 — Research: map-editing replacement for FastAsyncWorldEdit on Minestom

**Description**
Own research epic, explicitly required before E6 can be planned. Determine how map
editing (region selection, copy/paste, undo, schematic import/export — whatever subset of
FAWE's functionality the setup workflow actually uses) will work without Paper/FAWE on a
Minestom setup server.

**Acceptance Criteria**
- [ ] Current FAWE usage in `plugins/setup` is inventoried: exact operations used (not
      "FAWE" as a whole), so the replacement scope is bounded by actual need, not by
      FAWE's full feature set
- [ ] At least two candidate approaches are evaluated with concrete pro/contra (examples
      to investigate, not prescribed: a Minestom-native region-edit tool if one exists;
      hand-rolled block-region operations sufficient for the actual inventory above; a
      hybrid where heavy editing still happens on a throwaway Paper+FAWE instance and only
      the result is imported into the Minestom setup flow)
- [ ] A recommendation is presented to the project owner via `AskUserQuestion` with
      trade-offs, per the Decision Framework — this research ticket does not conclude with
      a unilateral choice
- [ ] Finding is written into `docs/research/` (Lumen's domain) documenting methodology
      and rationale, not just the conclusion
- [ ] **E6 epic and E6.1+ sub-tickets are not scheduled for implementation until this
      ticket's recommendation is approved by the project owner**

**Technical Details:** Risk register: "FAWE has no Minestom equivalent" — Impact: "Blocks
E6". Resolution: "Own research epic before E6 planning." This ticket is that epic.

**Dependencies:** None (can start any time; does not block E1–E5)
**Agents:** Scout (voyager-researcher) leads, Origami (voyager-paper-expert) for FAWE
usage inventory (last use of Paper-domain knowledge in this project), Atlas
(voyager-architect) for the architectural trade-off framing, Lumen (voyager-scientist) for
the written findings.
**Estimate:** L (research), with the explicit caveat that this is a spike whose output is
a decision, not code.

---

### E6.1 — Setup server bootstrap on Minestom

**Description**
Stand up `voyager-setup` as a Minestom application (own composition root), removing the
Paper plugin entry point.

**Acceptance Criteria**
- [ ] `voyager-setup` has its own `main()`/bootstrap, Guice composition root per E4.8's
      pattern, zero `org.bukkit..` imports
- [ ] Depends only on `api, platform` per the module graph

**Dependencies:** E6.0 (recommendation approved), E4.8
**Agents:** Helix (voyager-minestom-expert)
**Estimate:** M (placeholder, pending E6.0)

---

### E6.2 — Map editing tool implementation

**Description**
Implement the map-editing capability chosen and approved in E6.0.

**Acceptance Criteria:** Defined once E6.0 closes and the approach is chosen — placeholder
until then. At minimum: a map builder can perform the inventoried FAWE-equivalent
operations from E6.0 on the Minestom setup server.

**Dependencies:** E6.0, E6.1
**Agents:** Helix (voyager-minestom-expert), Spark (voyager-junior-creative) if a novel
approach is chosen
**Estimate:** XL (placeholder, pending E6.0 — likely needs its own breakdown)

---

### E6.3 — Conversation-API verification on Minestom

**Description**
Confirm `shared/conversation-api`'s platform-agnostic design (already Bukkit-free per
`CLAUDE.md`) works unmodified for the Minestom setup wizard, or port it into the new
module tree if it needs a new home.

**Acceptance Criteria**
- [ ] Conversation/prompt wizard flow (map/cup/portal configuration) runs against the
      Minestom setup server end-to-end
- [ ] No Paper type is referenced anywhere in the conversation flow

**Dependencies:** E6.1
**Agents:** Helix (voyager-minestom-expert), Forge (voyager-senior-backend)
**Estimate:** M

---

### E6.4 — Cross-server Anvil compatibility confirmation

**Description**
Confirm (rather than newly build) that maps saved by the Minestom setup server load
correctly on the Minestom game server, closing the Anvil-compatibility risk the spec notes
disappears once both sides share the same loader.

**Acceptance Criteria**
- [ ] A map created/edited entirely on `voyager-setup` loads without error or data loss on
      `voyager-server` (E4.6's loader), verified with an integration test spanning both
      modules
- [ ] The former cross-format risk (Paper 1.21.5 setup vs. Minestom 26.2 game, data
      version 4903) is recorded as closed in this ticket, with the reasoning ("both sides
      now use the same library") stated explicitly rather than assumed

**Dependencies:** E6.1, E6.2, E4.6
**Agents:** Helix (voyager-minestom-expert), Quench (voyager-senior-testing)
**Estimate:** M

---

## E7 — Cut-over

### Epic ticket: [E7] Cut-over — old tree removed, Java 25 everywhere

**Description**
Remove the old module tree once the cut-over gate is satisfied (E4 reached and the E2
trace suite green — **not** feature parity with the current Java tree), and complete the
documentation transition (fresh ADR series, archived old ADRs, replaced migration status
doc).

**Acceptance Criteria**
- [ ] `server/`, `plugins/game/`, `plugins/setup/`, and all four `shared/*` modules are
      removed from `settings.gradle.kts` and deleted from the repository
- [ ] The ~34 tests exclusively covering dead code (`GameLoopSystemTest`,
      `GameSessionTest`, `CupFlowServiceTest`, `CupScoringTest`, and siblings) are removed
      along with the code they tested — not left as an orphaned suite
- [ ] `./gradlew build` succeeds with only the eight new modules in the tree; Java 25
      `--release` applies everywhere (no Java 21 `--release` block remains anywhere in the
      build)
- [ ] Old `shared/database` Flyway `V1`–`V4` migrations are deleted (not archived in the
      active migration path) — `voyager-persistence`'s `V1__baseline.sql` is the only
      active migration history going forward
- [ ] A fresh ADR series starts at `docs/decisions/0001-...`; the existing ADRs
      (`0001`–`0011`, minus the never-written `0002`) move to `docs/decisions/archive/`
- [ ] `docs/migration/status.md` is replaced (not patched) with accurate module layout,
      real test counts, and real milestone status
- [ ] CI, Release Please, and Renovate configs require no changes beyond removing
      references to deleted module paths

**Technical Details**
Cut-over gate restated precisely (do not let this drift when transferred): **E4 reached +
green E2 trace suite.** E5 (persistence) and E6 (setup) may still be open when E7 starts —
"Everything from E5 onward may follow the cut" is explicit in the spec. E7 removes the old
tree; it does not wait for E5/E6 to finish.

**Dependencies:** E4 (reached) + E2 (trace suite green) — the cut-over gate. Does **not**
strictly require E5 or E6 to be complete.

**Estimate:** L — see sub-tickets.

---

### E7.1 — Remove old module tree

**Description**
Delete `server/`, `plugins/game/`, `plugins/setup/`, `shared/common`,
`shared/conversation-api`, `shared/database`, `shared/spline` and their entries in
`settings.gradle.kts`.

**Acceptance Criteria**
- [ ] All listed paths removed from the filesystem and from `settings.gradle.kts`
- [ ] `./gradlew build` succeeds against the remaining eight-module tree
- [ ] The ~34 dead-code tests named in the spec's "Removed at cut-over" list are confirmed
      gone (not just the production classes)
- [ ] No remaining module references a deleted package (compile-time enforced, not just
      searched)

**Dependencies:** Cut-over gate (E4 + green E2 trace suite)
**Agents:** Atlas (voyager-architect), Hangar (voyager-devops-expert) for CI cleanup
**Estimate:** M

---

### E7.2 — Archive old ADRs, start fresh series

**Description**
Move `docs/decisions/0001`–`0011` (existing files) to `docs/decisions/archive/`; begin a
new ADR series at `docs/decisions/0001-...` for the new stack.

**Acceptance Criteria**
- [ ] All existing ADR files present on disk today move to `docs/decisions/archive/`
      unmodified except for a header note marking them historical/superseded
- [ ] New `docs/decisions/0001-...` exists for at least one real decision from this
      rebuild (candidate: the module architecture itself, or D10's DI choice)
- [ ] Any doc still linking to the never-written `docs/decisions/0002-elytra-flight-
      client-authority.md` is corrected (this was already flagged in E1.5; this ticket is
      the final check that it didn't regress)

**Dependencies:** E7.1
**Agents:** Scribe (voyager-tech-writer)
**Estimate:** S

---

### E7.3 — Replace `docs/migration/status.md`

**Description**
Replace the stale migration status document with one describing the completed cut-over
state.

**Acceptance Criteria**
- [ ] Document is a full replacement, not a patch (per spec: "replaced, not patched")
- [ ] Module layout, test count, and milestone status are all verified against the actual
      repository state at time of writing, not carried over from the old document

**Dependencies:** E7.1
**Agents:** Scribe (voyager-tech-writer)
**Estimate:** S

---

### E7.4 — Delete old Flyway migrations

**Description**
Delete the `shared/database` `V1`–`V4` migration scripts as part of the module removal
(called out separately because it's easy to accidentally leave migration history behind
even after the module itself is deleted).

**Acceptance Criteria**
- [ ] No `V1`–`V4` files from the old `shared/database` remain anywhere in the repository
- [ ] `voyager-persistence`'s `V1__baseline.sql` is confirmed as the sole active migration
      history (no accidental version collision)

**Dependencies:** E7.1, E5.1 (persistence's own baseline must exist first)
**Agents:** Vault (voyager-database-expert)
**Estimate:** S

---

### E7.5 — Final documentation and Java-25-everywhere pass

**Description**
Confirm and document that Java 25 applies across the entire remaining tree, and do a
final consistency pass on `CLAUDE.md` now that the old tree is gone.

**Acceptance Criteria**
- [ ] No `--release 21` (or any non-25 release flag) remains in any `build.gradle.kts`
- [ ] `CLAUDE.md`'s "removed at E7 cut-over, present during migration" language (added in
      E1.5) is updated to reflect that removal has happened
- [ ] Build commands section re-verified against the real, now old-tree-free repository

**Dependencies:** E7.1, E7.2, E7.3
**Agents:** Scribe (voyager-tech-writer)
**Estimate:** S

---

### E7.6 — Cut-over announcement

**Description**
Announce the completed migration to the community once the cut-over is merged.

**Acceptance Criteria**
- [ ] Announcement drafted referencing the real, shipped state (not aspirational) —
      Java 25, Minestom everywhere, old Paper-based tree retired
- [ ] Reviewed by the project owner before posting (Human in the Loop — community-facing
      communication)

**Dependencies:** E7.1–E7.5
**Agents:** Beacon (voyager-social-media)
**Estimate:** S

---

## Risk-register cross-reference

Every row from the design spec's "Risks and open questions" table, and where it lands.

| Risk | Where it's handled |
|---|---|
| FAWE has no Minestom equivalent | E6.0 (blocking research epic, precedes all of E6) |
| `air_drag_modifier` may alter the 26.2 elytra drag path | E2.3 (decompile check, blocks E2 completion) |
| Trace tolerances set too tight or too loose | E2.6 (calibration against first real traces) |
| Plausibility thresholds reject legitimate fast pilots | E4.4 (log-only v1 by design, not armed) |
| Minecraft 26.3 ships during the rebuild | Not a standalone ticket — re-check `releases.atom` is a standing item for whoever plans the E4 iteration boundary; flagged here so it is not forgotten. Recommend Flightplan (voyager-project-manager) own this as a recurring check during E4 sprint planning. |
| Vanilla 26.2 recording setup is more work than estimated | E2.1 (prototype spike, time-boxed, precedes E2.2) |
| Server-side recording lacks the client's internal velocity | Accepted risk, no mitigation ticket — documented in E2.2's AC as an explicit non-goal |
| `io.airlift:guice` is a single-vendor fork | E4.8 (pin exact version, documented re-check trigger before any JDK 26 migration) |

## Configuration open-questions cross-reference

| Question | Where it's handled |
|---|---|
| Does the CloudNet wrapper propagate node env vars to every service? | E4.12 (spike, before E5) |
| Which CloudNet RC does the organisation pin? | E4.0 (spike, before E4's CloudNet work starts) |
| Is `voyager-cloudnet-bridge` needed for post-cup lobby routing? | E4.13 (decision ticket during E4 planning) |
| Are tuning revisions worth segmenting leaderboards by? | Recorded regardless from E5.1; segmentation explicitly deferred to a backlog item, not built in E1–E7 |

---

## Notes for transfer to GitHub Issues

- Create epic issues first (7 issues, one per E1–E7), each labeled `epic`; then create
  sub-tickets with "part of #<epic-number>" in the description and a matching epic label
  reference.
- E6's epic issue should be created with a clear "blocked" label/status until E6.0 closes
  and its recommendation is approved — do not let E6.1+ sub-tickets look actionable before
  that approval lands.
- Suggested labels beyond `epic`: `physics`, `race`, `platform`, `persistence`, `setup`,
  `cut-over`, `research-spike`, `blocked`, `needs-owner-decision`.
- Every ticket marked "requires explicit user approval" or "requires user decision" above
  (E1.5, E4.13, E5.10, E6.0's recommendation, E7.6) should carry a `needs-owner-decision`
  label so they surface distinctly from ordinary implementation work.
