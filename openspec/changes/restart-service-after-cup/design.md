# Design

## Context

### Facts the design relies on (read from the code on `main`, 8461a4e)

- A cup's end is handled in `WaitingRoom.tick`. When a cup that was running has finished (`session.cupFinished()`), `lingerTicks`
  starts at 0 and counts to `RESULTS_LINGER_TICKS` (200 ticks, 10 s). At 200, `advanceLinger` calls `handOff(racers())`, which
  sends every racer online to `HubHandoff`, and then `rotation.armNext()`, which writes the armed cup through the store.
- While the linger runs, the start verdict is `HOLD`, so no countdown starts and no racer is aborted. Hence a cup that finishes
  with nobody online still reaches the hand-off with an empty list (`handOff` returns early on an empty list).
- The finale (`CupFinale`, 80 ticks) starts on the tick the cup ends (`CupSession.advancePhase`), so it runs inside the linger's
  200 ticks and ends before the linger does.
- `ServiceShutdown.request()` runs the one clean shutdown: `MinecraftServer.stopCleanly()` and `System.exit(0)`, on a platform
  thread. The `stop` line on standard input and `/stop` both use it.
- `CupRotationStore.write` returns nothing. `FileCupRotationStore` logs a failed write as one WARN and carries on, so the
  caller cannot tell from the return value whether the state was written.
- `ServerSettings.KEYS` is the list the loader view and the configuration check read. `CupRotationSettings` shows the pattern:
  its own record, its keys listed in `KEYS`, and its problems merged into `ConfigCheck.settingsProblems`.
- The platform slices are frozen for cycles (`SliceBoundaryRulesTest`, `platform.(*)..`). `platform.handoff` and `platform.lobby`
  already depend on each other's neighbours without a cycle, and `WaitingRoom` already imports `platform.handoff`.

### Live facts (from `rotate-cups-per-round`, checked read-only on mc01 at that time)

- The task `ElytraRace` is dynamic, `autoDeleteOnStop: true`, `minServiceCount: 1`, heap 1024 MB, one node. Every stop wipes the
  service directory; the next start is a fresh copy of the template. A node-local state directory survives stops and does not
  exist yet.

## Goals and non-goals

Goals: a fresh service after each finished cup, with the next cup armed; no racer kicked on the normal path; no cup starting while
the restart is pending; default behaviour unchanged.

Non-goals: restarting after an aborted cup (open question in the proposal); changing the GO or lobby start path; changing the
bridge, the hand-off port or the CloudNet task; a new player-facing message.

## Decisions

### D1. The setting is `VOYAGER_RESTART_AFTER_CUP`, parsed by its own record

`RestartSettings` (in `voyager/server` `config`, next to `CupRotationSettings`) holds one boolean and the key. It has a static
`from(Map<String,String>)` and a static `problems(Map<String,String>, Function<String,String>)`.

- Unset or blank is off. `true` and `false` are accepted case-insensitively. Any other value is an ERROR under the key, because
  `Boolean.parseBoolean` would read a typo such as `ture` as off, and a silently missing restart is the failure to avoid.
- An ERROR when the restart is on, `VOYAGER_CUP_ROTATION` lists more than one cup, and `VOYAGER_CUP_ROTATION_STATE` is not set.
  Each restart would begin at the first cup, so the rotation would never reach the next cup.
- The key is added to `ServerSettings.KEYS`, which the loader view and the check's key list (`VoyagerServer.CHECK_KEYS`) are built
  from. `ConfigCheck.settingsProblems` adds `RestartSettings.problems`.
- It is not a component of the `ServerSettings` record. That record has several constructor overloads, and a component would change
  every one of them and the test call sites without adding anything the restart needs.

### D2. The trigger is the existing hand-off point

