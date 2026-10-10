# Spec Delta

## Purpose

How a Voyager server takes its bind address from its service environment and how it stops cleanly. A CloudNet node
starts the server with `service.bind.host` and `service.bind.port` set and no positional arguments, and it stops the
server by writing `stop` to standard input. The server must exit on its own on that line, and a player may stop it only
with the node's permission.

## ADDED Requirements

### Requirement: Bind address precedence
**Priority:** MoSCoW Must

THE SYSTEM SHALL take the host and port from the positional arguments when given. Otherwise THE SYSTEM SHALL take them
from the system properties `service.bind.host` and `service.bind.port`. Otherwise THE SYSTEM SHALL use `0.0.0.0` and
25565.

#### Scenario: Node sets the properties
- **WHEN** the server starts with `-Dservice.bind.host=10.0.0.5 -Dservice.bind.port=25571` and no arguments
- **THEN** the server binds `10.0.0.5:25571`

#### Scenario: Arguments win over properties
- **WHEN** the server starts with arguments `127.0.0.1 25600` and `service.bind.port=25571`
- **THEN** the server binds `127.0.0.1:25600`

### Requirement: An invalid bind port refuses to start
**Priority:** MoSCoW Must

IF `service.bind.port` is not a number from 1 to 65535, THEN THE SYSTEM SHALL refuse to start, name the property in the
error, and exit with a non-zero status.

#### Scenario: Port out of range
- **WHEN** `service.bind.port` is `70000`
- **THEN** the server exits with a non-zero status and the error names `service.bind.port`

### Requirement: Stdin stop shuts the server down cleanly
**Priority:** MoSCoW Must

WHEN the line `stop` arrives on standard input, THE SYSTEM SHALL stop the game server cleanly and exit with status 0. THE
SYSTEM SHALL ignore the line `end`.

#### Scenario: Node sends stop
- **WHEN** the node writes `stop` to standard input
- **THEN** the server shuts down and the process exits with status 0 without being killed

#### Scenario: Unknown line is ignored
- **WHEN** the node writes `end` to standard input
- **THEN** the server keeps running

### Requirement: The shutdown runs off the reader thread
**Priority:** MoSCoW Must

THE SYSTEM SHALL run the clean shutdown on a thread other than the one that reads standard input, because stopping the
server closes that reader.

#### Scenario: Shutdown thread differs from reader
- **WHEN** a stop request is accepted
- **THEN** the shutdown action runs on a different thread from the reader

### Requirement: A player stops the server only with the node
**Priority:** MoSCoW Must

WHEN a player runs `/stop`, THE SYSTEM SHALL stop the server only if the player holds `voyager.command.stop`. THE SYSTEM
SHALL always allow the console to stop the server.

#### Scenario: Player without the node
- **WHEN** a player without `voyager.command.stop` runs `/stop`
- **THEN** the server keeps running and the player receives the denial message

#### Scenario: Console stop
- **WHEN** the console runs `/stop`
- **THEN** the server shuts down

### Requirement: End of input keeps the server running
**Priority:** MoSCoW Should

IF standard input ends or cannot be read, THEN THE SYSTEM SHALL keep the server running without a console reader.

#### Scenario: Server started without stdin
- **WHEN** the server starts with standard input at end of file
- **THEN** the server starts and accepts players
