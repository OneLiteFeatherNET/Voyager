# architecture/dependency-injection-boundaries Specification

## Purpose
Defines which rings of the Voyager rebuild may carry which dependency-injection annotations, how composition roots and
per-slice factories are organised with avaje-inject and `jakarta.inject`, how the per-tick pipeline order is declared,
and which practices are banned (service locators and static singletons). Bootstrap behaviour of the server is specified
separately in `server/dependency-injection`; this capability sets the boundaries that the wiring must respect.

## Requirements

### Requirement: Entities and use cases carry no DI annotation
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep `jakarta.inject` and `io.avaje.inject` annotations and types out of `voyager-api`, `voyager-physics`
and `voyager-race`. Their classes SHALL take their dependencies as ordinary constructor parameters.

#### Scenario: Use case annotated for injection
- **WHEN** a class in `voyager-race` carries `@Inject`, `@Singleton` or any avaje annotation
- **THEN** the architecture rules fail the build

#### Scenario: Use case constructed by hand
- **WHEN** a test needs a race run
- **THEN** it constructs the run with `new` and passes its ports as arguments

### Requirement: Adapters carry no DI annotation
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT place any `jakarta.inject` or avaje annotation on a class, constructor, method or field of
`voyager-platform`, and the same holds for `voyager-persistence` when it exists. A composition root wires an adapter by a
`@Bean` method that calls its constructor, because the composition root alone decides the scope and the name of a bean.

#### Scenario: Platform class marked for injection
- **WHEN** a platform class or its constructor carries `@Inject`, `@Singleton`, `@Named` or any avaje annotation
- **THEN** the architecture rules fail the build and name the offending class

#### Scenario: Platform class constructed by the root
- **WHEN** the graph needs `CatalogHolder`
- **THEN** a `@Bean` method in `voyager-server` calls its constructor, and `CatalogHolder` carries no annotation

### Requirement: Only composition roots use DI annotations and the avaje container
**Priority:** MoSCoW Must

THE SYSTEM SHALL use `jakarta.inject` annotations and avaje-inject `@Factory`, `@Bean` and `BeanScope` only in the composition roots
`voyager-server` and `voyager-setup`, and avaje-inject only in their `inject` packages and in the one process entry-point class
of each deployable (`VoyagerServer` for the server, `SetupServer` for the setup server). No avaje type SHALL appear in a public signature outside those places.

#### Scenario: Container type in a slice
- **WHEN** a class in `net.elytrarace.voyager.race.cup..` or `net.elytrarace.voyager.platform..` imports `io.avaje.inject..`
- **THEN** the architecture rules fail the build

#### Scenario: Inject annotation in a slice
- **WHEN** a class in `net.elytrarace.voyager.race..` or `net.elytrarace.voyager.platform..` imports `jakarta.inject..`
- **THEN** the architecture rules fail the build

### Requirement: Injection annotations need a scope and in-module compilation
**Priority:** MoSCoW Must

WHERE a composition root uses `jakarta.inject.Inject` on a constructor, THE SYSTEM SHALL also declare `@Singleton` or `@Component`
on that class, and the class SHALL be compiled in the composition root's module. Spike 1.3 of `switch-di-to-avaje-inject`
showed that avaje ignores `@Inject` alone and does not wire classes from other modules. A class outside the composition root
is wired by a `@Bean` method.

#### Scenario: Inject without scope in the root
- **WHEN** a class in `voyager-server` carries `@Inject` on its constructor and no `@Singleton` or `@Component`
- **THEN** the class is not wired from the container, and the composition root declares a `@Bean` method for it instead

#### Scenario: Container type in a public signature
- **WHEN** a public method of a server class other than `VoyagerServer` and the `inject` packages returns a `BeanScope`
- **THEN** the design review rejects the change

### Requirement: One composition root per deployable
**Priority:** MoSCoW Must

THE SYSTEM SHALL have exactly one composition root per deployable: `voyager-server` for the game server, and `voyager-setup`
for the setup server when it exists. A deployable SHALL NOT share a composition root with another deployable.

#### Scenario: Second deployable reuses the server root
- **WHEN** the setup server needs the server's factories
- **THEN** the change moves the shared wiring into a module both roots depend on, or wires it again in `voyager-setup`; it does not import `voyager-server` from `voyager-setup`

