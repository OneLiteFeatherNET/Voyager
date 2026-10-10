# Design

## Context

See proposal.md, "Why", and specs/race-course-reset/spec.md for the requirements.

The relevant current state:

- A racer's run is a value, `net.elytrarace.voyager.race.run.RaceRun`, advanced by
  `RaceRun.advance(map, clock, position, gliding)`. Its `RingProgress` holds the count of passed rings
  (`passedCount`), which is also the index of the next ring to pass, and the last `CHECKPOINT` index. The last ring
  passed is therefore `map.rings().get(passedCount - 1)`.
- `RaceRuns` (`net.elytrarace.voyager.platform.cup`) holds one run per racer and is the only place a run is written.
  `advance` is the only write today. `UnstartedRunException` lives in `platform.cup.exception`.
- `CupSession.raceTick` (`platform.cup`) advances each racer's run with the client-reported position, then reports
  ring passes, renders the HUD and draws the racing line. `raceTick` is called only in the `GAME` phase
  (`XerusPhaseDriver`), and only for racers who hold a run.
- `MapTransition` (`platform.cup`) moves racers onto a map with a chunk wait and a confirmed teleport, and gives them a
  fresh run. Its same-world branch is the one a reset needs.
- `Racers` (`platform.flight`) sets a racer gliding and sends the launch impulse through `VelocityExit`, the only class
  that sends velocity to Minestom. `Racers.launch(player, map)` aims horizontally from the spawn toward ring 0.
- `FlightTickDriver` (`platform.tick`) keeps a server-side shadow of each flight, seeded from the client's observation
  when a flight starts, and has `forget(UUID)`.
- `FireworkBoostTracker` (`platform.flight`) holds a burn and a cooldown per racer, and has `forget(UUID)` (which drops
  both) and `clear()`.
- The waiting lobby is merged: `CupSession` has `situation`, `disarm` and `abort`, and `WaitingRoom` is in
  `platform.lobby`. A waiting racer, or a racer held by an aborted cup, holds no run.
- Ring normals: the map schema says the normal's sign decides which way through a ring counts, so the normal is the
  flow direction. Its unit length is enforced by `Ring`.

## Goals / Non-Goals

**Goals:**

- Pure decisions (is out of bounds, is a landing, where does the reset go, what does the run become) in `voyager-race`,
  with no Minestom type.
- Minestom effects (teleport, relaunch, cancel a burn, forget the shadow flight, title and sound) in `voyager-platform`,
  in the cup slice, which already depends on `race`.
- No new frozen-rule violation, no store growth, no new Gradle module, no new ArchUnit rule.
- Existing maps unchanged; no new map field.

**Non-Goals:**

- Horizontal bounds, a per-map bounds field, a minimum air time, a cooldown after a reset, a time penalty, a point
  penalty, a forfeit of rings, collision damage, the missed-ring signal. See proposal, "Scope and slices".
- Changing the signature of `RaceRun.advance`. A secondary constructor keeps the existing five-argument call sites
  unchanged.

## Decisions

### D1. Out of bounds is a vertical test against the dimension, checked on the position

`RunReset.isOutOfBounds(Vec3 position, double floorY, double ceilingY)` is `position.y() < floorY || position.y() > ceilingY`.
The platform passes `floorY = instance.getCachedDimensionType().minY()` and
`ceilingY = floorY + instance.getCachedDimensionType().height()` for the racer's instance. Strictly below the floor or
strictly above the ceiling: standing on the floor is in bounds. The method rejects a ceiling below the floor with
`IllegalArgumentException`, so a bad caller fails loudly rather than resetting everybody.

