# Spec Delta

## Purpose

Defines how the rebuilt Voyager server assembles its object graph at startup: when wiring errors are
detected, which startup failures refuse to run the server, how the system pipeline order is fixed, and where
dependency-injection concerns are allowed to live. Players do not see this capability directly; it is the
contract the server and the architecture suite rely on.

## ADDED Requirements

### Requirement: Missing or ambiguous wiring fails the build
**Priority:** MoSCoW Must

WHEN the composition root declares a dependency that no bean provides, or two beans satisfy one dependency,
THE SYSTEM SHALL fail the build before any server process is started. A missing dependency is named by its type
in the failure message. An ambiguous dependency is reported by the names of the conflicting `@Bean` methods,
which the message identifies as returning the same type without a unique name qualifier.

#### Scenario: Missing bean breaks compilation
- **WHEN** a constructor of a wired class requires a type that no bean in the composition root provides
- **THEN** the build fails and the failure message names the missing type

#### Scenario: Two candidate beans break compilation
- **WHEN** two `@Bean` methods in the composition root return the same type and no qualifier separates them
- **THEN** the build fails and the failure message names both conflicting `@Bean` methods, identifying them as
  returning the same type without a unique name qualifier

### Requirement: Startup refuses to listen on an incomplete graph
**Priority:** MoSCoW Must

WHEN the server starts, THE SYSTEM SHALL construct every singleton it owns and resolve the cup and every world the
cup plays before it accepts a connection. IF any of these steps fails, THEN THE SYSTEM SHALL log the reason,
exit with a non-zero status, and not bind the network port.

#### Scenario: Valid configuration starts and listens
- **WHEN** the server starts with a valid data directory and worlds directory
- **THEN** every singleton is constructed before the server reports that it is listening

#### Scenario: Cup naming an unknown map refuses to start
- **WHEN** the configured cup names a map that the map catalogue does not hold
- **THEN** the server exits with a non-zero status and does not bind the network port

#### Scenario: Missing world refuses to start
- **WHEN** a world required by the cup has no region data on disk
- **THEN** the server exits with a non-zero status before any client can connect

### Requirement: Bean lifetimes and identities are preserved
**Priority:** MoSCoW Must

THE SYSTEM SHALL provide each process-wide service as a single instance per server process, and each
per-call value as a new instance per request, matching the lifetimes the server had before this capability
was introduced.

#### Scenario: Process-wide service is shared
- **WHEN** two components that both need the cup session are constructed during one startup
- **THEN** both receive the same cup session instance

#### Scenario: Online-player supplier reads the live roster
- **WHEN** a player joins after startup and the cup session asks for the online players
- **THEN** the player appears in the result without restarting the server

### Requirement: System pipeline order is fixed and declared once
**Priority:** MoSCoW Must

THE SYSTEM SHALL run the per-tick systems of the race in one declared sequence. THE SYSTEM SHALL NOT derive
that sequence from the order in which the container happens to enumerate candidates.

#### Scenario: Systems run in declared order
- **WHEN** one server tick runs
- **THEN** the systems execute in the sequence declared in the composition root

#### Scenario: Reordering the declaration changes execution order
- **WHEN** the declared sequence is changed in the composition root
- **THEN** the next tick executes the systems in the new sequence without any other change

### Requirement: No container lookups after startup
**Priority:** MoSCoW Must

WHILE the server is running, THE SYSTEM SHALL NOT resolve a bean from the dependency container on a game tick,
a player event, or a command.

#### Scenario: Tick path has no resolution
- **WHEN** a race runs for a full lap with several players
- **THEN** no bean is resolved from the container after startup completes

### Requirement: Dependency-injection annotations stay in the composition root
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep `jakarta.inject` and `io.avaje.inject` annotations and types out of the API, physics, race and
platform modules. THE SYSTEM SHALL use them only in `voyager-server`, the composition root. A class outside
`voyager-server` SHALL be wired by a `@Bean` method in `voyager-server` that calls its constructor.

#### Scenario: API type carries an injection annotation
- **WHEN** a type in the API module is annotated with `@Inject`, `@Singleton` or any avaje annotation
- **THEN** the architecture suite fails and names the offending type

#### Scenario: Platform class carries an injection annotation
- **WHEN** a class in the platform module is annotated with `@Inject`, `@Singleton`, `@Named` or any avaje annotation
- **THEN** the architecture suite fails and names the offending class

#### Scenario: Platform class is wired by a bean method
- **WHEN** the graph needs a platform class
- **THEN** a `@Bean` method in `voyager-server` calls its constructor, and the platform class carries no annotation

#### Scenario: Injection annotation outside the composition root
- **WHEN** a class outside `voyager-server` uses a `jakarta.inject` or avaje annotation
- **THEN** the architecture suite fails and names the offending class

### Requirement: Constructor injection in the composition root needs a scope and in-module compilation
**Priority:** MoSCoW Must

WHERE a class compiled in `voyager-server` carries `@Inject` on its constructor, THE SYSTEM SHALL rely on the generated
wiring only if the class also carries `@Singleton` or `@Component`. THE SYSTEM SHALL NOT rely on `@Inject` alone, and
THE SYSTEM SHALL NOT rely on the wiring of a class that is compiled outside `voyager-server`.

#### Scenario: Inject without a scope annotation
- **WHEN** a class in `voyager-server` carries `@Inject` on its constructor and no `@Singleton` or `@Component`
- **THEN** the build fails with a missing-dependency error, or the class is not wired, and the class is wired by a `@Bean` method instead

#### Scenario: Scoped inject class compiled in the server
- **WHEN** a class in `voyager-server` carries both `@Singleton` and `@Inject` on its constructor
- **THEN** the generated wiring constructs it from the beans of the composition root

### Requirement: Startup failure messages name the failing type
**Priority:** MoSCoW Should

IF the composition root cannot build the graph at startup, THEN THE SYSTEM SHALL log a message that names the type
or the configured value that failed.

#### Scenario: Bad cup name is named in the log
- **WHEN** the configured cup name matches no cup in the catalogue
- **THEN** the log names the configured cup name

### Requirement: Graph wiring is readable in one place
**Priority:** MoSCoW Should

THE SYSTEM SHALL declare the wiring of every service that the composition root provides in one composition-root
source set, so that a reader can see each service's dependencies without searching other modules.

#### Scenario: Every provided service is declared in the composition root
- **WHEN** a service is added to the server
- **THEN** its wiring is declared in the composition root and not in a platform or domain module

### Requirement: Single classes are constructible without the container
**Priority:** MoSCoW Could

THE SYSTEM SHALL allow each platform class to be constructed directly from its collaborators, without starting
the container, so that single classes can be tested without the full graph. Because platform classes carry no
annotation, this holds by construction.

#### Scenario: Unit test builds a single class without the container
- **WHEN** a unit test needs one platform class
- **THEN** the test constructs it directly with its collaborators and does not start the container
