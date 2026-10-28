# Design

## Context

Three edges of a map are rough. The root causes are in the proposal's table. This design decides how each is fixed and where
the new code lives.

### The timeline between two maps, as the code runs it

`race/flow/RaceTimings.java:53-54` gives the production timings: a lobby of 20 s before every map (`RaceStateMachine.java:62`
and `69-70` apply `timings.lobby()` to every LOBBY phase, and `105` moves to the next map's lobby), an END of 8 s when another
map follows, and of 20 s after the last map (`endBetweenMaps`, `endAfterLastMap`). The development timings are shorter (`server/config/ServerSettings.java:154-156`:
lobby 10 s, between maps 5 s). The start countdown is three seconds (`flow/StartGate.java`, `COMMIT_WINDOW`), so the arrival of
the next map is at GO minus three seconds, inside the lobby.

For a racer who finishes a map, the time to the next GO is the END (8 s) plus the lobby (20 s), 28 s in production. The
glide-out (2.5 s) fits inside the END window, and so does the between-maps countdown of `competitive-race-feedback`.

### Where the freeze and the view distance meet

`platform/world/Arrivals.java:47-62` (`arrive`) loads every chunk within `min(player view distance, target view distance) + 1`
of the spawn, and only then calls `setInstance(...).join()` (line 61). Both waits are blocking, and they run on the server tick
(`CupSession.java:698`, inside `enterMap`, called from `lobbyTick` at 620-636). The preload radius therefore sets how long the
tick stalls before the racer can be placed.

The deployment's JVM flags now raise the view distance: `-Dminestom.chunk-view-distance=16` and
`-Dminestom.entity-view-distance=10`. The effect on this design:

- The preload radius is `min(client, 16) + 1`, so up to 17 chunks in each direction. That is up to 35 x 35 = 1,225 chunks,
  against 19 x 19 = 361 at radius 9 (a view distance of 8 plus one, for comparison). The preload is larger by up to about
  3.4 times, and it runs on the tick that also counts the three-second countdown.
- The racer must wait frozen while the chunks load. Because the preload is a blocking `join()` on the tick, the whole
  server pauses while it loads, including the countdown digits. The racer is not yet in the new world during that wait,
  so the freeze is in the old world's position until the move succeeds. The freeze requirement covers that wait.
- The entity view distance (10) is not used by the preload. It decides which racers a racer sees, not which chunks load.

These are deployment settings. The code does not read them: they are set on the server's launch command, and Voyager's own
settings still go through avaje-config, not `System.getProperty`. No requirement in this change depends on the value. The
owner asked for them as a deployment choice, so they are recorded here, and the code PR measures the preload on the playtest
server at 16 and 10 (task 4.4).

A larger preload makes the pre-GO window the place where a stall is most visible. Open question 2 asks whether the arrival
should move earlier, into the lobby, so that the freeze is longer but the countdown is not blocked.

## Decisions

### 1. Finish glide-out: the racer keeps flying, and their speed is damped

When the finish ring is passed, the racer enters a glide-out of 50 ticks (2.5 s). During it:

- The racer keeps the elytra and keeps flying on the client. The server does not turn gliding off.
- The server sends a damped velocity each tick, through the same channel as the boosts (`VelocityExit`). The speed at tick
  `t` of the window is `v0 * k^t`, with `k` chosen so that the speed at tick 50 is one quarter of `v0`, where `v0` is the speed
  at the finish tick. The speed is estimated from the racer's last two positions. The curve is a pure function in
  `voyager/race` (`race/glide`), with no Minestom import, and it is tested without a server.
- A finish firework and a finish sound play at the finish ring, once. The sound is not the countdown beat, the ring bell or
  the lobby chime, so it respects "each sound means one thing" (`race-feedback`).
- No ring effect and no reset apply to a finished racer. This is already true (`CupSession.java:808`, `776`) and stays.

At the end of the window the racer stands down: the elytra is turned off, the velocity is zero, and the racer is placed at
the spawn of the map they just finished (not at the lobby hub), without elytra. The racer then waits there until the next
map's arrival, which is the same place the freeze uses.

The stand-down is deferred. `CupSession.mapFinished` (line 857) currently calls `Racers.standDown` for every racer when the
game phase ends. The change keeps that call for every racer who is not gliding out, and moves a finisher's stand-down to the
end of their glide-out. The game phase may end on the last finish (`RaceStateMachine.java:79-80`) while the glide-out runs;
the glide-out is ticked in the END phase as well as in GAME, so it completes in the END window (8 s, 5 s in development, both
longer than 2.5 s).

#### Options considered

| Option | Pro | Con |
|---|---|---|
| A. Damped server velocity over 50 ticks (chosen) | Uses the channel boosts already use. Reads as a braking, not a stop. Testable as a pure curve. | The client's own elytra physics runs too, so the damping is an approximation the owner must feel in play. |
| B. Slow Falling for 50 ticks | One potion effect, no per-tick packets. | Slow Falling reduces the fall, not the forward speed. The racer floats, which does not read as braking. |
| C. Keep the stand-down at the finish (today) | No new code. | The owner's complaint: an abrupt cut-off. |

Option A is recommended. Option B is kept as the fallback if the damped velocity fights the client's flight in the smoke test.

### 2. Velocity authority: a second kind of server-sent velocity

`CLAUDE.md` says velocity is sent only for external forces: firework boosts, ring boost and slow effects, and out-of-bounds
resets. The glide-out is a braking applied by the course's end, so it is a new external force of the same kind. It needs a
MADR decision (Proposed, not accepted), so the owner can accept it before the code PR. The number is the next free one after
the decisions already in flight (ADR-0040 is held by `add-catalogue-keys-and-authors`). Task 3.2 writes it.

### 3. Spawn freeze: pinned until GO, on every map

From the moment a racer is placed on a map (`enterMap`) until the GO tick:

- The racer is pinned to the spawn. Each tick, if the racer's position differs from the spawn by more than a small tolerance
  (0.1 block, a value the code PR may tighten), the server returns them to the spawn and sends a zero velocity.
- The racer is not gliding. Each tick before GO the server turns flying off, so a racer who jumps and deploys is pulled
  back and stays on the ground. The elytra stays in the racer's hand, as `Racers.faceCourse` documents (`Racers.java:101-107`),
  because the racer is meant to see it during the count.
- The racer faces ring 1. This is already the behaviour of `Racers.faceCourse` and is kept.
- GO releases the racer as before. There is no impulse at GO and the racer is not set gliding (#311, `0c9166d`).
- The freeze holds while the preload runs. The racer is not in the new world before the preload finishes, so they are held in
  their previous place. A failed preload (`ArrivalFailedException`) leaves the racer where they were, as `Arrivals.arrive`
  documents.

The freeze is per racer and per map, so it applies to every map of a cup. It is not tied to the first map's lobby, which
`waiting-lobby` covers separately.

The pin is applied in the platform module. The rule that decides "is this racer pinned at this tick" is a pure function of
the map, the spawn, the racer's position and the clock, and is tested without a server. This keeps `race` free of Minestom.

### 4. The guide line starts at the last ring passed

`GuideLineRenderer.stretch` draws from `max(passedCount - 1, 0)` instead of `passedCount`, in both `FULL` and `WINDOW` modes.
The stretch then covers the segment the racer is flying, from the last ring passed to the next one. The segment is dropped
when the next ring is passed, which is the moment the racer has actually flown through it.

The comment at `CupSession.java:739-742`, which says the racer must not be shown the stretch they have just flown out of, is
wrong for the segment in front of the last ring and is replaced. The rule it protected (no flown stretch behind the racer) still
holds for everything before the last ring passed.

`lookAheadOf` (`FULL` reaches to the last ring) and the reach cull are unchanged. The cost is one more ring of points per
refresh.

### 5. The race logic stays pure

- `race/glide`: the damping curve and the glide-out length. No Minestom import.
- `race/line`: no change. The line's `between` is reused.
- `platform/flight`: the freeze pin and the stand-down position, using `Racers`.
- `platform/cup`: the glide-out ticker and the `mapFinished` change.
- `platform/render`: the stretch start.

Fitness: `voyager-fitness` already requires that `race` has no platform imports. The new pure curve needs one test in the
`race` module. No new fitness rule is added, because no new boundary is created.

## Architecture

| Type or change | Module | Package |
|---|---|---|
| `GlideOut` (length, damping, speed at tick t) | `voyager-race` | `net.elytrarace.voyager.race.glide` (with `package-info` @NotNullByDefault) |
| Glide-out ticker and deferred stand-down | `voyager-platform` | `net.elytrarace.voyager.platform.cup` (`CupSession`) |
| Freeze pin and spawn stand-down | `voyager-platform` | `net.elytrarace.voyager.platform.flight` (`Racers`) |
| Finish firework and sound | `voyager-platform` | `net.elytrarace.voyager.platform.hud` (`RaceFeedback`) |
| Stretch start | `voyager-platform` | `net.elytrarace.voyager.platform.render` (`GuideLineRenderer`) |

Dependency direction holds: `race` does not import `platform`, and `platform` calls `race`.

## Open questions

1. **DNF at the time limit.** A racer still airborne when the time limit ends is stood down at once, as today. Should they get
   the same glide-out? Recommendation: yes, for the same reason, but it is a separate decision and this change does not do it.
2. **Arrival earlier.** The preload runs on the tick that counts the countdown. At radius 17 it may stall the countdown. Should
   the arrival move into the lobby, about 17 s before GO, so the freeze is longer and the countdown is not blocked? The code PR
   measures the preload time on the playtest server first (task 4.4) and the owner decides.
3. **Damping feel.** The quarter-speed target is a first estimate. The owner decides after the playtest whether a shorter or
   longer glide is right, within the 2 to 3 s the owner asked for.
4. **Pulse review.** `CLAUDE.md` requires the game-psychologist review of a gameplay change. It has not happened for this
   change, and the owner decides whether it happens before the code PR.
