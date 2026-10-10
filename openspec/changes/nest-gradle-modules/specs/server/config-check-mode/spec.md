## MODIFIED Requirements

### Requirement: The Gradle task runs the check against the run directories
Priority: Must. The task `:voyager:server:validateCatalog` SHALL run the check with the working directory and the data and worlds paths the run tasks use, and SHALL honour the `-PdataPath` and `-PworldsPath` overrides. It SHALL depend on `prepareRunData`, so the check reads the directory the server reads, and `prepareRunData` SHALL copy with sync semantics (change `fix-run-data-sync`).

#### Scenario: the override reaches the check
- **WHEN** the task runs with `-PworldsPath=/mnt/worlds`
- **THEN** the check's worlds path is `/mnt/worlds`

#### Scenario: the default path is the doubled run path
- **WHEN** the task runs without overrides
- **THEN** the report names `<root>/run/run/worlds` and `<root>/run/run/data`

#### Scenario: a map deleted from the repository is not validated
- **WHEN** a map file is deleted from the repository's resources and the task runs
- **THEN** the file is absent from the run data directory before the check reads it
