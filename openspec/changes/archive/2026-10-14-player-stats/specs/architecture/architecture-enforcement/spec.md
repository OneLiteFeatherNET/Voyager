# Spec Delta

## Purpose

Adds the persistence module to the architecture enforcement of this change: the module is covered by the fitness rules of
design D11, and the coverage check fails for a module that has no rule.

## ADDED Requirements

### Requirement: The persistence module is covered by the fitness rules
**MoSCoW:** Must. THE SYSTEM SHALL cover `voyager-persistence` with the rules of design D11 in `voyager-fitness`, and
`FitnessCoverageTest` SHALL fail when the module is on the classpath and no rule names its package.

#### Scenario: a persistence class depends on the race domain
- **WHEN** a class in `net.elytrarace.voyager.persistence..` depends on a class in `..race..`
- **THEN** the rule `persistenceDependsOnlyOnApiAndTheStorageLibraries` fails the build

#### Scenario: a module without a rule
- **WHEN** `voyager-persistence` is on the classpath and no `@ArchTest` rule names its package
- **THEN** `FitnessCoverageTest` fails and names the module
