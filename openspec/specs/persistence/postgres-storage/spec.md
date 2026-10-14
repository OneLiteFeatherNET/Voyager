# persistence/postgres-storage Specification

## Purpose
Defines how race and cup results reach PostgreSQL: whether the database is used, what happens when it is missing or fails,
how writes leave the tick thread, and how a finished race is stored as one transaction. It is the operator's contract for
`VOYAGER_PERSISTENCE` and the `VOYAGER_DB_*` variables.

## Requirements

### Requirement: The database is used only when the operator sets `on`
**MoSCoW:** Must. WHEN `VOYAGER_PERSISTENCE` is unset or `off`, THE SYSTEM SHALL bind a recorder that keeps nothing, SHALL log
one warning at boot that no statistics are kept, and SHALL answer `/stats` that statistics are not recorded on this server.
WHEN `VOYAGER_PERSISTENCE` is `on`, THE SYSTEM SHALL use the database as the rest of this capability describes.

#### Scenario: persistence unset
- **WHEN** the server starts with `VOYAGER_PERSISTENCE` unset and no `VOYAGER_DB_URL`
- **THEN** the server starts, exactly one warning says that no statistics are kept, and no connection to a database is attempted

#### Scenario: persistence off
- **WHEN** the server starts with `VOYAGER_PERSISTENCE=off` and no `VOYAGER_DB_URL`
- **THEN** the server starts, one warning names the state, and no connection to a database is attempted

### Requirement: A database that cannot be used at boot does not stop the game
**MoSCoW:** Must. WHEN `VOYAGER_PERSISTENCE` is `on` and the database is unreachable, the migration fails, or the schema does not
match the mappings at boot, THE SYSTEM SHALL start the game without statistics, SHALL log one warning at boot that names the failing
setting and SHALL NOT contain the password, and SHALL retry the database in the background. THE SYSTEM SHALL NOT refuse to start
because of the database.

#### Scenario: on with an unreachable database at boot
- **WHEN** the server starts with `VOYAGER_PERSISTENCE=on` and a URL that refuses connections
- **THEN** the game starts and listens, one warning names `VOYAGER_DB_URL` without the password, completions are buffered, and the writer retries until the database returns

#### Scenario: on without a user
- **WHEN** `VOYAGER_PERSISTENCE=on` and `VOYAGER_DB_USER` is not set
- **THEN** one warning names `VOYAGER_DB_USER`, statistics are off, and the game runs normally

### Requirement: Writes never block the game loop
**MoSCoW:** Must. WHEN a race or cup completes, THE SYSTEM SHALL hand the completion to a bounded queue without waiting, and
no class in `race` or `platform` SHALL call a blocking database operation or wait on a future.

#### Scenario: the queue has room
- **WHEN** a map finishes and the queue has room
- **THEN** the completion is queued and the tick continues in the same tick

#### Scenario: a fitness check
- **WHEN** a class in `race..` or `platform..` calls `CompletableFuture.get`, `join` or `Future.get`
- **THEN** the build fails

### Requirement: A full queue drops the newest completion with a warning
**MoSCoW:** Must. IF the queue is full, THEN THE SYSTEM SHALL drop the new completion, SHALL log one warning naming its map or
cup key and the count of drops, and SHALL keep serving the race.

#### Scenario: the queue is full
- **WHEN** the queue holds its capacity of completions and one more finishes
- **THEN** the new completion is dropped, a warning is logged, and the race result is still shown to the players

### Requirement: A lost database is retried with backoff and does not stop the game
**MoSCoW:** Must. WHILE the database is unreachable after a successful start, THE SYSTEM SHALL keep the queued completions,
SHALL retry with a backoff from one second up to sixty seconds, and SHALL log one warning when the outage starts and one when
it ends.

#### Scenario: the database restarts during a cup
- **WHEN** the database is stopped during a cup and started again after two maps
- **THEN** the completions of those maps are written in their finish order, and the cup keeps running throughout

### Requirement: A data error drops one completion and the writer goes on
**MoSCoW:** Must. IF a write fails with a data error (SQLSTATE class 22 or 23), THEN THE SYSTEM SHALL drop that completion
after one attempt, SHALL log an error naming its keys, and SHALL write the following completions.

#### Scenario: a constraint violation
- **WHEN** a completion violates a check constraint
- **THEN** it is logged as an error with its keys, and the next completion is written

### Requirement: One finished race or cup is one transaction
**MoSCoW:** Must. THE SYSTEM SHALL write a finished map's session, its results, its splits, its personal best upserts and its
player statistics in one transaction, and a cup's session, results and statistics in one transaction, so that a reader never
sees part of a race.

#### Scenario: a failure midway
- **WHEN** the insert of the third racer's result fails with a connection error
- **THEN** no row of that race is visible, and the whole completion is retried

### Requirement: The schema is owned by Flyway and checked by Hibernate
**MoSCoW:** Must. THE SYSTEM SHALL apply `V1__baseline.sql` in schema `voyager` under a database lock before the session factory
is built, SHALL run Hibernate with `hbm2ddl.auto=validate`, and SHALL NOT run `baselineOnMigrate` or `clean`.

#### Scenario: a fresh database
- **WHEN** the server starts against an empty database with `on`
- **THEN** version 1 is applied once and the mappings validate

### Requirement: Shutdown drains the queue for at most ten seconds
**MoSCoW:** Should. WHEN the server stops, THE SYSTEM SHALL write the queued completions for at most ten seconds, log how many
remain unwritten, and close the connection pool after the writer.

#### Scenario: a race finishes just before stop
- **WHEN** a map finishes and the server is stopped within the same second
- **THEN** the completion is written before the pool closes

### Requirement: The password is never printed
**MoSCoW:** Must. THE SYSTEM SHALL NOT print, log or include in an exception message the value of `VOYAGER_DB_PASSWORD` or
the content of `VOYAGER_DB_PASSWORD_FILE`.

#### Scenario: a failed connection
- **WHEN** the connection fails with a password set
- **THEN** no log line and no exception message contains the password
