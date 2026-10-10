# ADR-0022: A cup waits in the first map's world and its countdown is the cup's first lobby

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. The change that
implements it is `add-waiting-lobby`, which does not mark this record Accepted.

## Date

2026-10-10

## Decision makers

- TheMeinerLP (project owner). Decided on 2026-10-10 the defaults of the change: the minimum is 2 in production and 1
  under `voyager.dev`, `/race start` stays dev-only, and late joiners are neither rejected nor spectated.
- Drafted with the change `add-waiting-lobby`, from issue #170 (minimum player count, waiting state, late joiners) and
  issue #101 (the lobby countdown shown in ticks).

## Context and problem statement

The first player to join started the cup (`session.start(false)` in the join handler). A single tester therefore ran a
whole rotation alone, a second player who arrived later landed in the middle of a cup and waited for the next map, and
nothing told a waiting racer why. The fixed 20 second lobby in `RaceTimings` was documented as a stopgap whose correct
shape is "wait for players".

The rebuild needs a waiting state that starts the cup once enough racers are online, cancels a countdown when racers
leave early, and commits it near the launch. It must not change the per-tick behaviour of a started cup, which the
Golden Master pins tick by tick.

## Decision drivers

- A cup starts only when enough racers are online, and the minimum is configurable per run mode.
- The waiting state needs no new asset: no second world and no catalogue entry that is not a map.
- The start countdown reuses the existing three-digit countdown, which lives in the cup's first lobby.
- The cup's per-tick order and the golden transcripts stay byte-identical.
- The pure rule is testable without a server, and the Minestom glue sits in a platform slice.

## Considered options

1. **Keep the start on first join.** Rejected: one tester runs a whole rotation alone, and late joiners have no defined
   outcome.
2. **A dedicated lobby world for waiting racers.** Rejected: it needs a new asset and a catalogue entry that is not a
   map, for one room. A generated flat world is a second world nobody asked for.
3. **Waiting racers stand at the first map's spawn, and the countdown is the cup's own first lobby, driven by a gate
   outside the tick pipeline (chosen).** The gate decides when to call `CupSession.start(false)`, cancel the countdown or
   abort the cup. It is not a step of `TickPipeline`.
4. **A pausable `LOBBY` inside `RaceStateMachine`.** Rejected: it changes a pure, tested state machine and its
   transition table for a rule that lives above it, and it risks the golden timing.
5. **The gate counts its own countdown and calls `start(true)` when it ends.** Rejected: `start(true)` skips the lobby,
   so the three-digit countdown is lost or the lobby is counted twice.

## Decision outcome

Chosen option: **3**.

- **D1, no lobby world.** Waiting racers stand at the first map's spawn of the current catalogue. A racer who joins a
  running cup stands there too and enters the next map, as before.
- **D2, the countdown is the cup's own lobby.** When the minimum is met the cup starts, and its first `LOBBY` (20 s in
  production, 10 s under dev mode) runs with the existing three-digit countdown. While more than three seconds of that
  lobby remain, a drop below the minimum cancels it. In its last three seconds the start is committed.
- **Gate outside the tick pipeline.** `race.flow.StartGate` is a pure decision table. `platform.lobby.WaitingRoom` runs
  it after `TickPipeline.run()` in the same guarded task. The pipeline keeps its three steps, which a test asserts.

The rule for the situations, and the rules that the table does not carry (an operator start with nobody online, a
finished cup returning to waiting), are written in `openspec/changes/add-waiting-lobby/design.md`, decisions D3 and D5.

## Consequences

- Good: a single tester in production waits for a second racer instead of running a rotation alone. The countdown is
  shown in seconds, which closes the unit error of issue #101 for the rebuild.
- Good: the golden transcripts stay byte-identical, because `CupSession.start(boolean)` keeps its meaning and the gate
  sits outside the pipeline.
- Bad, breaking: a production server with a single player no longer starts a cup on its own. Operators set
  `VOYAGER_MIN_PLAYERS=1`, or use `/race start`, to restore the old start.
- Bad: a racer who drops and rejoins during the countdown cancels it, and the countdown restarts from its full length.
  A grace period is a separate decision.
- Neutral: the per-map time limit of issue #170 is not built here. It depends on time-bracket scoring (ADR-0003) and is
  a separate change.

## Confirmation

- `StartGateTest` tests the eight rows of the decision table, the minimum and the commit window.
- `CupSessionLobbyTest` and `WaitingRoomTest` drive the cup with explicit ticks and check each transition on both sides
  of the commit boundary.
- `TickPipelineOrderTest` asserts the three steps and their order, so the gate cannot enter the pipeline unnoticed.
- `CupSessionGoldenMasterTest` stays byte-identical.

## Pros and cons of the options

### Option 3, chosen

- Good: no new asset, one gate, a pure and tested rule, and the golden timing is untouched.
- Bad: a reconnect within the countdown restarts it, and the gate is one more per-tick adapter to keep in step with the
  cup.

### Option 4, a pausable lobby in the state machine

- Good: one state machine owns every phase.
- Bad: the state machine would need a notion of racers online, which is not a phase concern, and its transition table
  would change under the golden timing.

## More information

- Issue #170: minimum player count, waiting state, late joiners and DNF.
- Issue #101: the lobby countdown was shown in ticks as if it were seconds.
- `docs/reference/config-check.md`: the `VOYAGER_MIN_PLAYERS` setting and its check.
- `docs/guides/how-to-run-a-playtest.md`: how to run a playtest with N racers.
