## ADDED Requirements

### Requirement: The server validates and exits without binding a socket
Priority: Must. When the system property `voyager.config.check` is `true`, the server SHALL run the validation, print its report, and exit. It SHALL NOT bind a network port and SHALL NOT start the Minestom server. Basis: NFR-007.

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
Priority: Must. The task `:voyager-server:validateCatalog` SHALL run the check with the working directory and the data and worlds paths the run tasks use, and SHALL honour the `-PdataPath` and `-PworldsPath` overrides. It SHALL depend on `prepareRunData`, so the check reads the directory the server reads.

#### Scenario: the override reaches the check
- **WHEN** the task runs with `-PworldsPath=/mnt/worlds`
- **THEN** the check's worlds path is `/mnt/worlds`

#### Scenario: the default path is the doubled run path
- **WHEN** the task runs without overrides
- **THEN** the report names `<root>/run/run/worlds` and `<root>/run/run/data`

### Requirement: Run tasks stop before the server starts when the check fails
Priority: Must. `runServer` and `runServerDev` SHALL depend on `validateCatalog`. A failing check SHALL stop the Gradle build before the server's JVM starts, and the missing-worlds warning in the run task's `doFirst` SHALL be replaced by the check's report.

#### Scenario: a missing worlds directory stops the run
- **WHEN** `runServerDev` runs and `run/run/worlds` does not exist
- **THEN** the build fails with the check's report and the server JVM is not started

#### Scenario: a valid configuration starts the server
- **WHEN** `runServerDev` runs and the check passes
- **THEN** the server JVM starts with the same arguments as before this change

### Requirement: Normal boot refuses the same problems
Priority: Should. A server started without the check property SHALL refuse to start on an invalid configuration, and the refusal message SHALL list every error from the same report.

#### Scenario: boot refusal lists every error
- **WHEN** the server starts normally and two errors exist
- **THEN** the refusal names both, not only the first
