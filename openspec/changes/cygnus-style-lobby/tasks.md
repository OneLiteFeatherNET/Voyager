# Tasks

Commit types: `feat(race)` for the platform, server and their tests, and for the bundles; `docs` for `docs/` pages and the ADR
file; `build` for the Pica dependency and its catalogue entry. A golden update is its own commit inside the same PR. Each
behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleeps, no clock
reads (the countdown is ticked explicitly), a fresh `WaitingRoom` per test, no shared static state. Task checkboxes are ticked
in the follow-up PR, never in the code PR.

## 1. Decisions and records

- [ ] 1.1 Record in the PR description the list of LOBBY-phase `actionbar` lines that change in `golden/cup-session/` (design, "Golden masters") before the golden update is reviewed (`docs`)
- [ ] 1.2 Confirm `docs/decisions/` still has no `0039-*` file on `origin/main` at the time the code PR opens; if one exists, take the next free number and update the design (`docs`)

## 2. Spikes (no commit; each result goes into the code PR description)

- [ ] 2.1 Confirm the client shows at most fifteen sidebar lines on Minestom 2026.08.28-26.2, and record the line limit used by the sidebar of `LobbySidebar` (`docs`)
- [ ] 2.2 Confirm whether `Sidebar.ScoreboardLine` has a blank number format on Minestom 2026.08.28-26.2. If not, record the fallback chosen in design decision 5 (`docs`)
- [ ] 2.3 Confirm that `BLOCK_NOTE_BLOCK_PLING`, `ENTITY_ITEM_PICKUP` and `BLOCK_NOTE_BLOCK_CHIME` are not the sample of the ring bell, last-ring chime, reset, DNF, missed ring or race countdown beat in `RaceFeedback` (`docs`)
- [ ] 2.4 Open a Pica 0.1.6 multi-page dialog with Back, Next and Close on a throwaway server built on Minestom 2026.08.28-26.2 (no commit); record that the buttons work and that the dialog text follows the client locale (`docs`)
- [ ] 2.5 Record that `aonyx-bom` 0.8.9 cannot be read anonymously (401 on `repo.onelitefeather.dev/onelitefeather`, 404 on `releases`), so Pica is pinned explicitly (`docs`)

## 3. Red: lobby HUD, sidebar, hologram, sounds, items, tutorial

- [ ] 3.1 Red: `LobbyHudTest` "one racer short of two shows the missing count", in English ("Need 1 more player to start") and German ("Noch 1 Spieler bis zum Start") (`feat(race)`)
- [ ] 3.2 Red: `LobbyHudTest` "the experience level is the whole seconds left and the bar is the fraction left", and "a cancelled countdown clears the level and the bar" (`feat(race)`)
- [ ] 3.3 Red: `LobbyHudTest` "the lobby HUD never shows a boss bar", in the lobby and in the first map without a lobby (`feat(race)`)
- [ ] 3.4 Red: `LobbySidebarTest` "the title is the display name", "the racers line reads 3/16", "fifteen maps show nine and +6 more", "no line carries a score number", and "the status line reads starts in N s" (`feat(race)`)
- [ ] 3.5 Red: `LobbyHologramTest` "the hologram text has no more-maps line and names only the display names" (`feat(race)`)
- [ ] 3.6 Red: `LobbyCountdownSoundsTest` "the pling plays at 10, 5, 3, 2, 1 and 0 seconds with the table's volume and pitch" and "no pling at seven seconds" (`feat(race)`)
- [ ] 3.7 Red: `LobbyCountdownSoundsTest` "the soft join sound reaches the waiting racers and not the joining racer", and "the chime plays once when the lobby shortens" (`feat(race)`)
- [ ] 3.8 Red: `LobbySoundsDistinctTest` "no lobby sound is a race sound sample" (`feat(race)`)
- [ ] 3.9 Red: `LobbyItemsTest` "a joining racer receives the door in slot 8 and the book in slot 1", "the items are removed at the GO tick", "a drop and a swap are cancelled", and the names in both languages (`feat(race)`)
- [ ] 3.10 Red: `LeaveCommandTest` "the leave item and /leave hand the racer to the hub and do not record a handoff", and "/hub is refused in a map with a translated line" (`feat(race)`)
- [ ] 3.11 Red: `TutorialPagesTest` "five pages in the stated order", "Back is absent on the first page and Next on the last", and "reopening starts at page one" (`feat(race)`)
- [ ] 3.12 Red: `TutorialBookTest` "no dialog opens at join" and "a right click on the book opens page one" (`feat(race)`)
- [ ] 3.13 Red: `voyager-fitness` rule "only the tutorial package imports Pica", with `allowEmptyShould(false)`; it fails until the package exists (`feat(race)`)
- [ ] 3.14 Red: confirm 3.1 to 3.13 fail for the stated reason, and record the messages in the PR (`feat(race)`)