The restart is requested inside `WaitingRoom.advanceLinger`, after `handOff` and `armNext`, on the tick the linger reaches
`RESULTS_LINGER_TICKS`. The finale finishes inside the linger, so the restart is after the finale and needs no timer of its own.
The brief asked to reuse the finale's existing duration; the finale's 80 ticks are already covered by the 200-tick linger that gates
the hand-off, so no second wait is added.

### D3. The order is hand-off, arm, verify, drain, stop

1. `handOff(racers())`: every racer online goes to `HubHandoff`. A refused move disconnects that racer with the translated
   `handoffDisconnect` message and a WARN, as today.
2. `rotation.armNext()`: the next cup is armed and `store.write(name)` is called. This is synchronous.
3. `CupRestart.request(finished, next, keepsState)`: when the rotation has more than one cup (`keepsState`), it reads the store
   back and compares the name with `rotation.armedName()`. A mismatch refuses the restart: one ERROR names the state file, no stop
   is requested, and the server keeps running with the armed cup in memory, as it does today. A match accepts the restart and logs
   the one INFO line.
4. `CupRestart.tick(connected)` on every server tick after `waitingRoom.tick()`: it returns true once, when `connected` is 0, or
   when `DRAIN_TICKS` (100) ticks have passed with someone still connected (one WARN then names the count).
5. On true, the existing `ServiceShutdown.request()` runs the clean stop with exit status 0.

Without a rotation of more than one cup there is no state to verify, and step 3 accepts at once.

### D4. The drain counts all connected players, not the hand-off list

While the restart is pending every connected player is either a racer handed away or a joiner handed away at once (D5), so
"no player connected" is the same as "every handed-off racer has left". Counting all connected players needs no bookkeeping of
the hand-off list in `CupRestart`, and it covers an empty server (D6).

### D5. Joins while the restart is pending are handed away at once

`WaitingRoom.joined` checks `restart.requested()` first. A joiner is sent through `HubHandoff` at once and added to `handedOff`,
so `racers()` never counts them and `StartGate` never sees a racer to start a cup with. Nothing is shown to the joiner except what
`HubHandoff` already does. A joiner whose move is refused is disconnected with the existing translated message.

### D6. An empty server restarts

`advanceLinger` does not look at the number of racers before it requests the restart. With nobody online, `handOff` is a no-op, the
state is written, and the drain ends on the first tick, because `connected` is 0.

### D7. Aborted cups do not restart

The linger starts only when `session.cupFinished()` is true, so an aborted cup never reaches `advanceLinger`. This is the owner's
wording. The open question in the proposal is the only way this changes.

### D8. The composition root builds the restart and owns the stop

- `CupRestart` is built in `VoyagerServer.main` from `RestartSettings` and the rotation store, as the store is. It is passed to the
  graph through a new `openGraph` overload as a bean. The existing four-argument `openGraph` delegates with `CupRestart.disabled()`,
  so the tests that call it are unchanged.
- `ServerBeans.waitingRoom` takes `CupRestart` as a parameter. `WaitingRoom` gets a constructor that takes it; the existing
  eleven-argument constructor delegates with `CupRestart.disabled()`, so `WaitingRoomTest` and the golden-master tests keep their
  call sites.
- `ServiceShutdown` is built before the tick task is scheduled, so `scheduleTick` can call `shutdown.request()` once
  `restart.tick` returns true. The stop action itself is unchanged.
- One INFO line at boot says the restart is on, so an operator can see the setting took effect. It is not the restart reason.

### D9. `CupRestart` lives in `platform.handoff`

`CupRestart` is the restart's state machine: `disabled()`, `after(CupRotationStore)`, `requested()`,
`request(String finishedCup, String nextCup, boolean keepsState)`, and `tick(int connected)`. When `keepsState` is true (a rotation of
more than one cup), `request` reads the store back and accepts only when it reads `nextCup`; the INFO line names both cups. It names no Minestom type and no CloudNet type and has no
clock: its drain is counted in ticks, as the linger is. It depends on `race.rotation.CupRotationStore` only, which platform may do.

