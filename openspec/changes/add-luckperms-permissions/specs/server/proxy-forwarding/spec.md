# Spec Delta

## Purpose

How the game and setup servers accept player identity from a Velocity proxy. Behind a proxy the server must read the real
player UUID that the proxy forwards, because LuckPerms and every per-player lookup key on it. The secret that signs the
forwarded identity is read from the environment or a system property and is never printed.

## ADDED Requirements

### Requirement: Velocity forwarding starts when a secret is configured
**Priority:** MoSCoW Must

WHEN the environment variable `VOYAGER_VELOCITY_SECRET`, or else the system property `voyager.velocity.secret`, holds a
non-blank value, THE SYSTEM SHALL start the server with Minestom's Velocity modern forwarding using that value as the
secret.

#### Scenario: Secret from the environment
- **WHEN** `VOYAGER_VELOCITY_SECRET` is set to `s3cret` and the property is also set to another value
- **THEN** the server uses the environment value for forwarding

#### Scenario: Secret from the property
- **WHEN** `VOYAGER_VELOCITY_SECRET` is absent and `voyager.velocity.secret` is `s3cret`
- **THEN** the server uses the property value for forwarding

### Requirement: No secret means offline authentication with a warning
**Priority:** MoSCoW Must

WHEN neither the environment variable nor the system property is set, THE SYSTEM SHALL start with offline authentication
and SHALL log one WARN line that says player UUIDs are not forwarded.

#### Scenario: No secret configured
- **WHEN** the server starts with neither source set
- **THEN** the server accepts connections in offline mode and logs the WARN line

### Requirement: A blank secret refuses to start
**Priority:** MoSCoW Must

IF a secret source is set but blank, THEN THE SYSTEM SHALL refuse to start and exit with a non-zero status. THE SYSTEM
SHALL NOT fall back to offline authentication in that case.

#### Scenario: Blank environment secret
- **WHEN** `VOYAGER_VELOCITY_SECRET` is set to spaces
- **THEN** the server exits with a non-zero status before it binds the port

### Requirement: The secret never appears in output
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT write the secret value to any log line, exception message, config-check report or `toString` output.

#### Scenario: Settings are printed
- **WHEN** the settings record is converted to a string
- **THEN** the output contains no part of the secret value

### Requirement: A forwarded connection with a wrong signature is refused
**Priority:** MoSCoW Must

WHEN a player connects through the proxy with a forwarding signature that does not verify against the configured secret,
THE SYSTEM SHALL refuse that connection.

#### Scenario: Wrong secret at the proxy
- **WHEN** a connection arrives with a signature made from a different secret
- **THEN** the connection is refused and no player entity is created
