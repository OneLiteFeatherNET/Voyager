# Tasks

Commit types and scope: every behaviour ships as `feat(setup)`. `docs` for the how-to guide. `test` only for a test-only commit
that changes no behaviour. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows
F.I.R.S.T.: plain JUnit for `DraftHistory`, a fresh `Env` per Minestom test, `@TempDir` for files, fixed fixtures built by
`DraftEditor`, no sleeps, no system-time reads, no shared static state. The task of the fix `keep-draft-metadata-on-edit`
(change 6) must be merged before task 1.8 is written. The open questions in `design.md` are answered by the owner before
section 2 starts. Task checkboxes are ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the history and the commands

- [ ] 1.1 Red: `DraftHistoryTest`: undo after one recorded edit returns the draft before it, and `canUndo()` is false on a new history (`feat(setup)`)
- [ ] 1.2 Red: `DraftHistoryTest`: undo after an appended ring restores the rings before it (`feat(setup)`)
- [ ] 1.3 Red: `DraftHistoryTest`: undo after a removed ring restores the removed ring at its original index, with the later rings at their original indices (`feat(setup)`)
- [ ] 1.4 Red: `DraftHistoryTest`: redo after an undo re-applies the undone edit, and `canRedo()` is false afterwards (`feat(setup)`)
- [ ] 1.5 Red: `DraftHistoryTest`: a new edit after an undo clears the redo history (`feat(setup)`)
- [ ] 1.6 Red: `DraftHistoryTest`: after 101 recorded edits, undoing 100 times reaches the second draft and not the first, so the oldest snapshot was dropped (`feat(setup)`)
- [ ] 1.7 Red: `MapSession` test with a fake `DraftStore` that fails the save: an undo whose save fails keeps the current draft and the history, and the command reply is the save-failed message (`feat(setup)`)
- [ ] 1.8 Red: Minestom test (`@EnvTest`): `/map undo` after a spawn set on a map with a display name restores the spawn and keeps the display name; needs `keep-draft-metadata-on-edit` (`feat(setup)`)
- [ ] 1.9 Red: Minestom test: `/map undo` with an empty history replies the none message and writes no file (`feat(setup)`)
- [ ] 1.10 Red: Minestom test: `/map redo` with an empty redo history replies the none message and writes no file (`feat(setup)`)
- [ ] 1.11 Red: `SetupPermissionTest`: a builder without `voyager.setup.use` cannot run `/map undo` or `/map redo`, and the draft file keeps its bytes (`feat(setup)`)
- [ ] 1.12 Red: Minestom test: after a session ends (the builder opens another map, closes, or disconnects) a new session has no undo history (`feat(setup)`)
- [ ] 1.13 Red: confirm 1.1 to 1.12 fail for the stated reason, and record the failure messages in the code pull request (`feat(setup)`)

## 2. Green: the history and the session

- [ ] 2.1 Green: `DraftHistory` record in `net.elytrarace.voyager.setup.mapsetup` with `start`, `record`, `undo`, `redo`, `canUndo` and `canRedo`; the earlier drafts are capped at 100 (`feat(setup)`)
- [ ] 2.2 Green: `MapSession` holds the history; `commit` and the new `undo` and `redo` methods share the private `persist(DraftHistory)`, which saves before it installs the candidate and then shows the previews (`feat(setup)`)
- [ ] 2.3 Green: `/map undo` and `/map redo` as gated subcommands in `SetupCommands`, each with the no-session reply of `spawn` (`feat(setup)`)
- [ ] 2.4 Green: messages `voyager.setup.undo.done` (argument: ring count), `voyager.setup.undo.none`, `voyager.setup.redo.done` (argument: ring count) and `voyager.setup.redo.none` in `SetupMessages` (`platform/text`) and in `voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties`, the German in informal "du" (`feat(setup)`)
- [ ] 2.5 Refactor: `MapSession.draft()` reads the current draft of the history, so no second copy of the draft is kept; change no behaviour (`feat(setup)`)

## 3. Docs

- [ ] 3.1 Add a section "Undo and redo an edit" to `docs/guides/how-to-build-a-map-with-setup.md`, stating the limit of 100, that the history is lost when the builder closes the map or disconnects, and that the file on disk is always the last saved state (`docs`)

## 4. Verify

- [ ] 4.1 Run `./gradlew :voyager:setup:test :voyager:fitness:test` and confirm `setupDomainOnlyDependsOnApiAndJdk` and `MessageBundleParityTest` pass (`test`)
- [ ] 4.2 Run `./gradlew build` on both trees (`test`)

## 5. Ship

- [ ] 5.1 Open the code pull request under the title `feat(setup): undo and redo draft edits`. Fill the sections of `.github/pull_request_template.md`, tell the story of a builder who removes the wrong ring, list the messages and the failing tests of 1.13, and end with the referral line of the repository's pull request rule. Do not add a session link (`feat(setup)`)
