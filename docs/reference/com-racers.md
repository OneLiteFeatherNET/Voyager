# Computer racers

This reference documents the computer racers ("COM n"): the two server settings that switch them on, the difficulty
table, the lifecycle on a cup, how they are scored and shown, and what the server does not do yet. The design and its
decisions are in [ADR-0035](../decisions/0035-com-racers.md) and `openspec/changes/com-racers/design.md`. The movement
research is in [the movement research note](../research/com-racers-movement.md).

A computer racer is a body that the server moves along the racing line of the map. It is not a player: it is not
counted as online, it does not start a cup, and it never gets a permission, a LuckPerms node or a hub handoff.

## Settings

Both settings are global, per server. They are read at boot through the avaje-config loader, and each key also reads
its environment variable of the same name. The run tasks do not forward them, so set them as environment variables or
as Java system properties.

| Setting | Type | Default | Accepted values | What it sets |
|---|---|---|---|---|
| `VOYAGER_COM_COUNT` | integer | `0` | `0` to `15` | The grid size: how many racers fill the grid, humans included. `0` switches computer racers off. |
| `VOYAGER_COM_DIFFICULTY` | enum | `MEDIUM` | `EASY`, `MEDIUM`, `HARD` (case-insensitive) | How hard every computer racer plays. |

A value that is not a whole number, or is outside `0` to `15`, refuses the boot and names `VOYAGER_COM_COUNT` and the
text read. An unknown difficulty refuses the boot, names `VOYAGER_COM_DIFFICULTY` and lists the three names. A blank
value counts as unset. Both problems are also reported by the configuration check (see
[configuration check](config-check.md)).

The maximum of `15` is the cap. With `VOYAGER_MAX_PLAYERS` at its default of 16, one human can join a grid of 16 with
15 computer racers.

### The count is the grid size

`VOYAGER_COM_COUNT` is the total number of racers on the grid, human racers included. The server adds computer racers
until the grid holds that many racers. Each human on the grid reduces the number of computer racers by one, and a count
no larger than the number of humans adds none.

| Humans on the grid | `VOYAGER_COM_COUNT` | Computer racers |
|---|---|---|
| 1 | `4` | 3 |
| 3 | `4` | 1 |
| 4 | `4` | 0 |
| 5 | `4` | 0 |

### The boot line

When the count is above zero, the server logs one line at info level after it reads the settings:

```text
computer racers: 5 at HARD
```

The line states the count and the difficulty. When the count is `0`, the server logs no such line.

## Difficulty

`VOYAGER_COM_DIFFICULTY` picks one row of the table. The values are starting values from the design. They are tuned
after the owner's playtest and are not measured constants.

| Knob | EASY | MEDIUM | HARD | Meaning |
|---|---|---|---|---|
| Speed factor | 0.85 | 0.93 | 1.00 | Share of the reference speed the racer aims for |
| Line offset amplitude (blocks) | 1.2 | 0.6 | 0.2 | How far the racer wanders off the racing line |
| Wobble period (blocks) | 40 | 60 | 80 | Length of one wander along the course |
| Miss chance per ring | 0.08 | 0.02 | 0.00 | Chance a ring is missed once |
| Boost reaction delay (ticks) | 10 | 4 | 0 | Ticks before a boost ring's speed applies (see [known limits](#known-limits)) |

Each racer also gets a seeded personal variation of plus or minus 2 per cent on the speed factor, so two racers at the
same difficulty differ. The effective speed factor is capped at 1.00. No difficulty makes a computer racer faster than
the reference speed, so HARD is the reference racer and not a cheat.

### Speed bounds

These are the bounds the design sets from the measured elytra traces (the measurements are in the
[research note](../research/com-racers-movement.md)). Speeds are in blocks per tick, angles in degrees per tick.

| Bound | Value | Where it lives |
|---|---|---|
| Cap (reference speed) | 1.7 blocks per tick | `ComTuning.REFERENCE_SPEED` |
| Floor | 0.70 blocks per tick | `ComTuning.FLOOR_SPEED`, applied by `ComPilot` |
| Acceleration bound | 0.05 blocks per tick, per tick | `ComPilot.ACCELERATION_BOUND` |
| Yaw bound | 0.91 degrees per tick | Design only, not enforced yet |
| Pitch bound | 7.23 degrees per tick | Design only, not enforced yet |
| Vertical sink bound | 0.134 blocks per tick, downward | Design only, not enforced yet |

