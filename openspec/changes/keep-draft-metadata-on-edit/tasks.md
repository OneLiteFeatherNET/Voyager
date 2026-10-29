# Tasks

Commit types and scope: `fix(setup)` for every production change in this change, `test` only for a test that changes no
behaviour. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: a
fresh `MapDraft` fixture per test, `@TempDir` for the draft folder, no shared static state and no wall-clock reads. Task
checkboxes are ticked in the follow-up pull request, never in the code pull request.

## 1. Red: every edit keeps the name and the authors

- [ ] 1.1 Red: `DraftEditorTest` asserts that `withRingAppended` keeps the display name "Blue & Red" and two authors (`fix(setup)`)
- [ ] 1.2 Red: `DraftEditorTest` asserts that `withoutRing` keeps the display name and two authors, and renumbers the remaining rings (`fix(setup)`)
- [ ] 1.3 Red: `DraftEditorTest` asserts that `withSpawn` keeps the display name and two authors and sets the new spawn (`fix(setup)`)
- [ ] 1.4 Red: `DraftEditorTest` asserts that a draft without a name or authors stays without them after an append (`fix(setup)`)
- [ ] 1.5 Red: round-trip test with `@TempDir` and `JsonDraftStore`: a map with the name and two authors gets a ring, is saved and is loaded again with both fields (`fix(setup)`)
- [ ] 1.6 Red: confirm 1.1 to 1.5 fail for the stated reason (a null display name and an empty author list), and record the failure messages in the pull request (`test`)

## 2. Green: the editor keeps the metadata

- [ ] 2.1 Green: `DraftEditor.with` passes the edited draft's `displayName` and `authors` to the canonical `MapDraft` constructor, so that all three edits keep them (`fix(setup)`)
- [ ] 2.2 Green: confirm that the tests of 1.1 to 1.5 pass and that no other test in `voyager/setup` changes its result (`fix(setup)`)

## 3. Pull request

- [ ] 3.1 Open the code pull request titled `fix(setup): keep display name and authors on every draft edit`, with the body filled from `.github/pull_request_template.md`
