# Design

## Context

- A ring pass reaches the platform as `CupAnnouncer.report` (`platform/cup/CupAnnouncer.java:76-80`), which calls
  `RaceFeedback.ringPassed` with the index and the ring count only. The `Ring` in the catalogue already carries `points()` and
  `type()` (`voyager/api/src/main/java/net/elytrarace/voyager/api/race/Ring.java:14`).
- The flash window is computed in `RaceHud.flashing` from `lastRingGameTick` (`hud/RaceHud.java:217-220`), so the points marker
  needs the ring's points stored beside that tick, not a second timer.
- The missed-ring line is a timed replacement of the flight line (`RaceHud.missedRing`, `hud/RaceHud.java:132`, 30 ticks). The
  checkpoint and boost lines use the same mechanism.
- The checkpoint ordinal is the count of CHECKPOINT rings at or before the passed ring's index. It is a fact about the map
  (`MapDefinition.rings()`), so it is computed once per map, not per tick.

## Decisions

### 1. Each new fact is computed in the race slice and read by the platform

Dependency direction: `api` (Ring, RingType) <- `race` (checkpoint ordinal, pure function of the rings) <- `platform`
(RaceFeedback, RaceHud, CupAnnouncer). The platform does not compute the ordinal; it calls
`race/flow` for it, so the rule stays in a module without Minestom imports.

- `CheckpointOrdinal` (pure, `voyager/race`, package `net.elytrarace.voyager.race.flow`): `static int of(List<Ring>, int ringIndex)`.
  New type; lives in `voyager/race`; no Minestom import.
- `RingCue` (sealed interface, `voyager/platform`, package `net.elytrarace.voyager.platform.hud`): one permit per cue (`Points`,
  `Boost`, `Slow`, `Checkpoint`, `Missed`). The sealed hierarchy keeps the precedence switch exhaustive. Decision 2 below.

No new ADR: the precedence and the sound mapping are presentation decisions recorded in the specs.

### 2. Precedence is a fixed order, not a queue

`RaceHud` picks the line for a tick from the cues that are due: missed ring first, then checkpoint, boost or slow, then the flight
line. A pre-empted cue is dropped. A queue would show a line seconds after the ring it describes, which is worse than no line.

### 3. Sound selection takes the ring type

`RaceFeedback.ringSound(int ringIndex, int ringCount)` becomes `ringSound(Ring ring, int ringCount)`, or takes `RingType` as a third
argument. The existing last-ring chime rule is kept; the BOOST, SLOW and CHECKPOINT samples are new `Key`s, not the ring bell.

### 4. Fitness rules

- `voyager-fitness` already holds `race` free of Minestom imports and `api` free of implementation. The new `CheckpointOrdinal`
  is covered by that existing rule; no new rule is needed.
- The new sound keys are checked by `LobbySoundsDistinctTest` (existing test, lobby sample set).
- `CheckpointOrdinal` and the cue selection are pure, so they are tested without a server.

## Risks

- The 26.2 sound registry does not confirm the new sample keys. The first task checks them against the registry before Green.
- Golden transcripts record the action bar; they are regenerated in the code change and reviewed line by line.
