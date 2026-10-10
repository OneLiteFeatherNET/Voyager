# Spec Delta

## Purpose

The setup server is a Minestom application with its own composition root (`voyager-setup`). It starts only when its
data directory exists, and it does not depend on the game server's module.

## ADDED Requirements

### Requirement: Setup server boots from its own composition root
**Priority:** MoSCoW Must

THE SYSTEM SHALL start the setup server from a `main` in `net.elytrarace.voyager.setup` that builds its object graph
with avaje-inject in a `@Factory` under `net.elytrarace.voyager.setup.inject`, and SHALL NOT depend on `voyager-server`.

#### Scenario: Setup server starts with a valid data directory
- **WHEN** the setup server starts and its data directory exists
- **THEN** it builds the graph, registers its commands and listeners, and accepts player connections on its configured host and port

#### Scenario: Setup module imports the game server
- **WHEN** a class in `net.elytrarace.voyager.setup..` depends on a class in `net.elytrarace.voyager.server..`
- **THEN** the architecture rules fail the build

### Requirement: Setup server refuses to start on a missing data or worlds directory
**Priority:** MoSCoW Must

IF the configured data directory or the configured worlds directory does not exist or is not a directory, THEN THE SYSTEM SHALL
log one message per missing directory that names the absolute path, SHALL NOT open a listening socket, and SHALL exit with a
non-zero status.

#### Scenario: Data directory is absent
- **WHEN** the setup server starts with a data path that does not exist
- **THEN** it logs the absolute path it expected, exits with status 1, and never calls the server's start method

#### Scenario: Worlds directory is absent
- **WHEN** the setup server starts with a worlds path that does not exist
- **THEN** it logs the absolute path it expected, exits with status 1, and never calls the server's start method

### Requirement: Setup server uses its own default data directory
**Priority:** MoSCoW Should

THE SYSTEM SHALL resolve the data directory from the system property `VOYAGER_DATA_PATH` and otherwise use `run-setup/data`,
and the worlds directory from `VOYAGER_WORLDS_PATH` and otherwise use `run-setup/worlds`, both under the project root, so that
the setup server and the game server do not share a directory by default.

#### Scenario: No property given
- **WHEN** the setup server starts without `VOYAGER_DATA_PATH` or `VOYAGER_WORLDS_PATH`
- **THEN** it uses the default directories and reports them in its startup log line
