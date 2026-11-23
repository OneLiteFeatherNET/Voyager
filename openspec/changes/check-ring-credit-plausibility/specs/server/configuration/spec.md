# Spec Delta

## Purpose

The ring plausibility setting is read like every other setting: through the configuration loader, listed with the other keys,
checked before the server starts, and documented.

## ADDED Requirements

### Requirement: The ring plausibility key is read through the loader, checked and documented
**MoSCoW:** Must. THE SYSTEM SHALL read `VOYAGER_RING_PLAUSIBILITY` (`off`, `observe` or `enforce`; unset means `observe`) through
the configuration loader, SHALL list it in the server's keys, and SHALL name it in `docs/reference/configuration.md`.

#### Scenario: the key is unrecognised
- **WHEN** the configuration check runs with `VOYAGER_RING_PLAUSIBILITY=strict`
- **THEN** the report names `VOYAGER_RING_PLAUSIBILITY` as an error and the exit status is 1

#### Scenario: the key is documented
- **WHEN** an operator reads `docs/reference/configuration.md`
- **THEN** the key lists its system property, its environment name, its default and its three values