### Requirement: Wiring is grouped in one factory per slice
**Priority:** MoSCoW Must

THE SYSTEM SHALL group the beans of a composition root into `@Factory` classes under its `inject` package, one per slice or
slice group, named for the slice (for example `RunBeans`, `CupBeans`, `FlightBeans`). Each factory SHALL contain only
`@Bean` methods that construct objects and SHALL NOT contain game logic.

#### Scenario: Cup beans factory
- **WHEN** the cup wiring is added to the server
- **THEN** it lives in `CupBeans` under `net.elytrarace.voyager.server.inject`, and each `@Bean` method only calls a constructor

#### Scenario: Factory with logic
- **WHEN** a `@Bean` method computes a value other than the constructor arguments
- **THEN** the design review rejects the change and the logic moves into a slice

### Requirement: The per-tick pipeline is one explicit ordered list
**Priority:** MoSCoW Must

THE SYSTEM SHALL declare the sequence of per-tick steps as one unmodifiable ordered list, built in one `@Bean` method of the
composition root. The system SHALL NOT inject the steps as a collection, because the order of a collection injection is
not a guaranteed property.

#### Scenario: Current order preserved
- **WHEN** the pipeline runs one tick
- **THEN** the flight sampling step runs first, the boost burn counter second, and the phase advance last, matching the order the server uses today

#### Scenario: Pipeline list is mutated
- **WHEN** code tries to add or remove a step from the built pipeline
- **THEN** the operation fails, because the list is unmodifiable

### Requirement: Unit tests construct with new and never use the container
**Priority:** MoSCoW Must

THE SYSTEM SHALL construct the objects under test with `new` in every unit test. Only composition-root tests MAY build a
`BeanScope`, and such tests SHALL build a fresh scope per test with a fresh configuration, so that no test depends on the
order or the outcome of another.

#### Scenario: Unit test of a platform adapter
- **WHEN** a unit test of `CatalogReloader` runs
- **THEN** it passes its `Clock` and `WorldOpener` to the constructor and does not start a container

#### Scenario: Graph test
- **WHEN** a composition-root test resolves the server beans
- **THEN** it builds its own scope in a `@TempDir`-backed configuration, and no static field carries the scope

### Requirement: No service locator
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT look up a bean by type or name outside a composition root. A class SHALL receive each of its
dependencies through its constructor, and SHALL NOT call `BeanScope`, `Injector` or any similar lookup.

#### Scenario: Slice fetches a dependency from the container
- **WHEN** a class calls a container lookup method to obtain a collaborator
- **THEN** the architecture rules fail the build

### Requirement: No static singletons and no mutable static state
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT declare a mutable static field in any rebuild module. Static methods MAY exist only where they are pure
functions or factory methods of an abstract utility class with a private constructor, as ManisGame rule 2 requires.
Process-wide services SHALL be singletons of the composition root, not static fields.

#### Scenario: Mutable static field
- **WHEN** a class declares `static` on a field that is not `final`
- **THEN** the architecture rules fail the build

#### Scenario: Process-wide service
- **WHEN** the cup session must be shared by two platform classes
- **THEN** the composition root creates one instance and passes it to both

### Requirement: Dependencies are narrow ports
**Priority:** MoSCoW Should

WHEN a constructor takes a collaborator, THE SYSTEM SHALL declare the parameter with the narrowest interface that the class
calls, and SHALL NOT pass a whole composition object or a catalogue when a single port is enough.

#### Scenario: Cup selection needs the catalogue
- **WHEN** cup selection needs a cup by name
- **THEN** the selection rule takes the `CupCatalog` port from `voyager-api` or the parsed definitions, not a platform reader. Today `CupResolution` takes the platform's `CatalogReading`; that is migration item 23, and the scenario holds once it is fixed

### Requirement: Factories are injectable through a creator
**Priority:** MoSCoW Should

WHERE a use-case slice creates objects through an abstract factory, THE SYSTEM SHALL expose the factory through a
`@FunctionalInterface` creator parameter, so that a test can substitute a different implementation (ManisGame rule 7).

#### Scenario: Test substitutes a creator
- **WHEN** a test needs a fixed run creator
- **THEN** it passes a lambda as the creator and the slice uses it without any container
