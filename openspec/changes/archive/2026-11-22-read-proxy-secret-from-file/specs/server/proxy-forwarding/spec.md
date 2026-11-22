# Spec Delta

## Purpose

The forwarding secret may be read from a file, which keeps it out of the process list. The system property stays as a
compatibility source, reported with a warning. Both the game server and the setup server follow these rules.

## MODIFIED Requirements

### Requirement: Velocity forwarding starts when a secret is configured
**Priority:** MoSCoW Must

WHEN a secret source holds a non-blank value, THE SYSTEM SHALL start the server with Minestom's Velocity modern forwarding using
that value as the secret. The sources are, highest first: the file named by the environment variable `VOYAGER_VELOCITY_SECRET_FILE`,
the file named by the system property `voyager.velocity.secret.file`, the environment variable `VOYAGER_VELOCITY_SECRET`, and the
system property `voyager.velocity.secret`. The first source that is set decides.

#### Scenario: Secret from the file named by the environment
- **WHEN** `VOYAGER_VELOCITY_SECRET_FILE` names a file whose content is `s3cret`, and `VOYAGER_VELOCITY_SECRET` is set to another value
- **THEN** the server uses the file's value for forwarding

#### Scenario: Secret from the environment
- **WHEN** `VOYAGER_VELOCITY_SECRET_FILE` is absent and `VOYAGER_VELOCITY_SECRET` is `s3cret`, and the property is set to another value
- **THEN** the server uses the environment value for forwarding

#### Scenario: Secret from the property
- **WHEN** no file and no environment variable are set, and `voyager.velocity.secret` is `s3cret`
- **THEN** the server uses the property value for forwarding

## ADDED Requirements

### Requirement: The secret file is read once, trimmed, and must hold a secret
**Priority:** MoSCoW Must

WHEN a file source names a path, THE SYSTEM SHALL read the file as UTF-8 once at boot, trim leading and trailing whitespace, and use
the result as the secret. THE SYSTEM SHALL NOT read the file again while it runs.

#### Scenario: Surrounding whitespace is not part of the secret
- **WHEN** the file's content is `s3cret` followed by a newline
- **THEN** the secret is `s3cret`

#### Scenario: An empty file refuses to start
- **WHEN** the file holds only whitespace
- **THEN** the server exits with a non-zero status before it binds the port, and the message names the setting and the path

### Requirement: A file that cannot be used refuses to start
**Priority:** MoSCoW Must

IF a file source names a path that is blank, missing, a directory, unreadable, or not UTF-8, THEN THE SYSTEM SHALL refuse to start
and exit with a non-zero status. THE SYSTEM SHALL NOT fall back to a lower source or to offline authentication in that case.

#### Scenario: A missing file
- **WHEN** `VOYAGER_VELOCITY_SECRET_FILE` names a path that does not exist, and `VOYAGER_VELOCITY_SECRET` is set
- **THEN** the server exits with a non-zero status, and the message names `VOYAGER_VELOCITY_SECRET_FILE` and the path

#### Scenario: A broken file is not bypassed by a valid environment secret
- **WHEN** `VOYAGER_VELOCITY_SECRET_FILE` names a file that is empty, and `VOYAGER_VELOCITY_SECRET` is `s3cret`
- **THEN** the server exits with a non-zero status and does not start with `s3cret`

### Requirement: The system property is reported as a warning
**Priority:** MoSCoW Must

WHEN the secret comes from the system property `voyager.velocity.secret`, THE SYSTEM SHALL log one WARN line at boot that names
`VOYAGER_VELOCITY_SECRET_FILE` as the recommended source, and THE SYSTEM SHALL report one WARNING for it in the configuration check.
The server SHALL still start with that secret.

#### Scenario: The property is the source
- **WHEN** the server starts with only `voyager.velocity.secret` set
- **THEN** the server starts, and one WARN line names `VOYAGER_VELOCITY_SECRET_FILE` and no line contains the secret's value

#### Scenario: The configuration check reports the property
- **WHEN** the configuration check runs with only `voyager.velocity.secret` set
- **THEN** the report holds one WARNING that names `voyager.velocity.secret`, the exit status is 0, and the report holds no part of the value

### Requirement: The configuration check reports an unusable secret file as an error
**Priority:** MoSCoW Must

WHEN the configuration check runs with a file source that cannot be used, THE SYSTEM SHALL report one ERROR that names the setting
and the path, and SHALL exit with status 1.

#### Scenario: An unreadable file
- **WHEN** the configuration check runs with `VOYAGER_VELOCITY_SECRET_FILE` naming a missing file
- **THEN** the report holds one ERROR that names `VOYAGER_VELOCITY_SECRET_FILE`, and the exit status is 1

## MODIFIED Requirements

### Requirement: The secret never appears in output
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT write the secret value, or the content of the secret file, to any log line, exception message, config-check
report or `toString` output. A path is written when it names the setting at fault.

#### Scenario: Settings are printed
- **WHEN** the settings record is converted to a string
- **THEN** the output contains no part of the secret value

#### Scenario: A file cannot be read
- **WHEN** the secret file holds `s3cret` and cannot be read as UTF-8 or is empty after trimming
- **THEN** the refusal message contains the path and no part of the file's content

### Requirement: No secret means offline authentication with a warning
**Priority:** MoSCoW Must

WHEN no secret source is set, THE SYSTEM SHALL start with offline authentication and SHALL log one WARN line that says player UUIDs
are not forwarded, and that names `VOYAGER_VELOCITY_SECRET_FILE` among the ways to set a secret.

#### Scenario: No secret configured
- **WHEN** the server starts with no source set
- **THEN** the server accepts connections in offline mode and logs the WARN line

### Requirement: A blank secret refuses to start
**Priority:** MoSCoW Must

IF a secret source is set but blank, THEN THE SYSTEM SHALL refuse to start and exit with a non-zero status. THE SYSTEM SHALL NOT
fall back to offline authentication in that case. This applies to the file sources, the environment variable and the property alike.

#### Scenario: Blank environment secret
- **WHEN** `VOYAGER_VELOCITY_SECRET` is set to spaces
- **THEN** the server exits with a non-zero status before it binds the port

### Requirement: A forwarded connection with a wrong signature is refused
**Priority:** MoSCoW Must

WHEN a player connects through the proxy with a forwarding signature that does not verify against the configured secret, THE SYSTEM
SHALL refuse that connection.

#### Scenario: Wrong secret at the proxy
- **WHEN** a connection arrives with a signature made from a different secret
- **THEN** the connection is refused and no player entity is created
