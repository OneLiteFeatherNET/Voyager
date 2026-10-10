## ADDED Requirements

### Requirement: The race command is a bean of the composition root
**Priority:** MoSCoW Should

THE SYSTEM SHALL construct the race command in a `@Bean` method of the server's composition root, and `VoyagerServer` SHALL
obtain it from the object graph and register it. No class outside the composition root's `inject` package SHALL call the
command's constructor. The command SHALL keep depending on the concrete cup session.

#### Scenario: Command built by hand in the bootstrap
- **WHEN** `VoyagerServer` calls `new RaceCommand(...)`
- **THEN** the change is rejected, and the command is taken from the graph with `graph.get(RaceCommand.class)` instead

#### Scenario: Command constructed outside the composition root
- **WHEN** a class outside `net.elytrarace.voyager.server.inject..` calls a `RaceCommand` constructor
- **THEN** the rule added to `voyager-fitness` for this requirement fails and names the class
