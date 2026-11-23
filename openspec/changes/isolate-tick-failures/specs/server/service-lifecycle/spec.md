## ADDED Requirements

### Requirement: A throwing cup tick stops only that cup
**Priority:** MoSCoW Must. WHEN a cup's tick throws a `RuntimeException`, THE SYSTEM SHALL stop that cup, log the failure once
with the cup's state, and SHALL keep running the waiting room, the restart hand-off and the statistics replies on every later
tick.

#### Scenario: The cup stops, the lobby keeps moving
- **WHEN** the cup's tick throws on tick 100 and a player joins the waiting room on tick 101
- **THEN** the cup is stopped, the failure is logged once, and the player is placed in the waiting room on tick 101

#### Scenario: The failure is logged once
- **WHEN** the cup stays stopped for 1 200 ticks after its failure
- **THEN** the failure is logged exactly once and no further cup failure is logged

### Requirement: Statistics replies are not stopped by a cup failure
**Priority:** MoSCoW Must. WHILE a cup is stopped after a failure, THE SYSTEM SHALL still send the statistics replies that a
read has finished.

#### Scenario: A stats reply after a cup failure
- **WHEN** a statistics read finishes on tick 300 and the cup stopped on tick 100
- **THEN** the reply is sent on tick 300

### Requirement: The next cup round starts from validated state
**Priority:** MoSCoW Must. WHEN a new cup round starts after a stopped cup, THE SYSTEM SHALL clear the stopped state and SHALL
validate the cup's state before its first tick. IF the validation fails, THEN THE SYSTEM SHALL keep the cup stopped and log the
reason.

#### Scenario: Restart after a failure
- **WHEN** an operator runs `/race start` after a cup stopped on a failure, and the cup state is valid
- **THEN** the cup runs its first tick on the next tick and the stopped state is cleared

#### Scenario: Restart refused by an invalid state
- **WHEN** an operator runs `/race start` and the cup state fails validation
- **THEN** the cup stays stopped and the operator is told why
