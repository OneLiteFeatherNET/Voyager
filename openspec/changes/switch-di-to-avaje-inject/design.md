# Design

## Context

`voyager-server` is the only composition root in the rebuild. It assembles the graph in
`VoyagerServer.main` with `Guice.createInjector(Stage.PRODUCTION, new VoyagerModule(settings))`, and
`VoyagerModule` is an `AbstractModule` with 13 `@Provides` methods, all `@Singleton`. The container appears in
three places outside the module: the dependency in `voyager-server/build.gradle.kts`, the `VoyagerServer`
bootstrap, and `ApiPurityTest`, whose rules forbid `com.google.inject..` and `io.airlift..` outside
`voyager-server`. Decision D10 of the greenfield design (table row, "Dependency injection" section, risk row)
and `CLAUDE.md` "Key Decisions" record Guice. See proposal.md for why the switch is made.

Relevant constraints: Java 25; Minestom ticks at 20 TPS, so nothing may resolve beans on the tick path; the
startup order in `VoyagerServer` (settings, `MinecraftServer.init()`, graph, world opening, events, listen) must
stay; the version catalog is programmatic in `settings.gradle.kts`; there is no `module-info.java` anywhere in
the rebuild and none is added.

## Goals / Non-Goals

**Goals:**
- Wiring errors become build errors for every bean the composition root declares (spec: missing or ambiguous wiring).
- Startup behaviour, lifetimes and the refusal messages stay as they are (spec: startup, lifetimes).
- (Moved) The system pipeline order is not declared in this change; see Decision 5.
- The architecture suite enforces where DI annotations may appear, with no rule passing vacuously.

**Non-Goals:**
- No behaviour change for players, no change to any domain type's logic.
- No `avaje-inject-javax` artifacts, no AOP or interceptors, no avaje HTTP or config modules.
- No JPMS descriptors (decision recorded in ADR-0015 on the unmerged branch; not contradicted here).
- No change to `voyager-setup` (not yet in the build) and no change to the tree being replaced.
- No annotation on any class in `voyager-api`, `voyager-physics`, `voyager-race` or `voyager-platform`. All four stay
  DI-free; the platform and race classes are wired from `voyager-server` by `@Bean` methods.

## Decisions

### 1. Dependency rule (Clean Architecture)

Dependencies point inward, toward `voyager-api`:

```
voyager-server (composition root: avaje @Factory/@Bean, BeanScope, jakarta @Inject, all DI annotations live here)
   -> voyager-platform (adapters: no DI annotation, constructors called from voyager-server @Bean methods)
      -> voyager-race -> voyager-physics -> voyager-api   (no DI annotation of any kind)
```

| Module | DI annotations allowed | New types in this change |
|---|---|---|
| `voyager-api` | none (no `jakarta.inject`, no `io.avaje.inject`) | none |
| `voyager-physics` | none | none |
| `voyager-race` | none | none |
| `voyager-platform` | none (no `jakarta.inject`, no `io.avaje.inject`) | none; no class is touched |
| `voyager-server` | `jakarta.inject`, `io.avaje.inject` (`@Factory`, `@Bean`, `BeanScope`) | `inject/ServerBeans` (`@Factory`), `inject/RaceBeans` (`@Factory`, `CupSession`) |

`VoyagerModule` is deleted. Its bindings move one-for-one into the two factory classes; the spike task maps
each old `@Provides` to its new `@Bean` so none is dropped silently.

### 2. avaje-inject with JSR-330 annotations, container types only in the root

Chosen: `io.avaje:avaje-inject:12.7` at runtime, `io.avaje:avaje-inject-generator:12.7` as annotation
processor in `voyager-server`, `io.avaje:avaje-inject-test:12.7` for tests. Non-`javax` artifacts only.

Alternatives considered:
- **Keep Guice (`io.airlift:guice:10`).** Rejected: reflection at runtime, and the maintenance risk D10 records.
- **Dagger 2.** Rejected: its component/module model is heavier than the fourteen-odd bindings here, and it is
  not the front-runner that ADR-0013 (unmerged branch) named.
- **Manual wiring, no container.** Viable and what ADR-0013 defers to. Not chosen because the user
  asked for avaje; the cost is that the graph is then only checked by the compiler inside one hand-written
  method, with no `@Bean` list to review. The spec is written so that either approach satisfies it.

### 3. Explicit wiring for cross-module classes

The annotation processor runs in `voyager-server` and sees only the classes compiled there. Spike 1.3 (passed)
showed that `jakarta.inject.@Inject` alone is not enough: a class is wired only when it carries `@Singleton` (or
`@Component`) and is compiled in `voyager-server`. A plain `@Inject` class without a scope annotation fails with
"No dependency provided", and a `@Singleton @Inject` class from `voyager-platform` is not wired from
`voyager-server` at all. Therefore every class in `voyager-platform` and `voyager-race` is wired by a `@Bean`
method in `voyager-server` that spells out its constructor parameters, and no annotation is placed on those
classes (decision 1). Classes that `voyager-server` owns may use `@Singleton` with `@Inject`, but the `@Bean`
form is the default for consistency. A `@Bean` method that falls out of step with its constructor fails
compilation, and the graph test resolves every type.

### 4. Lifecycle: eager construction, BeanScope at the root

