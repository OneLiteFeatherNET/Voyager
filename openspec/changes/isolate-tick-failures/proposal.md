# Proposal

**Type and scope:** `fix(server)`. Ships as one code change under this title. It changes what happens when one tick of the
race loop throws: only the cup that threw stops, and the lobby and the statistics replies keep running.

**Depends on:** nothing.

## Why

The server runs one scheduled task on every game tick. It runs the cup's tick, the waiting room, the restart hand-off and the
statistics replies inside one guard. The guard is a local `AtomicBoolean` named `broken`, set on the first `RuntimeException`
and never cleared (`voyager/server/src/main/java/net/elytrarace/voyager/server/VoyagerServer.java:591-613`, flag at 593).
After the first failure every later tick returns at the top of the task, so:

- the cup stays stopped,
- the waiting room no longer moves players between lobby and cup,
- the restart hand-off no longer drains its racers,
- `/stats` replies never arrive,

until the process is restarted. One rare exception in one map ends the game for everyone on the server. The guard's own comment
(`VoyagerServer.java:585-590`) justifies stopping the cup, but not stopping the rest.

The `/race` command text says a cup can be restarted (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/cup/CupSession.java:141-142`:
"`/race start` restarts it on demand"). That is impossible while the guard is set, so the operator's only remedy is a restart.

## What Changes

- **Cup isolation.** A throw in the cup's tick stops that cup, logs the failure once with the cup's state, and nothing else.
- **Lobby and statistics outside the cup's guard.** The waiting room and the statistics replies run on every tick, also after a
  cup has stopped on a failure.
- **Guard reset at the next round.** The stopped state is cleared when the next cup round starts (`/race start`). The next round
  starts from validated cup state rather than from whatever the failing tick left behind.
- The guard's state is held in a small class inside `voyager-server` (the composition root), so the rule can be tested with
  explicit ticks and no Minestom scheduler.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `server/service-lifecycle`: ADDED requirements for tick-failure isolation. The existing requirements (bind address, stdin stop,
  permissions) are unchanged.

## Trade-offs

### Pros

- A rare exception in one cup no longer ends the game for every player; the lobby, the stats replies and the restart hand-off
  keep working.
- `/race start` works after a failure, as the `/race` text already promises.
- Small and testable: the tick-guard class takes its steps as functions and is driven by a test one tick at a time.

### Cons

- After a throw the cup's state may be inconsistent. The next round must validate it, which adds a check that can itself fail.
- One more guard state (per cup, not per server), so two states must be reasoned about: the cup's and the server's.
- It touches the tick path, which every race depends on. A mistake here affects every tick, so the change needs the full test
  run before merge.

## Out of scope

- Fixing the exception itself. Each throw that the owner sees in the log is a separate defect.
- The race loop's own error handling inside `CupSession` (per-racer exceptions are not caught here).
- Changing the 20 TPS scheduling or moving the tick to another thread.

## Open questions for the owner

- Should a stopped cup be restarted automatically at the next round, or only by `/race start`? This proposal assumes the
  latter, which keeps the operator in control.
