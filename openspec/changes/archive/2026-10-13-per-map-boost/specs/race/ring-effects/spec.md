# Spec Delta

## Purpose

Defines what a BOOST or SLOW ring does to a racer's velocity: which factor applies, when it applies, how two effects in
quick succession combine, the optional per-map speed cap, and how an effect relates to a reset. The ring effects are the
one velocity the server writes during a race besides the zero that ends a run.

## ADDED Requirements

### Requirement: A BOOST or SLOW ring multiplies the racer's velocity by the map's factor
**Priority:** MoSCoW Must. WHEN a gliding racer with an unfinished run passes a ring of type `BOOST` or `SLOW`, THE SYSTEM
SHALL multiply that racer's velocity by the factor of the map's `ringEffects` for that type, and SHALL send the result to
the client and hold it in the server's own flight simulation.

#### Scenario: A BOOST ring with the default factor
- **WHEN** a gliding racer at velocity `(0, 0, 2)` blocks per tick passes a BOOST ring on a map with no `ringEffects`
- **THEN** the racer's velocity becomes `(0, 0, 3)` on that tick, in the client and in the server's simulation

#### Scenario: A SLOW ring with a map factor
- **WHEN** a gliding racer at velocity `(0, 0, 2)` passes a SLOW ring on a map with `"slowMultiplier": 0.25`
- **THEN** the racer's velocity becomes `(0, 0, 0.5)`

#### Scenario: A STANDARD or CHECKPOINT ring changes nothing
- **WHEN** a racer passes a STANDARD or CHECKPOINT ring
- **THEN** no velocity is sent for the ring

### Requirement: The factors are optional map data with today's values as defaults
**Priority:** MoSCoW Must. THE SYSTEM SHALL read `ringEffects.boostMultiplier` and `ringEffects.slowMultiplier` from the map
file as optional finite numbers of zero or more, defaulting to 1.5 and 0.5, and SHALL refuse a value that is negative or
not finite, naming the field.

#### Scenario: A map without ringEffects
- **WHEN** a map file has no `ringEffects` object
- **THEN** the BOOST factor is 1.5 and the SLOW factor is 0.5

#### Scenario: A negative factor is refused
- **WHEN** a map file states `"boostMultiplier": -1`
- **THEN** the file is refused and the message names `boostMultiplier`

### Requirement: Effects apply only to a gliding racer with an unfinished run
**Priority:** MoSCoW Must. WHERE a racer is not gliding, or holds a finished run, or holds no run, THE SYSTEM SHALL NOT
apply a ring effect to that racer.

#### Scenario: A racer on foot crosses a BOOST ring
- **WHEN** a racer who is not gliding crosses a BOOST ring
- **THEN** the racer's velocity is not changed and nothing is sent

#### Scenario: A finished racer crosses a ring
- **WHEN** a racer whose run is finished crosses a ring
- **THEN** nothing is sent for that ring

### Requirement: Effects in quick succession compose in the order the rings were crossed
**Priority:** MoSCoW Must. WHEN a racer crosses more than one ring with an effect, THE SYSTEM SHALL apply the effects one
after another, each to the velocity the previous one left, in the order the rings were crossed, and SHALL send one velocity
for that tick.

#### Scenario: BOOST then SLOW
- **WHEN** a racer crosses a BOOST ring and then a SLOW ring with the default factors, one after the other
- **THEN** the racer's velocity is three quarters of its velocity before the BOOST ring

#### Scenario: Two BOOST rings in one tick
- **WHEN** a racer crosses two BOOST rings on the same tick, on a map with no speed cap
- **THEN** the racer's velocity is 2.25 times its velocity before the first ring, and one velocity is sent

### Requirement: A map may cap the velocity a ring effect writes
**Priority:** MoSCoW Should. WHERE a map states `boostConfig.maxSpeedBlocksPerTick`, THE SYSTEM SHALL limit the magnitude of
the velocity that a ring effect writes to that value, keeping the direction, and SHALL NOT limit any other velocity.

#### Scenario: A BOOST that exceeds the cap
- **WHEN** a map states a cap of 3.5 and a racer at 2.5 blocks per tick passes a BOOST ring with factor 1.5
- **THEN** the racer's velocity becomes 3.5 blocks per tick in the same direction, not 3.75

#### Scenario: Normal flight above the cap is not corrected
- **WHEN** a map states a cap of 3.5 and a racer glides at 4 blocks per tick without passing a ring
- **THEN** the server sends no velocity for the racer

#### Scenario: No cap is stated
- **WHEN** a map states no cap
- **THEN** the racer's velocity after a ring is the multiplied velocity, however large

### Requirement: A ring effect on the tick of a reset is discarded
**Priority:** MoSCoW Must. WHEN a racer is reset on the tick they cross a ring, THE SYSTEM SHALL NOT apply that ring's
effect, and the racer's velocity SHALL be the zero that the reset sends.

#### Scenario: Crossing a ring out of bounds
- **WHEN** a racer falls below the floor on the tick they would pass a BOOST ring, and the reset applies
- **THEN** no BOOST factor is applied, and the racer's velocity is zero after the reset

### Requirement: The server's simulation and the client hold the same velocity after an effect
**Priority:** MoSCoW Must. WHEN a ring effect is applied, THE SYSTEM SHALL write the same velocity to the server's flight
simulation and to the client, on the same tick.

#### Scenario: The next tick starts from the boosted velocity
- **WHEN** a BOOST ring is applied on tick n
- **THEN** the server's simulation starts tick n+1 from the boosted velocity, not from the velocity before the ring

### Requirement: The only external velocity the server sends in a race is a zero or a ring effect
**Priority:** MoSCoW Must. THE SYSTEM SHALL send a velocity to a racer during a race only through `VelocityExit`, and
only for a reset or the end of a map (a zero) or for a ring effect (the result of this requirement's rules).

#### Scenario: Rockets send no velocity
- **WHEN** a racer uses a rocket
- **THEN** the server sends no velocity for the racer
