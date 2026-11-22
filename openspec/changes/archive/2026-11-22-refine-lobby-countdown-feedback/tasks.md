# Tasks

Commit type and scope: `fix(race)` for the platform code and its tests, `docs` for `docs/` pages. Each behaviour gets a failing
test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleeps, no clock reads (the countdown is
ticked explicitly through `WaitingRoomTest`'s fixture), a fresh fixture per test, no shared static state. Task checkboxes are
ticked in the follow-up PR, never in the code PR.

## 1. Red: the full-lobby chime and the item timing

- [ ] 1.1 Red: `LobbyCountdownSoundsTest` "the full-lobby sound is the note-block chime at volume 0.8 and pitch 1.0" (`fix(race)`)
- [ ] 1.2 Red: `LobbySoundsDistinctTest` "the full-lobby chime is the only lobby sound that shares a race sample" (`fix(race)`)
- [ ] 1.3 Red: `WaitingRoomTest` "the lobby items are still held in the committed window and are removed on the GO tick" (`fix(race)`)
- [ ] 1.4 Red: run 1.1 to 1.3 and confirm each fails for the stated reason (xylophone key, missing exception, items gone at
  COMMITTED); record the failure messages in the code PR description (`fix(race)`)

## 2. Green: the chime and the item timing

- [ ] 2.1 Green: `LobbyCountdownSounds` plays `block.note_block.chime` for the full lobby, and its Javadoc states the exception
  from `race/lobby-feedback` (`fix(race)`)
- [ ] 2.2 Green: `WaitingRoom.announce` removes the lobby items only when the situation is `RUNNING` (`fix(race)`)
- [ ] 2.3 Refactor: confirm 1.1 to 1.3 pass, and that the other sound and item tests are unchanged and still pass (`fix(race)`)

## 3. Docs

- [ ] 3.1 Update `docs/reference/hud.md` (the lobby items paragraph, around the line that says a waiting racer holds a leave
  door) and `docs/reference/message-style-guide.md` (the sound table) to say the items stay until GO and the full lobby plays
  the chime (`docs`)

## 4. Checks

- [ ] 4.1 Run `./gradlew :voyager:platform:test`, `./gradlew :voyager:server:test` and `./gradlew :voyager:fitness:test`; confirm
  the golden lobby transcript is unchanged (`fix(race)`)
- [ ] 4.2 Run `./gradlew build` for both trees (`fix(race)`)
- [ ] 4.3 Smoke-boot the server shadow JAR with LuckPerms as the apply rules require, and quote the start lines in the code PR
  (`fix(race)`)

## 5. Ship

- [ ] 5.1 Open the code pull request under the title `fix(race): play a chime when the lobby fills and keep lobby items until go`.
  Write the description as the racer sees the lobby: the chime when the lobby fills, and the items that stay until the race
  starts. Include the Red messages (1.4) and the smoke lines (4.3). Do not add a session link (`fix(race)`)