*Alternatives:* a constant of -64 and 320 (the old tree's `MIN_Y` and `MAX_Y`). Rejected: it ties the rule to the
overworld and hides a world with a different height. The dimension is already the source of truth for the world.

### D2. A landing is a transition from gliding to standing, with the racer on the ground

`RunReset.isLanded(RaceRun before, boolean gliding, boolean onGround)` is
`before.gliding() && !gliding && onGround && !before.finished()`.

`RaceRun` gets a `gliding` component: whether the racer was gliding on the tick that produced the run. `atStart()`
gives `false`, so a racer who has not yet taken off cannot land. `onGround` is Minestom's `Player#isOnGround()`. The
flying flag alone is not enough, because a client can stop gliding in mid-air, and that is not a landing.

The five-argument constructor of `RaceRun` stays, and delegates with `gliding = false`. Existing call sites that do not
care about the flag are unchanged.

### D3. The reset target is the last ring passed, or the spawn

`RunReset.plan(MapDefinition map, RaceRun run, ResetCause cause)` returns a `ResetPlan`, and refuses a finished run with
`RunNotResettableException`.

- If `run.progress().passedCount() > 0`, the last ring is `map.rings().get(passedCount - 1)`, of any type. The target
  position is that ring's `center()`. The target direction is `center().plus(normal())`, so the racer faces along the
  flow normal. The target ring number is `passedCount` (1-based), which is what the title shows.
- Otherwise the target position is `map.spawn()`, and the target direction is `map.rings().getFirst().center()`, which is
  what a map start faces. The ring number is empty, meaning "the start".

In both cases the reset run is the same run with its transient fields cleared: `previous = null` (the first tick after
the teleport tests no segment, which is `ProgressTracker`'s existing behaviour), `justPassed = null`, and
`gliding = false` (so the relaunch does not register as a landing, D5). Progress, the tick each ring was passed on, and
`finishedAt` are kept unchanged. No ring is forfeited.

*Alternatives considered:* the spawn for every reset (the old tree's out-of-bounds rule). Rejected: it forfeits a whole
map for one fall, which the owner ruled out. Resetting to a checkpoint only. Rejected by the owner: any ring is a valid
return point, so checkpoints need no special treatment in the reset.

### D4. Penalty: position only

The race clock is not touched and no points are removed. The time a finisher is scored on is still the clock of the
tick they passed the final ring. A racer who falls back to the last ring loses the flight back to it and the time it
takes, and nothing else. That is the whole cost, and it is legible because the clock keeps running.

### D5. Relaunch with the existing launch impulse, aimed along the facing

`Racers.launch(Player, Vec3 from, Vec3 toward)` is the one launch routine. It turns the racer toward `toward`, sets
gliding, and sends the impulse `launchVelocity(from, toward)`: `LAUNCH_FORWARD_PER_TICK` along the horizontal part of
`toward - from`, plus `LAUNCH_UP_PER_TICK`. `Racers.launch(Player, MapDefinition)` becomes
`launch(player, map.spawn(), map.rings().getFirst().center())`, so a map start is unchanged.

A reset calls `launch(player, target.position(), target.toward())`. For a ring target, `toward` is the centre plus the
normal, so the horizontal impulse follows the flow normal. A ring whose normal is vertical has no horizontal part, so
the impulse is straight up, which is the existing rule for that geometry.

`VelocityExit`'s list of external forces names "the launch that starts a map", and the javadoc changes to "the launch
that starts a map or relaunches a racer after a reset". The list stays at three.

*Alternative:* zero velocity and let the racer re-open the elytra. Rejected: a racer who falls out of the world would stand
on a spawn with no glide, and the old tree's "re-activate your elytra" depended on the racer knowing to do so.

### D6. The reset is performed by one class in `platform.cup`, decided by `CupSession.raceTick`

`RunResetter` (`platform.cup`) takes the racer, the map, the cause and the `ResetPlan`, and does, in this order:

1. replaces the racer's run with `plan.run()` (`RaceRuns.resetTo`, package-private, `UnstartedRunException` if the racer
   holds none);
2. cancels an active firework burn (`FireworkBoostTracker.cancelBurn`); the cooldown is kept, so a reset cannot refresh
   a rocket;
3. forgets the racer's shadow flight (`FlightTickDriver.forget`), so the next flight sample re-seeds from the client;
4. teleports the racer to the target (`MapTransition.reposition`, the same-world path with the chunk wait and the
   confirmed teleport);
5. relaunches (`Racers.launch`, D5);
6. shows the title and plays the sound (`RaceFeedback.reset`, text from `Messages`).

`CupSession.raceTick` decides the reset. Per racer, after `runs.advance` and `announcer.report`:

- the racer must hold a run before the tick (`held`) that is unfinished, and the advanced run must not be finished;
- if `RunReset.isOutOfBounds(position, floorY, ceilingY)` holds for the advanced position, where the floor and ceiling
  come from the racer's instance dimension, the cause is `OUT_OF_BOUNDS`;
- otherwise, if `RunReset.isLanded(held, racer.isFlyingWithElytra(), racer.isOnGround())` holds, the cause is `LANDED`;
- on a reset, the HUD and the racing line of that tick show `plan.run()`, not the advanced run, so the display never
  disagrees with the run the tick left behind.

The guards for the waiting lobby are the ones already in the code and are made explicit here: `raceTick` runs only in
`GAME` (`XerusPhaseDriver`), and a racer with no run is skipped before any check. A waiting racer, a racer held by an
aborted cup, and a player who joined mid-race hold no run and are never considered.

`CupSession` builds its one `RunResetter` from the run board, the boost tracker and the flight driver it already holds,
as it builds its announcer and renderers. No bean is added to `CupBeans`, and the cup's constructor and its call sites
stay as they were. The reset is decided in the same tick as the pass it follows, so the feedback is in the tick it is
detected.

*Alternative:* a separate `RacePhaseListener` method or a new tick step. Rejected: a reset must observe the same advanced
run and the same position the ring check used, and a separate step would read a second copy of both.

### D7. Feedback: one title and a low note, in the detecting tick

`RaceFeedback.reset(Player, Component title, Component subtitle)` sends a title with no fade-in (the existing rule: a late
confirmation is worse than none) and plays `block.note_block.bass` at pitch 0.6, volume 0.8, on `MASTER`. A low single
note is the opposite of the rising ring bell, so a racer hears the difference without reading.

`Messages` takes primitives only, because `platform.text` is frozen against `race`:
`resetTitle(boolean outOfBounds)` and `resetSubtitle(OptionalInt ringNumber)`, where the number is 1-based and an empty
value means the start. The keys go in `voyager-server/src/main/resources/voyager_en_US.properties`:

- `voyager.reset.title.outofbounds`
- `voyager.reset.title.landed`
- `voyager.reset.target.ring` (argument 0 is the ring number)
- `voyager.reset.target.start`

They use MiniMessage `<arg:N>` placeholders, never `{N}`. `MessageBundleTest` already checks the keys.

### D8. Burn cancellation keeps the cooldown

`FireworkBoostTracker.cancelBurn(UUID)` ends a burn and leaves the cooldown. The existing `forget(UUID)` drops both and is
used on disconnect and between maps; a reset is neither, and dropping the cooldown would let a racer refresh a rocket by
landing. A new method is therefore needed, not a reuse.

### D9. Placement of `race.reset`

The new package `net.elytrarace.voyager.race.reset` holds `RunReset`, `ResetPlan`, `ResetCause`, and
`exception.RunNotResettableException` (in `race.reset.exception`, with its own `package-info.java`). `RunReset` follows
the design rules: abstract utility, private constructor, `@ApiStatus.Internal`, `@Contract(pure = true)` on each factory
or predicate. `ResetPlan` is a record whose compact constructor rejects a finished run. `race.reset` needs a
`package-info.java` with `@NotNullByDefault`.

## Golden master

Affected scenarios: none expected. The four committed scenarios of `CupSessionGoldenMasterTest` (`lobby-and-start`,
`racing-boost-disconnect`, `skip-finish`, `restart-pending-reload`) record `CupSession.describe()` every tick. The reset
changes that output, so a scenario that triggered one would fail byte-identity. The planning check was that no racer in a
committed scenario leaves the bounds or lands during `GAME`. Task 4.1 verifies it by running the test.

If a transcript changes, the change stops and the owner decides. The golden file is then regenerated in its own
`test(race): ...` commit that states the scenario, the tick and the reason. Golden files are never regenerated in the same
commit as the feature.

## Dependency direction and placement

```
voyager-server  (composition root: CupBeans builds RunResetter, CupSession, TickPipeline)
   |
voyager-platform
   platform.cup     CupSession, RunResetter, RaceRuns, MapTransition.reposition   --> race, flight, hud, text, tick, world
   platform.flight  Racers.launch(from, toward), FireworkBoostTracker.cancelBurn    --> race.run, race.progress (Ring)
   platform.hud     RaceFeedback.reset                                              --> no race type
   platform.text    Messages.resetTitle / resetSubtitle                             --> primitives only
   platform.tick    FlightTickDriver.forget (unchanged)
   platform.convert VelocityExit (javadoc only)
   |
voyager-race
   race.reset       RunReset, ResetPlan, ResetCause, exception.RunNotResettableException --> race.run, race.progress, api
   race.run         RaceRun (+ gliding component)                                         --> race.progress, api
   |
voyager-api         Vec3, Ring, MapDefinition (unchanged)
```

| New type or change | Module | Package | Notes |
|---|---|---|---|
| `RunReset` (abstract utility, `@ApiStatus.Internal`, private constructor) | voyager-race | `race.reset` | Pure rules D1 to D3 |
| `ResetPlan` (record: cause, run, position, toward, ring) | voyager-race | `race.reset` | Compact constructor rejects a finished run |
| `ResetCause` (enum: `OUT_OF_BOUNDS`, `LANDED`) | voyager-race | `race.reset` | |
| `RunNotResettableException` (extends `RuntimeException`) | voyager-race | `race.reset.exception` | Planning a reset of a finished run is a programming error |
| `RaceRun.gliding` (new component, secondary constructor) | voyager-race | `race.run` | `atStart()` gives `false`; `advance` sets it from its `gliding` argument |
| `RunResetter` | voyager-platform | `platform.cup` | The only class that performs a reset |
| `RaceRuns.resetTo(UUID, RaceRun)` | voyager-platform | `platform.cup` | Package-private, like `startFresh` |
| `MapTransition.reposition(Player, Pos)` | voyager-platform | `platform.cup` | Same-world confirmed teleport, extracted from `arrive` |
| `Racers.launch(Player, Vec3, Vec3)` | voyager-platform | `platform.flight` | Map start and reset both call it |
| `FireworkBoostTracker.cancelBurn(UUID)` | voyager-platform | `platform.flight` | |
| `RaceFeedback.reset(...)` | voyager-platform | `platform.hud` | The only file that names the reset sound |
| `Messages.resetTitle`, `Messages.resetSubtitle` | voyager-platform | `platform.text` | Primitives only |
| `RunResetter` construction in `CupSession` | voyager-platform | `platform.cup` | No bean; see D6 |

No DI annotation appears outside `voyager-server`. `race.reset` needs a `package-info.java` with `@NotNullByDefault`
(rule 5, `NullabilityConventionTest`); `race.reset.exception` needs its own `package-info.java`.

## Fitness coverage

- **R3 (`platform.{text,convert,world,tick,render}` must not depend on `race`)**: no new reference from these packages.
  `Messages` takes primitives; `MapTransition.reposition` takes Minestom types and sits in `platform.cup`; `VelocityExit`
  changes only its javadoc. The frozen store must not gain an entry.
- **R4 (platform slices free of cycles)**: `platform.cup` already depends on `world`, `flight`, `hud`, `text` and `tick`,
  and none of those depends on `cup`. The change adds no edge from them back to `cup`.
- **R7 (race slices free of cycles)**: `race.reset` depends on `race.run` and `race.progress`; `race.run` must not depend
  on `race.reset`.
- **ApiPurityTest**: `race.reset` and `race.run` import no Minestom type.
- **NullabilityConventionTest, DesignRuleTest**: the new package and the exception follow the naming and nullability rules.
- **FitnessCoverageTest**: no new module, so no entry changes.

No new ArchUnit rule is added. The store must not gain an entry.

## Velocity authority

Normal flight stays client-authoritative. The reset sends velocity through `VelocityExit` in one place, the relaunch,
which is the sanctioned launch impulse. No other code calls `Player#setVelocity` for a reset. The shadow flight
(`FlightTickDriver`) is forgotten on reset, not written; the next flight sample re-seeds it from the client's observation,
which is the behaviour a flight start already has.

## Risks / Trade-offs

- **[The reset blocks the tick thread on a chunk wait and a confirmed teleport]** → Mitigation: the same-world path is the
  one `MapTransition` already uses for a same-world move, and the chunks around a ring the racer has just flown through
  are normally loaded already. The test environment exercises the join; a real-client measurement is recorded as an open
  point, not claimed.
- **[The shadow flight is forgotten mid-flight]** → Mitigation: the next flight sample re-seeds from the client. A test in
  `RunResetterTest` asserts that the next flight sample starts from the client's position.
- **[A reset during a boost burn]** → Mitigation: the burn is cancelled before the teleport, so no impulse from it applies
  on the new position.
- **[The landing check depends on `onGround`, which is client-reported]** → Mitigation: the same signal the flight tracker
  uses. A false landing resets a racer who was gliding, and the relaunch restores them within one tick.
- **[A ring whose normal is vertical relaunches straight up]** → Mitigation: that is the existing rule for that geometry,
  and no committed ring is horizontal. Recorded here, not hidden.
- **[Pulse and Drift have not reviewed the feel of a reset]** → Mitigation: the title, the sound and the no-penalty rule
  are the parts to review before apply; they are one-file changes (`RaceFeedback`, `Messages`).

## Migration Plan

No data migration. The map files are untouched. Deployment is the normal build. Rollback is reverting the feature commits.

## Open Questions

- Is a horizontal limit wanted before the alpha? The time limit already ends a racer who flies away. If yes, it is a new
  requirement and, if it needs map data, a new optional map field; that would reopen the specs.
- Should the design be recorded as an ADR? It is a game rule, not an architecture decision, so no ADR is proposed. If the
  owner wants one, it takes the next free number after ADR-0021 at apply time. Any ADR stays Proposed.
