# ADR-0016: Replace Guice with avaje-inject for dependency injection

## Status

Accepted

## Date

2026-10-09

## Decision makers

- TheMeinerLP (project owner, approved the choice on 2026-10-09)
- Atlas (architect)

## Context and problem statement

The greenfield rebuild wires its composition root in `voyager-server` with `io.airlift:guice:10`. Decision D10 of the
greenfield design chose this fork and restricted DI annotations to the composition roots. Two properties of Guice
make that choice worse than it looks for this project:

- Guice resolves the object graph at runtime through reflection. A missing or mis-typed binding surfaces as a
  boot-time exception, not as a build failure, and the tick path runs on a 20 TPS budget that must not carry
  container machinery.
- The fork is a single-vendor artifact aligned to Trino's needs. D10 records its abandonment as a risk that
  would force a DI migration. Upstream `com.google.inject:guice` has had no release since 2023-05-12, and its
  Java 25 issues have no maintainer response.

The rebuild is still small: one composition root and roughly fourteen bindings. The switch is cheap now, while only
`voyager-server` and one `@Provides` module use the container. ADR-0013 on the unmerged `refactor/architecture-ratchet`
branch rejected Guice, deferred the container, and named Avaje Inject as the front-runner to validate before adoption.
This ADR takes that step.

## Decision drivers

- Wiring mistakes must fail at build time, not at server start
- No reflection in the startup or tick path
- Domain, API and platform classes stay constructible with `new` and carry no DI annotation
- The container must be maintained by an active project with a Java 25 release line
- Ordering of the ECS system pipeline must stay explicit and correct, a correctness property of a fixed-step simulation
- The "Clean Architecture" dependency rule holds: dependencies point inward, and the composition root is the only place
  that knows the container
- The version catalog stays programmatic in `settings.gradle.kts`

## Considered options

- `io.airlift:guice:10` (status quo in D10)
- Upstream `com.google.inject:guice` 7
- Dagger 2
- `io.avaje:avaje-inject` 12.7
- Manual wiring with no container

## Decision outcome

Chosen option: **`io.avaje:avaje-inject` 12.7**, because it generates the wiring at compile time with no reflection, so a
missing or ambiguous bean becomes a compiler error, and the generated code is plain Java that the team can read. It
uses the JSR-330 annotations of `jakarta.inject`, so the annotation vocabulary is a standard and not a vendor API.

Concretely:

- `io.avaje:avaje-inject:12.7` is the runtime dependency, `io.avaje:avaje-inject-generator:12.7` the annotation processor,
  and `io.avaje:avaje-inject-test:12.7` the test support, all in `voyager-server`. They are declared in
  `settings.gradle.kts`.
- `jakarta.inject:jakarta.inject-api:2.0.1` and the `io.avaje.inject` annotations (`@Factory`, `@Bean`, `@Singleton`)
  appear only in `voyager-server`, the composition root. `voyager-setup` follows the same rule when it arrives.
- `voyager-api`, `voyager-physics`, `voyager-race` and `voyager-platform` carry no DI annotation and do not depend on
  `jakarta.inject` or avaje. A class from another module is wired by a `@Bean` method in `voyager-server` that calls
  its constructor. Avaje does not wire a class from another module through an annotation alone.
- `inject/ServerBeans` and `inject/RaceBeans` are `@Factory` classes that replace `VoyagerModule`. Each former `@Provides`
  method maps to one `@Bean` with the same lifetime and semantics.
- The ECS system pipeline is one explicit, unmodifiable `List` built in one `@Bean` method. It is not an injected
  collection, because avaje does not guarantee the order of `List<T>` injection.
- `VoyagerServer` builds the graph with `BeanScope` and keeps its startup order: settings, `MinecraftServer.init()`,
  graph, world opening, events, listen.
- `ApiPurityTest` forbids `io.avaje.inject..` in place of the Guice packages, and forbids `jakarta.inject..` and
  `io.avaje.inject..` in every module outside `voyager-server`.

This decision supersedes decision D10 of `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`. It builds on
ADR-0013 (prior art on `refactor/architecture-ratchet`, which deferred the container and named Avaje Inject as the front-runner).

### Consequences

- Good, because a missing or ambiguous bean fails `compileJava`, not server start
- Good, because the generated wiring is plain Java and runs without reflection, so the tick path stays free of container code
- Good, because the maintenance risk recorded in D10 is replaced by an actively released library on a Java 25 line
- Good, because the DI annotations stay in one module, so a later change of container still touches one composition root
- Good, because the pipeline order is written out in one place and asserted by a test
- Bad, because cross-module classes need hand-written `@Bean` methods that repeat their constructor parameters; a drifting
  method fails compilation, but the list is maintained by hand
