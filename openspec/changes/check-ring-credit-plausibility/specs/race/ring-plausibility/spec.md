# Spec Delta

## Purpose

Ring credit is granted from the position a client reports. This capability bounds how far a racer may move between two sampled
ticks, using the shadow simulation's speed, and flags a tick that moves farther. In observe mode a flag is recorded and changes
nothing a racer sees. In enforce mode a flagged tick withholds ring credit. The check never kicks or bans a racer.

## ADDED Requirements

### Requirement: The displacement bound is a pure function of the shadow speed
**MoSCoW:** Must. WHEN two consecutive samples of a racer are compared, THE SYSTEM SHALL compute the largest displacement the
racer may make between them from the shadow simulation's speed for those ticks and named slack constants, and SHALL return a
verdict that names the excess when the reported displacement is larger.

#### Scenario: a displacement inside the bound
- **WHEN** the reported displacement between two samples is no larger than the bound for the shadow speed
- **THEN** the verdict is plausible and the excess is zero

#### Scenario: a displacement outside the bound
- **WHEN** the reported displacement is larger than the bound
- **THEN** the verdict is implausible and the excess is the reported displacement minus the bound

#### Scenario: the bound is computed without the game
- **WHEN** the bound is computed in a unit test with a fixed speed and a fixed displacement
- **THEN** the result is the same on every run and no server, world or clock is needed

### Requirement: Observe mode records a flag and changes no credit
**MoSCoW:** Must. WHILE `VOYAGER_RING_PLAUSIBILITY` is `observe`, THE SYSTEM SHALL record each implausible tick for the run and
keep the largest excess, and SHALL grant ring credit and scores exactly as when the check is off.

#### Scenario: an implausible tick in observe mode
- **WHEN** a racer's tick is flagged and the ring it crosses is otherwise valid
- **THEN** the ring is credited, the flag is recorded, and the largest excess of the run is kept

#### Scenario: a clean run in observe mode
- **WHEN** no tick of a run is flagged
- **THEN** the run's plausibility error is absent and the result is the same as with the check off

### Requirement: Enforce mode withholds credit for a flagged tick
**MoSCoW:** Should. WHILE `VOYAGER_RING_PLAUSIBILITY` is `enforce`, WHEN a racer's tick is flagged, THE SYSTEM SHALL withhold
ring credit for the segment of that tick and SHALL show the racer one action-bar line that says the ring did not count.

#### Scenario: a flagged tick in enforce mode
- **WHEN** a flagged tick is the segment that crosses a ring
- **THEN** the ring is not credited, the racer sees the action-bar line once for that ring, and the racer is not kicked

#### Scenario: a flagged tick never ends a race
- **WHEN** a flagged tick occurs at the finish
- **THEN** the finish is not granted by that tick, and the race continues until a valid tick finishes it

### Requirement: The check never punishes beyond ring credit
**MoSCoW:** Must. THE SYSTEM SHALL NOT kick, ban or change the scores of a racer for a flag, in any mode, and IF
`VOYAGER_RING_PLAUSIBILITY` is `off`, THEN THE SYSTEM SHALL skip the check and grant ring credit as before.

#### Scenario: a racer is flagged in enforce mode
- **WHEN** a racer is flagged repeatedly in enforce mode
- **THEN** the racer is neither kicked nor banned, and the score is the sum of the credited rings only

#### Scenario: the check is off
- **WHEN** `VOYAGER_RING_PLAUSIBILITY` is `off`
- **THEN** no tick is checked and ring credit is the same as before this change

### Requirement: Legitimate flight is not flagged on recorded traces
**MoSCoW:** Must. WHEN the check runs on the recorded legitimate traces kept as fixtures, THE SYSTEM SHALL flag no tick of them,
and the bound constants SHALL be changed only together with a trace that justifies the change.

#### Scenario: the legitimate trace fixtures
- **WHEN** the fixture traces of legitimate flight, including ring BOOST and SLOW effects and firework bursts, are checked
- **THEN** no tick is flagged

#### Scenario: a constant changed without a trace
- **WHEN** a slack constant changes and the fixtures are not updated with a justifying trace
- **THEN** the test that checks the fixtures fails
