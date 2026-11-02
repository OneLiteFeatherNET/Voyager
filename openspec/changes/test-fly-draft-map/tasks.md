# Tasks

Commit types and scope: `feat(setup)` for the flight state, the guard, the wrappers, the messages and their tests; `docs` for the
`docs/` pages and the ADR move; `test` only for test-only commits, such as the architecture rule if it ships alone. One type per
commit. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: scripted
positions, `env.tick()` to drive time, no sleeps, no wall-clock reads, no shared static state, a fresh `Env` per Minestom test.
This change is independent of `publish-map-to-game`. Task checkboxes are ticked in the follow-up pull request, never in the code
pull request.

## 0. Spikes and owner decisions (no production code)

- [ ] 0.1 Spike: read `Racers` and the racers' state set-up, and record the game mode the racers play and whether they may fly without an elytra. Record it in `design.md`, decision 8 (`test`)
- [ ] 0.2 Spike: confirm that `MapDefinitionAdapter` is usable from the setup server for a single map file (its visibility and its entry point), and that it reads the saved draft file of `maps/` (`test`)
- [ ] 0.3 Spike: confirm how the elytra is equipped and how a firework rocket is used by the client, so that the flight items can be marked and removed without touching other items (`test`)
- [ ] 0.4 Owner: boosts and ring effects are in version 1 (owner decision 3, 2026-11-02). Confirm or change the defaults of open questions 1 to 4 of `design.md`. Not blocking: section 1 starts on the defaults (`docs`)
- [ ] 0.5 Spike: confirm that `FireworkBoostTracker` imports no Minestom type, and list the velocity sends a race makes for a firework burn, a boost ring and a slow ring, with the platform code that makes each (`test`)

## 1. Red: the flight, the guard and the commands

- [ ] 1.1 Red: `TestFlightTest` asserts that a ring passed in order is counted, that a ring passed out of order is not, and that the rings passed are reported against the map's count (`feat(setup)`)
- [ ] 1.2 Red: `TestFlightTest` asserts that the finish gives the time from the race clock, and that the finish after the last ring is reported once (`feat(setup)`)
- [ ] 1.3 Red: `TestFlightTest` asserts that a restart begins at the spawn with an empty progress, and that no time of the earlier run is kept (`feat(setup)`)
- [ ] 1.4 Red: `FlightResultTest` asserts the medal tier of a time against the map's brackets, using the race's `MapScorer`, and the format of the time (`feat(setup)`)
- [ ] 1.5 Red: `FlightGuardTest` asserts that a draft with no spawn and a draft with no ring are each refused with their problem, and that a stored copy that differs from the session's copy is refused with the changed-on-disk key (`feat(setup)`)
- [ ] 1.6 Red: `FlightCommandTest` (`Env`) asserts that a builder without `voyager.setup.use` is refused and no item is given, that a builder with no map open is told so, and that a valid map starts a flight with the flight items and the start reply (`feat(setup)`)
- [ ] 1.7 Red: `FlightEditRefusalTest` (`Env`) asserts that while a flight runs, ring placement, ring removal, `/map spawn`, `/map undo` and `/map redo` change no ring, the draft file keeps its bytes, and each refusal names the stop command (`feat(setup)`)
- [ ] 1.8 Red: `FlightStopTest` (`Env`) asserts that `/map fly stop` restores creative mode, removes the flight items and gives back the wand, with the claim, the draft and the history unchanged (`feat(setup)`)
- [ ] 1.9 Red: `FlightStopTest` asserts that `/map fly stop` without a flight replies that no flight runs and changes nothing (`feat(setup)`)
- [ ] 1.10 Red: `FlightEndsWithSessionTest` (`Env`) asserts that `/map close`, an open of another map, a disconnect and an unlock each end a running flight, and that the claim follows `single-builder-map-claims` (`feat(setup)`)
- [ ] 1.11 Red: `FlightItemsTest` asserts that the flight items are removed when the builder next enters the setup server after a disconnect in the middle of a flight, and that other items are not removed (`feat(setup)`)
- [ ] 1.12 Red: `FlightRecordsNothingTest` (`Env`) asserts that the draft file's bytes and the history are the same after a completed flight, and that no claim is taken or released by the flight (`feat(setup)`)
- [ ] 1.13 Red: `FlightCommandTest` asserts that a second `/map fly` restarts the flight at the spawn and keeps no earlier time (`feat(setup)`)
- [ ] 1.14 Red: the architecture rule `testFlightUsesOnlyApiRaceAndJdk` in `voyager-fitness` fails when a class under `setup.testflight` imports a platform or Minestom type, other than the pure `platform.flight.FireworkBoostTracker`, and the rule matches the flight's classes so that it cannot pass empty (`test`)
- [ ] 1.15 Red: `TestFlightTest` asserts that a firework burn and a boost ring change the flight's velocity as the race's rules do, and that a slow ring applies the race's slow effect, from scripted positions (`feat(setup)`)
- [ ] 1.16 Red: `MessageBundleParityTest` fails for the flight keys until both bundles hold them (`feat(setup)`)
- [ ] 1.17 Red: confirm that 1.1 to 1.16 fail for the stated reason, and record the failure messages in the pull request (`test`)

