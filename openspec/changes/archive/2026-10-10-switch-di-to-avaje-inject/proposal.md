# Proposal

**Conventional Commits:** `refactor(server)`. Squash-merge PR title: `refactor(server): replace guice with avaje-inject`.

## Why

The rebuild's composition root (`voyager-server`) wires its object graph with `io.airlift:guice:10`, a
reflection-based container chosen in decision D10 of the greenfield design. Guice resolves the graph at
runtime, so a missing or mis-typed binding surfaces as a boot-time exception, and the fork's maintenance
status is the open question D10 itself records as a risk. avaje-inject generates the wiring at compile time
with no reflection, so the same mistakes become build failures, and the tick path stays free of container
machinery. The switch is cheap now: only one module and one `@Provides` module use the container.

## What Changes

- Replace `io.airlift:guice:10` with `io.avaje:avaje-inject:12.7` (runtime) and
  `io.avaje:avaje-inject-generator:12.7` (annotation processor) in `voyager-server`, and
  `io.avaje:avaje-inject-test:12.7` for tests. Declared programmatically in `settings.gradle.kts`, not in a
  version catalog file.
- Replace `VoyagerModule` (AbstractModule with 13 `@Provides` methods) with avaje `@Factory` and `@Bean`
  classes in `voyager-server`. Bindings and their semantics (singletons, the `Supplier<Collection<Player>>`
  that re-reads the roster, the cup consistency check) are preserved.
- `jakarta.inject` (JSR-330) and avaje annotations (`@Factory`, `@Bean`, `@Singleton`) appear only in `voyager-server`,
  the composition root. `voyager-platform` stays annotation-free, like `voyager-api`, `voyager-physics` and
  `voyager-race`: no annotation is added to any of its classes or constructors. Every platform and race class the
  graph needs is constructed by a `@Bean` method in `voyager-server` that calls its constructor (spike 1.3: avaje
  does not wire a class from another module through an annotation).
- `voyager-server` gets `jakarta.inject-api:2.0.1` as its only `jakarta.inject` dependency, and its shadow jar is
  configured with `mergeServiceFiles()` so avaje's `META-INF/services` entries survive packaging.
- `VoyagerServer.main` builds the graph with `BeanScope` instead of `Guice.createInjector(Stage.PRODUCTION, ...)`
  and keeps its existing order: settings, `MinecraftServer.init()`, graph, world opening, events, listen.
- (Moved) The system pipeline order is no longer part of this change. `CupSession.tick()` is not a list of uniform
  systems (the flight result is folded into `lastSimulated`, boosts advance after it, a null-driver early return,
  and a conditional post-step mutates `currentMap`), so making it a list is a redesign. It belongs to the cup-slice
  refactor in `define-clean-architecture-with-vertical-slices` (decided by the user, 2026-10-09).
- `ApiPurityTest` rules that forbid `com.google.inject..` and `io.airlift..` outside the composition root now
  forbid `io.avaje.inject..` instead. `jakarta.inject..` and `io.avaje.inject..` are forbidden in `voyager-api`,
  `voyager-physics`, `voyager-race` and `voyager-platform`, and outside `voyager-server` in every module.
- Documentation describing the swap is updated in the same change: greenfield design spec (D10 row, the
  "Dependency injection" section, the risk table row), `CLAUDE.md` "Key Decisions", and a new ADR
  `docs/decisions/0016-*.md` (MADR 4.0) that references ADR-0013 from `refactor/architecture-ratchet` as prior art.
- Accepted deviation: no separate `@Bean` for the ports `MapCatalog` and `CupCatalog`. avaje registers
  `JsonMapCatalog` and `JsonCupCatalog` under the interfaces they implement; a second `@Bean` returning the same
  instance made resolution ambiguous. The ports resolve to the same singletons (see design.md, decision 8).
- **BREAKING** for internal code only: `VoyagerModule` is removed; no external API changes.

Why the docs ride along: the D10 text and `CLAUDE.md` would be false the moment the code merges without them,
and the ADR is the decision record for the swap itself. They are separate `docs(...)` commits inside the same
PR, so the squash commit on `main` carries one type (`refactor`) and the commit-per-type rule holds on the branch.

Out of scope: `voyager-setup` (arrives in a later stage; it will adopt the same composition-root rules when
it lands), the tree being replaced (`server`, `plugins/*`, `shared/*`), any change to domain behaviour, and
any slice (race, ring, cup, map setup) other than the wiring that connects them.

## Capabilities

### New Capabilities
- `server/dependency-injection`: how the rebuilt server assembles its object graph: compile-time wiring,
  fail-at-build semantics for missing or ambiguous beans, the explicit ordering of the system pipeline, and
  the rule that DI annotations stay inside the composition roots. The change is recorded as a spec because
  these properties are contracts other modules and the fitness suite rely on, even though the feature
  behaviour seen by players does not change.

### Modified Capabilities
- None. No existing capability under `openspec/specs/` covers dependency injection or the server boot sequence.

## Impact

- **Build:** `voyager-server/build.gradle.kts` (dependency swap, processor added, `jakarta.inject-api` added,
  shadow jar `mergeServiceFiles()`, `guiceVersion` removed);
  `settings.gradle.kts` (catalog entries). Build-time cost of the annotation processor is expected to be small.
- **Code:** `voyager-server` (`VoyagerServer`, `inject/VoyagerModule` replaced, new factory classes), a comment in
  `game/CupSession.java` that names the Guice module. `voyager-platform` and `voyager-race` source is unchanged.
- **Tests:** new composition-root test asserting the graph builds and every bean `VoyagerModule` provides
  today resolves; `ApiPurityTest` updated. The compile-time behaviour for missing and
  ambiguous beans is verified by spike 1.1 and 1.2 (recorded in tasks.md), not by a permanent test.
- **Fitness:** `voyager-fitness` `ApiPurityTest`. `FitnessCoverageTest` must still see every module covered.
- **Docs:** spec D10, `CLAUDE.md`, ADR-0016.
- **Runtime:** none expected for players; boot still fails fast if the graph is incomplete.
