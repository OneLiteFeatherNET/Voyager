# Tasks

Commit types and scope: every behaviour ships as `fix(setup)`. `docs` for the how-to note, `test` only for a test-only commit that
changes no behaviour. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.:
a fake `Clock` for the wand window, no sleeps, no system-time reads, no shared static state, a fresh `Env` per Minestom test.
Task checkboxes are ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the refusals and the wand answer

- [ ] 1.1 Red: `SetupMessagesTest`: each domain exception (`DraftNotFoundException`, `DraftLocationConflictException`, `InvalidDraftException`, `UnknownWorldException`, `InvalidMapIdException`) maps to exactly one key with its arguments, in both bundles (`fix(setup)`)
- [ ] 1.2 Red: Minestom test (`@EnvTest`): `/map open canyon` with no draft replies the not-found key that names `canyon` and tells the builder to run `/map new canyon` (`fix(setup)`)
- [ ] 1.3 Red: Minestom test: `/map open ../etc` and `/map unlock ../etc` reply the invalid-id key, the same as `/map new ../etc` (`fix(setup)`)
- [ ] 1.4 Red: Minestom test: a builder whose client language is German receives the German not-found text, with no English sentence from the domain (`fix(setup)`)
- [ ] 1.5 Red: `WandWrongWorldTest` with a fake `Clock`: a right-click in another world than the open map's world changes no ring and sends one line naming the map (`fix(setup)`)
- [ ] 1.6 Red: `WandWrongWorldTest`: ten clicks within 5 seconds send one line; a click 6 seconds later on the fake clock sends a second (`fix(setup)`)
- [ ] 1.7 Red: `SetupArchitectureTest` in `voyager-fitness`: a rule that no setup adapter class passes `getMessage()` to `SetupMessages.refused` fails today, and `allowEmptyShould(false)` is set (`fix(setup)`)
- [ ] 1.8 Red: confirm 1.1 to 1.7 fail for the stated reason, and record the failure messages in the code pull request (`fix(setup)`)

## 2. Green: keys, mapping and the wand line

- [ ] 2.1 Green: new keys `voyager.setup.draft.notFound` (arg: id, hint), `voyager.setup.draft.conflict`, `voyager.setup.draft.invalid`, `voyager.setup.world.unknown` (arg: world) in `SetupMessages` (`platform/text`) and in `voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties` (informal "du") (`fix(setup)`)
- [ ] 2.2 Green: private mapping methods in `SetupCommands` from each domain exception to its key; `open`, `unlock`, `create` and `enter` use them (`fix(setup)`)
- [ ] 2.3 Green: `WandListener.openSession` sends the wrong-world line through a per-builder last-answer record, using the injected `Clock`; the record is dropped on disconnect (`fix(setup)`)
- [ ] 2.4 Refactor: remove `SetupMessages.refused(String)` once 2.2 leaves no caller; change no behaviour (`fix(setup)`)

## 3. Docs

- [ ] 3.1 Add a short section "When a command is refused" to `docs/guides/how-to-build-a-map-with-setup.md`: what a refusal names, and that a wand click in the wrong world is answered once per five seconds (`docs`)

## 4. Verify

- [ ] 4.1 Run `./gradlew :voyager:setup:test :voyager:fitness:test` and confirm `MessageBundleParityTest` and the new rule pass (`test`)
- [ ] 4.2 Run `./gradlew build` on both trees (`test`)

## 5. Pull request

- [ ] 5.1 Open the code pull request titled `fix(setup): name refusals in the builder's language` against `main` (`ci`)
