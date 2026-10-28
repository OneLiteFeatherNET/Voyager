# Design

## Context

### The timeline, as the code runs it

`race/flow/RaceTimings.java:53-54` gives the production timings: a lobby of 20 s before each map (`RaceStateMachine.java:62`,
`69-70`, and the move to the next map's lobby at `105`), an END of 8 s when another map follows, and 20 s after the last map.
Development timings are shorter (`server/config/ServerSettings.java:154-156`: lobby 10 s, END 5 s). The start countdown is
three seconds at the end of the lobby (`race/flow/StartGate.java`, `COMMIT_WINDOW`).

The wait before a map is therefore the END of the previous map plus its lobby: 28 s in production. The next GO is at the
end of that wait.

### Where the racer is placed today

`CupSession.lobbyTick` (`platform/cup/CupSession.java:620-636`) enters the map at the first countdown digit, three seconds
before GO. `enterMap` (`680-702`) sets the cup's current map, calls `MapTransition.advanceTo` (`698`) and `Racers.faceCourse`
(`700`). `MapTransition.advanceTo` (`platform/cup/MapTransition.java:104-123`) clears every run with `runs.clearAll()`
(`113`) and then moves each racer with `Arrivals.arrive`. Both `Arrivals` waits block the tick: `awaitChunksAround` joins every
chunk future (`platform/world/Arrivals.java:81-90`), and `arrive` joins the teleport (`61`).

With the deployment's view distance, the preload radius is `min(client, instance) + 1`, up to 17 chunks in each direction
(`Arrivals.java:55`). That is up to 35 x 35 = 1,225 chunks, against 361 at radius 9 (a view distance of 8 plus one, for
comparison). The owner set `-Dminestom.chunk-view-distance=16` and `-Dminestom.entity-view-distance=10` as JVM flags. These are
deployment settings: the code does not read them. Voyager's own settings still go through avaje-config.

### Why the arrival moves to the start of the wait

Three seconds is too short for a preload at view distance 16. Placing the racer at the start of the wait gives the chunks
28 seconds, and the freeze then lasts that long. The freeze's cost is nothing to the race: the racer is not flying.

## Decisions

### 1. The arrival moves to the start of the wait, per racer

The wait before a map starts when the previous map's game phase ends (`mapFinished`), or, for the first map of a cup, at its
first lobby tick. A racer is placed on the next map as early as they can be:

- A racer who finished the previous map is placed when their own glide-out ends. Other racers may still be racing that map.
- A racer who was still airborne when the previous map's time ran out gets the same glide-out, then is placed when it ends.
- A racer who was not gliding when the map ended (on the ground, or not flying) is placed when the map ends, with no glide-out.
- A racer who joins during a wait is placed when their own preload completes.

Placing is per racer. The cup-wide part of the move (the current map, the block source, the ring effects and the tally) happens
once, when the previous map's game phase ends. `MapTransition.advanceTo` is split: `runs.clearAll()` (`113`) and the cup-wide
state move to the phase end, and `Arrivals.arrive` moves to the per-racer placement. `runs.clearAll` must not run while another
racer still races the previous map, because it would drop their run.

### 2. A placed racer leaves the previous map's race

A racer placed on the next map is marked as having left the previous map. `CupSession.raceTick` (`platform/cup/CupSession.java:708`)
skips a racer marked as having left: no run advance, no HUD for the previous map, no guide line for the previous map. Their
run is kept, so that `mapFinished` can still score it when the phase ends (`round.recordMap` at `CupSession.java:850-857`).

`mapStarted` (`CupSession.java:639-668`) re-runs the transition on GO. It must place only the racers not yet placed on this map,
so that it does not move a racer who is already standing at the spawn.

### 3. The glide-out, and the hold after it

The glide-out (2.5 s, 50 ticks) applies to a finisher from their finish tick, and to a racer airborne at the time limit from
the phase end. The damping is the pure curve of decision 4 in `race/glide`.

After the glide-out, the racer's elytra is turned off and their velocity is zero, and they are held in place (position pinned,
see decision 5) until they are placed. The hold uses the racer's current position, not the spawn of the map just finished,
because the next map's spawn is not yet loaded. The finish firework and sound play once, at the finish ring, when the racer
passes the last ring.

The glide-out captures the map it started on (the `MapDefinition` at the finish), not `currentMap`, because the cup's current
map changes at the phase end while the glide-out may still run.

### 4. The damping curve

The speed at tick `t` of the glide is `v0 * k^t`, where `v0` is the speed at the finish tick and `k` is chosen so that the speed
at tick 50 is one quarter of `v0`. The speed is estimated from the racer's last two positions. The curve is a pure function in
`voyager-race` (`race/glide`, no Minestom import), so it is tested without a server.

The velocity is sent through the channel that boosts use (`VelocityExit`). `CLAUDE.md` names the external forces that justify
a server-sent velocity; braking after the finish is one more, and the decision record below records it.

Option B (Slow Falling) was considered and rejected, because it lowers the fall and not the forward speed. It is the fallback if
the damped velocity fights the client's flight in the smoke test.

### 5. The preload does not block the tick

