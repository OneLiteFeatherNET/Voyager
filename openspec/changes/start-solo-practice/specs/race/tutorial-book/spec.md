# Spec Delta

## Purpose

The tutorial explains what a run means before a racer flies it. One line on the first page says whether the run is ranked.

## ADDED Requirements

### Requirement: The first tutorial page says whether the run is ranked
**MoSCoW:** Must. THE SYSTEM SHALL show on the first tutorial page one line that says the run is ranked when the armed cup is a
RACE cup, and says it is practice and not ranked when the cup is the practice cup, in the racer's language.

#### Scenario: A ranked cup
- **WHEN** a racer opens the tutorial while a RACE cup is armed
- **THEN** the first page carries the ranked line in the racer's language

#### Scenario: The practice cup
- **WHEN** a racer opens the tutorial while the practice cup is armed
- **THEN** the first page carries the practice line, which says the run is not ranked, in the racer's language
