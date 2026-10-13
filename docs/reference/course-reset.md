# Course reset

This reference states the rules that send a racer back during a map's race: when a reset happens, where the racer goes, what it costs, and what the racer is shown. The rules are pure functions in `race.reset` (`RunReset`); `CupSession` decides when they apply and `RunResetter` performs them.

## When a reset happens

A reset is checked on every race tick, for a racer who holds a run that is not finished, before and after the tick.

| Condition | Rule |
|---|---|
| Below the floor | The racer's height is strictly below the minimum Y of the dimension of the world they stand in. A racer standing exactly on the floor is in bounds. Not gated by the airborne grace or the cooldown. |
| Above the ceiling | The racer's height is strictly above the minimum Y plus the height of that dimension. A racer exactly on the ceiling is in bounds. Not gated by the airborne grace or the cooldown. |
| Landed | The racer was gliding on the previous race tick, is on the ground, is no longer gliding, has been airborne for at least 20 consecutive race ticks, and no reset of the racer happened in the 40 race ticks before this tick. |

Out-of-bounds is checked first. The overworld's floor is -64 and its ceiling is 320.

The landing rule has two gates, both counted in race ticks. The grace of 20 airborne ticks (one second) means a racer who has only just left the ground, or who was placed on it, is not reset by touching it. The cooldown of 40 ticks (two seconds) after a reset means a racer placed by a reset is not reset again while the cooldown runs. These are the values the tree being replaced used, and the playtest decides whether they stay (see [ADR-0031](../decisions/0031-checkpoint-resets-with-a-deployed-elytra.md)).

A reset never happens:

- in the lobby or the end phase of a map;
- to a player who holds no run on the map: a player waiting for a cup, or one who joined mid-race;
- to a racer whose run has passed every ring of the map, whatever they do afterwards.

Taking off is not a landing. A glide that stops in the air is not a landing, and neither is a touch of the ground before the grace has passed or during the cooldown.

## Where the racer goes

| Last `CHECKPOINT` ring passed | Placed at | Faces toward | Gliding |
|---|---|---|---|
| One or more | One block behind the centre of that checkpoint ring, along its flow normal, and one block above it | One step along the flow normal, from the centre | Yes: the elytra is deployed by the server, with no impulse |
| None | The map's spawn | The centre of the first ring, as a map start does | No: the racer stands on the spawn and takes off by jumping and deploying the elytra, as at GO |

The flow normal is the direction a racer passes through the ring. Only a `CHECKPOINT` ring is a reset target; a `STANDARD` or `BOOST` ring passed does not move the target. A map with no `CHECKPOINT` ring resets every racer to the spawn.

Both placements give the racer the elytra and the rockets in hand. The reset gives no impulse, and it zeroes the racer's velocity, so a fall is not carried into the target. A checkpoint reset starts the glide on the server side, and the client keeps it only if it honours the flag on its own player; see ADR-0031 for the fallbacks.

## What the reset costs

- The rings up to the target stay passed, and their points stay in the score. The rings passed after the target are removed from the run: the racer must fly them again, in order. A spawn reset removes every ring.
- A removed ring scores its points once more when the racer passes it again. A ring passed after the reset scores once, as any ring does.
- The race clock keeps running. No time is added to the result, and no points are removed by the reset itself.
- An active firework burn ends. Its cooldown keeps running, so a reset does not refresh a rocket.

## What the racer is shown

One title is shown in the tick the reset is detected, and one sound is played. The keys are in `voyager_en_US.properties`.

| Part | Key | Arguments |
|---|---|---|
| Title, out of bounds | `voyager.reset.title.outofbounds` | None |
| Title, landed | `voyager.reset.title.landed` | None |
| Subtitle, to a checkpoint | `voyager.reset.target.checkpoint` | 0: the one-based number of the checkpoint the racer is returned to |
| Subtitle, to the start | `voyager.reset.target.start` | None |

The sound is a single low note, `block.note_block.bass`, at pitch 0.6. It is the opposite of the rising ring bell, so the two are told apart by ear.

## Not part of the rules

The following are not part of the reset and are not implemented by it: a limit on horizontal distance from the course, a per-map bounds field, a time or point penalty, and collision damage. The catalogue does not check that a `CHECKPOINT` ring lies inside the vertical bounds of its map.

## Related topics

- [Map and cup files](map-and-cup-files.md), for the ring types, including `CHECKPOINT`.
- [HUD](hud.md), for how the title and sound are rendered.
- [Firework boost](firework-boost.md), for the burn and cooldown a reset interacts with.
- [Architecture](../explanation/architecture.md), for the `reset` slice.
- [ADR-0031](../decisions/0031-checkpoint-resets-with-a-deployed-elytra.md), for the checkpoint target and the deployed elytra.
