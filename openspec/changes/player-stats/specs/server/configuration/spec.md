# Spec Delta

## Purpose

Adds the persistence keys to the server configuration: the switch `VOYAGER_PERSISTENCE` and the `VOYAGER_DB_*` settings. They
are read through the loader of `server/configuration` (after `adopt-avaje-config`), and a problem with them never refuses the start.

## ADDED Requirements

### Requirement: The persistence switch and the database keys are read through the loader
**MoSCoW:** Must. THE SYSTEM SHALL read `VOYAGER_PERSISTENCE` (`off` or `on`; unset means `off`), `VOYAGER_DB_URL`,
`VOYAGER_DB_USER`, `VOYAGER_DB_PASSWORD`, `VOYAGER_DB_PASSWORD_FILE`, `VOYAGER_DB_POOL_SIZE` and `VOYAGER_DB_QUEUE_CAPACITY` through
the one loader, with the precedence of this capability.

#### Scenario: the switch is unset
- **WHEN** no layer sets `VOYAGER_PERSISTENCE`
- **THEN** the server runs with statistics off and logs exactly one warning that no statistics are kept

#### Scenario: the switch has an unknown value
- **WHEN** `VOYAGER_PERSISTENCE` is set to a value other than `off` or `on`
- **THEN** the server logs one warning that names `VOYAGER_PERSISTENCE`, runs with statistics off, and starts

### Requirement: A database setting problem never refuses the start
**MoSCoW:** Must. WHEN `VOYAGER_PERSISTENCE` is `on` and `VOYAGER_DB_URL`, `VOYAGER_DB_USER` or the password is missing, or
`VOYAGER_DB_POOL_SIZE` or `VOYAGER_DB_QUEUE_CAPACITY` is out of range, THE SYSTEM SHALL log one warning that names the setting,
SHALL run with statistics off, and SHALL NOT refuse to start. THE SYSTEM SHALL NOT print the value of the password.

#### Scenario: on without a URL
- **WHEN** `VOYAGER_PERSISTENCE=on` and `VOYAGER_DB_URL` is not set
- **THEN** one warning names `VOYAGER_DB_URL`, statistics are off, and the server starts

#### Scenario: a pool size out of range
- **WHEN** `VOYAGER_PERSISTENCE=on` and `VOYAGER_DB_POOL_SIZE` is 1
- **THEN** one warning names `VOYAGER_DB_POOL_SIZE`, statistics are off, and the server starts
