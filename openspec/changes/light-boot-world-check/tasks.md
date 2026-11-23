# Tasks

Commit type and scope: `perf(server)` for the code and its tests, `docs` for `docs/reference/config-check.md` and the ADR note.
Each behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no real world files
(fixtures under `@TempDir`), no sleeps, no clock, a fresh fixture per test, no shared static state.

This change does not start until the owner has accepted a corrupt chunk surfacing mid-rotation.

## 0. Measure first

- [ ] 0.1 Record the cold boot time of a three-cup rotation on the current code, on the same machine and worlds, in the code pull
  request (`perf(server)`)

## 1. Red: the two modes

- [ ] 1.1 Red: `RotationValidationModeTest` "with HEADERS the deep chunk read is never called for any world" (`perf(server)`)
- [ ] 1.2 Red: `RotationValidationModeTest` "with FULL the deep chunk read is called once per present world" (`perf(server)`)
- [ ] 1.3 Red: `RotationValidationModeTest` "a missing world is reported the same way in both modes" (`perf(server)`)
- [ ] 1.4 Red: `ConfigCheckBootTest` "a normal boot with a rotation uses HEADERS" (`perf(server)`)
- [ ] 1.5 Red: `ConfigCheckBootTest` "the config check with voyager.config.check set uses FULL" (`perf(server)`)
- [ ] 1.6 Red: `ArmedCupCorruptionTest` "a corrupt region in a later cup is reported when that cup is armed, naming the file and the cup"
  (`perf(server)`)
- [ ] 1.7 Red: run 1.1 to 1.6 and record each failure message in the code pull request (`perf(server)`)

## 2. Green: the two modes

- [ ] 2.1 Green: `WorldCheckMode` sealed interface with `Headers` and `Full` records in `platform/rotation`, with `package-info`
  @NotNullByDefault (`perf(server)`)
- [ ] 2.2 Green: `RotationValidation.check` takes the mode and calls the deep read only for `Full` (`perf(server)`)
- [ ] 2.3 Green: `ConfigCheck.rotationBoot` passes `Headers`; the config check passes `Full` (`perf(server)`)
- [ ] 2.4 Green: the arming check runs the `Full` read for the armed cup's worlds off the racers' path (`perf(server)`)
- [ ] 2.5 Refactor: confirm 1.1 to 1.6 pass, and that the existing boot-refusal test is moved to the config-check test without
  changing its assertion (`perf(server)`)

## 3. Verify

- [ ] 3.1 Record the cold boot time of the three-cup rotation after the change, and the time of the arming read (`perf(server)`)
- [ ] 3.2 Run `./gradlew :voyager:platform:test :voyager:server:test :voyager:fitness:test` and confirm green (`perf(server)`)

## 4. Docs

- [ ] 4.1 Update `docs/reference/config-check.md` so a rotation boot's checks match the code (`docs`)
- [ ] 4.2 Add the measured cold boot to `docs/decisions/0047-restart-service-after-cup.md` (`docs`)

## 5. Pull request

- [ ] 5.1 Open the code pull request titled `perf(server): check rotation worlds by headers at boot` from the implementation branch,
  with no file under `openspec/` (`perf(server)`)
