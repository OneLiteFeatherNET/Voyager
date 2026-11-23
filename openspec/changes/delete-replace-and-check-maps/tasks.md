# Tasks

Commit types and scope: every behaviour ships as `feat(setup)`; the how-to and the ADR are `docs`. Each behaviour gets a failing test (Red)
before its production code (Green). Every test follows F.I.R.S.T.: a fake `Clock` for timestamps, `@TempDir` for every file, a fresh `Env` per
Minestom test, drive the off-tick check through a fake executor, no sleeps, no system-time reads. Task checkboxes are ticked in the follow-up
pull request, never in the code pull request. Delete and replace start only after `publish-map-to-game` defines the published revision, or
the tasks restrict both to `drafts/` and say so in the reply.

## 1. Red: trash and delete

- [ ] 1.1 Red: `DraftTrashTest` with `@TempDir`: a delete moves the draft and the world into `drafts/trash/<id>-<timestamp>/` with the same bytes (`feat(setup)`)
- [ ] 1.2 Red: `DraftTrashTest`: a delete whose timestamp folder exists refuses and moves nothing (`feat(setup)`)
- [ ] 1.3 Red: `DraftTrashTest`: a failed move of the world moves the draft back and reports the failure (`feat(setup)`)
- [ ] 1.4 Red: Minestom test: `/map delete canyon` without `confirm` names both items and changes nothing (`feat(setup)`)
- [ ] 1.5 Red: Minestom test: `/map delete canyon confirm` refuses a draft in `maps/` and a draft a cup names, and changes nothing (`feat(setup)`)
- [ ] 1.6 Red: Minestom test: after `/map delete canyon confirm`, `/map new canyon` succeeds (`feat(setup)`)
- [ ] 1.7 Red: `ArchitectureTest` in `voyager-fitness`: `Files.delete` is called only from the atomic-save temp removal and the partial replace copy removal (`feat(setup)`)
- [ ] 1.8 Red: confirm 1.1 to 1.7 fail for the stated reason, and record the failure messages in the code pull request (`feat(setup)`)

## 2. Green: trash and delete

- [ ] 2.1 Green: `DraftTrash` in `setup/adapter` with `move` that is a rename within the setup data directory, the timestamp from `Clock` and the no-overwrite rule (`feat(setup)`)
- [ ] 2.2 Green: `/map delete <id> [confirm]` in `SetupCommands` with the refusals of the spec (`feat(setup)`)

## 3. Red: replace

- [ ] 3.1 Red: `ReplaceImportTest` with `@TempDir`: a replace of an incomplete draft trashes the old world and draft and installs the new (`feat(setup)`)
- [ ] 3.2 Red: `ReplaceImportTest`: a copy that fails leaves the old draft and world in place and removes the partial `.new/` folder (`feat(setup)`)
- [ ] 3.3 Red: Minestom test: `/map import elytrarace-cube skycube` without `--replace` refuses and names `--replace` (`feat(setup)`)
- [ ] 3.4 Red: Minestom test: `--replace` on a draft in `maps/` is refused and changes nothing (`feat(setup)`)
- [ ] 3.5 Red: confirm 3.1 to 3.4 fail for the stated reason, and record the failure messages in the code pull request (`feat(setup)`)

## 4. Green: replace

- [ ] 4.1 Green: the copy-then-swap flow in `ImportCommand` through `DraftTrash`, with `--replace` parsed as the only option (`feat(setup)`)

## 5. Red: status, terrain, catalogue and medals

- [ ] 5.1 Red: `MapStatusTest`: a world with zero full chunks lists "no built terrain" as blocking; a partial chunk does not block (`feat(setup)`)
- [ ] 5.2 Red: `MapStatusTest`: a catalogue problem reported by the injected check is listed as blocking (`feat(setup)`)
- [ ] 5.3 Red: `StatusCheckTest` with a fake executor: the reply waits for both checks, and a check that does not finish in 30 fake seconds yields "did not finish" (`feat(setup)`)
- [ ] 5.4 Red: `MedalsTest`: a reference time of 60 seconds yields 60.0, 66.0, 75.0 and 90.0 (`feat(setup)`)
- [ ] 5.5 Red: Minestom test: `/map status` on an unmodified void world lists the empty terrain as blocking, without blocking the tick (`feat(setup)`)
- [ ] 5.6 Red: confirm 5.1 to 5.5 fail for the stated reason, and record the failure messages in the code pull request (`feat(setup)`)

## 6. Green: status and medals

- [ ] 6.1 Green: `MapStatus` gains the terrain and catalogue values as pure inputs; the census and the check are ports implemented in `platform` (`feat(setup)`)
- [ ] 6.2 Green: the off-tick runner for the two checks, with the 30-second bound (`feat(setup)`)
- [ ] 6.3 Green: `/map medals` in `SetupCommands` and the messages in both bundles (`feat(setup)`)
- [ ] 6.4 Green: ArchUnit rule in `voyager/fitness`: `mapsetup` does not import `platform` (`feat(setup)`)

## 7. Docs and decision

- [ ] 7.1 Write the MADR ADR for delete-as-move, copy-then-swap and the trash, the next free number in `docs/decisions/` (`docs`)
- [ ] 7.2 Add sections on delete, replace, status and medals to `docs/guides/how-to-build-a-map-with-setup.md` (`docs`)

## 8. Verify

- [ ] 8.1 Run `./gradlew :voyager:setup:test :voyager:platform:test :voyager:fitness:test` and confirm the bundle parity and new rules pass (`test`)
- [ ] 8.2 Run `./gradlew build` on both trees (`test`)

## 9. Pull request

- [ ] 9.1 Open the code pull request titled `feat(setup): delete, replace and check maps` against `main` (`ci`)
