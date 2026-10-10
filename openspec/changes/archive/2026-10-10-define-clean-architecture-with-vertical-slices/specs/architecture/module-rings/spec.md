# Spec Delta

## Purpose

Defines the "Clean Architecture" rings of the Voyager rebuild as Gradle modules: which module is which ring, which
direction dependencies may point, where ports live, what data may cross a boundary, and which exceptions belong to
which ring. This is the contract that every slice and every composition root is checked against.

## ADDED Requirements

### Requirement: The rebuild is organised in four rings
**Priority:** MoSCoW Must

THE SYSTEM SHALL assign every rebuild module to exactly one of four rings, numbered from the innermost: ring 1 entities
(`voyager-api`, `voyager-physics`), ring 2 use cases (`voyager-race`), ring 3 interface adapters and frameworks
(`voyager-platform`, `voyager-persistence`), and ring 4 the composition roots (`voyager-server`, `voyager-setup`).
`voyager-fitness` is test-only and lies outside the rings.

#### Scenario: Module ring is looked up
- **WHEN** a contributor needs to know where new code belongs
- **THEN** the module's ring in this specification identifies the allowed dependencies and the allowed framework types

#### Scenario: New module without a ring
- **WHEN** a module is added to `settings.gradle.kts` without a ring assignment in this specification
- **THEN** the change that adds it also amends this specification, or the change is rejected in review

### Requirement: Dependencies point inward only
**Priority:** MoSCoW Must

WHEN a rebuild module declares a dependency on another rebuild module, THE SYSTEM SHALL allow the target only from the
set in this table: `voyager-physics` on `voyager-api`; `voyager-race` on `voyager-api` and `voyager-physics`;
`voyager-platform` on `voyager-api`, `voyager-physics` and `voyager-race`; `voyager-persistence` on `voyager-api`;
`voyager-setup` on `voyager-api` and `voyager-platform`; `voyager-server` on every other rebuild module.

#### Scenario: Platform depends on the composition root
- **WHEN** a class in `voyager-platform` imports a type from `net.elytrarace.voyager.server..`
- **THEN** the architecture rules fail the build

#### Scenario: Use case depends on an adapter
- **WHEN** a class in `voyager-race` imports a type from `net.elytrarace.voyager.platform..`
- **THEN** the architecture rules fail the build

#### Scenario: Entity depends on any other rebuild module
- **WHEN** a class in `voyager-api` imports a type from `physics`, `race`, `platform` or `server`
- **THEN** the architecture rules fail the build

### Requirement: Entities and use cases contain no framework types
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep `voyager-api`, `voyager-physics` and `voyager-race` free of Minestom, Xerus, Paper, Falco,
Gson, JDBC and Hibernate types, and free of `java.nio.file` and other file I/O, so that a whole race can run in a
plain JUnit test.

#### Scenario: Race logic runs without a server
- **WHEN** a race is played through the use cases in a unit test
- **THEN** no Minestom instance, network socket or file is needed to complete the race

### Requirement: Framework adapters live in voyager-platform
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep the Minestom, Xerus, Gson and Falco adapter code (listeners, entity and block access, commands that
touch the server, message components) in `voyager-platform`. A composition root MAY call Minestom only to bootstrap the
server and to wire adapters. A composition root SHALL NOT hold a Minestom adapter or a rule of a slice; those move to ring 3
or ring 2.

#### Scenario: Minestom listener in the setup module
- **WHEN** a class in `voyager-setup` registers a Minestom event listener
- **THEN** the change is rejected until the listener moves into `voyager-platform`; today `voyager-setup` holds seven such classes (migration item 26)

#### Scenario: Server bootstrap calls Minestom
- **WHEN** `VoyagerServer` starts the Minestom server and builds the graph
- **THEN** the call is allowed, because it is bootstrap and wiring

#### Scenario: Minestom type inside the use-case ring
- **WHEN** a class in `voyager-race` imports `net.minestom..`
- **THEN** the architecture rules fail the build

### Requirement: Ports are interfaces declared inward and implemented outward
**Priority:** MoSCoW Must

WHEN an inner ring needs something from the outside, THE SYSTEM SHALL declare an interface port in the innermost module
that calls it, and SHALL declare the port in `voyager-api` when more than one module calls it. THE SYSTEM SHALL implement
every port in an outer ring, never in the ring that declares it.

#### Scenario: Collision port used by physics and platform
- **WHEN** the physics simulation needs block collision
- **THEN** the `CollisionSpace` interface is declared in `voyager-api` and implemented in `voyager-platform`

#### Scenario: Port implemented in the inner ring
- **WHEN** a class that implements a port is found in `voyager-api`, `voyager-physics` or `voyager-race` and the port is declared outward
- **THEN** the design review rejects the change

### Requirement: Only platform converts between platform and domain types
**Priority:** MoSCoW Must

THE SYSTEM SHALL convert between domain values (`Vec3`, `Aabb` and the api records) and platform values (Minestom
`Vec`, `Pos`, Adventure `Component`, Gson `JsonElement`) only in `voyager-platform`. Data that crosses a module boundary
SHALL be an immutable record or enum that carries no platform type.

#### Scenario: Use case returns a platform value
- **WHEN** a method in `voyager-race` returns a Minestom type
- **THEN** the architecture rules fail the build

#### Scenario: Vector conversion
- **WHEN** a Minestom velocity is set from a domain vector
- **THEN** the conversion happens in the platform's velocity exit class and nowhere else

### Requirement: Domain values reject invalid input at construction
**Priority:** MoSCoW Should

WHEN a record in `voyager-api` receives a value that breaks its invariant, THE SYSTEM SHALL reject it in the record's
compact constructor by throwing an exception from the same domain's `exception` package, before the value is used.

#### Scenario: Non-finite vector component
- **WHEN** a `Vec3` is constructed with a NaN or infinite component
- **THEN** construction fails with a non-finite-vector exception and no `Vec3` instance exists

### Requirement: Each exception belongs to the ring that raises it
**Priority:** MoSCoW Must

THE SYSTEM SHALL place each domain exception in an `exception` subpackage of the slice or module that raises it, with a
`package-info.java`, extending `RuntimeException`, named with the `Exception` suffix. An adapter SHALL translate an
external exception (I/O, Minestom, Gson, Falco) into an exception of its own ring before the exception reaches an inner
ring; an inner ring SHALL NOT catch an exception that belongs to an outer ring.

#### Scenario: Missing catalogue file
- **WHEN** the JSON map catalogue cannot read a file
- **THEN** the platform raises its catalogue exception with the file path, and no `IOException` leaves `voyager-platform`

#### Scenario: Inner ring catches an outer exception
- **WHEN** a class in `voyager-race` catches a platform exception type
- **THEN** the architecture rules fail the build

### Requirement: Entities and use cases are deterministic
**Priority:** MoSCoW Should

THE SYSTEM SHALL make every entity and use case in `voyager-api`, `voyager-physics` and `voyager-race` a deterministic
function of its inputs, its ports and an injected `java.time.Clock`, so that the same inputs produce the same outputs in
any test run.

#### Scenario: Clock-dependent race timing
- **WHEN** a test advances a race clock by fixed steps
- **THEN** the recorded race times are identical on every run and on every machine
