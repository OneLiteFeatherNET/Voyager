# Proposal

**Type and scope:** `fix(race)`. Ships as one code change under this title. It changes three things a racer meets at the edges
of a map: how the flight ends at the finish, when and where a racer waits for the next map, and how far behind a racer the
racing line is drawn. It does not change scoring, ring detection, the medals, the length of the start countdown, the GO
release of #311 (no impulse at GO), or the between-maps countdown and standings (`competitive-race-feedback`).

**Depends on:** nothing for the merge order. `competitive-race-feedback` (`feat(race)`) shows its countdown and standings
while racers already stand in the next map, so this change's arrival must land first or the two are merged together.
`configurable-guide-particles` (`feat(catalog)`) is independent.

## Why

The owner playtested the three-map `legacy_cup` (palmbeach, dragonlobby, memoria) and found three rough edges where one map
hands over to the next:

1. **Finish.** A racer who passes the last ring is stopped in mid-air. The map's game phase ends when the last racer finishes,
   and its end clears the elytra and sends a zero velocity. The racer drops out of the sky.
2. **Arrival.** The racer is moved into the next map's world about three seconds before GO, holding the elytra, and nothing
   holds them still. They can walk, jump and deploy, and can fly off, land wrong or lose their bearings. The move also blocks
   the server tick while the chunks load, and with the deployment's larger view distance that stall grows.
3. **Guide line.** The racing line stops being drawn under a racer who is still flying through the gap they have just left.

## What Changes

- **Glide-out.** A racer who passes the last ring keeps flying for 2.5 seconds with their speed damped to a quarter. A firework
  and a sound mark the finish. A racer still airborne when the map's time runs out gets the same glide-out.
- **Arrival at the start of the wait.** Racers are placed on the next map at the start of the wait before it, not three seconds
  before GO. A racer who finished early is placed when their own glide-out ends, while other racers still race the previous
  map. A racer still airborne when the previous map ends is placed when their glide-out ends. The wait before the first map
  starts at its first lobby tick.
- **Spawn freeze.** From placement until GO, a racer stands at the spawn of the next map, facing its first ring, without
  gliding, and cannot move away. This applies to every map of a cup.
- **Non-blocking preload.** The chunks around the spawn load in the background. The server tick never waits for them. The racer
  is held in place until every chunk is present, and is then placed on the next tick. A failed load leaves the racer held and
  is reported.
- **Guide line.** The line stays drawn from the last ring the racer passed until the racer has passed the next ring. This is a
  MODIFIED requirement of `race/racing-line-display` for both modes.

## Root causes (verified on `origin/main` at `daadf33`)

| Point | Root cause | Where |
|---|---|---|
| Finish cut-off | The game phase ends when the last racer finishes; the phase end stands every racer down with a zero velocity and no elytra. | `race/flow/RaceStateMachine.java:79-80`; `platform/cup/CupSession.java:842-857` (`mapFinished`, `standDown` at 857); `platform/flight/Racers.java:133-136` |
| Early arrival | `lobbyTick` enters the next map at the first countdown digit, three seconds before GO. `enterMap` moves the racers and gives the elytra, and nothing pins them. | `platform/cup/CupSession.java:620-636`, `680-702`; `platform/flight/Racers.java:101-107` |
| Blocking preload | `Arrivals.awaitChunksAround` joins every chunk future on the tick, and `Arrivals.arrive` joins the teleport. | `platform/world/Arrivals.java:47-62`, `81-90` |
| Guide line gap | The stretch starts at the ring the racer is heading for, and `passedCount` advances on the tick the ring is crossed, so the segment still being flown is dropped. The comment at `CupSession.java:739-742` makes this deliberate. | `platform/render/GuideLineRenderer.java:174-179`; `race/line/RacingLine.java:98-105`; `platform/cup/CupSession.java:721`, `739-743` |

Ruled out for the guide line: `lookAheadRings` (the default mode is `FULL`, which does not use it), the reach cull (160 blocks),
and ring detection timing (`race/run/RaceRun.java:171-186`, `race/progress/ProgressTracker.java:38-45`).

The GO release of #311 (`0c9166d`) is kept.

## Capabilities

### New Capabilities

- `race/finish-glide-out`: the 2.5-second damped glide after the finish and after the time limit, the finish firework and
  sound, and the hold after the glide until placement.
- `race/wait-arrival`: when a racer is placed on the next map (at their own glide-out end, at the start of the wait, or at the
  first lobby tick), and what stays with the previous map's race.
- `race/async-arrival-preload`: the preload runs without blocking the tick, the racer is held until the chunks are present, and
  a failed preload is reported.
- `race/map-start-freeze`: the freeze from placement until GO on every map.

### Modified Capabilities

- `race/racing-line-display`: the stretch of the line starts at the last ring passed, in `FULL` and `WINDOW` modes.

## Out of scope

- The between-maps countdown, the standings, and the place lines: `competitive-race-feedback`.
- The particle and colour: `configurable-guide-particles`.
- The chunk view distance. It is a deployment setting (JVM flags), not a code requirement. `design.md` records its effect.
- `Arrivals.reposition`, used for a reset inside a map, which keeps its blocking join. It is noted for a later change.
- The start countdown's length and the GO release.
