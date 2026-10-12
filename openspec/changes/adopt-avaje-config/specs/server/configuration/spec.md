# Spec Delta

## Purpose

Defines how the server reads its configuration after the move to avaje-config: one loader, one precedence, one exception, and
the rule that the rest of the code does not touch the JVM or the environment. Every existing key keeps its name and meaning.

## ADDED Requirements

### Requirement: One loader reads every setting
**MoSCoW:** Must. THE SYSTEM SHALL read every server setting through one loader in `voyager-platform`, and no other class
outside the composition roots SHALL read a system property or an environment variable.

#### Scenario: a setting read from the JVM outside the loader
- **WHEN** a class outside `platform.config`, `server` and `setup` calls `System.getProperty` or `System.getenv`
- **THEN** the fitness rule `environmentAccessIsConfinedToTheEdge` fails the build and names the class

### Requirement: Precedence follows the layers of the design
**MoSCoW:** Must. WHEN the same key is set in more than one place, THE SYSTEM SHALL use the value from the highest of these:
the system property, the environment variable, the file named by `CONFIG_FILE`, the `application.yaml` in the working
directory, and otherwise the default in code.

#### Scenario: a system property beats the environment
- **WHEN** `VOYAGER_CUP=alpha` is in the environment and `-DVOYAGER_CUP=beta` is on the command line
- **THEN** the server plays the cup `beta`

#### Scenario: the environment beats the working-directory file
- **WHEN** `application.yaml` sets `VOYAGER_MIN_PLAYERS: 4` and the environment sets `VOYAGER_MIN_PLAYERS=2`
- **THEN** the server uses a minimum of two racers

#### Scenario: a file in the working directory sets a value
- **WHEN** the working directory holds an `application.yaml` with `VOYAGER_MAX_PLAYERS: 12` and no other source sets it
- **THEN** the maximum racer count is twelve

### Requirement: The velocity secret takes the environment before the property
**MoSCoW:** Must. WHEN both `VOYAGER_VELOCITY_SECRET` and `voyager.velocity.secret` are set, THE SYSTEM SHALL use the
environment value, as `server/proxy-forwarding` requires, and SHALL not print either value.

#### Scenario: both set to different values
- **WHEN** `VOYAGER_VELOCITY_SECRET=s3cret` and `voyager.velocity.secret=other` are both set
- **THEN** forwarding uses `s3cret`

#### Scenario: a blank environment secret refuses to start
- **WHEN** `VOYAGER_VELOCITY_SECRET` is set to only spaces
- **THEN** the server refuses to start and the message names `VOYAGER_VELOCITY_SECRET` without its value

### Requirement: Existing keys keep their names, defaults and refusals
**MoSCoW:** Must. THE SYSTEM SHALL keep the keys `VOYAGER_DATA_PATH`, `VOYAGER_WORLDS_PATH`, `VOYAGER_CUP`,
`VOYAGER_MIN_PLAYERS`, `VOYAGER_LOBBY_WORLD`, `VOYAGER_LOBBY_SPAWN`, `VOYAGER_MAX_PLAYERS`, `voyager.dev`,
`voyager.config.check`, `service.bind.host` and `service.bind.port` with their defaults and validation messages unchanged.

#### Scenario: an invalid port
- **WHEN** `service.bind.port=abc` is set
- **THEN** the server refuses to start and the message names `service.bind.port` and the text read

#### Scenario: the CloudNet node sets the bind settings
- **WHEN** a service starts with `-Dservice.bind.host=0.0.0.0 -Dservice.bind.port=25565`
- **THEN** the server binds that address and port

### Requirement: The configuration check report does not change
**MoSCoW:** Must. WHEN the configuration check runs with `-Dvoyager.config.check=true`, THE SYSTEM SHALL print the same
problems, sources and exit status as the release before this change for every input in the golden matrix.

#### Scenario: the golden matrix
- **WHEN** the check runs on each input of the golden matrix with the old and the new jar
- **THEN** the two reports are identical

### Requirement: The API and the domain do not read configuration
**MoSCoW:** Must. THE SYSTEM SHALL keep `voyager-api`, `voyager-physics` and `voyager-race` free of avaje-config and of any
environment or property access, and `ApiPurityTest` SHALL fail when they are not.

#### Scenario: a domain class reads a property
- **WHEN** a class in `net.elytrarace.voyager.race` imports `io.avaje.config` or calls `System.getProperty`
- **THEN** the fitness rule fails the build

### Requirement: Environment names are documented, including the new ones
**MoSCoW:** Should. THE SYSTEM SHALL document in `docs/reference/configuration.md` the environment name of every key,
the precedence, and the keys that gained an environment name by this change.

#### Scenario: an operator looks up a key
- **WHEN** an operator reads the reference page
- **THEN** each key lists its system property, its environment name and its default
