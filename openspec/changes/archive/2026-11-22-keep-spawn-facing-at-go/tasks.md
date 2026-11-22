# Tasks

Commit type and scope: `fix(race)` for every code commit of this change. The golden update is its own commit inside the
same `fix(race)` PR. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows
F.I.R.S.T.: no sleeps, no clock reads, explicit ticks, a fresh Minestom instance per test, no shared static state. Task
checkboxes are ticked in the follow-up PR, never in the code PR.

## 1. Red: the facing rule is pinned by failing tests

- [ ] 1.1 Red: add a unit test `ArrivalFacingTest` that asserts the pure facing function returns yaw 90 for spawn (109, -62, 54) and ring 0 centre (85, -54, 54), with a behaviour name such as "a racer facing the first ring of the shipped map has yaw 90" (`fix(race)`)
- [ ] 1.2 Red: add `ArrivalFacingTest` cases "the pitch looks up towards ring 0 when ring 0 is above the spawn" and "a ring 0 directly above the spawn defines a facing instead of failing" (`fix(race)`)
- [ ] 1.3 Red: add a tick-driven platform test "a racer is facing ring 0 after GO" that runs the countdown and the GO tick on a fresh instance and asserts yaw within one degree of 90 for the shipped map (`fix(race)`)
- [ ] 1.4 Red: add a test "a join into a running cup places the racer facing ring 0" and "a map transition places every racer facing ring 0 of the new map" (`fix(race)`)
- [ ] 1.5 Red: confirm that tasks 1.1 to 1.4 fail for the stated reason (yaw 0 at GO, missing helper), and record the failure messages in the PR description

## 2. Green: one facing function for every map start

- [ ] 2.1 Green: add the pure helper in `voyager/platform` `flight` package that computes yaw and pitch from the spawn to the centre of ring 0 (`fix(race)`)
- [ ] 2.2 Green: use the helper in `MapTransition` (`:111`), `Arrivals` (`:61`, `:49`), `JoinArrival` (`:41-43`) and `LobbyTransition` (`:34`) when a racer is placed at a map start, so the spawn `Pos` carries the facing (`fix(race)`)
- [ ] 2.3 Green: make the 1.1 to 1.4 tests pass without changing the order of the GO call (`fix(race)`)
- [ ] 2.4 Refactor: remove the duplicate `Vectors.toMinestom(...).asPos()` spawn construction that the helper replaces, and keep `Racers.faceCourse` unchanged (`fix(race)`)

## 3. Golden masters and the lobby gate

- [ ] 3.1 Run `voyager/server` golden tests and confirm that only the yaw and pitch of racers at the GO tick differ from the committed transcripts (`fix(race)`)
- [ ] 3.2 Update those GO lines in the transcripts under `voyager/server/src/test/resources/golden/cup-session/`, reviewed line by line, in a separate commit (`fix(race)`)
- [ ] 3.3 Confirm the `race/waiting-lobby` scenario "The golden scenarios still pass" holds with the stated exception (`fix(race)`)

## 4. Checks

- [ ] 4.1 Grep the platform and server modules for `asPos()` and `setInstance(` on map spawns, and record any call that does not carry the facing (`fix(race)`)
- [ ] 4.2 Check whether computer racers (`ComField.java:136`) face ring 0 at the GO tick, and record the answer in the PR. Change nothing for computer racers in this PR unless the check shows a racer-visible defect (`fix(race)`)
- [ ] 4.3 Run `./gradlew :voyager:fitness:test` and confirm the architecture rules still hold (`fix(race)`)
- [ ] 4.4 Run `./gradlew build` on both trees (`fix(race)`)

## 5. Docs

- [ ] 5.1 Add one line to the page in `docs/` that describes the race start, stating that racers face the first ring at GO. Find the page with a search for "countdown" or "GO" under `docs/` (`docs`)

## 6. Ship

- [ ] 6.1 Open the code pull request under the title `fix(race): keep racers facing the first ring at GO and on arrival`. Put the failing-test messages (1.5) and the golden diff summary (3.1) in the description, and write it as the player sees the fix. Do not add a session link (`fix(race)`)
