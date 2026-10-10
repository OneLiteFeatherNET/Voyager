# Design

## Context

See proposal.md, "Why", for the motivation. The current state that shapes the approach:

- `VoyagerServer` starts the cup from the `PlayerSpawnEvent` handler on the first join (`session.start(false)`). A
  joiner into a running cup is only told `joined.midcup`.
- `CupSession.start(boolean)` is the one primitive that begins a cup. It pins the catalogue, resets the round, and
  creates an `XerusPhaseDriver` whose first phase is `LOBBY` with `RaceTimings.lobby()` (20 s, 10 s under dev mode).
  `StartCountdown` shows the three digits in the last three seconds of that lobby and enters the first map then.
- `TickPipeline` is three steps (flight sample, boost burn, phase advance), built in `CupBeans`.
- The Golden Master (`CupSessionGoldenMasterTest`) drives `CupSession.start(false)` and the pipeline directly, with
  `Env` ticks. It never fires `PlayerSpawnEvent`. Its four transcripts start with `## start` and contain no armed text.
- `RaceStateMachine` is pure and has no unbounded phase. `RaceTimings.lobby` is a fixed `Duration`, and its javadoc
  calls it a stopgap whose correct shape is "wait for players".
- `Racers.prepare` (on first spawn) sets adventure mode, invulnerability, the elytra and the rocket stack.
  `Racers.standDown` only clears flight and sends zero velocity.
- `MapScorer` already scores a racer who has not finished a map as `MedalTier.DNF`, with ring points only.
- The tree being replaced has `MinestomLobbyPhase` (a `TimedPhase` counting down in ticks and firing a callback).
  It is behavioural reference only. Its timer unit is the source of issue #101.

## Goals / Non-Goals

**Goals:** a cup starts only when enough racers are online; the countdown is cancelled below the minimum until it is
committed; waiting racers cannot fly; the Golden Master transcripts stay byte-identical; the pure rules are unit
tested without a server.

**Non-Goals:** a separate lobby world, a per-map time limit, spectator mode, changing the `RaceStateMachine` or the
cup's per-tick order, changing `RaceTimings` values, scoring changes, production exposure of `/race start`.

## Decisions

### D1. Racers wait in the first map's world at its spawn (no lobby world)

The join handler already spawns racers into `catalog.current().rotation().getFirst()`, and `VoyagerServer` already
says why there is no lobby world.

- *Alternatives:* a dedicated lobby world needs a new asset and a catalogue entry that is not a map, which is a schema
  change for one room. A generated flat world is the second world nobody asked for. Both rejected.
- *Consequence:* a joiner during a running cup also stands in the first map's world, not in the racing instance, which
  is the current behaviour.

### D2. The countdown is the cup's own first lobby; the gate sits outside the state machine

Waiting means the cup is not started. The start countdown is the cup's first `LOBBY` phase, driven by the existing
`XerusPhaseDriver`, with the existing three-digit countdown. The gate only decides *when to call* `start(false)` and
when to cancel or abort.

- *Alternative A:* the gate counts its own countdown and then calls `start(true)`. Rejected: it either loses the
  digits or double-counts the lobby, and `start(true)` skips the lobby.
- *Alternative B:* add a pausable `LOBBY` to `RaceStateMachine`. Rejected: it changes a pure, tested state machine
  and its transition table for a rule that lives above it, and risks the golden timing.
- *Chosen:* the gate is a separate per-tick adapter. `CupSession.start(boolean)` keeps its meaning, so the golden
  scenarios need no harness change.

### D3. The start rule is a pure decision table in voyager-race

`race.flow.StartGate` is an abstract utility class (design rule 2: `@ApiStatus.Internal`, private constructor,
`@Contract(pure = true)`). It owns the commit window (3 s, the length of the digit countdown), and the pure decision:

