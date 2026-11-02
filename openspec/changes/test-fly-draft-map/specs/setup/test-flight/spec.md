# Spec Delta

## Purpose

A builder must fly the course as it stands before it is published, with the rings in order, the guide line, the timing and the
medals the game uses, and must be able to stop without anything being changed or recorded. A flight uses the race's own rules,
so what the builder learns is what the racers face. Requirements use EARS patterns and MoSCoW priorities, as
`openspec/config.yaml` requires.

Terms: a **flight** is one run of the builder over the saved course, from the spawn until the last ring or until the builder
stops it. A **flight item** is an elytra or a firework rocket given for the flight and marked as the flight's own.

## ADDED Requirements

### Requirement: A builder flies the map the builder holds
**Priority:** MoSCoW Must

WHEN a builder who holds the claim of a map runs `/map fly`, THE SYSTEM SHALL start a flight of that map from its spawn, with
the builder holding `voyager.setup.use`. IF the builder holds no map, THEN THE SYSTEM SHALL refuse and change nothing.

#### Scenario: Flight starts on a valid map
- **WHEN** a builder holds `skyfortress`, which has a spawn and rings, and runs `/map fly`
- **THEN** the builder is at the spawn with an elytra and firework rockets, and the reply says that the flight has started

#### Scenario: No map open
- **WHEN** a builder with no open map runs `/map fly`
- **THEN** the builder is told that no map is open, and no item is given

### Requirement: A flight starts only from a game-loadable saved draft
**Priority:** MoSCoW Must

IF the saved draft has no spawn or no ring, THEN THE SYSTEM SHALL refuse the flight, naming each blocking problem of
`/map status`, and SHALL change nothing.

#### Scenario: Draft without rings
- **WHEN** a builder runs `/map fly` on a draft with no ring
- **THEN** the reply names the missing ring, and no flight item is given

### Requirement: A flight refuses a stored copy that changed on disk
**Priority:** MoSCoW Must

IF the stored copy of the draft differs from the copy the session holds, THEN THE SYSTEM SHALL refuse the flight with the
changed-on-disk message, and SHALL change nothing.

#### Scenario: Second setup server changed the file
- **WHEN** another setup server changed `skyfortress` after the builder opened it, and the builder runs `/map fly`
- **THEN** the flight is refused with the changed-on-disk message, and the file keeps its bytes

### Requirement: The flight follows the race's rules for rings, the finish and the time
**Priority:** MoSCoW Must

WHILE a flight runs, THE SYSTEM SHALL count a ring only in the map's ring order, SHALL take the time from the race clock, and SHALL
score the finish with the map's reference time and medal brackets, using the race's own rules.

#### Scenario: Ring out of order is not counted
- **WHEN** the builder passes ring 3 before ring 2
- **THEN** ring 3 is not counted, and ring 2 is counted when the builder passes it

#### Scenario: Finish shows time, rings and medal
- **WHEN** the builder passes the last ring
- **THEN** the reply shows the time, the number of rings passed out of the map's count, and the medal tier of the time

### Requirement: The flight shows the racing line and applies boosts and ring effects as a race does
**Priority:** MoSCoW Must

WHEN a flight starts, THE SYSTEM SHALL show the map's guide line to the builder as the racers see it. WHILE a flight runs,
THE SYSTEM SHALL apply firework boost, boost rings and ring effects with the race's own rules, and SHALL send the velocity
changes that a race sends for them (owner decision 3, 2026-11-02).

#### Scenario: Boost ring in a flight
- **WHEN** a builder flying a map with a boost ring passes the ring
- **THEN** the builder's velocity is changed as it is in a race, and the time at the finish includes that effect

#### Scenario: Firework burn in a flight
- **WHEN** a builder flying a map uses a firework rocket
- **THEN** the builder receives the same burn velocity as a racer, and the flight's rules count it as the race's rules do

### Requirement: While flying, edits are refused and change nothing
**Priority:** MoSCoW Must

