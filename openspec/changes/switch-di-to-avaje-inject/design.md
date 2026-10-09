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
- The system pipeline order is declared once, as an explicit list (spec: pipeline order).
- The architecture suite enforces where DI annotations may appear, with no rule passing vacuously.

**Non-Goals:**
- No behaviour change for players, no change to any domain type's logic.
- No `avaje-inject-javax` artifacts, no AOP or interceptors, no avaje HTTP or config modules.
- No JPMS descriptors (decision recorded in ADR-0015 on the unmerged branch; not contradicted here).
- No change to `voyager-setup` (not yet in the build) and no change to the tree being replaced.
- No constructor-injection rewrite of `voyager-api`, `voyager-physics` or `voyager-race`. Those stay DI-free.

## Decisions

### 1. Dependency rule (Clean Architecture)

Dependencies point inward, toward `voyager-api`:

```
voyager-server (composition root: avaje @Factory/@Bean, BeanScope, jakarta @Inject allowed)
   -> voyager-platform (adapters: jakarta @Inject on constructors allowed, no avaje types)
      -> voyager-race -> voyager-physics -> voyager-api   (no DI annotation of any kind)
```

| Module | DI annotations allowed | New types in this change |
|---|---|---|
| `voyager-api` | none (no `jakarta.inject`, no `io.avaje.inject`) | none |
| `voyager-physics` | none | none |
| `voyager-race` | none | none |
| `voyager-platform` | `jakarta.inject` on constructors only | `@Inject` added to existing constructors of wired classes |
| `voyager-server` | `jakarta.inject`, `io.avaje.inject` (`@Factory`, `@Bean`, `BeanScope`) | `inject/ServerBeans` (`@Factory`), `inject/RaceBeans` (`@Factory`, ordered pipeline) |

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

The annotation processor runs in `voyager-server` and sees only the classes compiled there. Classes in
`voyager-platform` and `voyager-race` are therefore wired by `@Bean` methods in `voyager-server`, which spell out
their constructor parameters. `jakarta.inject.@Inject` on a platform constructor declares the dependencies in
the JSR-330 form and is read by the processor only where that class is compiled in `voyager-server`. The
spike (task 1) confirms this. If the processor does not honour `@Inject` on a constructor at all, the
annotation is kept as documentation and the `@Bean` method is the wiring. Either way the fitness suite and
a graph test catch a `@Bean` method that falls out of step with the constructor.

### 4. Lifecycle: eager construction, BeanScope at the root

`VoyagerServer.main` builds one `BeanScope` after `MinecraftServer.init()` and before any `getInstance`-style
lookup. Every `@Singleton`/`@Bean` the server owns must be constructed when the scope is built, because the spec
requires refusal before listening. The spike confirms that avaje constructs singletons eagerly at build; if it
does not, `VoyagerServer` resolves each bean it needs at startup, and the graph test asserts every one. The
settings instance is supplied to the builder as a bean (exact builder method confirmed in the spike). The scope
is closed in the existing shutdown task.

### 5. Pipeline order as one explicit list

The per-tick system sequence becomes a single `@Bean` method in `RaceBeans` returning
`List.of(...)` wrapped in `Collections.unmodifiableList`, with the order written out in code. No `List<T>`
injection of systems, because avaje does not guarantee the order of collection injection. A test asserts the
exact sequence; that is the only place the order is defined.

### 6. Fitness rules

`ApiPurityTest` changes as follows:
- `apiDoesNotDependOnADiContainer` and `physicsDoesNotDependOnADiContainer`: `com.google.inject..` becomes
  `io.avaje.inject..`; `jakarta.inject..` stays.
- `onlyServerDependsOnDiContainer`: forbids `io.avaje.inject..` outside `net.elytrarace.voyager.server..`. It
  no longer forbids `jakarta.inject..`, because platform constructors legitimately carry it (decision 1).
- A rule forbidding `jakarta.inject..` in `voyager-race` is added if the spike finds none (race is the only
  other domain module; its exclusion is the Clean Architecture boundary in decision 1).
- All rules keep `allowEmptyShould(false)`. `FitnessCoverageTest` keeps seeing each module covered.

### 7. Documentation

- `docs/decisions/0016-replace-guice-with-avaje-inject.md` (MADR 4.0). Number 0016 because `main` ends at
  0011 and 0012 to 0015 are taken on the unmerged `refactor/architecture-ratchet` branch. The ADR cites ADR-0013
  there as prior art: that ADR deferred the container and named avaje as front-runner, and this decision
  takes that step. If the branch merges first with a different number, renumber this file before merge.
- Greenfield design spec: D10 row, "Dependency injection" section, risk row.
- `CLAUDE.md` "Key Decisions" entry for dependency injection.

## Risks / Trade-offs

- **[The processor does not fail on a missing bean]** → Spike task 1 is first and must prove a deliberately
  missing bean breaks `compileJava`. If it does not, the fallback is a graph test that fails on any unresolved
  bean, and the spec scenario is re-worded before tasks continue.
- **[Cross-module wiring duplicated between `@Inject` constructors and `@Bean` methods]** → A `@Bean` method
  that drifts from its constructor fails compilation (argument mismatch) rather than at runtime. The graph test
  resolves every type.
- **[Shadow jar drops or duplicates avaje's generated or service entries]** → `runServer` boot is part of
  the build task list; the spike runs the shadow jar and checks it reaches "Listening on".
- **[Build time grows from the processor]** → Measured before and after in the PR description; accepted if it
  is within a few seconds.
- **[Lost `Stage.PRODUCTION` semantics]** → Replaced by eager construction (decision 4) and asserted by a test.
- **[Two DI styles in the codebase while the old tree remains]** → The old tree does not use this container; no
  shared module is touched.
- **[ADR number collision]** → Handled in decision 7.

## Migration Plan

One PR, squash-merged. Commits on the branch, one type each: `build(server)` for the catalog and Gradle change;
`refactor(server)` for factories, `BeanScope` bootstrap and removal of `VoyagerModule`; `test(server)` for the
graph and order tests (written first); `test(fitness)` for `ApiPurityTest`; `docs` commits for the spec, `CLAUDE.md` and ADR-0016. The squash commit on `main` is
`refactor(server): replace guice with avaje-inject`.

Rollback: the squash commit is a single revert, which restores Guice, `VoyagerModule` and the rules together.
No data migration, no runtime configuration change.

## Open Questions

- Whether `voyager-setup`'s composition root should share a `@Factory` base type with `voyager-server` is
  deferred until `voyager-setup` exists. It does not change this change's specs or tasks.