## 4. Green: lobby HUD, sidebar, hologram and sounds

- [ ] 4.1 Green: `LobbyHud` sends the action bar and the experience bar per racer, and creates no boss bar (`feat(race)`)
- [ ] 4.2 Green: `LobbyBoard`, `LobbySidebar` (title, racers line, ten map lines, status, footer, no score numbers, using the fallback from 2.2 when needed) (`feat(race)`)
- [ ] 4.3 Green: `LobbyHologram` shows the display names only (`feat(race)`)
- [ ] 4.4 Green: `LobbyCountdownSounds` (pure table) wired into the lobby tick of `WaitingRoom` (`feat(race)`)
- [ ] 4.5 Green: the join sound and the full-lobby chime (`feat(race)`)
- [ ] 4.6 Green: add every lobby, item and tutorial key to `voyager_en_US.properties` and `voyager_de_DE.properties` with the same placeholders and palette tags (`feat(race)`)
- [ ] 4.7 Refactor: remove the hard-coded lines `Messages.java:306` and `:318` and the boss bar keys `voyager.lobby.bossbar.*` once nothing reads them (`feat(race)`)

## 5. Green: ADR, dependency, items, commands and tutorial

- [ ] 5.1 Add `docs/decisions/0039-tutorial-dialogs-with-pica.md` from the ADR text in the design, with status Proposed (`docs`)
- [ ] 5.2 Add `pica` 0.1.6 to the programmatic version catalogue in `settings.gradle.kts` and `implementation(libs.pica)` to `voyager/platform/build.gradle.kts` (`build`)
- [ ] 5.3 Green: `LobbyItems` gives the items on join, removes them at the GO tick, cancels drop and swap, and is registered on an event node by the server (`feat(race)`)
- [ ] 5.4 Green: `LeaveCommand` and `HubCommand` in `voyager-server`, registered with the other commands in `VoyagerServer` (`feat(race)`)
- [ ] 5.5 Green: `TutorialPages` (the pure page model) and `TutorialBook` (Pica dialogs) in `platform.tutorial` (`feat(race)`)
- [ ] 5.6 Green: a right click on the book opens page one for that racer; nothing else opens it (`feat(race)`)
- [ ] 5.7 Refactor: confirm that only `platform.tutorial` imports Pica, and that `voyager-fitness` passes (`feat(race)`)

## 6. Golden masters and checks

- [ ] 6.1 Run `CupSessionGoldenMasterTest`; confirm only the LOBBY-phase `actionbar` lines differ, and review each line (`feat(race)`)
- [ ] 6.2 Update those lines in `voyager/server/src/test/resources/golden/cup-session/` in a separate commit (`feat(race)`)
- [ ] 6.3 Run `MessageBundleParityTest` and confirm the new keys exist in both bundles (`feat(race)`)
- [ ] 6.4 Run `./gradlew :voyager:fitness:test` and `./gradlew build` on both trees (`feat(race)`)

## 7. Client check

- [ ] 7.1 On the dev server, capture the lobby (action bar, experience bar, sidebar), the hotbar items and the tutorial pages, in `en_US` and `de_DE`; attach them to the code PR (`feat(race)`)

## 8. Docs

- [ ] 8.1 Update the lobby description in `docs/reference/` (find it with a search for "lobby") to the new displays, items and tutorial (`docs`)
- [ ] 8.2 Update the colour rules for lobby lines in `docs/reference/message-style-guide.md` (`docs`)

## 9. Ship

- [ ] 9.1 Open the code pull request under the title `feat(race): rework the waiting lobby with items, a tutorial and an XP countdown`. Put the spike results (2.1 to 2.5), the golden line list (1.1) and the screenshots (7.1) in the description, and write it as the racer sees the lobby. Do not add a session link (`feat(race)`)
