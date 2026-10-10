# server/config-check-mode Specification

## Purpose
Defines the validate-and-exit run mode of the Voyager server, enabled with `-Dvoyager.config.check=true`. In this
mode the server runs the catalogue and settings check, prints every problem, and exits 0 or 1 without binding a port
or starting the game. The Gradle task `validateCatalog` runs it against the run directories, and the run tasks stop
before the server starts when it fails, unless `-PskipCatalogCheck` is set. Normal boot refuses with the same full
report.

## Requirements

### Requirement: The server validates and exits without binding a socket
Priority: Must. When the system property `voyager.config.check` is `true`, the server SHALL run the validation, print its report, and exit. It SHALL NOT bind a network port and SHALL NOT start the game server (no tick loop, no listener). The check MAY initialise Minestom's registries, which the deep world check needs. Basis: NFR-007.

#### Scenario: a valid configuration exits zero
- **WHEN** the check runs against a valid data directory and worlds directory
- **THEN** the process exits with code 0 and the port the server would bind is still free

#### Scenario: an invalid configuration exits one
- **WHEN** the check runs and at least one error exists
- **THEN** the process exits with code 1 and prints every error, one per line, each with its source and key

### Requirement: Settings are validated without throwing on the first problem
Priority: Must. The check SHALL evaluate the settings (host, port, data path, worlds path, selected cup) and SHALL collect every settings problem. It SHALL NOT use the throwing compact constructor of `ServerSettings` for this purpose. The normal boot path SHALL keep its refusal.

#### Scenario: two missing directories are both reported
- **WHEN** neither the data directory nor the worlds directory exists
- **THEN** the report contains one error for each directory, with its absolute path

#### Scenario: a selected cup that does not exist is reported
- **WHEN** `VOYAGER_CUP` names a cup that the catalogue does not provide
- **THEN** the report contains an error with key `VOYAGER_CUP` naming the cup

### Requirement: The Gradle task runs the check against the run directories
Priority: Must. The task `:voyager-server:validateCatalog` SHALL run the check with the working directory and the data and worlds paths the run tasks use, and SHALL honour the `-PdataPath` and `-PworldsPath` overrides. It SHALL depend on `prepareRunData`, so the check reads the directory the server reads, and `prepareRunData` SHALL copy with sync semantics (change `fix-run-data-sync`).

#### Scenario: the override reaches the check
- **WHEN** the task runs with `-PworldsPath=/mnt/worlds`
- **THEN** the check's worlds path is `/mnt/worlds`

#### Scenario: the default path is the doubled run path
- **WHEN** the task runs without overrides
- **THEN** the report names `<root>/run/run/worlds` and `<root>/run/run/data`

#### Scenario: a map deleted from the repository is not validated
- **WHEN** a map file is deleted from the repository's resources and the task runs
- **THEN** the file is absent from the run data directory before the check reads it

### Requirement: Run tasks stop before the server starts when the check fails
Priority: Must. `runServer` and `runServerDev` SHALL depend on `validateCatalog`. A failing check SHALL stop the Gradle build before the server's JVM starts, and the missing-worlds warning in the run task's `doFirst` SHALL be replaced by the check's report.

#### Scenario: a missing worlds directory stops the run
- **WHEN** `runServerDev` runs and `run/run/worlds` does not exist
- **THEN** the build fails with the check's report and the server JVM is not started

#### Scenario: a valid configuration starts the server
- **WHEN** `runServerDev` runs and the check passes
- **THEN** the server JVM starts with the same arguments as before this change

### Requirement: The run tasks have a skip switch for the check
Priority: Should. When the Gradle property `skipCatalogCheck` is set, `runServer` and `runServerDev` SHALL NOT depend on `validateCatalog`, and SHALL log one warning line saying that the check was skipped. `validateCatalog` itself SHALL still run when invoked directly.

#### Scenario: a broken catalogue is run with the skip switch
- **WHEN** `runServerDev` runs with `-PskipCatalogCheck` and the worlds directory is missing
- **THEN** the server JVM starts, the warning line names the skipped check, and no check report is printed

#### Scenario: the check still runs when asked for directly
- **WHEN** `validateCatalog` runs with `-PskipCatalogCheck`
- **THEN** the check runs and its report is printed

### Requirement: Normal boot refuses with the same report
Priority: Should. A server started without the check property SHALL refuse to start on an invalid configuration, and the refusal message SHALL list every error from the same report, not only the first.

#### Scenario: boot refusal lists every error
- **WHEN** the server starts normally and two errors exist
- **THEN** the refusal names both, not only the first

### Requirement: The minimum racer setting is validated by the check and by boot
Priority: Must. The check SHALL evaluate `VOYAGER_MIN_PLAYERS` together with the other settings and SHALL report a value
that is not a whole number or is below 1 as an error with key `VOYAGER_MIN_PLAYERS`. An unset value is not a problem.
Normal boot SHALL refuse with the same report.

#### Scenario: a value that is not a number is reported
- **WHEN** `VOYAGER_MIN_PLAYERS` is set to `two`
- **THEN** the report contains one error with key `VOYAGER_MIN_PLAYERS` and the check exits with status 1

#### Scenario: a value below one is reported
- **WHEN** `VOYAGER_MIN_PLAYERS` is set to `0`
- **THEN** the report contains an error with key `VOYAGER_MIN_PLAYERS` naming the value 0

#### Scenario: an unset value is not a problem
- **WHEN** `VOYAGER_MIN_PLAYERS` is not set
- **THEN** the report contains no error for the minimum racer setting
