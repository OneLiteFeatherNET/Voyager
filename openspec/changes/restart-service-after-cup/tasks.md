# Tasks

Commit types: `feat(race)` for the restart, its settings, its wiring and their tests; `docs` for `docs/`. Each behaviour gets a
failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleep, no clock (the restart counts in
ticks), a fresh fixture per test, no shared static state, log lines asserted through `LogCapture`. The code pull request contains no
file under `openspec/`, and no task is ticked in it.

## 1. Red: the setting and its checks

- [ ] 1.1 Red: `RestartSettingsTest` "the restart is off when the key is unset or blank" (`feat(race)`)
- [ ] 1.2 Red: `RestartSettingsTest` "`true` in any case turns the restart on, and `false` keeps it off" (`feat(race)`)
- [ ] 1.3 Red: `RestartSettingsTest` "any other value is one error that names the key" (`feat(race)`)
- [ ] 1.4 Red: `RestartSettingsTest` "the restart with two cups and no state file is one error that names both keys" (`feat(race)`)
- [ ] 1.5 Red: `RestartSettingsTest` "the restart with one cup needs no state file" (`feat(race)`)
- [ ] 1.6 Red: `ConfigCheckRunTest` "check mode exits 1 for an unrecognised restart value and for a rotation without a state file" (`feat(race)`)

## 2. Green: the setting and its checks

- [ ] 2.1 Green: `RestartSettings` record in `voyager/server` `config`, with `VOYAGER_RESTART_AFTER_CUP` added to `ServerSettings.KEYS`
  and `RestartSettings.problems` called from `ConfigCheck.settingsProblems` (`feat(race)`)

## 3. Red: the restart's state machine

- [ ] 3.1 Red: `CupRestartTest` "a disabled restart is never requested and never asks for a stop" (`feat(race)`)
- [ ] 3.2 Red: `CupRestartTest` "a restart with one cup is accepted without a state read, and logs one INFO line that names the cup" (`feat(race)`)
- [ ] 3.3 Red: `CupRestartTest` "a restart with a rotation is accepted when the store reads back the next cup" (`feat(race)`)
- [ ] 3.4 Red: `CupRestartTest` "a state that does not read back refuses the restart, logs one ERROR that names the file, and never asks for a stop" (`feat(race)`)
- [ ] 3.5 Red: `CupRestartTest` "the stop is asked for once, when no racer is connected" (`feat(race)`)
- [ ] 3.6 Red: `CupRestartTest` "the stop waits while a racer is connected, and is asked for on the 100th tick with one WARN that names the count" (`feat(race)`)
- [ ] 3.7 Red: `CupRestartTest` "a second request after an accepted one changes nothing and logs nothing" (`feat(race)`)

## 4. Green: the state machine

- [ ] 4.1 Green: `CupRestart` in `voyager/platform` `platform.handoff`: `disabled()`, `after(CupRotationStore)`, `requested()`,
  `request(finishedCup, nextCup, keepsState)`, `tick(connected)`; no Minestom or CloudNet import; `package-info` already present (`feat(race)`)

## 5. Red: the room's end of a cup

- [ ] 5.1 Red: `WaitingRoomRestartTest` "a finished cup hands every racer to the hub before it arms the next cup and requests the restart" (`feat(race)`)
- [ ] 5.2 Red: `WaitingRoomRestartTest` "a finished cup with nobody online still requests the restart" (`feat(race)`)
- [ ] 5.3 Red: `WaitingRoomRestartTest` "an aborted cup requests no restart" (`feat(race)`)
- [ ] 5.4 Red: `WaitingRoomRestartTest` "a player who joins while the restart is pending is handed to the hub, is not counted, and starts no cup" (`feat(race)`)
- [ ] 5.5 Red: `WaitingRoomRestartTest` "with the restart off, a finished cup requests nothing and the room behaves as before" (`feat(race)`)
- [ ] 5.6 Red: run 1.1 to 5.5 and confirm each fails for the stated reason; record the messages in the code PR description (`feat(race)`)

## 6. Green: the room's hooks

- [ ] 6.1 Green: `WaitingRoom` gets a constructor that takes `CupRestart`; the eleven-argument constructor delegates with
  `CupRestart.disabled()`. `advanceLinger` calls `restart.request` after `handOff` and `armNext`; `joined` hands a joiner away while
  `restart.requested()` (`feat(race)`)

## 7. Red then Green: the composition root

- [ ] 7.1 Red: `RestartWiringTest` "a graph built with an enabled restart gives the room that restart, and a graph built without one gives it the disabled restart" (`feat(race)`)
- [ ] 7.2 Green: `VoyagerServer.main` reads `RestartSettings`, builds `CupRestart` from the store, passes it to `openGraph` (a new
  overload; the four-argument overload delegates with `CupRestart.disabled()`), and logs the one boot INFO line; `ServerBeans.waitingRoom`
  takes `CupRestart`; `ServiceShutdown` is built before `scheduleTick`, which calls `restart.tick` after `waitingRoom.tick` and
  `shutdown.request()` when it returns true (`feat(race)`)

## 8. Docs

- [ ] 8.1 Write `docs/decisions/0047-restart-service-after-cup.md` as Proposed, from the decisions in design.md. The owner accepts it later (`docs`)
- [ ] 8.2 Add `VOYAGER_RESTART_AFTER_CUP` to `docs/reference/configuration.md`, with the state-file requirement (`docs`)
- [ ] 8.3 Add the restart check to `docs/reference/config-check.md` (`docs`)
- [ ] 8.4 Update `docs/guides/how-to-deploy-on-cloudnet.md`: a restart section with the live enabling steps; change the sentence that says the server keeps running between cups to say it does so unless the restart is on (`docs`)
- [ ] 8.5 Add the setting to `docs/deployment/README.md` if it names CloudNet keys, otherwise record in the PR that it does not (`docs`)

## 9. Verify

- [ ] 9.1 Targeted tests, one at a time under the shared lock: `flock ~/.cache/claude-heavy.lock ./gradlew :voyager:platform:test --tests '*CupRestart*' --tests '*WaitingRoom*' :voyager:server:test --tests '*RestartSettings*' --tests '*ConfigCheck*' --tests '*RestartWiring*'` (`feat(race)`)
- [ ] 9.2 `flock ~/.cache/claude-heavy.lock ./gradlew :voyager:fitness:test`; confirm no slice or handoff store entry was added (`feat(race)`)
- [ ] 9.3 `flock ~/.cache/claude-heavy.lock ./gradlew build` for both trees (`feat(race)`)
- [ ] 9.4 Smoke boot the shadow JAR in a scratch directory with `VOYAGER_CUP=alpha_cup`, `VOYAGER_RESTART_AFTER_CUP=true`, the apply-rules flags, and `stop` on stdin. Expect the boot INFO line, `Listening on` and `Voyager started`, and exit 0 (`feat(race)`)
- [ ] 9.5 Configuration check runs against the same jar with `voyager.config.check=true`: the setting `yes` exits 1; the setting `true` with `VOYAGER_CUP_ROTATION=alpha_cup,legacy_cup` and no state file exits 1 and names both keys. Quote the lines (`feat(race)`)

## 10. Ship

- [ ] 10.1 Open the code pull request under the title `feat(race): restart the game service after each cup`. Write the description as the operator and the racer see it: the hub move, then a fresh service with the next cup. Include the Red messages (5.6), the smoke lines (9.4) and the check lines (9.5). No file under `openspec/`, no session link (`feat(race)`)
