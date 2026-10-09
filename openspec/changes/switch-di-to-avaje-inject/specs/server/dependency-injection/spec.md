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
THE SYSTEM SHALL fail the build with a message that names the unresolved or ambiguous type, before any
server process is started.

#### Scenario: Missing bean breaks compilation
- **WHEN** a constructor of a wired class requires a type that no bean in the composition root provides
- **THEN** the build fails and the failure message names the missing type

#### Scenario: Two candidate beans break compilation
- **WHEN** two beans in the composition root provide the same type and no qualifier separates them
- **THEN** the build fails and the failure message names the ambiguous type

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

### Requirement: Dependency-injection annotations stay in composition roots
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep dependency-injection annotations and container types out of the API, physics and race
modules. THE SYSTEM SHALL confine container-specific annotations to the composition root. Standard
injection annotations MAY appear on constructors in platform and composition-root code only.

#### Scenario: API type carries an injection annotation
- **WHEN** a type in the API module is annotated with an injection annotation
- **THEN** the architecture suite fails and names the offending type

#### Scenario: Platform constructor carries a standard annotation
- **WHEN** a platform-module class declares its constructor with a standard injection annotation
- **THEN** the architecture suite passes

#### Scenario: Container-specific annotation outside the composition root
- **WHEN** a class outside the composition root uses a container-specific annotation
- **THEN** the architecture suite fails and names the offending class

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
the container, so that single classes can be tested without the full graph.

#### Scenario: Unit test builds a single class without the container
- **WHEN** a unit test needs one platform class
- **THEN** the test constructs it directly with its collaborators and does not start the container