WHILE a flight runs, THE SYSTEM SHALL refuse ring placement, ring removal, `/map spawn`, `/map undo` and `/map redo`, SHALL reply
that the flight must be stopped first, and SHALL change no draft, file or history.

#### Scenario: Ring placed during a flight
- **WHEN** a builder flying `skyfortress` places a ring with the wand
- **THEN** no ring is added, the draft file keeps its bytes, and the reply says to stop the flight first

### Requirement: Stopping a flight returns the builder to editing
**Priority:** MoSCoW Must

WHEN the builder runs `/map fly stop`, THE SYSTEM SHALL end the flight, SHALL restore the editing state (creative mode, the wand
and the ring previews), SHALL remove the flight items, and SHALL keep the claim, the draft and the history as they were.

#### Scenario: Stop returns to editing
- **WHEN** a builder flying `skyfortress` runs `/map fly stop` after placing no ring
- **THEN** the builder is in creative mode with the wand, holds `skyfortress`, and the undo history is the one the builder had

#### Scenario: Stop without a flight
- **WHEN** a builder who is not flying runs `/map fly stop`
- **THEN** the builder is told that no flight is running, and nothing changes

### Requirement: A flight records nothing
**Priority:** MoSCoW Must

THE SYSTEM SHALL write no file, take or release no claim, add no history entry, and record no statistic or leaderboard entry for a
flight. The flight's time and medal exist only in the reply.

#### Scenario: Completed flight leaves the files unchanged
- **WHEN** a builder completes a flight of `skyfortress`
- **THEN** the draft file and the world folder keep their bytes, and the claim is held as before the flight

### Requirement: Closing, opening, disconnecting or unlocking ends a flight
**Priority:** MoSCoW Must

WHEN a flight is running and the builder runs `/map close`, opens another map, disconnects, or an administrator's `/map unlock`
ends the builder's session, THE SYSTEM SHALL end the flight with the session, and SHALL keep the claim rules of
`single-builder-map-claims`.

#### Scenario: Disconnect during a flight
- **WHEN** a builder flying `skyfortress` disconnects
- **THEN** the claim of `skyfortress` is released, and nothing about the flight is kept

#### Scenario: Unlock during a flight
- **WHEN** an administrator unlocks `skyfortress` while its builder flies it
- **THEN** the flight ends with the session, and the holder moves to the setup lobby

### Requirement: Flight items never outlive the flight
**Priority:** MoSCoW Must

THE SYSTEM SHALL remove the flight items when the flight stops, when the builder closes the map, and when the builder next enters
the setup server, including after a disconnect in the middle of a flight.

#### Scenario: Items after a disconnect
- **WHEN** a builder disconnects while flying and returns to the setup server later
- **THEN** the elytra and the rockets of the earlier flight are not in the builder's inventory

### Requirement: A flight is restarted by running it again
**Priority:** MoSCoW Should

IF a flight is already running when the builder runs `/map fly`, THEN THE SYSTEM SHALL restart the flight at the spawn with an empty
progress, and SHALL keep no time of the earlier run.

#### Scenario: Restart at the spawn
- **WHEN** a builder has passed five rings and runs `/map fly` again
- **THEN** the builder is at the spawn, no ring is counted, and the earlier time is not shown

### Requirement: The flight messages are bilingual
**Priority:** MoSCoW Must

THE SYSTEM SHALL send every flight message from a key present in both `voyager_setup_en_US` and `voyager_setup_de_DE`, with the same
`<arg:N>` placeholders, and German SHALL use the informal "du". The medal text SHALL come from the shared medal message.

#### Scenario: Parity of the flight keys
- **WHEN** the bundle parity test runs
- **THEN** every flight key exists in both bundles with the same placeholders

## Won't (this change)

- The cup's orchestration in a flight: the lobby, the GO hold, the glide-out and the rotation.
- Out-of-bounds reset during a flight.
- Any record of a flight: no statistic, leaderboard entry, personal best or file.