## 2. Green: the pure flight, the guard, the adapter and the messages

- [ ] 2.1 Green: `TestFlight`, `FlightTick` and `FlightResult` in `net.elytrarace.voyager.setup.testflight`, calling `RaceRun.advance`, `RingProgress`, `RaceClock` and `MapScorer.score` with the map's `MedalBrackets`. Imports `api`, `race` and the JDK only. `package-info.java` with `@NotNullByDefault` (`feat(setup)`)
- [ ] 2.2 Green: `FlightGuard` (pure) and `FlightSession` (pure: one builder's current `TestFlight`, or none) in `net.elytrarace.voyager.setup.testflight` (`feat(setup)`)
- [ ] 2.3 Green: the flight read of `maps/<id>.json` through `MapDefinitionAdapter`, and the stored-copy comparison by the canonical JSON that `DraftStore` uses, in the platform's catalog package, read only (`feat(setup)`)
- [ ] 2.4 Green: `FlightCommand` (`/map fly` and `/map fly stop`) in `setup.inject`, asking `PermissionPolicy` for `voyager.setup.use` and replying with `SetupMessages`. Register it in `SetupServer` next to the map command, with the syntax attachment that task 0.5 of `publish-map-to-game` confirms (`feat(setup)`)
- [ ] 2.5 Green: `FlightTicker` in `setup.inject`, which calls `TestFlight.tick` once per server tick for each flying builder, `FlightItems`, which gives, marks and removes the flight items, and the velocity sends of a firework burn and of boost and slow rings, as a race makes them (decision 11) (`feat(setup)`)
- [ ] 2.6 Green: the edit refusals in `WandListener`, `SetupCommands.spawn`, `undo` and `redo`, each a check of `FlightSession` that replies with the stop message and changes nothing (`feat(setup)`)
- [ ] 2.7 Green: the end of a flight in the paths of `BuilderSessions` (close, open, disconnect and unlock), and the flight item removal on the next entry to the setup server (`feat(setup)`)
- [ ] 2.8 Green: the flight message keys in `SetupMessages` (`platform.text`) and in both bundles, German in the informal "du", and the medal text from `Messages.medal` (`feat(setup)`)
- [ ] 2.9 Green: confirm that every test of section 1 passes, and that `./gradlew :voyager:setup:test :voyager:fitness:test` is green, run through `flock ~/.cache/claude-heavy.lock` (`test`)
- [ ] 2.10 Fitness: confirm that `SliceBoundaryRulesTest.r6_setupAdapterDoesNotUseMinestom` passes without a change and that no file under `voyager/fitness/src/test/resources/archunit_store` gains an entry. If one would, stop and report it (`test`)

## 3. Smoke, refactor and docs

- [ ] 3.1 Smoke: boot the setup shadow jar in a scratch directory, fly a saved map over a test connection with scripted positions, including a firework burn and a boost ring, record the result line in the pull request, run `/map fly stop`, and stop the server with `stop` on standard input, expecting a clean exit (`test`)
- [ ] 3.2 Docs: add the section "Test-fly a map" to `docs/guides/how-to-build-a-map-with-setup.md`, with the refusals, the stop command, the items, what a flight records (nothing) and what it does not apply, the cup's orchestration and the out-of-bounds reset (`docs`)
- [ ] 3.3 Docs: `docs/reference/permission-nodes.md` states that `/map fly` needs `voyager.setup.use` alone, with no new node (`docs`)
- [ ] 3.4 Docs: move the text of ADR-0049 from `design.md` to `docs/decisions/0049-test-flight-on-race-rules.md` with the status the owner gives it, Proposed unless accepted (`docs`)
- [ ] 3.5 Run `./gradlew :voyager:fitness:test` and `./gradlew build` for both trees, through `flock ~/.cache/claude-heavy.lock`, before the pull request is opened (`test`)

## 4. Ship

- [ ] 4.1 Open the code pull request under the title `feat(setup): fly a draft map in a test flight before publishing`. Fill the sections of `.github/pull_request_template.md`, with the type `feat:` ticked. Describe the flight from the builder's side: what it shows, what it does not show, and that it leaves nothing behind. Quote the failure messages of task 1.17 and the smoke line of 3.1. State that boosts and ring effects are in version 1 (owner decision 3, 2026-11-02), and which defaults of the open questions the change uses. End the body with the referral line the repository's rule asks for. Do not add a session link (`feat(setup)`)
