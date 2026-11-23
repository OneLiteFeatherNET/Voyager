# Tasks

Commit types: `feat(server)` for the observer, its wiring and `/race` output; `docs` for `docs/`. Each behaviour gets a failing test
(Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleep, no system clock (durations are supplied by a
stub), a fresh fixture per test, no shared static state, log lines asserted through `LogCapture`. The code pull request contains
no file under `openspec/`, and no task is ticked in it.

## 1. Red: the tick window

- [ ] 1.1 Red: `TickWindowTest` "the summary is produced on the 600th tick with the mean and the maximum of the 600 durations" (`feat(server)`)
- [ ] 1.2 Red: `TickWindowTest` "no summary is produced before the 600th tick" (`feat(server)`)
- [ ] 1.3 Red: `TickWindowTest` "the window starts again after a summary" (`feat(server)`)
- [ ] 1.4 Red: run 1.1 to 1.3 and confirm each fails for the stated reason (`feat(server)`)

## 2. Green: the tick window

- [ ] 2.1 Green: `TickWindow`, `TickSummary` in `voyager-platform` `platform.observe`, duration supplied as `LongSupplier` (`feat(server)`)

## 3. Red then Green: particle packets

- [ ] 3.1 Red: `PacketCounterTest` "the count covers one window and resets at its end" (`feat(server)`)
- [ ] 3.2 Green: `PacketCounter` in `platform.observe`; `Fireworks` and the render path increment it on the tick thread (`feat(server)`)

## 4. Red then Green: the window log

- [ ] 4.1 Red: `WindowLogTest` "one INFO line per window with mean, maximum and packet count" (`feat(server)`)
- [ ] 4.2 Red: `WindowLogTest` "the writer's four counts appear when statistics are on and are absent when off" (`feat(server)`)
- [ ] 4.3 Green: `WindowLog` in `platform.observe` is the only observer class that calls a logger (`feat(server)`)

## 5. Red then Green: the composition root and /race

- [ ] 5.1 Red: `RaceStatusViewTest` "before the first window the view says no window has completed and shows no measurement" (`feat(server)`)
- [ ] 5.2 Green: `RaceCommand` with no subcommand renders the last `TickSummary` from a volatile snapshot (`feat(server)`)
- [ ] 5.3 Red: `ObserverWiringTest` "the scheduled tick feeds the window once per tick and the writer's counters are read at the window's end" (`feat(server)`)
- [ ] 5.4 Green: `VoyagerServer.scheduleTick` times each tick with `System::nanoTime` and calls the window (`feat(server)`)

## 6. Red then Green: fitness

- [ ] 6.1 Red then Green: two `@ArchTest` rules in `voyager-fitness`: `platform.observe` imports no `java.io`, `java.nio` or
  `java.net`; only `WindowLog` depends on a logging API. Both with `allowEmptyShould(false)` (`feat(server)`)

## 7. Docs

- [ ] 7.1 `docs/reference/configuration.md` gains the rows for `VOYAGER_PERSISTENCE` and the `VOYAGER_DB_*` keys, as
  `server/configuration` already requires (`docs`)
- [ ] 7.2 `docs/reference/permission-nodes.md` line on `/race` says the status view now shows the tick summary (`docs`)
- [ ] 7.3 `docs/reference/database.md` names the writer counters and where to read them (`docs`)

## 8. Open the code pull request

- [ ] 8.1 Open the pull request under `feat(server): log tick and packet health every thirty seconds`. It contains no file under
  `openspec/`, and no task is ticked in it.