The yaw, pitch and sink bounds are not enforced by the brain today. No test asserts them.

## Lifecycle on a cup

| Moment | What happens |
|---|---|
| Cup start | The start gate commits only when at least `VOYAGER_MIN_PLAYERS` humans are online. The grid is then filled to the count, and the first map starts with the computer racers on it. |
| Each map start | The grid is topped up to the count. A computer racer that is already on the grid keeps its body and its name, and is moved to the new start. A seat a human has taken is given up at this point. |
| During a map | Computer racers are never removed. A racer that is still racing keeps running until it finishes or is removed as did-not-finish. |
| Did-not-finish | A racer that passes no ring for 400 ticks (20 seconds) is removed from the map as did-not-finish. |
| Cup end | Every body and every tab-list entry is removed. |
| Cup abort | Every body and every tab-list entry is removed, as at cup end. |
| Joins mid-cup | A human who joins mid-cup waits in the lobby until the next map. The next map counts that human, and the grid is topped up. |

Computer racers never count toward the start gate, the join line (`x/y`), the boss bar or `VOYAGER_MIN_PLAYERS`. They are
not in the online-player set, so they never start a cup and never raise a count. They never appear in the lobby world.

## Missed rings

A missed ring is a delay, not a skip. The seeded miss roll takes the racer a few blocks off the ring's plane, outside its
disc, and the racer turns back and crosses the ring's disc again. Each missed ring costs time, not progress. The
crossing counts as it does for a human racer.

## Scoring and results

Computer racers run the course and get a time, and they are shown in the results. They do not change the cup for anyone
else.

| Question | Answer |
|---|---|
| Cup points | None. |
| Placement bonus | None. |
| Medals | None. |
| Stored record | None. Stats record humans only. |
| In the results table | Shown as `COM n`, with their time, placed among the humans by time. |

The numbers run from `COM 1` to `COM n` in seat order, and a number keeps its name for the life of the cup.

Each computer racer finish gives one chat line to every human racer, in the language of that racer:

| Language | Line |
|---|---|
| `en_US` | `» COM 3 finished in 1:02.4` |
| `de_DE` | `» COM 3 ist ins Ziel gekommen in 1:02.4` |

There is one line per finish. No line is sent for a ring.

## Appearance

| Element | Value |
|---|---|
| Body | An entity of the player type with no connection. The server moves it. |
| Name above the head | `COM n`, always visible. |
| Skin | The default skin. No skin is fetched from the network, and the tab entry has no texture property. |
| Tab list | One entry per computer racer, shown to every human. The entries are synced once per tick, so a human who joins mid-cup gets the entries of the current bodies. |

## Known limits

- **Body visibility (S2).** The client shows a player-type entity with no live connection only if the client accepts
  it. This is not tested. The owner checks it in playtest. If the body is hidden, the body switches to an armour stand
  before the change ships.
- **No per-cup settings.** Both settings are per server. A cup cannot have its own field size or difficulty.
- **No bots in the lobby.** Computer racers stand only on the grid. The lobby world never shows them.
- **Dive recording above the cap is out of scope.** A human can dive faster than the cap, reaching about 2.35 blocks per
  tick without a rocket. A computer racer never goes above the cap, so it cannot match that dive.
- **Boost delay is inert.** The brain has a boost reaction delay, but the server passes it no boost effect yet, so the
  delay has no effect in a running server. It is tested against a fake ring effect. It takes effect when the boost ring
  effects reach the brain.
- **Yaw, pitch and sink bounds are not enforced.** See [speed bounds](#speed-bounds).
- **Hub handoff exclusion is structural, not yet a code change.** A computer racer is not a player, so the hub handoff
  never sees one. The `TODO(com-bots)` markers in `WaitingRoom` and `HubHandoff` are still in the code.
- **Tuning is open.** The difficulty table is a starting point until the owner's playtest tunes it.

## Related

- [ADR-0035](../decisions/0035-com-racers.md) for the decision and its options.
- [Configuration check](config-check.md) for the settings the server validates at boot.
- [How to run a playtest](../guides/how-to-run-a-playtest.md) for how to turn computer racers on and what to look for.