`Arrivals.arrive` and `awaitChunksAround` block the tick today (`Arrivals.java:61`, `89`). The new path does not:

- The preload is started with `Instance.loadOptionalChunk` for every chunk in range, and the futures are kept, not joined.
  This is the same call `awaitChunksAround` makes (`Arrivals.java:84`).
- A `PendingArrival` holds the racer, the target instance, the spawn and the futures. It is ticked once per server tick by the
  race loop. Each tick it checks `allOf(...).isDone()`, and does nothing until then.
- When every future is done without an exception, the tick calls `player.setInstance(target, spawn)`. The placement is done by
  the next tick's check that the teleport future has completed. The tick never waits on it.
- If any future completed exceptionally, the arrival is failed. The racer stays held in place, the failure is logged once with
  the world name, and the arrival is retried on the next wait. Nothing is thrown into the tick.
- The chunk load is injected through a `ChunkLoader` functional interface (`load(Instance, int chunkX, int chunkZ)` returning
  `CompletableFuture<?>`). Production passes `Instance::loadOptionalChunk`. A test passes a loader that completes futures by hand.
  This follows the Injectable Creator rule (rule 7 of the ten design rules in `CLAUDE.md`).

`Arrivals.reposition` (used for a reset inside a map) keeps its blocking join. It is out of scope and is noted in the proposal.

With the view distance at 16 a preload of up to 1,225 chunks runs in the background over the wait, and the tick does not stall.
The chunks of one instance are loaded once for all racers placed there.

### 6. Freeze from placement until GO

From a racer's placement until the GO tick, the racer is pinned to the spawn facing ring 1, without gliding:

- Each tick, if the racer's position differs from the spawn by more than 0.1 block, the server returns them to the spawn and sends
  a zero velocity.
- Each tick before GO the server turns gliding off, so a jump and deploy does not lift the racer off the spawn. The elytra stays in
  the racer's hand, as `Racers.faceCourse` (`Racers.java:101-107`) documents.
- GO releases the racer with no velocity and no gliding, as before (`waiting-lobby`).

The freeze is per racer and per map, so it applies to every map of a cup.

### 7. Computer racers

A computer racer has no client and is not placed. It is counted in the places and gaps of `competitive-race-feedback`. The
glide-out curve is applied to its simulated speed only if the competitor model exposes that speed. The code PR checks it. If it
does not, computer racers stop at the time limit as they do today, and the code PR says so.

### 8. Deployment setting: the view distance

`-Dminestom.chunk-view-distance=16` and `-Dminestom.entity-view-distance=10` are set on the server's launch command. They
change how far the preload reaches (decision 5) and how many entities a racer sees. The code does not depend on their values.
The code PR measures the longest tick during a preload on the playtest server (task 4.4) to confirm the tick does not stall.

## Architecture

| Type or change | Module | Package |
|---|---|---|
| `GlideOut` (50 ticks, damping to a quarter, speed at tick t) | `voyager-race` | `net.elytrarace.voyager.race.glide` (with `package-info` @NotNullByDefault) |
| `ChunkLoader`, `PendingArrival`, the tick check | `voyager-platform` | `net.elytrarace.voyager.platform.world` |
| Per-racer placement, cup-wide split of `MapTransition` | `voyager-platform` | `net.elytrarace.voyager.platform.cup` |
| Glide-out ticker, the left-the-map mark, the hold | `voyager-platform` | `net.elytrarace.voyager.platform.cup` (`CupSession`) |
| Freeze pin | `voyager-platform` | `net.elytrarace.voyager.platform.flight` (`Racers`) |
| Finish firework and sound | `voyager-platform` | `net.elytrarace.voyager.platform.hud` (`RaceFeedback`) |
| Stretch start | `voyager-platform` | `net.elytrarace.voyager.platform.render` (`GuideLineRenderer`) |

Dependency direction holds: `race` does not import `platform`, and `platform` calls `race`. No new fitness rule is needed,
because no new boundary is created. If the code PR finds that `ChunkLoader` must be imported by `race`, the boundary changes
and a rule is added in the same PR.

## Decision record

Decision 6 (a second kind of server-sent velocity: braking after the finish and after the time limit) is a MADR decision with
status Proposed. Its number is the next free one after the decisions in flight; ADR-0040 is held by `add-catalogue-keys-and-authors`. Task 3.2 writes it.

## Open questions

1. **The first map's wait.** The owner's request names the start of the wait before the next map. For the first map there is no
   earlier wait, so this design places racers at the first lobby tick, twenty seconds before GO rather than three. Recommendation:
   accept. The owner confirms before the code PR.
2. **A late preload.** If a preload is not done by GO, the racer is placed when it is done, and misses the start. Recommendation:
   accept, and report it in the playtest. The 28-second wait makes it unlikely with the owner's view distance.
3. **Damping feel.** The quarter-speed target is a first estimate. The owner decides after the playtest whether a shorter or longer
   glide is right, within the 2 to 3 s the owner asked for.
4. **Pulse review.** `CLAUDE.md` requires the game-psychologist review of a gameplay change. The owner decides whether it happens
   before the code PR.
