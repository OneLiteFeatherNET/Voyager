# Tasks

Commits follow the types the change names: `feat(race)` for every code change and its tests, `docs(openspec)` for
artifact edits, `docs` for `docs/` pages and ADRs. Each behaviour gets a failing test (Red) before its production code
(Green). Every test follows F.I.R.S.T.: no sleeps, `Clock` injected, `@TempDir` for worlds, a fresh Minestom instance per
test with explicit ticks.

## 1. Decisions and owner answers

- [x] 1.1 Record the owner's answers OQ1 to OQ5 as decisions in design.md, "Owner decisions (2026-10-12)" (`docs(openspec)`)
- [ ] 1.2 Owner accepts ADR-0029 and its Status line changes from Proposed to Accepted (`docs`) (Not accepted yet: ADR-0029 stays Proposed until the owner has playtested it.)

## 2. Operator task: the lobby world (not code; the source stays untouched)

- [x] 2.1 Copy `/mnt/projects/lab/voyager-legacy-worlds/team-build/ElytraLobby` into a run directory outside `/mnt/projects/lab`, and remove `session.lock`, `paper-world.yml` and `bluemap.id` from the copy, as the converted maps did (Done on the live node by the operator.)
- [x] 2.2 Upgrade the copy to 26.2 with the vanilla server `--forceUpgrade --eraseCache --nogui`, as `converted/tools/upgrade.sh` does; record the DataVersion before and after and keep the upgrade log (Done on the live node by the operator.)
- [x] 2.3 Read the spawn from the copy's `level.dat`, confirm its chunk is fully generated, and run `-Dvoyager.config.check=true` with `VOYAGER_LOBBY_WORLD` and `VOYAGER_LOBBY_SPAWN` set; record the boot memory of the held lobby world in the PR description (Done on the live node by the operator. The smoke boots below are not this task.)

Operator note (2026-10-13, from the operator session): ElytraLobby was upgraded to 26.2 (DataVersion 4903) with vanilla
`forceUpgrade`; the original is unchanged. Spawn -10,105,-39 stands on `waxed_oxidized_copper` with 2 blocks of air
above, configured as `VOYAGER_LOBBY_SPAWN=-9.5,105,-38.5`. The lobby has 16 regions and 8676 chunks. Heap after boot is
532 MB of 1024 MB with the lobby and 166 MB without. v1.15.0 runs live with the lobby and cups have started. The owner
playtest (10.2) and ADR-0029 acceptance (1.2) remain open.

## 3. api: the lobby definition

- [x] 3.1 Red: `LobbyDefinitionTest` names the behaviours "a blank world name is refused", "a lobby keeps its world and spawn", and "two definitions with the same world and spawn are equal" (`feat(race)`)
- [x] 3.2 Green: add `LobbyDefinition(String world, Vec3 spawn)` in `net.elytrarace.voyager.api.race` and `InvalidLobbyException` in `api.race.exception` with its `package-info.java` (`feat(race)`)

## 4. race: the full-lobby countdown rule

- [x] 4.1 Spike, no commit: check whether `XerusPhaseDriver` can change the remaining time of a running `LOBBY` phase. Record the result in design.md D5 and, if the answer is no, record the `CupSession` deadline fallback there
- [x] 4.2 Red: `StartGateShortenTest` names the behaviours "a full lobby with fifteen seconds left shortens to ten", "a full lobby with six seconds left is unchanged", "a committed start is never shortened", "a lobby below the maximum is unchanged", and "a countdown is never lengthened" (`feat(race)`)
- [x] 4.3 Green: add `StartGate.FULL_LOBBY_COUNTDOWN` (10 s) and `StartGate.shortenedCountdown(...)` as a pure function with no clock (`feat(race)`)

## 5. platform: text and the lobby boss bar

- [x] 5.1 Red: `Messages` tests name the behaviours "the join line reads name, count and maximum", "the waiting boss bar reads the count of the minimum", and "the countdown boss bar reads the seconds left" (`feat(race)`)
- [x] 5.2 Green: add `voyager.lobby.player.join`, `voyager.lobby.bossbar.waiting` and `voyager.lobby.bossbar.countdown` to `voyager_en_US.properties` with `<arg:N>` placeholders, and the matching `Messages` factories (`feat(race)`)
- [x] 5.3 Red: `LobbyHudTest` names the behaviours "the boss bar shows only in the lobby instance", "an unchanged title and progress send no update", and "the bar is hidden when the racer leaves the lobby" (`feat(race)`)
- [x] 5.4 Green: add `platform.lobby.LobbyHud` owning one boss bar per racer (`feat(race)`)

## 6. platform: the cup's countdown and the lobby transfer

