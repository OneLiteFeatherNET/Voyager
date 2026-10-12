# Spec Delta

## Purpose

Changes where a reset leaves a racer and what it gives them. A reset no longer relaunches the racer; the racer takes off
by themselves, as a racer does at a map start.

## REMOVED Requirements

### Requirement: A reset relaunches the racer with the launch of a map start
**Reason**: The server-side launch impulse is removed (see `race/waiting-lobby`). A reset returns the racer without it.

## ADDED Requirements

### Requirement: A reset returns the racer without an impulse
**MoSCoW:** Must. WHEN a racer is reset, THE SYSTEM SHALL place the racer at the reset target, facing the direction the
target gives, with the elytra and the rockets in hand; THE SYSTEM SHALL NOT set the racer gliding and SHALL NOT send an
impulse. The racer takes off by deploying the elytra. For a reset to the spawn, the facing is the direction of the map's
first ring, as at a map start. The arrival of the racer SHALL NOT count as a landing.

#### Scenario: Reset racer is placed with the equipment
- **WHEN** a racer is reset to a ring
- **THEN** the racer is at that ring's centre, faces that ring's flow normal, holds the elytra and the rockets, and is not gliding

#### Scenario: Reset zeroes the racer's velocity
- **WHEN** a racer is reset
- **THEN** the racer's velocity is zero on that tick, so a fall is not carried into the target; no other velocity is sent

#### Scenario: Reset racer takes off by themselves
- **WHEN** a reset racer deploys the elytra
- **THEN** the racer is gliding from that tick and the reset does not add to their flight

#### Scenario: Arrival is not a landing
- **WHEN** a reset racer arrives at the target and has not yet deployed the elytra
- **THEN** the system does not reset that racer

#### Scenario: Reset racer falls without a reset
- **WHEN** a reset racer who has not deployed the elytra falls to the ground
- **THEN** the system does not reset that racer, because a landing requires a previous glide
