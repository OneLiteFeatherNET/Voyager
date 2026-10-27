# Spec Delta

## Purpose

A racer who arrives in a map, or is released at GO, looks down the course: the first ring is in front of the racer. The
facing set during the countdown is the facing the racer has at GO, and every arrival in a map faces ring 0 of that map.

## ADDED Requirements

### Requirement: A racer arriving in a map faces the first ring
**MoSCoW:** Must. WHEN a racer is placed at the start of a map, whether by a join into a running cup, a map transition or the
countdown, THE SYSTEM SHALL set the racer's yaw and pitch so that the racer looks at the centre of ring 0 of that map.

#### Scenario: A racer joins a running cup
- **WHEN** a racer joins while a map is racing and is placed at that map's spawn
- **THEN** the racer's yaw and pitch point at the centre of ring 0 of that map

#### Scenario: A racer moves to the next map of a cup
- **WHEN** the cup advances to the next map and the racers are placed at its spawn
- **THEN** every racer's yaw and pitch point at the centre of ring 0 of the new map

### Requirement: The GO release keeps the first ring in front of the racer
**MoSCoW:** Must. ON the GO tick, THE SYSTEM SHALL leave each racer who is already in the map facing ring 0 of that map, with
the yaw and pitch that the countdown set or a yaw and pitch that look at ring 0 again. THE SYSTEM SHALL NOT place the
racer at the spawn's default facing (yaw 0, pitch 0) at GO.

#### Scenario: Yaw after GO on the shipped map
- **WHEN** the GO tick of the map "elytraraceblueandred" is reached, with the spawn at (109, -62, 54) and the centre of ring 0 at (85, -54, 54)
- **THEN** the racer's yaw is 90 degrees within one degree, and the racer's pitch looks up towards ring 0

#### Scenario: Facing is the same at GO as at the end of the countdown
- **WHEN** a racer is facing ring 0 at the last countdown digit and the GO tick is reached
- **THEN** the racer's yaw and pitch at GO equal the yaw and pitch of the last countdown tick, within one degree

### Requirement: A reset keeps its placement
**MoSCoW:** Must. THE SYSTEM SHALL NOT change where or how a reset places a racer. A reset places the racer as
`race-course-reset` specifies, and this capability changes none of those rules.

#### Scenario: A reset to the start is unchanged
- **WHEN** a racer is reset to the start of the map
- **THEN** the racer is placed as `race-course-reset` specifies, with the same position and facing as before this change
