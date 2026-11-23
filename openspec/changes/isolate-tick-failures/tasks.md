# Tasks

Commit type and scope: `fix(server)` for the code and its tests, `docs` for the `docs/` pages. Each behaviour gets a failing test
(Red) before its production code (Green). Every test follows F.I.R.S.T.: the tick is driven explicitly by the test, no sleeps,
no shared static state, a fresh `TickGuard` per test with lambda steps. Task checkboxes are ticked in the follow-up pull request,
never in the code pull request.

## 1. Red: tick isolation

- [ ] 1.1 Red: `TickGuardTest` "a throwing cup step stops the cup and the lobby step still runs on the same tick" (`fix(server)`)
- [ ] 1.2 Red: `TickGuardTest` "a stopped cup is not stepped again, and its failure is logged exactly once" (`fix(server)`)
- [ ] 1.3 Red: `TickGuardTest` "the statistics step runs after a cup failure" (`fix(server)`)
- [ ] 1.4 Red: `TickGuardTest` "a throwing lobby step does not stop the statistics step on the same tick" (`fix(server)`)
- [ ] 1.5 Red: `TickGuardTest` "the cup, lobby, restart and statistics steps run in that order on a healthy tick" (`fix(server)`)
- [ ] 1.6 Red: run 1.1 to 1.5 and confirm each fails for the stated reason; record the failure messages in the code pull request

## 2. Green: the tick guard

- [ ] 2.1 Green: add `TickSteps` and `TickGuard` in `voyager-server`, and route `VoyagerServer.scheduleTick` through them (`fix(server)`)
- [ ] 2.2 Green: make 1.1 to 1.5 pass (`fix(server)`)
- [ ] 2.3 Refactor: remove the local `AtomicBoolean` from `VoyagerServer` (`fix(server)`)

## 3. Red and Green: round reset

- [ ] 3.1 Red: `TickGuardTest` "a new round clears the stopped state and the cup runs its first tick after validation" (`fix(server)`)
- [ ] 3.2 Red: `TickGuardTest` "a new round whose validation fails stays stopped and reports the reason" (`fix(server)`)
- [ ] 3.3 Green: clear the stopped state on a new round after validation, and keep it stopped on failure (`fix(server)`)

## 4. Documentation

- [ ] 4.1 Document in `docs/` (Diataxis reference for `/race`) that `/race start` restarts a cup stopped by a failure, and that
  the lobby keeps running (`docs`, separate commit)
- [ ] 4.2 Record the per-cup stop as a MADR decision in `docs/decisions/`, with the alternatives considered (`docs`, separate commit)

## 5. Verify

- [ ] 5.1 Run `./gradlew :voyager:server:test :voyager:fitness:test` through `heavy` and confirm both pass
- [ ] 5.2 Run `./gradlew build` (both trees) through `heavy` before marking the change done

## 6. Open the code pull request

- [ ] 6.1 Open the code pull request titled `fix(server): isolate tick failures to the cup that threw`, branched from the remote
  main branch that contains this proposal
