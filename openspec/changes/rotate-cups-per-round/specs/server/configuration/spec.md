# Spec Delta

## Purpose

Two keys are added for the cup rotation. Both are read through the one loader, as every key is, and both are documented with the
existing keys. No existing key changes its name, meaning or default.

## ADDED Requirements

### Requirement: The rotation keys are read through the loader and documented
**MoSCoW:** Must. THE SYSTEM SHALL read `VOYAGER_CUP_ROTATION` and `VOYAGER_CUP_ROTATION_STATE` through the configuration loader, with
the environment names `VOYAGER_CUP_ROTATION` and `VOYAGER_CUP_ROTATION_STATE`, and SHALL document both in
`docs/reference/configuration.md` with their system property, environment name and default.

#### Scenario: The rotation read from the environment
- **WHEN** the environment variable `VOYAGER_CUP_ROTATION` is set and no system property sets it
- **THEN** the server reads the rotation from the environment variable

#### Scenario: The reference page lists both keys
- **WHEN** an operator reads `docs/reference/configuration.md`
- **THEN** each of the two keys lists its system property, its environment name, and its default: no rotation and no state file. The
state file's example path is `/home/cloud/cloudnet/local/voyager-state/rotation`

### Requirement: A state file whose parent directory is missing refuses the configuration check
**MoSCoW:** Should. IF `VOYAGER_CUP_ROTATION_STATE` names a path whose parent directory does not exist, THEN THE SYSTEM SHALL report it as a
configuration error in the configuration check, naming the key and the path, and the boot SHALL refuse with the same report.

#### Scenario: The state directory does not exist
- **WHEN** `VOYAGER_CUP_ROTATION_STATE=/var/lib/voyager-missing/rotation` and `/var/lib/voyager-missing` does not exist
- **THEN** the configuration check reports one error that names `VOYAGER_CUP_ROTATION_STATE` and the path