| Situation (from the platform) | Racers online | Verdict |
|---|---|---|
| `WAITING` (no cup running, or the last cup finished) | `>= minimum` | `START_CUP` |
| `WAITING` | `< minimum` | `HOLD` |
| `COUNTDOWN` (first map's lobby, no map entered, more than 3 s left) | `< minimum` | `CANCEL_COUNTDOWN` |
| `COUNTDOWN` | `>= minimum` | `HOLD` |
| `COMMITTED` (first map's lobby, at most 3 s left) | `0` | `ABORT_CUP` |
| `COMMITTED` | `>= 1` | `HOLD` |
| `RUNNING` (any other point of a started cup) | `0` | `ABORT_CUP` |
| `RUNNING` | `>= 1` | `HOLD` |

`minimum < 1` is an `IllegalArgumentException`. `ABORT_CUP` and `CANCEL_COUNTDOWN` both return the room to waiting.
The table is a parameterised JUnit test with no clock and no server.

`COUNTDOWN` requires that no map has been entered in this cup, so a `PRACTICE` cup's retry lobby (which returns to
`LOBBY` on the same map index) is `RUNNING` and never cancelled.

### D4. Glue lives in a new platform slice `platform.lobby`

`platform.lobby.WaitingRoom` holds the Minestom-side work: it reads the online racers through the same
`Supplier<Collection<Player>>` the cup uses, derives the situation from `CupSession`, asks `StartGate` for the verdict,
and acts on it: `start(false)`, `disarm()`, or `abort()`. It also sends the waiting and countdown actionbars and holds
or equips racers.

- `platform.cup` must not depend on `platform.lobby`. `WaitingRoom` depends on `platform.cup`, `platform.flight`,
  `platform.text` and `race.flow`. R4 (platform slices free of cycles) therefore stays satisfied.
- Platform infrastructure packages (`text`, `convert`, `world`, `tick`, `render`) gain no race dependency (R3).
  `Messages` new methods take `int` and `Duration`.
- No DI annotation. `ServerBeans` or `CupBeans` constructs the room.

### D5. Ticking: the gate runs after the cup pipeline, in the same guarded task

`VoyagerServer.scheduleTick` already runs `pipeline.run()` inside one guarded runnable, so one broken tick stops the
cup once. The gate is called after `pipeline.run()` inside that same runnable: `pipeline.run(); waitingRoom.tick();`.

- It is not a fourth `TickStep`. The pipeline's order is the cup's (`CupBeans`), and the golden harness builds the
  same three steps. Keeping the gate out of it is what keeps those transcripts identical.
- A test asserts that `TickPipeline` still has exactly the three step names, in order.

### D6. CupSession gains three narrow operations; its per-tick behaviour is unchanged

- `situation(Duration commitWindow)`: returns `StartGate.Situation`. `WAITING` when no driver runs, or the driver has
  finished the cup. `COUNTDOWN` / `COMMITTED` when the driver is in its first `LOBBY` (map index 0, `gameStarted`
  false) with `remaining` above or at most the window. `RUNNING` otherwise. This needs one private accessor on
  `XerusPhaseDriver` for the remaining lobby, which already exists as `remainingLobby`, and one `gameStarted` flag set
  in `mapStarted` and cleared in `start`.
- `disarm()`: legal only in `COUNTDOWN`. Finishes the driver, drops it, clears `currentMap`, `preparedMapIndex`,
  `countdown` and the block follow, and removes the racers' HUD. The next `start` pins the catalogue again, which is
  intended.
- `abort()`: legal in `COMMITTED` and `RUNNING`. Does what `stop()` does, and also drops the driver so that `describe()`
  reads as not started, forgets every run, clears the boosts, and stands every racer down. `stop()` is untouched: it is
  the failure path and keeps the state for `/race`.
- `start(boolean)` is unchanged. `describe()` keeps its running branches, and its armed branch is reworded to "armed,
  waiting for enough racers". No committed golden transcript contains the armed branch, so no golden file changes.

### D7. Waiting racers hold no flight equipment; equipment moves to map entry

`Racers.prepare` keeps adventure mode, invulnerability and the inventory clear, and loses the elytra and the rocket stack.
`Racers.equip` (new) gives the elytra and rockets, called from `faceCourse` at map entry. `Racers.hold` (new) removes
both, called on join while waiting, when a cup is aborted, and once when a cup finishes.

- *Why not keep the elytra and forbid flight:* racers could still glide off the spawn, and the waiting room would be a
  world with no rules. Removing the equipment is the rule that is visible to the player.
- *Overlap:* `Racers` is in the flight slice that `add-out-of-bounds-reset` touches. This change touches only
  `prepare`, `faceCourse` and two new static methods; it does not touch the tick, the run reset or `standDown`.

### D8. Settings: `VOYAGER_MIN_PLAYERS`, read as a system property like the other `VOYAGER_*` keys

`ServerSettings` gains a `minimumRacers` component (last), validated in its compact constructor (`>= 1`), with a default
of 1 under `voyager.dev` and 2 otherwise. A six-argument constructor keeps the defaults so that existing tests compile
unchanged. `ConfigCheck` parses the key without throwing and reports a non-number or a value below 1 with key
`VOYAGER_MIN_PLAYERS`, as the other settings do. Boot refuses through the same report.

### D9. Wiring, command and messages

- `ServerBeans.waitingRoom(CupSession, Supplier<Collection<Player>>, @External ServerSettings)` builds the room with
  `settings.minimumRacers()`. `RaceCommand` receives the room for the status line only.
- `/race start` stays registered in dev mode only, as today, and still calls `start(true)`, so it bypasses the gate.
  Production exposure is an open question.
- New keys in `voyager-server/src/main/resources/voyager_en_US.properties`, through `Messages` methods:
  `voyager.lobby.waiting` (actionbar: online of needed), `voyager.lobby.countdown` (actionbar: whole seconds, rounded
  up, the #101 fix), `voyager.lobby.cancelled` (chat: racers online of needed, when a countdown is cancelled). The
  existing `voyager.race.joined.midcup` is reused for a late joiner. `MessageBundleTest` already checks that every key
  is present.

## Risks / Trade-offs

- **[Reconnects restart the countdown]** A racer who drops and rejoins within the lobby cancels and restarts it, up to
  the full 20 s. → Accepted for this change. Mitigation would be a grace period, which is a separate decision (open
  question).
- **[Production needs two racers]** A single-player production server no longer starts on its own. → **BREAKING**,
  stated in the proposal. `VOYAGER_MIN_PLAYERS=1` or `/race start` restore the old behaviour.
- **[Golden drift]** Any change to the pipeline or to `start` would change the transcripts. → The gate is outside the
  pipeline; `start` is untouched; a test asserts the three step names; the golden files are not regenerated in this
  change. Any drift needs a dedicated commit with a stated reason.
- **[Catalogue re-pinned on every start]** A cancelled countdown and a new start pin the catalogue again, so a reload
  applied in between takes effect. → Matches the existing rule that a reload applies at the next round.
- **[Player count race]** A racer in the login phase may not be in the online set yet, so the first tick after a join
  can count one fewer. → The gate runs every tick and the countdown only starts on a tick that counts enough, so this
  delays the start by at most one tick.
- **[Waiting racers in the cup's world]** A mid-cup joiner stands in the first map's world, which may share an instance
  with a map that is racing (single-world cups). → Current behaviour; the joiner holds no run and is moved at the next
  map start.

## Migration Plan

No data migration. Deploy with the default minimum (2; 1 under dev mode). Rollback is a revert of the feature commit,
since no persisted state changes. Operators who need the old single-player start set `VOYAGER_MIN_PLAYERS=1`.

## Open Questions

- Should `/race start` be registered outside dev mode (behind `ReloadPermission`), so that an operator can start a
  production server below the minimum? Recommendation: yes, in a follow-up change with the operator-tools review. This
  change keeps dev-only registration, so no spec depends on the answer.
- Should a reconnect within the countdown keep the countdown rather than restart it? Deferrable: the answer changes
  one rule in `StartGate` and one scenario, not the structure.
- The per-map time limit of 150% of reference time (#170) is deferred. It depends on #168 time-bracket scoring and on
  a per-map race cap that `RaceStateMachine` does not have today.
