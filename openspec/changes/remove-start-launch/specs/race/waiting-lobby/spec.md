# Spec Delta

## Purpose

Changes what a racer experiences on the GO tick of a cup's first map: the racer is released, not launched.

## MODIFIED Requirements

### Requirement: The start countdown is the first map's lobby
**MoSCoW:** Must. WHEN a cup starts from waiting, THE SYSTEM SHALL play the first map's lobby for the lobby length of
the run's race timings, with the existing three-digit countdown. ON the GO tick, THE SYSTEM SHALL release each racer to
fly on their own: THE SYSTEM SHALL give no velocity and SHALL NOT set the racer gliding, and the racer keeps the elytra
and rockets given during the countdown.

#### Scenario: Production countdown length
- **WHEN** the minimum is met at the start of a production run
- **THEN** the first map's GO tick comes 20 seconds later, with the countdown digits shown in its last three seconds

#### Scenario: Dev countdown length
- **WHEN** the minimum is met under dev mode
- **THEN** the first map's GO tick comes 10 seconds later

#### Scenario: GO gives no impulse
- **WHEN** the GO tick of the first map is reached
- **THEN** each racer's velocity is zero and the racer is not gliding

#### Scenario: Racer keeps the equipment at GO
- **WHEN** the GO tick of a map is reached
- **THEN** each racer still holds the elytra in the chest slot and the rockets in the hotbar

#### Scenario: GO tells the racer how to take off
- **WHEN** the GO tick of a map is reached
- **THEN** each racer is shown the GO title and, in the same tick, a subtitle telling them to jump and deploy the elytra
  (translation key `voyager.race.go.jump`)

#### Scenario: Racer takes off by deploying the elytra
- **WHEN** a racer on the GO tick deploys the elytra
- **THEN** the racer is gliding from that tick, and the race clock is the one that started on GO