- [x] 6.1 Red: `CupSessionShortenLobbyTest` drives explicit ticks and names the behaviours "shortening takes effect on the next tick", "a committed lobby is not shortened", and "a shortened lobby is not lengthened" (`feat(race)`)
- [x] 6.2 Green: add `CupSession.shortenLobby(Duration)` by the mechanism chosen in 4.1 (`feat(race)`)
- [x] 6.3 Red: `LobbyTransitionTest` names the behaviours "a racer moves from the lobby to the first map with a fresh run", "a finished cup returns racers to the lobby spawn", and "a racer already in the lobby is repositioned" (`feat(race)`)
- [x] 6.4 Green: extract the arrival step (chunk wait, confirmed teleport) from `MapTransition` into a package-private helper in `platform.world`, with no change in behaviour; add `platform.lobby.LobbyTransition` on that helper. `MapTransitionTest` must pass unchanged (`feat(race)`)

## 7. platform: the waiting room

- [x] 7.1 Red: extend `WaitingRoomTest` with "a join sends the join line to every racer", "a full lobby calls shortenLobby", "a finished cup returns racers through LobbyTransition", and "a join beyond the maximum is not refused" (`feat(race)`)
- [x] 7.2 Green: `WaitingRoom` takes the maximum, sends the join line, calls `shortenLobby` through `StartGate.shortenedCountdown`, uses `LobbyTransition` for the return, and drives `LobbyHud` instead of the action bar (`feat(race)`)
- [x] 7.3 Update the existing tests that assert the waiting or countdown action bar (`WaitingRoomTest`, `CupSessionLobbyTest`) to the boss bar and the join line, red first, then green (`feat(race)`)

## 8. server: settings and the configuration check

- [x] 8.1 Red: `ServerSettingsLobbyTest` names the behaviours "both lobby settings are read", "a world without a spawn is refused and names the spawn", "a spawn that is not three numbers is refused", "the maximum defaults to 16", and "a maximum below the minimum is refused and names both" (`feat(race)`)
- [x] 8.2 Green: add the lobby and maximum components to `ServerSettings` with the existing compatibility constructors, and the setting exception in `server.config.exception` (`feat(race)`)
- [x] 8.3 Red: `ConfigCheckLobbyTest` names the behaviours "no lobby is a warning", "a lobby that names a map's world is an error", "an unreadable lobby world is an error", and "two worlds are checked with one open at a time" (`feat(race)`)
- [x] 8.4 Green: add the lobby checks to `ConfigCheck`, keyed by setting name, using the boot world rule of `specs/server/lobby-world` (`feat(race)`)

## 9. server: wiring, boot and fitness

- [x] 9.1 Red: `LobbyWiringTest` on a fresh Minestom instance, with a lobby world in a `@TempDir` and explicit ticks, names the behaviours "a joining racer spawns in the lobby instance", "without a lobby a joining racer spawns in the first map", "the lobby world is loaded at boot", and "the lobby world is not saved on stop" (`feat(race)`)
- [x] 9.2 Green: in `VoyagerServer`, load the lobby world in `openGraph` after the cup's worlds, set the spawning instance and respawn point to the lobby, give `WaitingRoom` the lobby and the maximum, and log the no-lobby warning (`feat(race)`)
- [x] 9.3 Red then green: add a characterisation `golden/cup-session/lobby-world-start.txt` for the lobby-to-first-map move and the return at cup end; the existing golden transcripts must not change (`feat(race)`)
- [x] 9.4 Red then green: add the ArchUnit rule `onlyServerConfigReadsLobbySettings` in `CompositionRootRulesTest` with `allowEmptyShould(false)`; it passes only once 8.2 is done (`feat(race)`)

## 10. Verification

- [x] 10.1 Run `./gradlew build` for both trees, plus `:voyager:race:test`, `:voyager:platform:test`, `:voyager:server:test` and `:voyager:fitness:test`; confirm `CupSessionGoldenMasterTest` and the existing transcripts are byte-identical
- [ ] 10.2 Run `./gradlew :voyager:server:validateCatalog` and `:voyager:server:runServer` on the playtest server with the operator's lobby world; check a join line, the boss bar, the countdown shortening at a maximum of two, the transfer to the first map, and the return to the lobby at cup end (Partly: `validateCatalog` passed against the smoke catalogue with the expected no-lobby warning. The playtest on the operator's lobby world is open, with 2.1 to 2.3.)

## 11. Documentation

- [x] 11.1 Update `docs/reference/config-check.md` with `VOYAGER_LOBBY_WORLD`, `VOYAGER_LOBBY_SPAWN` and `VOYAGER_MAX_PLAYERS`, and the no-lobby warning (`docs`)
- [x] 11.2 Update `docs/guides/how-to-run-a-playtest.md` with the lobby world and the boss bar (`docs`)
- [x] 11.3 Record the research of the lobby count and countdown in `docs/research/` (`docs`)
- [x] 11.4 Edit ADR-0022's Status to "Superseded by ADR-0029" after 1.2 is done; this change does not edit 0022 before then (`docs`)
- [x] 11.5 Sync the delta specs into `openspec/specs/` when the change ships with its implementation, and do not write a separate archive commit (`docs(openspec)`)

## 12. Ship

- [ ] 12.1 Open the pull request from this branch under the title `feat(race): add a dedicated lobby world for waiting racers`, with the boot memory from 2.3 and the playtest result from 10.2 in the description. End the description with exactly two lines: first `https://claude.ai/referral/m5Ak2Sa7aQ`, then `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`
