# Spec Delta

## Purpose

The run directory's catalogue is a mirror of the shipped catalogue. A map or cup removed from the repository must not remain
in the directory the server and the validation task read.

## ADDED Requirements

### Requirement: The run catalogue mirrors the shipped catalogue
**Priority:** MoSCoW Must

WHEN `prepareRunData` runs, THE BUILD SHALL make the `maps/` and `cups/` directories of the run data directory contain exactly
the files of the shipped `maps/` and `cups/` resources, with identical content, and SHALL remove every other file in that
directory.

#### Scenario: A map removed from the repository
- **WHEN** a map file is removed from the shipped resources and `prepareRunData` runs
- **THEN** the same file is absent from the run data directory afterwards

#### Scenario: A map added to the repository
- **WHEN** a map file is added to the shipped resources and `prepareRunData` runs
- **THEN** the file is present in the run data directory with the same content

#### Scenario: A file no source produces
- **WHEN** a file that no shipped resource produces is present in the run data directory and `prepareRunData` runs
- **THEN** the file is removed

### Requirement: An unchanged catalogue is not rewritten
**Priority:** Should

WHILE the shipped resources are unchanged since the last run, THE BUILD SHALL leave the run data directory's files untouched,
so that a second run of `prepareRunData` reports the task as up to date or as a no-op.

#### Scenario: Second run without a change
- **WHEN** `prepareRunData` runs twice with no change to the resources in between
- **THEN** the second run does not rewrite any file in the run data directory

### Requirement: The run data directory is build-owned
**Priority:** MoSCoW Must

THE BUILD SHALL describe the run data directory in the task description as a build-owned mirror, so that a file an operator places
there is known to be removed by the next run.

#### Scenario: Task description read by an operator
- **WHEN** an operator runs `./gradlew tasks --group voyager`
- **THEN** the description of `prepareRunData` says that the directory is a mirror and that other files are removed
