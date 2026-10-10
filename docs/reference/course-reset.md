# Course reset

This reference states the rules that send a racer back during a map's race: when a reset happens, where the racer goes, what it costs, and what the racer is shown. The rules are pure functions in `race.reset` (`RunReset`); `CupSession` decides when they apply and `RunResetter` performs them.

## When a reset happens

A reset is checked on every race tick, for a racer who holds a run that is not finished, before and after the tick.

| Condition | Rule |
|---|---|
| Below the floor | The racer's height is strictly below the minimum Y of the dimension of the world they stand in. A racer standing exactly on the floor is in bounds. |
| Above the ceiling | The racer's height is strictly above the minimum Y plus the height of that dimension. A racer exactly on the ceiling is in bounds. |
| Landed | The racer was gliding on the previous race tick, is on the ground, and is no longer gliding. |

Out-of-bounds is checked first. The overworld's floor is -64 and its ceiling is 320.

A reset never happens:

- in the lobby or the end phase of a map;
- to a player who holds no run on the map: a player waiting for a cup, or one who joined mid-race;
- to a racer whose run has passed every ring of the map, whatever they do afterwards.

Taking off is not a landing. A glide that stops in the air is not a landing. The relaunch after a reset is not a landing either.

## Where the racer goes

| Rings passed on this map | Placed at | Faces and launches toward |
|---|---|---|
| One or more | The centre of the last ring passed, of any type | One step along that ring's flow normal |
| None | The map's spawn | The centre of the first ring, as a map start does |

The flow normal is the direction a racer passes through the ring. The racer is launched with the impulse a map start gives: horizontal along the facing and upward. The numbers are the launch constants in `platform.flight.Racers`.

## What the reset costs

- Every ring already passed stays passed, and its points stay in the score. Nothing is forfeited.
- The race clock keeps running. No time is added to the result, and no points are removed.
- A ring passed after the reset scores once, as any ring does.
- An active firework burn ends. Its cooldown keeps running, so a reset does not refresh a rocket.

## What the racer is shown

One title is shown in the tick the reset is detected, and one sound is played. The keys are in `voyager_en_US.properties`.

| Part | Key | Arguments |
|---|---|---|
| Title, out of bounds | `voyager.reset.title.outofbounds` | None |
| Title, landed | `voyager.reset.title.landed` | None |
| Subtitle, after a ring | `voyager.reset.target.ring` | 0: the one-based number of the last ring passed |
| Subtitle, to the start | `voyager.reset.target.start` | None |

The sound is a single low note, `block.note_block.bass`, at pitch 0.6. It is the opposite of the rising ring bell, so the two are told apart by ear.

## Not part of the rules

The following are not part of the reset and are not implemented by it: a limit on horizontal distance from the course, a per-map bounds field, a minimum air time before a landing counts, a cooldown after a reset, a time or point penalty, and collision damage.

## Related topics

- [HUD](hud.md), for how the title and sound are rendered.
- [Firework boost](firework-boost.md), for the burn and cooldown a reset interacts with.
- [Architecture](../explanation/architecture.md), for the `reset` slice.
