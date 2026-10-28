# Tasks

Commit type and scope: `feat(race)` for the rotation, the boot check, the lobby and the tutorial, with their tests. `docs` for
`docs/` pages and ADR-0041. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows
F.I.R.S.T.: no sleeps, no clock reads (the rotation has no clock; the world check uses a fake health source), a fresh fixture per
test, `@TempDir` for the state file, no shared static state. Log lines that matter are asserted through a captured appender.
Task checkboxes are ticked in the follow-up PR, never in the code PR.

Depends on `add-catalogue-keys-and-authors` (#356) for the key form of cup identifiers. Tests in section 5 cover both the flat id
and the key form. Depends on `cygnus-style-lobby` for the tutorial base requirement. Rebase on #352 before section 11.

## 1. Red: the rotation rule

- [ ] 1.1 Red: `CupRotationTest` "a finished cup arms the next cup of the list, and the last one wraps to the first" (`feat(race)`)
- [ ] 1.2 Red: `CupRotationTest` "an aborted or restarted cup keeps the armed cup where it is" (`feat(race)`)
- [ ] 1.3 Red: `CupRotationTest` "a rotation refuses an empty list and a cup named twice" (`feat(race)`)
- [ ] 1.4 Red: `CupRotationTest` "a running round keeps its cup until it finishes" (`feat(race)`)
- [ ] 1.5 Red: run 1.1 to 1.4 and confirm each fails for the stated reason; record the messages in the code PR description (`feat(race)`)

## 2. Green: the rule and its port

- [ ] 2.1 Green: `voyager/race` `net.elytrarace.voyager.race.cup.rotation`: the `CupRotation` record (ordered names, position, `armed()`, `advance()`), and the `CupRotationStore` port, with `package-info.java` carrying `@NotNullByDefault` (`feat(race)`)
- [ ] 2.2 Refactor: confirm 1.1 to 1.4 pass; no new fitness rule is needed (`feat(race)`)

## 3. Red: the state file (SHOULD)

- [ ] 3.1 Red: `FileCupRotationStoreTest` "a written name is read back by a new store on the same file" (`feat(race)`)
- [ ] 3.2 Red: `FileCupRotationStoreTest` "a missing file reads as no name and does not warn" (`feat(race)`)
- [ ] 3.3 Red: `FileCupRotationStoreTest` "an unreadable file reads as no name and logs one WARN that names the file" (`feat(race)`)
- [ ] 3.4 Red: `FileCupRotationStoreTest` "a failed write logs one WARN and keeps the rotation in memory" (`feat(race)`)
- [ ] 3.5 Red: `FileCupRotationStoreTest` "an update is written through a temporary file that is then moved over the state" (`feat(race)`)

## 4. Green: the state file

- [ ] 4.1 Green: `voyager/platform` `net.elytrarace.voyager.platform.rotation.FileCupRotationStore` implements the port with a temporary file and a move; `package-info.java` with `@NotNullByDefault` (`feat(race)`)

## 5. Red: settings and refusals

- [ ] 5.1 Red: `CupRotationSettingsTest` "VOYAGER_CUP_ROTATION parses an ordered list and drops blank entries" (`feat(race)`)
- [ ] 5.2 Red: `CupRotationSettingsTest` "setting VOYAGER_CUP and VOYAGER_CUP_ROTATION together refuses boot and names both keys" (`feat(race)`)
- [ ] 5.3 Red: `CupRotationSettingsTest` "a rotation entry in the key form resolves to the same cup as the flat id" (`feat(race)`; fails until #356 lands, so it is marked as a precondition with #356)
- [ ] 5.4 Red: `ConfigCheckTest` "a state file whose parent directory is missing is one configuration error that names the key" (`feat(race)`)
- [ ] 5.5 Red: `ConfigLoaderTest` "the rotation keys are read through the loader and the environment, never System.getProperty" (`feat(race)`)

## 6. Green: settings

- [ ] 6.1 Green: `CupRotationSettings` in `voyager/server` `config`, with `VOYAGER_CUP_ROTATION` and `VOYAGER_CUP_ROTATION_STATE` added to `ServerSettings.KEYS` (`feat(race)`)
- [ ] 6.2 Green: the configuration check reports a missing state directory (`feat(race)`)

## 7. Red: the boot check over every cup of the rotation

- [ ] 7.1 Red: `RotationBootCheckTest` "the worlds of every rotation cup are checked, one cup at a time, releasing each before the next" (`feat(race)`)
- [ ] 7.2 Red: `RotationBootCheckTest` "a missing world skips its cup and logs exactly one WARN that names the cup and each world" (`feat(race)`)
- [ ] 7.3 Red: `RotationBootCheckTest` "a damaged world refuses boot, as it does for the played cup today" (`feat(race)`)
- [ ] 7.4 Red: `RotationBootCheckTest` "when no rotation cup is playable, boot is refused with the existing message listing each cup" (`feat(race)`)
- [ ] 7.5 Red: `RotationBootCheckTest` "a definition error in any rotation cup refuses boot; a cup outside the rotation does not" (`feat(race)`)
- [ ] 7.6 Red: `ConfigCheckRunTest` "check mode reports a skipped cup as a WARNING and exits 0 while a playable cup remains; a refusal exits 1" (`feat(race)`)

## 8. Green: the boot check

- [ ] 8.1 Green: `ConfigCheck.bootRefusals` checks the rotation's cups one at a time, and `CatalogReloader.loadInitial` skips a cup with missing worlds with one WARN (`feat(race)`)
- [ ] 8.2 Green: `ConfigCheck.run` reports a skipped cup as a WARNING and exits 0 while a playable cup remains (`feat(race)`)
- [ ] 8.3 Green: `VoyagerServer` opens the boot cup's worlds only, and logs the rotation and its position at boot (`feat(race)`)

## 9. Red: arming, release and the round

- [ ] 9.1 Red: `CupSessionRotationTest` "finishing alpha_cup arms legacy_cup once its results have lingered, and the next round pins it" (`feat(race)`)
- [ ] 9.2 Red: `CupSessionRotationTest` "an aborted round is replayed and the rotation does not advance" (`feat(race)`)
- [ ] 9.3 Red: `CupSessionRotationTest` "arming releases the previous cup's worlds before it opens the armed cup's worlds, so one cup's worlds are open at a time" (`feat(race)`)
- [ ] 9.4 Red: `CupSessionRotationTest` "a world shared by both cups stays open" (`feat(race)`)
- [ ] 9.5 Red: `CupSessionRotationTest` "a world that cannot be opened logs one ERROR and the rotation moves to the next cup that opens" (`feat(race)`)
- [ ] 9.6 Red: `CupSessionRotationTest` "a world is never released while a racer stands in it, because the hand-off comes first" (`feat(race)`)

## 10. Green: arming, release and the round

- [ ] 10.1 Green: `CupSession` arms the next cup when the results have lingered and the hand-off is done; it writes the state through the store and pins the armed cup at round start (`feat(race)`)
- [ ] 10.2 Green: `MapInstances.discard` releases the previous cup's worlds, except shared ones, before `MapInstances.open` opens the armed cup's worlds (`feat(race)`)

## 11. Red: the lobby and the tutorial

- [ ] 11.1 Red: `WaitingRoomTest` "the sidebar and the hologram name the armed cup from the tick it is armed, not before" (`feat(race)`)
- [ ] 11.2 Red: `WaitingRoomTest` "without a rotation the lobby shows the cup the holder currently holds, as before" (`feat(race)`)
- [ ] 11.3 Red: `TutorialBookTest` "the sixth page names the armed cup and lists its maps in play order" (`feat(race)`)
- [ ] 11.4 Red: `TutorialBookTest` "the sixth page keeps the sidebar's line limit and counts the rest" (`feat(race)`)
- [ ] 11.5 Red: `TutorialBookTest` "the book stores nothing about the racer and builds the sixth page on each open" (`feat(race)`)
- [ ] 11.6 Red: `MessageBundleParityTest` passes only when the new keys exist in `voyager_en_US` and `voyager_de_DE`, German in informal "du" (`feat(race)`)

## 12. Green: the lobby and the tutorial

- [ ] 12.1 Green: `WaitingRoom` and `ServerBeans` read the armed cup's board; the hologram spawns from it at boot (`feat(race)`)
- [ ] 12.2 Green: `TutorialBook` builds page six from the board on open; `TutorialPages.COUNT` becomes five fixed pages plus one (`feat(race)`)
- [ ] 12.3 Green: the new keys in `voyager_en_US.properties` and `voyager_de_DE.properties` (`feat(race)`)

## 13. Checks

- [ ] 13.1 Run `./gradlew :voyager:platform:test :voyager:race:test :voyager:server:test :voyager:fitness:test`; confirm the golden cup transcripts are unchanged for a single-cup server and say so in the PR (`feat(race)`)
- [ ] 13.2 Run `./gradlew build` for both trees (`feat(race)`)
- [ ] 13.3 Smoke boot the server shadow JAR at `-Xmx1024M` (the live heap) with `VOYAGER_CUP_ROTATION=alpha_cup,legacy_cup`, `VOYAGER_CUP_ROTATION_STATE` set to a file in the scratch directory, and the worlds `PalmBeach`, `DragonLobby` and `Memoria` absent. Expect one WARN naming `legacy_cup`, the boot plays `alpha_cup`, and `Listening on` and `Voyager started` appear. Quote the lines and the boot time (`feat(race)`)
- [ ] 13.4 Smoke run in dev mode with the same heap: finish `alpha_cup`, let the results linger, and confirm the server stays up, arms `legacy_cup` with its worlds present, releases `ElytraraceBlueAndRed`, and writes the state file. Then send `stop`, start again, and confirm the boot plays `legacy_cup`. Record the heap line before and after the arm (`feat(race)`)

## 14. Docs

- [ ] 14.1 Write `docs/decisions/0041-cup-rotation-state.md` as Proposed, from the options in design.md. The owner accepts it later (`docs`)
- [ ] 14.2 Document `VOYAGER_CUP_ROTATION` and `VOYAGER_CUP_ROTATION_STATE` in `docs/reference/configuration.md`, with the example state path `/home/cloud/cloudnet/local/voyager-state/rotation` (`docs`)
- [ ] 14.3 Update `docs/guides/how-to-deploy-on-cloudnet.md`: the in-process rotation, the state file as the way to keep the position across a deploy, and the operator's directory on mc01 (`docs`)
- [ ] 14.4 Update `docs/reference/hud.md` and `docs/reference/message-style-guide.md` for the armed-cup sidebar line and the sixth tutorial page (`docs`)

## 15. Ship

- [ ] 15.1 Open the code pull request under the title `feat(race): play the next cup of the rotation after each cup`. Write the description as the racer sees it: the next cup shown while they wait, and the cup they play after the last one. Include the Red messages (1.5), the smoke lines (13.3 and 13.4) and the boot time. Do not add a session link. No file under `openspec/` (`feat(race)`)
