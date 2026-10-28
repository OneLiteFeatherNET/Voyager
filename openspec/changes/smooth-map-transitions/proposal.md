# Proposal

**Type and scope:** `fix(race)`. Ships as one code change under this title. It changes three things a racer meets at the
edges of a map: how the flight ends at the finish, how a racer waits at the spawn before GO, and how far behind a racer the
racing line is drawn. It does not change scoring, ring detection, the medals, the start countdown's length, the GO release
of #311 (no impulse at GO), or the between-maps countdown and standings, which are `competitive-race-feedback`.

**Depends on:** nothing for the merge order. `competitive-race-feedback` (`feat(race)`) builds its between-maps countdown on
the END and LOBBY timings this change keeps. `configurable-guide-particles` (`feat(catalog)`) is independent.

## Why

The owner playtested the three-map `legacy_cup` (palmbeach, dragonlobby, memoria) and found three rough edges where one map
hands over to the next:

1. **Finish.** A racer who passes the last ring is stopped in mid-air. The flight ends in the same tick as the map: the
   map's game phase ends when the last racer finishes, and the end of the phase clears the elytra and sends a zero
   velocity. The racer drops out of the sky with no warning.
2. **Early arrival.** About three seconds before GO the racer is moved into the next map's world, holding the elytra, and
   nothing holds them still. They can walk, jump and deploy, and they can fly off, land wrong or lose their bearings before
   the race has started.
3. **Guide line.** The racing line stops being drawn under a racer who is still flying through the gap they have just left.
   It is drawn from the next ring, so the part of the line between the last ring passed and the next one disappears while
   the racer is still in it.

## What Changes

- **Glide-out.** A racer who passes the last ring keeps flying for 2.5 seconds. Their speed is damped towards a quarter of
  its value at the finish, with a firework and a sound at the finish ring. Then the racer stands down at the spawn of the
  map they finished. No hard stop. The end of the map does not cut a gliding finisher short.
- **Spawn freeze.** On every map of a cup, from the moment a racer is placed in the map until GO, the racer stands at the
  spawn facing the first ring, without gliding, and cannot move away. The freeze holds while the chunks around the spawn
  are still loading. This applies to the first map as well as the others.
- **Guide line.** The line stays drawn from the last ring the racer passed until the racer has passed the next ring. It
  is no longer drawn from the next ring. The part behind the last ring passed is still not drawn. This is a MODIFIED
  requirement of `race/racing-line-display` for both modes.

## Root causes (verified against the code on `origin/main` at `daadf33`)

| Point | Root cause | Where |
|---|---|---|
| Finish cut-off | The game phase ends when the last racer finishes (`allPlayersFinished`). The phase end calls `Racers.standDown`, which turns the elytra off and sends a zero velocity, on the tick of the finish. | `race/flow/RaceStateMachine.java:79-80`; `platform/cup/CupSession.java:842-857` (`mapFinished`, `standDown` at 857); `platform/flight/Racers.java:133-136` |
| Early arrival, no freeze | `lobbyTick` enters the map at the first countdown digit, three seconds before GO. `enterMap` moves the racers and turns them to ring 1, and `Racers.faceCourse` gives the elytra. Nothing pins them until GO. No freeze code exists in `voyager/platform/src/main`. | `platform/cup/CupSession.java:620-636`, `680-702`, `700` (`faceCourse`); `platform/flight/Racers.java:101-107` |
| Guide line gap | The stretch starts at the ring the racer is heading for. The renderer draws `between(passedCount, last)`, and `passedCount` increments on the tick the ring is crossed. The segment the racer is still flying (last ring to next ring) is therefore not drawn. The comment at `CupSession.java:739-742` makes this deliberate. The renderer never sends a removal: points stop being refreshed and their particles fade. | `platform/render/GuideLineRenderer.java:174-179` (`stretch`); `race/line/RacingLine.java:98-105` (`between`); `platform/cup/CupSession.java:721`, `739-743` |

Ruled out for point 5: `lookAheadRings` is not the cause, because the default mode is `FULL`, which ignores it. The reach
cull (`REACH_BLOCKS` 160) only drops points more than 160 blocks away. Ring detection is on time: the crossing is detected
on the same tick the segment meets the ring's plane (`race/run/RaceRun.java:171-186`, `race/progress/ProgressTracker.java:38-45`).
The cause is the start of the stretch, not the detection.

The GO release of #311 (commit `0c9166d`, "let racers take off themselves at GO and after a reset") is kept as it is.

## Capabilities

### New Capabilities

- `race/finish-glide-out`: how a finisher's flight ends after the finish ring, the damping, the firework and sound, and the
  stand-down position.
- `race/map-start-freeze`: where a racer waits from arrival until GO on every map, and what they cannot do there.

### Modified Capabilities

- `race/racing-line-display`: the stretch of the line starts at the last ring passed, not the next one, in `FULL` and
  `WINDOW` modes.

## Out of scope

- The between-maps countdown, the standings and the place lines: `competitive-race-feedback`.
- The particle type and colour: `configurable-guide-particles`.
- A glide-out for racers still airborne when the time limit ends (a DNF). See the open question in `design.md`.
- The chunk view distance. It is a deployment setting (JVM flags), not a code requirement. `design.md` records its effect
  on the freeze.
- Any change to the start countdown's length or to the GO release.

## Impact

- `voyager/race`: a pure glide-out curve (`race/glide`, no Minestom imports) and the stretch rule in `RacingLine`'s
  caller, with no change to `RaceRun` or `ProgressTracker`.
- `voyager/platform`: `CupSession` (finish glide-out ticker, `mapFinished` deferral, the stretch call at 743), `Racers`
  (freeze pin and the stand-down position), `GuideLineRenderer.stretch`, `RaceFeedback` (finish firework and sound).
- `docs/reference/hud.md` and `docs/reference/message-style-guide.md`: the glide-out and the freeze, and the sound row for the
  finish firework.
- One MADR decision (Proposed): the glide-out is a second kind of server-sent velocity, after boosts and resets. See
  `design.md`, decision 2.
