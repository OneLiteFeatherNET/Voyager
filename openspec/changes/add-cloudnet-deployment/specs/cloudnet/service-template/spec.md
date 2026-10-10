# Spec Delta

## Purpose

The directory layout and runtime contract of a Voyager game or setup service under a CloudNet node. The node copies a
template, starts the server with its bind address, and stops it on stdin. The layout separates the shared catalogue and
worlds from the per-service runtime data, so that two services never write the same LuckPerms file.

## ADDED Requirements

### Requirement: A service directory has a fixed layout
**Priority:** MoSCoW Must

THE SYSTEM SHALL run from a service directory that holds the application JAR at its root, the bridge extensions under
`extensions/`, the catalogue under `catalog/` (with `maps/` and `cups/`), the worlds under `worlds/`, and the runtime data
under `data/`.

#### Scenario: Template contents
- **WHEN** a game service is created from the template
- **THEN** its directory holds `extensions/`, `catalog/maps/`, `catalog/cups/` and `worlds/`, and `data/` is absent until the first start

### Requirement: Data paths are set through the service JVM options
**Priority:** MoSCoW Must

THE SYSTEM SHALL read the catalogue and worlds from the paths in the system properties `VOYAGER_DATA_PATH` and
`VOYAGER_WORLDS_PATH`, which the template's JVM options set to `catalog` and `worlds` relative to the service directory.

#### Scenario: Paths from the template
- **WHEN** a service starts with the template's JVM options
- **THEN** it reads maps from `catalog/maps` and cups from `catalog/cups`, and opens worlds from `worlds`

### Requirement: data is per service and never shared
**Priority:** MoSCoW Must

THE SYSTEM SHALL write LuckPerms configuration and its H2 database only under `data/`, and no two services SHALL share one
`data/` directory.

#### Scenario: Two services on one node
- **WHEN** two game services run on the same node
- **THEN** each holds its own `data/` directory and no lock on another service's H2 file occurs

### Requirement: The node stops a service with stdin stop
**Priority:** MoSCoW Must

WHEN the node writes `stop` to a service's standard input, THE SYSTEM SHALL shut the service down cleanly and exit on its own.
THE SYSTEM SHALL NOT need to be killed by the node.

#### Scenario: Node stops a service
- **WHEN** the node writes `end` and then `stop` to the service's standard input
- **THEN** the service exits on its own, and the log ends with the shutdown message

### Requirement: The node provides the bind address
**Priority:** MoSCoW Must

THE SYSTEM SHALL bind the host and port given in the system properties `service.bind.host` and `service.bind.port` that the node
sets, as defined in `server/service-lifecycle`.

#### Scenario: Node sets the bind address
- **WHEN** the node starts the service with its bind properties
- **THEN** the service listens on that address and port

### Requirement: The deployment targets CloudNet 4.0.0-RC16
**Priority:** MoSCoW Must

THE SYSTEM SHALL build the bridge and document the deployment against CloudNet `4.0.0-RC16`, the release the owner's network
runs with Cygnus. Its release notes name Minecraft 1.21.11 as the newest supported version, so THE SYSTEM SHALL record the
result of running on Minecraft 26.2 in design.md before the change is archived.

#### Scenario: Acceptance on Minecraft 26.2
- **WHEN** the node acceptance group starts
- **THEN** the spike result for the bridge on Minecraft 26.2 is already recorded in design.md, Risks