`VoyagerServer.main` builds one `BeanScope` after `MinecraftServer.init()` and before any `getInstance`-style
lookup. Every `@Singleton`/`@Bean` the server owns is constructed when the scope is built, because the spec
requires refusal before listening. Spike 1.4 (passed) confirmed eager construction: a singleton is constructed at
`build()` even if the test never requests it. The settings instance is supplied to the builder as a bean; the
exact builder method is checked when task 3.4 compiles. The scope is closed in the existing shutdown task.

### 5. (Moved) Pipeline order is not part of this change

An earlier draft made the per-tick system sequence an explicit unmodifiable `List` built in one `@Bean` method. That
is removed from this change by user decision (2026-10-09). `CupSession.tick()` (`voyager-server`
`game/CupSession.java`, about lines 291-310) is not a list of uniform systems: the flight result is folded into
`lastSimulated`, boosts advance after that, a null driver returns early, and `onUpdate()` is followed by a conditional
post-step that mutates `currentMap`. Turning that into a list is a redesign. It belongs to the cup-slice refactor
(moving `CupSession` to `race.cup`) in change `define-clean-architecture-with-vertical-slices`, which declares the
per-tick step order once and tests it. This change keeps `CupSession.tick()` as it is, and no `List<T>` injection of
steps is introduced.

### 6. Fitness rules

`ApiPurityTest` changes as follows:
- `apiDoesNotDependOnADiContainer` and `physicsDoesNotDependOnADiContainer`: `com.google.inject..` becomes
  `io.avaje.inject..`; `jakarta.inject..` stays.
- `onlyServerDependsOnDiContainer`: forbids both `io.avaje.inject..` and `jakarta.inject..` outside
  `net.elytrarace.voyager.server..`, across every rebuild module.
- A rule forbids `jakarta.inject..` and `io.avaje.inject..` in `voyager-race` and `voyager-platform` (decision 1),
  so the four non-composition modules each carry an explicit rule.
- All rules keep `allowEmptyShould(false)`. `FitnessCoverageTest` keeps seeing each module covered.

### 8. Bean mapping: the two catalogue ports have no separate `@Bean`

`VoyagerModule` provided `MapCatalog` and `CupCatalog` as well as the JSON catalogues. In `ServerBeans` the
`JsonMapCatalog` and `JsonCupCatalog` beans are the only ones. avaje registers each catalogue bean under every interface
it implements, so `MapCatalog` and `CupCatalog` resolve to those singletons. A second `@Bean` returning the same instance
for each port made resolution ambiguous (the compile-time error of spike 1.2). `VoyagerGraphTest` asserts that each port
and its implementation resolve to the same instance. This is a deliberate deviation from the one-to-one mapping in
decision 1; lifetimes and identity stay exactly as before.

### 7. Documentation

- `docs/decisions/0016-replace-guice-with-avaje-inject.md` (MADR 4.0). Number 0016 because `main` ends at
  0011 and 0012 to 0015 are taken on the unmerged `refactor/architecture-ratchet` branch. The ADR cites ADR-0013
  there as prior art: that ADR deferred the container and named avaje as front-runner, and this decision
  takes that step. If the branch merges first with a different number, renumber this file before merge.
- Greenfield design spec: D10 row, "Dependency injection" section, risk row.
- `CLAUDE.md` "Key Decisions" entry for dependency injection.

## Risks / Trade-offs

- **[The processor does not fail on a missing or ambiguous bean]** → Verified by spike 1.1 and 1.2 (passed). A
  missing type is named in the error; an ambiguous type is reported through the conflicting `@Bean` method names,
  not the type. The spec scenario is worded to match.
- **[Cross-module wiring written by hand in `@Bean` methods]** → A `@Bean` method that drifts from its
  constructor fails compilation (argument mismatch) rather than at runtime. The graph test resolves every type.
- **[Shadow jar drops or duplicates avaje's generated or service entries]** → Spike 1.5 (passed) showed the
  generator writes `META-INF/services/io.avaje.inject.spi.InjectExtension` and the shadow jar keeps it. Defensive
  `mergeServiceFiles()` is configured (task 2.3) and checked with `unzip -l | grep services`; `runServer` boot
  is still checked at task 8.2.
- **[Build time grows from the processor]** → Measured before and after in the PR description; accepted if it
  is within a few seconds.
- **[Pipeline order is not checked by this change]** → Accepted. The order stays in `CupSession.tick()`; the cup-slice
  refactor declares and tests it.
- **[Lost `Stage.PRODUCTION` semantics]** → Replaced by eager construction (decision 4) and asserted by a test.
- **[Two DI styles in the codebase while the old tree remains]** → The old tree does not use this container; no
  shared module is touched.
- **[ADR number collision]** → Handled in decision 7.

## Migration Plan

One PR, squash-merged. Commits on the branch, one type each: `build(server)` for the catalog, Gradle and shadow-jar change;
`refactor(server)` for factories, `BeanScope` bootstrap and removal of `VoyagerModule`; `test(server)` for the
graph and startup tests (written first); `test(fitness)` for `ApiPurityTest`; `docs` commits for the spec, `CLAUDE.md` and ADR-0016. The squash commit on `main` is
`refactor(server): replace guice with avaje-inject`.

Rollback: the squash commit is a single revert, which restores Guice, `VoyagerModule` and the rules together.
No data migration, no runtime configuration change.

## Open Questions

- Whether `voyager-setup`'s composition root should share a `@Factory` base type with `voyager-server` is
  deferred until `voyager-setup` exists. It does not change this change's specs or tasks.
