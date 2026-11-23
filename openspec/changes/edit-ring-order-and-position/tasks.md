# Tasks

Commit types and scope: every behaviour ships as `feat(setup)`; the ADR and the how-to are `docs`. Each behaviour gets a failing test
(Red) before its production code (Green). Every test follows F.I.R.S.T.: plain JUnit for `GuideRemap` and the editor operations,
a fresh `Env` per Minestom test, fixed fixtures built by `DraftEditor`, no sleeps, no system-time reads. This change starts after
`edit-ring-shape-and-type` is archived. Task checkboxes are ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the guide remapping and the editor

- [ ] 1.1 Red: `GuideRemapTest`: a move keeps every guide point's `orderIndex` (`feat(setup)`)
- [ ] 1.2 Red: `GuideRemapTest`: an insert at `k` moves each guide point at or above slot `k * 100` by 100, and no other (`feat(setup)`)
- [ ] 1.3 Red: `GuideRemapTest`: a reorder keeps each guide point between the same two rings, at the same fraction of the gap (`feat(setup)`)
- [ ] 1.4 Red: `GuideRemapTest`: a remap that would put a guide point on a ring slot, or outside a legal bracket, is refused (`feat(setup)`)
- [ ] 1.5 Red: `DraftEditorTest`: `moved` with `here` and with an offset sets the centre and keeps the normal and the index (`feat(setup)`)
- [ ] 1.6 Red: `DraftEditorTest`: `inserted` at 0, at the size and in between renumbers the rings so each index equals its position (`feat(setup)`)
- [ ] 1.7 Red: `DraftEditorTest`: `reordered` with `from == to` changes nothing; a range error throws and names the bound (`feat(setup)`)
- [ ] 1.8 Red: confirm 1.1 to 1.7 fail for the stated reason, and record the failure messages in the code pull request (`feat(setup)`)

## 2. Green: the operations

- [ ] 2.1 Green: `GuideRemap` as a pure record-free class in `mapsetup`, with the three rules of `design.md` (`feat(setup)`)
- [ ] 2.2 Green: `DraftEditor.moved`, `inserted` and `reordered`, each calling `GuideRemap` and renumbering, each returning a new `MapDraft` (`feat(setup)`)

## 3. Red: the commands and the undo

- [ ] 3.1 Red: Minestom test: `/map ring move 2 here` moves the ring, saves before the previews change, and shows the moved preview (`feat(setup)`)
- [ ] 3.2 Red: Minestom test: `/map ring move 7 here` on a five-ring map refuses with the index and the count, and writes no file (`feat(setup)`)
- [ ] 3.3 Red: Minestom test: `/map ring insert 1` while looking east on a three-ring map inserts at 1 and keeps the other shapes (`feat(setup)`)
- [ ] 3.4 Red: Minestom test: `/map ring insert 9` refuses with the range 0 to 3 and writes no file (`feat(setup)`)
- [ ] 3.5 Red: Minestom test: `/map ring reorder 4 1` reorders and remaps a guide point; a reorder that breaks a bracket is refused and writes no file (`feat(setup)`)
- [ ] 3.6 Red: Minestom test: `/map undo` after `/map ring reorder 4 1` restores the order and the guide points together (`feat(setup)`)
- [ ] 3.7 Red: `SetupPermissionTest`: a builder without `voyager.setup.use` cannot run any of the new commands, and the file keeps its bytes (`feat(setup)`)
- [ ] 3.8 Red: confirm 3.1 to 3.7 fail for the stated reason, and record the failure messages in the code pull request (`feat(setup)`)

## 4. Green: the commands

- [ ] 4.1 Green: `move`, `insert` and `reorder` as subcommands of `/map ring` in `SetupCommands`, each through `MapSession.commit` (`feat(setup)`)
- [ ] 4.2 Green: messages for the moved, inserted and reordered ring, and for the index and bracket refusals, in `SetupMessages` (`platform/text`) and both bundles (`feat(setup)`)
- [ ] 4.3 Green: an ArchUnit rule in `voyager/fitness` that `GuideRemap` is reached only through `DraftEditor` (`feat(setup)`)

## 5. Docs

- [ ] 5.1 Write the MADR ADR for index addressing and the guide remapping rule, the next free number in `docs/decisions/` (`docs`)
- [ ] 5.2 Add a section "Move, insert and reorder rings" to `docs/guides/how-to-build-a-map-with-setup.md` (`docs`)

## 6. Verify

- [ ] 6.1 Run `./gradlew :voyager:setup:test :voyager:fitness:test` and confirm `MessageBundleParityTest` and the new rule pass (`test`)
- [ ] 6.2 Run `./gradlew build` on both trees (`test`)

## 7. Pull request

- [ ] 7.1 Open the code pull request titled `feat(setup): move, insert and reorder rings` against `main` (`ci`)
