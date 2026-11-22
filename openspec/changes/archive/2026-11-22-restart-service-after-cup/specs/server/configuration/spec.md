# Spec Delta

## Purpose

The restart setting is read like every other setting: through the configuration loader, listed with the other keys, and checked by
the configuration check before the server starts.

## ADDED Requirements

### Requirement: The restart key is read through the loader and checked
**MoSCoW:** Must. THE SYSTEM SHALL read `VOYAGER_RESTART_AFTER_CUP` through the configuration loader, list it in the server's keys so
the configuration check and the boot log see it, and SHALL NOT read it with `System.getProperty`.

#### Scenario: The environment variable is honoured
- **WHEN** `VOYAGER_RESTART_AFTER_CUP=true` is set as an environment variable and no system property is set
- **THEN** the restart is on

#### Scenario: The configuration check reports the key
- **WHEN** the configuration check runs with `VOYAGER_RESTART_AFTER_CUP=yes`
- **THEN** the report names `VOYAGER_RESTART_AFTER_CUP` as an error and the exit status is 1

#### Scenario: The configuration check reports a rotation without a state file
- **WHEN** the configuration check runs with the restart on, `VOYAGER_CUP_ROTATION=alpha_cup,legacy_cup`, and no state file
- **THEN** the report names `VOYAGER_RESTART_AFTER_CUP` and `VOYAGER_CUP_ROTATION_STATE` and the exit status is 1
