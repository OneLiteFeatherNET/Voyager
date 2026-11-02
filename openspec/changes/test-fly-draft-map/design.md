# Design

## Context

A builder edits a map in the setup server (`MapSession`, `DraftEditor`, `RingPreviews`). The map's rings and spawn are saved
at each edit, and the saved file is the game's own map format: a draft that has a spawn and a ring sits in `maps/<id>.json`,
which the game reads (`JsonDraftStore`, and the guide's table of where a draft lives). So the saved file is already the
course as the game would load it, with no conversion step.

The game's rules for a run are pure and already tested:

- `RaceRun.advance(MapDefinition, RaceClock, Vec3, boolean)` takes one tick's position and returns the next run. It keeps
  the rings passed, in order (`RingProgress`), the tick each ring was passed on, and the finish (`finishedAt`).
- `MapScorer.score(RingProgress, MapDefinition, Duration)` scores a run against the map's reference time and its medal brackets
  (`MedalBrackets`, `MedalTier`).
- `RaceClock` counts the movement ticks played, so timing is tick-driven and needs no wall clock.
- The racing line is drawn by `GuideLineRenderer` (`platform.render`), and the time is printed by `RaceTimeFormat`
  (`platform.text`). The medal text is `Messages.medal(MedalTier)`.

The cup machinery is different. `CupSession` (`platform.cup`) orchestrates a cup: it starts on the first player to spawn
while idle, holds racers in a waiting room and a lobby, moves them through a rotation, records results through `RaceRecorder`,
and hands racers to the hub. A test flight has none of that. Using it would move the lobby, the rotation and the statistics
into the setup server, and the setup server must not depend on persistence (`PersistenceBoundaryTest`).

The flight is client-authoritative, as the game is (decision D5): the position a builder reports is where the builder flew,
and the server does not simulate the elytra for the rings. So a flight needs the positions the client sends, not a physics
run.

Constraints:

- New Minestom code outside `setup.inject` adds a freeze-store entry, which the apply rules stop on (`SliceBoundaryRulesTest.r6`).
- A flight records nothing. The statistics module and the leaderboard are not reachable from setup (`PersistenceBoundaryTest`).
- The claim registry (`EditClaims`, `single-builder-map-claims`) and the save guard (`DraftChangedException`) already exist,
  and the flight uses them and does not add a second mechanism.

## Goals / Non-Goals

Goals:

- A builder flies the saved course with the same ring order, finish, time and medal rules as a race.
- A flight changes nothing: no draft, no file, no claim, no undo history, no statistics.
- Leaving a flight returns the builder to editing with the draft and history they had.

Non-goals:

- Boost and ring effects, the cup's orchestration, out-of-bounds reset and multi-builder flights (see the proposal).

## Options

Quality attributes at stake: parity of the rules a builder tests against the rules the racers face (correctness); the cost of
the change and the coupling it adds (maintainability, cognitive load); the side effects a flight may have on the server
(safety); and reversibility.

| | A. Pure race rules in a setup-owned flight (recommended) | B. The cup machinery in-process, with a one-map cup | C. A simulation written for the setup server |
|---|---|---|---|
| What it is | The flight calls `RaceRun.advance` and `MapScorer.score` each tick with the reported position, and presents the result with the platform's guide line and time format. No cup, no lobby, no rotation. | Starts a `CupSession` with a cup of one map for the builder, through the same code the game runs. | Its own ring-crossing and timing code in setup. |
| Gains | The same rules as the game, shared rather than copied. Small. Plain JUnit tests with a scripted sequence of positions. No persistence, no hub, no lobby. | Exact orchestration parity: the finish, the glide-out and the GO timing are the game's. | No dependency on race. |
| Costs | The flight does not run the cup's orchestration. Boost rings and ring effects are not applied in version 1. | Pulls the lobby, the phase driver, the rotation, the results and the hub into setup. The cup starts on the first spawn, which a flight must not trigger. Any change to the cup changes the setup server. The setup server would need a recorder that records nothing. | Two copies of the ring rule. The first copy that drifts makes a builder's time wrong, silently. |
| Irreversible parts | None. The flight is an adapter over pure rules. | A cup-session dependency from setup into platform's cup code, which is hard to remove later. | A second rule that must be kept in step with the game. |
| Reversibility | High. | Low. | Medium. |
| Cognitive load | Low: one state machine, one adapter. | High: the cup lifecycle is needed to understand a flight. | Medium: two rule sets to explain. |

Recommendation: **A**. It gives the builder the rules the racers face, because the rules are the same code, and it adds no
game lifecycle to the setup server. Its costs are the boosts and the orchestration, and both are stated to the builder. B is
the choice if the owner wants the cup's orchestration in a flight, and then the cost is the lifecycle in setup. C is rejected
for drift.

## Decisions

1. **The flight is a pure state machine in `setup.testflight`, driven by the adapter.** `TestFlight` holds one run and one
   `RaceClock`. Its operations are `start(MapDefinition)`, `tick(Vec3 position, boolean gliding)` and `stop()`. Each `tick`
   calls `RaceRun.advance` and returns the next flight. When the run finishes, `tick` returns the result, which holds the time
   (`RaceClock` by `finishedAt`), the rings passed out of the map's count, and the medal from `MapScorer.score` and
   `MedalBrackets`. The class imports `api`, `race` and the JDK only.

2. **The map is read as the game reads it.** The flight reads `maps/<id>.json` through `MapDefinitionAdapter` (platform),
   which is the file publish would publish. The flight therefore flies exactly what the game would load. A draft that is not
   game-loadable (no spawn, or no ring) is refused with the two blocking problems of `MapStatus`, and nothing else is read.
   Rejected: converting the in-memory `MapDraft` to a `MapDefinition` in setup. It would be a second conversion, and the
   game-loadable file is the one the builder can check.

3. **A flight starts only when the stored copy equals the session's copy.** This is the save guard applied to a read: the
   flight reads the stored file and compares it with the session's draft by the canonical JSON that `DraftStore` already uses.
   A difference refuses with `DraftChangedException`'s message, and nothing starts. Rejected: flying the session's copy
   without a check, because a second setup server could have changed the file, and the flight would test a course that the
   game would not load.

4. **Per-builder state: Editing or Flying, with the claim held in both.** `BuilderSessions` already owns one open map per
   builder and its claim. A flight is a field of the builder's `MapSession`, and it is `null` while the builder edits. The claim
   is not released and not taken by a flight. The draft and the history are not touched by a flight.

5. **Edit commands are refused while flying.** While a flight is running, ring placement and removal (`WandListener`), `/map
   spawn`, `/map undo` and `/map redo` reply that the flight must be stopped first, and change nothing. Rejected: letting an edit
   run during a flight, because the builder would test a course that is changing under the flight, and an undo would replace the
   course mid-run.

6. **Ending a flight ends with the session.** `/map fly stop` ends the flight and returns the builder to editing. A flight also
   ends when the builder runs `/map close`, opens another map, disconnects, or an administrator's `/map unlock` ends the
   session. Each of those paths already runs through `BuilderSessions`, so the flight is cleared where the session is cleared.
   The claim follows the existing rules of `single-builder-map-claims`: close, open and disconnect release it, and an unlock
   releases it whoever holds it.

7. **Flight items are placed as the flight's own, and are removed at the next entry.** The elytra and the rockets are given at
   the start, and each is marked as a flight item. They are removed on `/map fly stop`, on a close, and when a builder next enters
   the setup server, whether or not a flight was running. This covers a disconnect in the middle of a flight, when no code runs
   at the leave. The wand and the creative mode come back on `/map fly stop` through the existing `Wand.giveIfMissing` and the
   editing setup.

8. **The game mode is the racers' game mode, and creative flight is off during a flight.** A builder in creative mode can fly
   without an elytra, so the flight sets the mode the racers use. Which mode that is (survival or adventure) is a spike, task
   0.1, because it is set where `Racers` sets the racers' state.

9. **Ticks drive the flight, not the clock.** The adapter updates each flying builder once per server tick, reads the builder's
   position and its gliding state, and calls `tick`. Tests drive ticks with `env.tick()` and script the positions. No test reads
   the wall clock.

10. **Nothing is recorded.** A flight writes no file, takes no claim, adds no history entry, and calls no `RaceRecorder`. The
    setup server has no statistics dependency, so this holds by the module graph as well as by the code (`PersistenceBoundaryTest`).

11. **The reply names the known gap.** On start, the reply says that firework boost, boost rings and ring effects are not
    applied in a test flight, so a builder reads the time of a boost course with that in mind.

12. **Messages.** New keys go into `SetupMessages` (`platform.text`) and both bundles, German in the informal "du". The medal text
    reuses `Messages.medal(MedalTier)`, so a medal reads the same in a race and in a flight.

## What happens to the claim, the undo history and an unlock

| Event during a flight | Flight | Claim | Draft and history |
|---|---|---|---|
| `/map fly stop` | ends | kept | unchanged; undo still works after the flight |
| `/map undo`, `/map redo`, spawn, ring placement or removal | refused, nothing changes | kept | unchanged |
| `/map close` | ends with the session | released | unchanged; the history is dropped with the session, as today |
| `/map open <other>` | ends with the session | moves to the other map | unchanged |
| Disconnect | ends with the session | released | unchanged |
| `/map unlock <id>` by an administrator | ends with the session | released whoever holds it | unchanged; the holder moves to the lobby |
| `/map publish <id>` (publish-map-to-game) | continues | kept | the publish reads the stored file, which the flight does not change |

## Dependency direction

| Type | Module | Package | Note |
|---|---|---|---|
| `TestFlight` (pure state machine), `FlightTick` (the record of one tick), `FlightResult` (time, rings, medal) | `voyager-setup` | `net.elytrarace.voyager.setup.testflight` | imports `api`, `race` and the JDK only |
| `FlightGuard` (pure: the blocking problems, the changed-on-disk test, the already-flying case) | `voyager-setup` | `net.elytrarace.voyager.setup.testflight` | imports `api` and the JDK only |
| `FlightSession` (pure: one builder's current `TestFlight`, or none, held by `MapSession`) | `voyager-setup` | `net.elytrarace.voyager.setup.testflight` | no Minestom type, so `MapSession` gains a field of a pure type and no freeze entry |
| `FlightCommand`, `FlightTicker`, `FlightItems` (Minestom wrappers) | `voyager-setup` | `net.elytrarace.voyager.setup.inject` | the r6 exemption of the composition root; they render replies and read the world |
| `MapDefinitionAdapter` (existing, read only) | `voyager-platform` | `net.elytrarace.voyager.platform.catalog.adapter` | the map file reader, unchanged |
| `GuideLineRenderer`, `RaceTimeFormat`, `Messages.medal` (existing, read only) | `voyager-platform` | `net.elytrarace.voyager.platform.render`, `platform.text` | presentation, unchanged |
| `RaceRun`, `RingProgress`, `RaceClock`, `MapScorer`, `MedalBrackets`, `MedalTier` (existing, read only) | `voyager-race`, `voyager-api` | `net.elytrarace.voyager.race.run`, `race.flow`, `race.scoring`, `api.race` | the pure rules, unchanged |

Dependency direction: `setup.testflight` depends inward on `race` and `api`. The adapter depends on the pure flight. Nothing in
`race`, `api` or `platform` depends on the flight. No new edge between two slices is added, because `setup` already depends on
`platform`, and `platform` already depends on `race`.

### Fitness rules

- New rule (test-only, in `voyager-fitness`): classes in `net.elytrarace.voyager.setup.testflight..` may depend on
  `net.elytrarace.voyager.api..`, `net.elytrarace.voyager.race..` and the JDK only. The rule uses `allowEmptyShould(false)`, and
  it matches the flight's classes, so it cannot pass by matching nothing. It guards the rule that the flight stays a rule
  adapter.
- Covered by existing rules: `PersistenceBoundaryTest` (setup does not reach persistence, so a flight cannot record), the
  Minestom placement rule (the wrappers sit in `setup.inject`, so no freeze entry is added), `PermissionBoundaryTest`
  (`FlightCommand` asks the policy for `voyager.setup.use`), `NullabilityConventionTest` (a `package-info.java` for
  `setup.testflight`), `DesignRuleTest` (no exception is added), and `MessageBundleParityTest`.
- `SliceBoundaryRulesTest.r6_setupAdapterDoesNotUseMinestom` stays frozen and unchanged, and no freeze entry is added. A
  Minestom class in `setup.adapter` would be a new violation and stops the apply.

## Testing

Every test follows F.I.R.S.T. and Red/Green TDD. Each test builds its own `Env` (for the Minestom tests) or plain objects (for the
pure tests). No sleeps, no wall-clock reads, no shared static state. Positions are scripted in each test as a fixed list of `Vec3`.

- Pure (plain JUnit, `setup.testflight`):
  - `TestFlightTest`: the rings are passed in order, a ring out of order is not counted, the finish gives the time from the
    clock, a restart begins at the spawn with an empty progress, and the rings passed out of the count are reported.
  - `FlightResultTest`: the medal tier of a time against the map's brackets, and the time's format.
  - `FlightGuardTest`: a draft with no spawn and a draft with no ring are each refused with their problem, a stored copy that
    differs from the session's copy is refused, and a flight that is already running is restarted rather than refused.
- Minestom (`@EnvTest`, `env.tick()`):
  - `FlightCommandTest`: a builder without `voyager.setup.use` is refused; a builder with a blocking draft is refused naming the
    problem; a builder with a valid draft starts a flight with the flight items and the reply that names the known gap.
  - `FlightEditRefusalTest`: while flying, ring placement, removal, spawn, undo and redo change nothing and reply the stop message.
  - `FlightStopTest`: `/map fly stop` restores creative mode, removes the flight items and gives the wand back, with the
    draft and history unchanged.
  - `FlightEndsWithSessionTest`: close, open of another map, disconnect and unlock each end a flight and leave no flight item.
  - `FlightRecordsNothingTest`: the draft file's bytes and the history are the same after a flight, and no claim is taken or
    released by the flight itself.
- Architecture: the new rule in `voyager-fitness` (above).
- Messages: `MessageBundleParityTest`.

## ADR-0049 (proposed): A test flight reuses the race rules in-process and leaves the cup machinery out

Status: **Proposed**. It is accepted only when the owner accepts it. The text moves to
`docs/decisions/0049-test-flight-on-race-rules.md` in the code pull request, with the status the owner gives it.

Number: 0049 follows the number of this proposal's sibling, `publish-map-to-game` (0048). Both numbers are provisional until the
proposal's check against `origin/main`.

### Context and problem statement

A builder needs to fly a saved course before it is published, with the rings, the guide line, the time and the medals the game
shows. The game's run rules are pure and shared. The cup's orchestration (lobby, rotation, results, hub hand-off) is not needed
for one flight, and it cannot be used from the setup server without the setup server taking on the game's lifecycle.

### Decision drivers

- A flight tests the rules the racers face, not a copy of them.
- The setup server gains no game lifecycle and no statistics dependency.
- A flight writes nothing.

### Considered options

1. **The pure race rules in a setup-owned flight (chosen).**
2. **The cup machinery in-process, with a cup of one map.** Rejected: it brings the lobby, the rotation and the results into setup.
3. **A simulation written for setup.** Rejected: a second copy of the rule drifts.

### Decision outcome

Option 1. It gives the same rules with one shared implementation, and it keeps the setup server's dependencies as they are.

### Positive consequences

- Ring order, finish, time and medal are one implementation, shared with the game.
- A flight is small to test with scripted positions.
- The setup server gains no lifecycle.

### Negative consequences / accepted trade-offs

- The flight does not run the cup's orchestration (GO hold, glide-out, rotation).
- Boost rings, boost bursts and ring effects are not applied in version 1, and the builder is told so.

### Confirmation

`TestFlightTest` pins the rules through the race's own `RaceRun`. The architecture rule pins the dependencies of the flight.

## Risks

| Risk | Impact | Response |
|---|---|---|
| The flight and the race diverge when a race rule changes | A builder's time differs from a racer's | The flight calls the same `RaceRun` and `MapScorer`; a race rule change changes both by construction |
| A boost course is timed without boosts | A builder reads a slow time | The reply names the gap; owner question 1 decides whether to add it |
| The game mode of a flight differs from the racers' | Flight timing differs | Spike 0.1 reads the racers' mode from `Racers` |
| A disconnect mid-flight leaves flight items in the inventory | A builder keeps an elytra in creative | Decision 7: items are removed at the next entry to the setup server |

## Open questions

1. **Boost and ring effects in version 1?** The recommendation is no, with the reply that names the gap. Owner decision: it changes
   the timing of any boost course, and adding it means applying the boost tracker and the effects in the flight.
2. **Command names.** `/map fly` and `/map fly stop`. The alternatives are `/map test` and `/map test stop`. Owner decision.
3. **Game mode during a flight.** Spike 0.1 reads it from `Racers`. Owner confirms if the racers' mode should differ.
4. **Result sharing.** The recommendation is that a flight keeps no best time, even in the session. Pulse and Drift review the
   feedback of the result (when it shows, what it says, how a builder leaves); this design fixes what the reply carries and does not
   decide the flow.
5. **Flights during a publish.** The recommendation is that a flight does not block a publish, because a publish reads the stored
   file and a flight changes nothing.