It is placed in `platform.handoff`, not `platform.lifecycle`, because `WaitingRoom` already imports `platform.handoff` and the
restart is part of the hand-off. That adds no new edge between platform slices, so the frozen cycle store is not touched.

## Dependency direction (Clean Architecture)

```
voyager/server  (composition root: VoyagerServer, ServerBeans, RestartSettings, ConfigCheck)
   |  builds CupRestart, passes it to WaitingRoom, calls ServiceShutdown.request()
   v
voyager/platform  platform.lobby.WaitingRoom  ->  platform.handoff.CupRestart (new)
                                             ->  platform.handoff.HubHandoff (existing)
                                             ->  platform.rotation.CupRotationDriver (existing)
   |
   v
voyager/race  race.rotation.CupRotationStore (port, unchanged)
```

No arrow points from `race` or `platform` into `server`. No new Minestom or CloudNet import: `CupRestart` and `RestartSettings`
are plain Java with slf4j, and `WaitingRoom` already imports Minestom.

## Module map

| New or changed | Module and package | Kind |
|---|---|---|
| `CupRestart` | `voyager/platform` `platform.handoff` | new class (final), no package change |
| `WaitingRoom` | `voyager/platform` `platform.lobby` | constructor overload, two hooks in `joined` and `advanceLinger` |
| `RestartSettings` | `voyager/server` `server.config` | new record |
| `ConfigCheck`, `ServerSettings` | `voyager/server` `server.config` | one call and one key added |
| `VoyagerServer`, `ServerBeans` | `voyager/server` `server` and `server.inject` | wiring; `ServiceShutdown` moves above `scheduleTick` |
| `docs/decisions/0047-restart-service-after-cup.md` | `docs` | MADR, Proposed |
| `docs/reference/configuration.md`, `docs/reference/config-check.md`, `docs/guides/how-to-deploy-on-cloudnet.md`, `docs/deployment/README.md` (if it names CloudNet keys) | `docs` | key rows and the restart section |

`WaitingRoom.tick` and the start verdict are not changed, which is what keeps this change out of the GO path.

## Fitness

- No new package, so `FitnessCoverageTest` and the package-info rules are unaffected.
- No new platform slice edge, so the frozen slice store is not touched. The task list checks this with the fitness run, and an
  added entry in any store stops the change for a report, per the apply rules.
- `HandoffBoundaryTest` is unaffected: `CupRestart` installs no handoff and imports no `api.handoff` type.
- No new player-facing text, so the message bundle parity and style tests are unaffected. The translated `handoffDisconnect` message
  already exists in both `voyager_en_US` and `voyager_de_DE`.

## Test plan (F.I.R.S.T.)

- Every test has a fresh fixture: an in-memory `CupRotationStore` fake, a recording hub, a fresh `CupRestart`, a fresh `WaitingRoom`.
  No file is written, so no `@TempDir` is needed for these tests; `FileCupRotationStore` is not involved.
- Time is driven by explicit `tick` calls, never by a sleep or a clock.
- Log lines that matter (the INFO reason, the ERROR for a state that did not read back, the WARN for the drain limit) are asserted
  through the existing `LogCapture` appender, never by reading console output.
- Red comes first for each behaviour (D1 to D8), and each Red run's failure message is recorded in the code PR description.

## Risks

- A stop races a hand-off that has not reached the proxy. The drain (D3, step 4) is the mitigation; 100 ticks is a judgement that
  the owner can change in the code, and the smoke run cannot show it.
- A state file that cannot be written leaves the server up with no racers, which is what happens today. The ERROR says so.
- `WaitingRoom` is shared with the GO change. The edits here are confined to the constructor, `joined` and `advanceLinger`; a
  conflict in `tick` is resolved by keeping the other branch's version and re-applying the two hooks.
- The live rollout needs the state directory on mc01 (an operator action) before the rotation's second cup is in play.
