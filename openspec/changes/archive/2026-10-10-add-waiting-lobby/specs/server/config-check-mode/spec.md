# Spec Delta

## ADDED Requirements

### Requirement: The minimum racer setting is validated by the check and by boot
Priority: Must. The check SHALL evaluate `VOYAGER_MIN_PLAYERS` together with the other settings and SHALL report a value
that is not a whole number or is below 1 as an error with key `VOYAGER_MIN_PLAYERS`. An unset value is not a problem.
Normal boot SHALL refuse with the same report.

#### Scenario: a value that is not a number is reported
- **WHEN** `VOYAGER_MIN_PLAYERS` is set to `two`
- **THEN** the report contains one error with key `VOYAGER_MIN_PLAYERS` and the check exits with status 1

#### Scenario: a value below one is reported
- **WHEN** `VOYAGER_MIN_PLAYERS` is set to `0`
- **THEN** the report contains an error with key `VOYAGER_MIN_PLAYERS` naming the value 0

#### Scenario: an unset value is not a problem
- **WHEN** `VOYAGER_MIN_PLAYERS` is not set
- **THEN** the report contains no error for the minimum racer setting
