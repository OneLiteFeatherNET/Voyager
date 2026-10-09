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
- Annotate constructors of classes wired by the composition root with JSR-330 `jakarta.inject` (`@Inject`,
  `@Singleton`, `@Named`) in `voyager-platform` and `voyager-server`. Avaje-specific annotations
  (`@Factory`, `@Bean`) appear only in `voyager-server`.
- `VoyagerServer.main` builds the graph with `BeanScope` instead of `Guice.createInjector(Stage.PRODUCTION, ...)`
  and keeps its existing order: settings, `MinecraftServer.init()`, graph, world opening, events, listen.
- The system pipeline order, which is a correctness property, becomes an explicit unmodifiable `List` built in
  one `@Factory` `@Bean` method, not an injected collection. Avaje does not guarantee order for `List<T>`
  injection.
- `ApiPurityTest` rules that forbid `com.google.inject..` and `io.airlift..` outside the composition root now
  forbid `io.avaje.inject..` instead. `jakarta.inject..` stays forbidden in `voyager-api`, `voyager-physics`
  and `voyager-race`.
- Documentation describing the swap is updated in the same change: greenfield design spec (D10 row, the
  "Dependency injection" section, the risk table row), `CLAUDE.md` "Key Decisions", and a new ADR
  `docs/decisions/0016-*.md` (MADR 4.0) that references ADR-0013 from `refactor/architecture-ratchet` as prior art.
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

- **Build:** `voyager-server/build.gradle.kts` (dependency swap, processor added, `guiceVersion` removed);
  `settings.gradle.kts` (catalog entries). Build-time cost of the annotation processor is expected to be small.
- **Code:** `voyager-server` (`VoyagerServer`, `inject/VoyagerModule` replaced, new factory classes),
  `voyager-platform` (`jakarta.inject` on constructors only), a comment in `game/CupSession.java` that names
  the Guice module.
- **Tests:** new composition-root test asserting the graph builds and every bean `VoyagerModule` provides
  today resolves; pipeline-order test; spike test proving a missing bean fails compilation; `ApiPurityTest` updated.
- **Fitness:** `voyager-fitness` `ApiPurityTest`. `FitnessCoverageTest` must still see every module covered.
- **Docs:** spec D10, `CLAUDE.md`, ADR-0016.
- **Runtime:** none expected for players; boot still fails fast if the graph is incomplete.
