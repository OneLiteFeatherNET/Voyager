# Map boost and ring-effect tuning

A speed cap, when a map states one, limits only the velocity that a `BOOST` or `SLOW` ring writes to a racer. It never limits normal flight or rockets.

Each map states its boost tuning and its ring factors in its own map file, next to its rings. No environment variable or operator setting changes them. A map file that states none of the optional fields races with 64 rockets, no speed cap and the factors 1.5 and 0.5, which is how every map played before these fields existed. The fields are described in [`map-and-cup-files.md`](map-and-cup-files.md) and in `map.schema.json`, which lets an editor check the bounds.

## boostConfig

| Field | Type | Required | Default | Bounds | Meaning |
|---|---|---|---|---|---|
| `burnDurationTicks` | integer | Yes | None | At least 1 | How many ticks one rocket boosts for. The server removes the rocket after exactly this many. |
| `cooldownTicks` | integer | Yes | None | More than `burnDurationTicks` | How many ticks after a boost starts before the next one may be used. |
| `rocketsPerRacer` | integer | No | `64` | 1 to 64 | How many rockets a racer is given when the map begins, and again after a reset. The stack is never spent. |
| `maxSpeedBlocksPerTick` | number | No | No cap | Finite and greater than 0 | The largest speed, in blocks per tick, that a `BOOST` or `SLOW` ring may leave a racer with. The direction is kept. |

The rocket item's flight duration follows the burn: `round(burnDurationTicks / 10)`, clamped to 1 to 3. A 30-tick burn states 3, the longest rocket a player can craft. A 10-tick burn states 1.

## ringEffects

| Field | Type | Required | Default | Bounds | Meaning |
|---|---|---|---|---|---|
| `ringEffects` | object | No | Both factors at their defaults | Must be an object | The velocity factors of this map's `BOOST` and `SLOW` rings. |
| `ringEffects.boostMultiplier` | number | No | `1.5` | Finite and 0 or more | The factor a `BOOST` ring multiplies the racer's velocity by. |
| `ringEffects.slowMultiplier` | number | No | `0.5` | Finite and 0 or more | The factor a `SLOW` ring multiplies the racer's velocity by. |

A factor that is left out keeps its default, so a file may change one factor and say nothing about the other.

## How the effects combine

- A gliding racer with an unfinished run that crosses a `BOOST` or `SLOW` ring has its velocity multiplied by that ring's factor.
- Effects in quick succession compose in the order the rings are crossed. A `BOOST` followed by a `SLOW` leaves three quarters of the speed.
- The speed cap applies once, to the result of the effects. A racer whose result is faster than the cap keeps their heading at the cap's speed.
- A ring crossed on foot, after the run has finished, or on the tick the racer is reset gets no effect.
- Rockets are never capped. Rocket impulse is Vanilla's and is not set per map.

## Example

```json
"boostConfig": {
  "burnDurationTicks": 30,
  "cooldownTicks": 40,
  "rocketsPerRacer": 12,
  "maxSpeedBlocksPerTick": 3.5
},
"ringEffects": {
  "boostMultiplier": 2.0
}
```

This map gives each racer 12 rockets. A `BOOST` ring doubles the racer's velocity, and no ring-written velocity exceeds 3.5 blocks per tick. The `SLOW` factor stays at its default of 0.5.

## Where the values appear

When a map's start countdown begins, the server logs one line that states the tuning the map is played with:

```text
Map 1/2 'elytraraceblueandred' tuning: burn 30, cooldown 40, rockets 64, no speed cap, BOOST 1.5, SLOW 0.5
```

## Version and deployment

The fields are read in files that declare `schemaVersion` 1, or that declare none. The format version stays at 1.

An older server ignores unknown fields inside `boostConfig`. It would race a map that uses these fields without its cap or its rocket count. Deploy a server that reads these fields before committing a map file that uses them. [ADR-0032](../decisions/0032-per-map-boost-tuning.md) records this decision.
