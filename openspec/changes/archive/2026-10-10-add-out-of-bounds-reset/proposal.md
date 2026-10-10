# Proposal

Conventional Commits title: `feat(race): reset racers who leave the course or land`

## Why

A racer who flies below the world floor, flies above the build ceiling, or lands mid-course has no defined
consequence in the rebuild. The rebuild has no `OutOfBoundsSystem` and no `LandingResetSystem`; the old tree has both,
in `server/src/main/java/net/elytrarace/server/ecs/system/`, and nothing in `voyager-platform` replaces them. Without
them a racer who falls into the void stays a racer whose run is still advanced, and a racer who lands mid-course can
keep a run they could not have flown. A playable alpha needs one defined answer to both.

The old tree's behaviour is the reference, not the design. It resets an out-of-bounds racer to the map spawn and
resets a landed racer's ring progress and score to zero without moving them. This change keeps the first and changes
both: a racer who leaves the bounds or lands is returned to the last ring they passed, with every point they already
earned kept, and the clock keeps running.

## What Changes

- **Out of bounds** is a racer's position below the floor or above the ceiling of their world's dimension, read from
  the Minestom dimension type (`minY` and `minY + height`), not from a constant. Checked on every `GAME` tick for a
  racer holding an unfinished run.
- **Landing** is the tick on which a racer who was gliding on the previous `GAME` tick is on the ground and no longer
  gliding. Take-off, a lobby, and a glide that never leaves the air are not landings.
- **Reset target**: the last ring the racer passed, of any type, placed at the ring's centre and facing along the
  ring's flow normal. If no ring has been passed yet on this map, the target is the map spawn, facing the first ring as
  a map start does, and the run is unchanged: there is nothing to forfeit.
- **Penalty**: none beyond the position itself. Every ring already passed keeps its points, the race clock keeps
  running, and no time is added. A finished run is never reset.
- **Relaunch**: the racer is placed gliding and launched with the existing launch impulse, horizontally along the
  direction they face and upward, the same impulse a map start uses. The relaunch does not register as a landing.
- **Feedback**: one title in the tick the reset is detected (the reason, and the target), and a low single note that is
  distinct from the rising ring bell. Text keys go in `voyager_en_US.properties`.
- **Burn**: an active firework burn is cancelled. The cooldown is kept, so a reset cannot be used to refresh a rocket.
- **Map files are unchanged.** No new field in `map.schema.json`; every committed map stays valid.

This is a behaviour change for racers who leave bounds or land during `GAME`. It is not a breaking change to any API.

## Scope and slices

Touched: `race.run` (the `gliding` component of a run), new `race.reset` (pure rules), `platform.cup` (the reset step in
`CupSession.raceTick`, a new `RunResetter`, `RaceRuns.resetTo`, and `MapTransition.reposition`), `platform.flight`
(`Racers.launch` from a point, `FireworkBoostTracker.cancelBurn`), `platform.hud` (`RaceFeedback.reset`), `platform.text`
(`Messages`, primitives only), `platform.convert` (javadoc of `VelocityExit`), `voyager-server` cup wiring and the cup
tests.

The waiting lobby is part of the surface this change must not disturb. `CupSession` has `situation`, `disarm` and
`abort` (archived `add-waiting-lobby`), and `WaitingRoom` lives in `platform.lobby`. A reset runs only in the `GAME`
phase, only for a racer who holds a run. A waiting racer, a racer held by an aborted cup, and a player who joined
mid-race hold no run, and are never reset or moved by this change.

Out of scope:

- Horizontal bounds (distance from the course). The `GAME` phase time limit ends a racer who flies away sideways.
- A per-map `bounds` field in `map.schema.json`. The default is the world's dimension height, which is what the old tree used.
- A minimum air time before a landing counts (the old tree's 20 ticks). A landing is a transition, so it needs none.
- A cooldown after a reset (the old tree's 40 ticks). A reset relaunches the racer at once and the transition rule
  cannot re-trigger.
- A time penalty, a point penalty, or a forfeit of rings. Points already earned are kept.
- Any reset in the lobby or the end phase.
- Collision damage, which is E5's, and the missed-ring signal.
- Any change to the golden master scenarios' inputs. See `design.md`, "Golden master".

## Capabilities

### New Capabilities

- `race-course-reset`: what happens to a racer who leaves the world's vertical bounds or lands during a map's `GAME`
  phase, where they return, what the return costs them and what they are told.

### Modified Capabilities

None. The map file format, the catalogue and the cup-session characterization requirements keep their current text.
The golden master pins the current behaviour and is expected to stay byte-identical (see `design.md`).

## Impact

- Gameplay: a racer who falls out of the world or lands returns to the last ring they passed, or to the start when they
  have passed none, relaunched, with the clock still running and every earned point kept.
- Code: the modules and types listed under "Scope and slices". No new Gradle module, no new dependency, no new
  ArchUnit rule: the existing fitness rules cover the new slice and the new packages.
- Documentation: `docs/reference/course-reset.md` (new); `docs/reference/hud.md` gains the title text.
- Process: the owner merges locally without a pull request. The squash commit carries the title above.