- Bad, because the annotation processor adds build time, which is measured in the pull request and accepted if it is small
- Bad, because the ambiguous-bean error names the conflicting `@Bean` methods rather than the type, so a reader has to
  look up the type from the method names
- Neutral, because `Stage.PRODUCTION` semantics are replaced by eager construction at `BeanScope` build, asserted by a test

### Confirmation

The spike (group 1 of the `switch-di-to-avaje-inject` change, throwaway code, deleted before implementation) verified the
assumptions this decision rests on:

- 1.1 A bean whose constructor needs a type that no bean provides fails `compileJava`, and the error names the missing type.
- 1.2 Two beans providing the same type fail `compileJava`. The error names the `@Bean` methods, not the type. The spec
  scenario is worded to accept that.
- 1.3 A `@Singleton @Inject` class in `voyager-server` is wired without a `@Bean` method. A class in `voyager-platform`
  is not wired from `voyager-server` without one, so cross-module classes use `@Bean` methods.
- 1.4 A singleton is constructed when `BeanScope.builder().build()` runs, before any `get`, which matches the
  eager-construction requirement.
- 1.5 The project builds without `module-info.java`. The shadow jar keeps the generated
  `META-INF/services/io.avaje.inject.spi.InjectExtension` entry, and `BeanScope` discovers the beans from the fat jar.

The change is confirmed when these hold in the repository:

- `ApiPurityTest` in `voyager-fitness` contains the `io.avaje.inject..` and `jakarta.inject..` rules listed above, each
  declared with `allowEmptyShould(false)`, so a rule that matches nothing fails instead of passing.
- `VoyagerGraphTest` resolves every type the former `VoyagerModule` provided and asserts singleton identity.
- A test asserts the exact order of the system pipeline and that the list is unmodifiable.
- `unzip -l` on the `voyager-server` shadow jar lists the `META-INF/services/io.avaje.inject.spi.InjectExtension` entry.

## Pros and cons of the options

### `io.airlift:guice:10`

- Good, because it runs on Java 25, uses the JDK's own class-file API for line numbers, and avoids Unsafe-based hidden-class definition
- Good, because the package names match upstream Guice, so migration from upstream is a coordinate change
- Bad, because the fork is maintained for one vendor's needs; D10 records its abandonment as a risk
- Bad, because the container resolves the graph at runtime through reflection, so wiring errors surface at boot
- Bad, because AOP is removed, which is harmless here but limits future options

### Upstream `com.google.inject:guice` 7

- Good, because it is the reference implementation with the widest documentation
- Bad, because no release since 2023-05-12 and Java 25 issues are unanswered by a maintainer
- Bad, because ASM rejects class file major version 69 and the damage is a logged warning, not a clean fix
- Bad, because it is still reflection-based at runtime

### Dagger 2

- Good, because it is compile-time and widely used on Android and server code
- Bad, because its component and module model is heavier than roughly fourteen bindings require
- Bad, because it is not the front-runner ADR-0013 named, and its generated code is harder to read for this graph

### `io.avaje:avaje-inject` 12.7 (chosen)

- Good, because it is compile-time, reflection-free and uses the `jakarta.inject` annotations
- Good, because a missing or ambiguous bean fails the build, and the spike confirmed both cases
- Good, because the generated `BeanScope` code is readable, and eager construction matches the startup requirement
- Bad, because it adds an annotation processor and a `@Bean` method for each cross-module class
- Bad, because the ambiguous-bean error names methods, not types

### Manual wiring with no container

- Good, because it adds no dependency and no processor, and matches ADR-0013's deferred path
- Good, because every wire is ordinary Java that the compiler checks inside one method
- Bad, because the graph lives in one hand-written method with no separate list of beans to review, and the lifetimes are only as correct as that method
- Bad, because the user approved avaje-inject on 2026-10-09, so this option is not the path taken

## More information

- Greenfield design, decision D10 and section "Dependency injection": `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`
- ADR-0013 "Adopt JSR-330 annotations, defer the DI container, reject Guice", on the unmerged branch
  `refactor/architecture-ratchet` at `docs/decisions/0013-jsr330-annotations-without-a-di-container.md`
- Numbers 0012 to 0015 are taken on `refactor/architecture-ratchet`; this ADR uses 0016. Renumber it if that branch merges
  with different numbers first.
- Change `switch-di-to-avaje-inject` in `openspec/changes/`, with its proposal, design and dependency-injection spec
- [Jakarta Dependency Injection (JSR-330)](https://jakarta.ee/specifications/dependency-injection/)
