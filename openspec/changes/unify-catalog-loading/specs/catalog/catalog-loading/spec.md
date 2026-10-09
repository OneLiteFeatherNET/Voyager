# Spec Delta

## Purpose

Defines how the rebuild reads its racecourse and cup definitions from disk. The `catalog` slice has one load point that
turns one data directory into an immutable snapshot. The load exposes every problem it finds as data, and boot refuses
an inconsistent catalogue on the first problem, with the exception that problem raised before this change. The
all-at-once report is a separate capability, owned by `add-catalog-validate-task`.

## ADDED Requirements

### Requirement: One load point produces the catalogue snapshot
**Priority:** MoSCoW Must

THE SYSTEM SHALL provide exactly one entry point that reads the `maps/` and `cups/` subdirectories of a data directory.
It SHALL return a `CatalogSnapshot` from `load`, and a `CatalogReading` (snapshot plus problems) from `read`. Every
consumer of map or cup definitions SHALL obtain them from that entry point's result.

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

### Requirement: Problems are exposed as data
**Priority:** MoSCoW Must

WHEN the data directory contains one or more problems, THE SYSTEM SHALL return every problem in the reading, each with its
source path and the exception it would have raised, and SHALL NOT throw for a problem in `read`.

#### Scenario: Two malformed map files
- **WHEN** two map files fail to parse and `read` runs
- **THEN** the reading holds two problems, one per file, each naming its file

#### Scenario: Missing directory and valid other directory
- **WHEN** `cups/` is absent, `maps/` is valid, and `read` runs
- **THEN** the reading holds exactly one problem, naming the missing `cups/` directory, and the map definitions are present in the snapshot

### Requirement: Boot refuses an inconsistent catalogue on the first problem
**Priority:** MoSCoW Must

IF the catalogue holds a problem at boot, THEN THE SYSTEM SHALL NOT produce a snapshot through `load`, SHALL throw the
exception of the first problem in the order maps, then cups, then cross-catalogue references, with its message unchanged
from before this change, and the server SHALL refuse to start with that exception as its cause.

#### Scenario: Two malformed map files at boot
- **WHEN** two map files fail to parse and the server boots
- **THEN** the server does not start, and the refusal is the malformed-file exception of the first file in sorted filename order, with today's message

#### Scenario: Cup naming an unknown map at boot
- **WHEN** a cup names a map that no map file provides and the server boots
- **THEN** the server does not start, and the refusal is today's unresolved-cup-map exception, listing the dangling entries as it does today

#### Scenario: Malformed map and dangling cup reference together at boot
- **WHEN** a map file fails to parse and a cup names a map
- **THEN** the refusal is the malformed-file exception of the map file, and the cross-catalogue check is not reached

#### Scenario: Clean catalogue
- **WHEN** the catalogue holds no problem
- **THEN** `load` returns a snapshot and boot continues

### Requirement: Failure messages name the file and the problem
**Priority:** MoSCoW Should

WHERE a problem concerns one file, THE SYSTEM SHALL name that file's path in the message; WHERE a problem concerns a
reference, THE SYSTEM SHALL name the referring cup and the missing map.

#### Scenario: Duplicate name in two files
- **WHEN** two map files declare the same name
- **THEN** the problem's message names both file paths and the duplicated name

#### Scenario: Cup naming an unknown map
- **WHEN** a cup names a map nothing provides
- **THEN** the message reads as "cup '<cup>' plays '<map>'" and names no other file

### Requirement: Problem order is deterministic
**Priority:** MoSCoW Should

THE SYSTEM SHALL read files in sorted filename order and SHALL list problems in that order, so that the same directory
produces the same reading on every machine.

#### Scenario: Same directory on two machines
- **WHEN** the same data directory is read on two machines with different directory iteration orders
- **THEN** both readings list the problems in the same order

### Requirement: Platform code carries no dependency-injection annotations
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep the `catalog` slice free of dependency-injection annotations. The `CatalogSnapshot` bean SHALL be
constructed by a `@Bean` method in the `voyager-server` `@Factory`.

#### Scenario: Injection framework on the platform classpath
- **WHEN** the architecture rules run over `voyager-platform`
- **THEN** no class in the `catalog` slice carries a dependency-injection annotation
