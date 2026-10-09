# Spec Delta

## Purpose

Defines how the rebuild reads its racecourse and cup definitions from disk. The `catalog` slice has one load point that
turns one data directory into an immutable snapshot, reports every problem it finds at once, and refuses a
catalogue that is not consistent as a whole. It is the foundation for scoped validation, the validate task and hot
reload, which build on the snapshot rather than on the directory.

## ADDED Requirements

### Requirement: One load point produces the catalogue snapshot
**Priority:** MoSCoW Must

THE SYSTEM SHALL provide exactly one operation that reads the `maps/` and `cups/` subdirectories of a data directory and
returns a `CatalogSnapshot`. Every consumer of map or cup definitions SHALL obtain them from that snapshot.

#### Scenario: Valid data directory loads once
- **WHEN** the server starts with a data directory holding valid maps and cups
- **THEN** the snapshot is built once and every map and cup lookup is answered from it

#### Scenario: No second read path
- **WHEN** a consumer needs a map or cup definition
- **THEN** it receives the snapshot or a port answered by the snapshot, and no class reads `maps/` or `cups/` directly

### Requirement: The snapshot is an immutable value
**Priority:** MoSCoW Must

THE SYSTEM SHALL make `CatalogSnapshot` immutable: its maps and cups SHALL be unmodifiable collections, and it SHALL
expose no operation that adds, replaces or removes a definition.

#### Scenario: Caller attempts to change the snapshot
- **WHEN** a caller tries to modify the map or cup collections of a snapshot
- **THEN** the modification is refused with an `UnsupportedOperationException`

#### Scenario: Snapshot keeps its contents after the directory changes
- **WHEN** a file in the data directory is deleted after the snapshot was built
- **THEN** the snapshot still answers with the definition it loaded

### Requirement: Every problem is collected before the load fails
**Priority:** MoSCoW Must

WHEN the data directory contains more than one problem, THE SYSTEM SHALL check every map file, every cup file and the
cross-catalogue references before it reports failure, and SHALL report all of them in one exception.

#### Scenario: Two malformed map files
- **WHEN** two map files fail to parse
- **THEN** the single reported failure names both files

#### Scenario: Malformed file and dangling cup reference together
- **WHEN** one cup file is malformed and another cup names a map that no map file provides
- **THEN** the single reported failure names the malformed file and the dangling reference

#### Scenario: Missing directory and valid other directory
- **WHEN** `cups/` is absent and `maps/` is valid
- **THEN** the single reported failure names the missing `cups/` directory

### Requirement: An inconsistent catalogue refuses to load
**Priority:** MoSCoW Must

IF any problem is found, THEN THE SYSTEM SHALL NOT produce a snapshot, and the server SHALL refuse to start with the
exception as its cause.

#### Scenario: Problem found at boot
- **WHEN** the catalogue holds a problem at boot
- **THEN** the server does not start and the log names the file or reference at fault

#### Scenario: Clean catalogue
- **WHEN** the catalogue holds no problem
- **THEN** the load returns a snapshot and boot continues

### Requirement: Failure messages name the file and the problem
**Priority:** MoSCoW Should

WHERE a problem concerns one file, THE SYSTEM SHALL name that file's path in the message; WHERE a problem concerns a
reference, THE SYSTEM SHALL name the referring cup and the missing map.

#### Scenario: Duplicate name in two files
- **WHEN** two map files declare the same name
- **THEN** the message names both file paths and the duplicated name

#### Scenario: Cup naming an unknown map
- **WHEN** a cup names a map nothing provides
- **THEN** the message reads as "cup '<cup>' plays '<map>'" and names no other file

### Requirement: Failure reports are deterministic
**Priority:** MoSCoW Should

THE SYSTEM SHALL read files in sorted filename order and SHALL list problems in that order, so that the same directory
produces the same report on every machine.

#### Scenario: Same directory on two machines
- **WHEN** the same data directory is loaded on two machines with different directory iteration orders
- **THEN** both failure reports list the problems in the same order

### Requirement: Platform code carries no dependency-injection annotations
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep the `catalog` slice free of dependency-injection annotations. The `CatalogSnapshot` bean SHALL be
constructed by a `@Bean` method in the `voyager-server` `@Factory`.

#### Scenario: Injection framework on the platform classpath
- **WHEN** the architecture rules run over `voyager-platform`
- **THEN** no class in the `catalog` slice carries a dependency-injection annotation
