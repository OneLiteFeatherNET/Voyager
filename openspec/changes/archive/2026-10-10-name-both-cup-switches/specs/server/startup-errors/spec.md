# Spec Delta

## Purpose

Startup errors that ask the operator to set a switch name every form in which that switch can be set, so the
advice works whether the server is started with `java -jar` or through Gradle.

## ADDED Requirements

### Requirement: A startup error names both forms of a switch it asks for
**Priority:** MoSCoW Must

WHEN the server refuses to start and its message tells the operator to set a switch, THE SERVER SHALL name the
Gradle property and the system property for that switch in the same message, in the form
`set -P<gradle>=<value> (Gradle) or -D<system>=<value> (java -jar)`.

#### Scenario: Ambiguous cup names both switches
- GIVEN a cup catalogue holding more than one cup and no cup selected
- WHEN the server refuses to start
- THEN the message contains `-Pcup=<name>` and `-DVOYAGER_CUP=<name>`

#### Scenario: Missing data directory names both switches
- GIVEN the data directory `VOYAGER_DATA_PATH` does not exist
- WHEN the server refuses to start
- THEN the message contains `-DVOYAGER_DATA_PATH=<dir>` and `-PdataPath=<dir>`

#### Scenario: Missing worlds directory names both switches
- GIVEN the worlds directory `VOYAGER_WORLDS_PATH` does not exist
- WHEN the server refuses to start
- THEN the message contains `-DVOYAGER_WORLDS_PATH=<dir>` and `-PworldsPath=<dir>`

### Requirement: Error text changes no selection behaviour
**Priority:** MoSCoW Must

WHEN the message of a startup error changes, THE SERVER SHALL keep the same property names, defaults, resolution
order and exit behaviour.

#### Scenario: Cup still resolves from either switch
- GIVEN `-Pcup=<name>` is passed through Gradle, which sets `VOYAGER_CUP`
- WHEN the server starts
- THEN the named cup is selected, as before this change
