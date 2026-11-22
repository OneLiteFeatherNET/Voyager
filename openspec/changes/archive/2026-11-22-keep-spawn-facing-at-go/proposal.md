# Proposal

**Type and scope:** `fix(race)`. Ships as one code change under this title.

## Why

Racers do not face the first ring. During the countdown the racer looks at ring 0, but at the GO tick the racer is
teleported back to the map spawn with yaw 0 and pitch 0, so the first flight starts looking the wrong way. The racer
has to turn round before the first ring is in view, and the first seconds of every map are lost to the turn.

The cause is a second placement at GO. The map transition places every racer already in the instance at the spawn
again, and that placement overwrites the facing that was set during the countdown. Joining racers and map arrivals
share the same placement and have the same problem.

## What Changes

- At the GO tick, a racer who is already in the map keeps the position and facing set during the countdown, or is
  turned to face ring 0 again. No racer is left facing the spawn's default yaw and pitch.
- Every arrival in a map faces ring 0 of that map: a join into a running cup, a map transition, and the GO release.
- Resets are already correct and do not change. A reset still returns the racer to the last checkpoint or the start,
  with the facing rules in `race-course-reset` unchanged.
- The GO release still gives no velocity and does not set the racer gliding (`race/waiting-lobby`, unchanged).

## Capabilities

### New Capabilities

- `race/arrival-facing`: the yaw and pitch a racer has when the racer arrives in a map or is released at GO. It owns the
  rule that the first ring is in front of the racer at the start of a map.

### Modified Capabilities

- `race/waiting-lobby`: the scenario "The golden scenarios still pass" gains one exception, the yaw and pitch recorded at
  the GO tick. The requirement about the GO release (velocity, gliding, elytra kept) does not change.

`race-course-reset` and `race-feedback` do not change.

## Out of scope

- Reset facing (`race-course-reset`), which is already correct and must not change.
- The countdown titles and sounds, and the lobby display. Those belong to `cygnus-style-lobby` and `polish-race-hud`.
- Computer racers, which take the same spawn but are placed by `race/com-racers`. The rule applies to them only if they
  share the arrival path; the design says how that is checked.

## Impact

- `voyager/platform` (`cup/CupSession`, `cup/MapTransition`, `race/Racers`, arrival code in the platform slice).
  No change to `voyager-api` or `voyager-race` types.
- Golden masters in `voyager/server/src/test/resources/golden/cup-session/`: the transcripts record each racer's client
  yaw and pitch, so the GO lines change in yaw and pitch only. The design records this.
- No catalogue, schema or message change.
