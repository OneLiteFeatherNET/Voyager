# Tasks

Commits follow the types the change names. `feat(race)` for the api, platform and server code and its tests. `feat(cloudnet)`
for the bridge module and its tests (same type, another scope). `refactor(race)` for removing dead code. `docs` for `docs/` pages
and ADRs. `docs(openspec)` for artifact edits. Each behaviour gets a failing test (Red) before its production code (Green). Every
test follows F.I.R.S.T.: no sleeps, no clock reads, explicit ticks, a fresh Minestom instance per test, `@TempDir` for worlds.

## 1. Decisions and owner answers

- [ ] 1.1 Record the owner's answers to design.md Q1 to Q9 in design.md, "Owner decisions" (`docs(openspec)`)
- [ ] 1.2 Owner accepts ADR-0033; its Status changes from Proposed to Accepted (`docs`)
- [ ] 1.3 After 1.2, mark ADR-0029 "Amended by ADR-0033" (its return rule is replaced; its Status stays Proposed) and add an amendment note to ADR-0026 (the bridge names `api.handoff`) (`docs`)
- [ ] 1.4 After 1.2 and only with the owner's approval of Q1, amend the greenfield design paragraph "the extension names no Voyager type" in `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` (`docs`)

## 2. Spikes on the owner's node (no commit; results go into design.md section 2)

- [ ] 2.1 Spike: a throwaway extension in the node's `extensions/` logs `PlayerHandoffs.class` identity against the root's, from `ExtensionClassLoader` parent-first delegation. Record the result in design.md section 2.2. If the identity differs, stop and reopen Q1.
- [ ] 2.2 Spike: call `connectToFallback()` for a test racer with a configured `ProxyFallback` and without one. Record in design.md Q4 what the racer sees and what the log shows.

## 3. api: the handoff port

- [ ] 3.1 Red: `PlayerHandoffsTest` names the behaviours "an empty holder answers no handoff", "an installed handoff is the one returned", "a later install replaces the earlier one", and "uninstall clears the holder". Each test uses its own install and uninstalls in `@AfterEach` so no test depends on another (`feat(race)`)
- [ ] 3.2 Green: add `PlayerHandoff`, `PlayerHandoffs` and `exception.HandoffUnavailableException` in `net.elytrarace.voyager.api.handoff`, each package with a `package-info.java` (`feat(race)`)
- [ ] 3.3 Red: add `handoffPortDependsOnlyOnJdkAndApi` and `onlyTheBridgeInstallsAPlayerHandoff` to the fitness module, with `allowEmptyShould(false)`. The first fails because the package does not exist yet; the second fails until task 8 (`feat(race)`)
- [ ] 3.4 Green: the first rule passes with the port from 3.2; the second passes once the bridge has the only install call (task 8.2) (`feat(race)`)

## 4. platform: the hub handoff and the results linger

- [ ] 4.1 Red: `HubHandoffTest` names the behaviours "an installed handoff receives every racer", "without an installed handoff each racer is disconnected with the translated message", and "a refused handoff disconnects with the same message and logs a warning naming the racer". The test installs a fake handoff and uninstalls it afterwards (`feat(race)`)
- [ ] 4.2 Green: add `platform.handoff.HubHandoff` (uses `PlayerHandoffs.current()`, falls back to a disconnect) and the translated key `voyager.handoff.disconnect` in `voyager_en_US.properties`, with a `Messages` factory (`feat(race)`)
- [ ] 4.3 Red: extend `WaitingRoomTest` with the explicit-tick behaviours "a finished cup hands racers online to the hub after 200 ticks", "a racer who joins during the linger is handed off too", "nothing is handed off on tick 199 after the result", "a running cup hands nobody off", and "a finished cup with nobody online hands nobody off" (`feat(race)`)
- [ ] 4.4 Green: `WaitingRoom` replaces the return branch with `HubHandoff` after `RESULTS_LINGER_TICKS` (200), counted in ticks, with no clock. It keeps the lobby field for joins (`feat(race)`)
- [ ] 4.5 Red then green: replace the test "a finished cup returns racers through LobbyTransition" in `WaitingRoomTest` and "a finished cup returns racers to the lobby spawn" in `LobbyTransitionTest`. Regenerate the golden `golden/cup-session/lobby-world-start.txt` for the cup end; the other golden transcripts must not change (`feat(race)`)
- [ ] 4.6 Refactor: remove `LobbyTransition.returnTo` once no caller is left, keeping `instance()` and `spawn()` for joins (`refactor(race)`)

## 5. platform: the cup name on the boss bar

- [ ] 5.1 Red: `Messages` tests name the behaviours "the waiting boss bar names the cup, the count and the minimum" and "the countdown boss bar names the cup and the seconds left" (`feat(race)`)
- [ ] 5.2 Green: add `voyager.lobby.bossbar.waiting.cup` and `voyager.lobby.bossbar.countdown.cup` with `<arg:N>` placeholders, the matching `Messages` factories, and `WaitingRoom` passes the cup name. The old two keys are removed in the same commit once nothing uses them (`feat(race)`)

## 6. platform: the lobby board, sidebar and hologram

- [ ] 6.1 Red: `LobbyBoardTest` names the behaviours "the board lists the maps in play order", "a cup of fifteen maps shows twelve and a +3 more line", "a cup of five maps shows no more line", "the board shows the pending cup while waiting after a reload", and "the board keeps the pinned cup during a countdown" (`feat(race)`)
- [ ] 6.2 Green: add `platform.lobby.LobbyBoard`, a pure view model with no Minestom type beyond `Component` (`feat(race)`)
- [ ] 6.3 Red: `LobbySidebarTest` on a fresh Minestom instance with explicit ticks names the behaviours "the sidebar shows while the room waits", "the sidebar is removed when a racer enters a map", and "no sidebar is shown while a cup races" (`feat(race)`)
- [ ] 6.4 Green: add `platform.lobby.LobbySidebar`, one Minestom `Sidebar` per racer, driven by `WaitingRoom.announce` (`feat(race)`)
- [ ] 6.5 Red: `LobbyHologramTest` names the behaviours "the hologram stands at the configured position in the lobby instance", "its text follows the board when the cup changes", and "no hologram is spawned without a position" (`feat(race)`)
- [ ] 6.6 Green: add `platform.lobby.LobbyHologram`, one `TextDisplay` entity updated from `LobbyBoard` (`feat(race)`)

## 7. server: settings, configuration check and boot

- [ ] 7.1 Red: `ServerSettingsHologramTest` names the behaviours "the hologram position is read", "a position that is not three numbers is refused and names the setting", "a position without a lobby world is refused and names both settings", and "no position is no hologram" (`feat(race)`)
- [ ] 7.2 Green: add the hologram setting to `ServerSettings` with the existing compatibility constructors, and `InvalidHologramException` in `server.config.exception` with its `package-info.java` (`feat(race)`)
- [ ] 7.3 Red: `ConfigCheckHologramTest` names the behaviours "no hologram is no problem", "a hologram without a lobby is an error that names both", and "a hologram of two numbers is an error that names the setting" (`feat(race)`)
- [ ] 7.4 Green: add the hologram checks to `ConfigCheck`, keyed by the setting name (`feat(race)`)
- [ ] 7.5 Red: `LobbyWiringTest` names the behaviours "the hologram is spawned in the lobby instance at boot" and "the hologram is not saved when the server stops" (`feat(race)`)
- [ ] 7.6 Green: `VoyagerServer` spawns the hologram after the lobby world is loaded, and passes `LobbySidebar`, `LobbyHologram` and `HubHandoff` to the room (`feat(race)`)

## 8. cloudnet-bridge: the hub connector (`feat(cloudnet)`)

- [ ] 8.1 Red: `CloudNetPlayerHandoffTest` uses a fake `FallbackConnector` (no CloudNet type on the test classpath) and names the behaviours "a handoff calls the connector with the player's id", "a connector failure becomes HandoffUnavailableException", and "a handoff never kicks the player" (`feat(cloudnet)`)
- [ ] 8.2 Green: add `CloudNetPlayerHandoff` and the `FallbackConnector` seam. `VoyagerCloudNetExtension.initialize` builds the production connector with `ServiceRegistry.registry().defaultInstance(PlayerManager.class).playerExecutor(uuid).connectToFallback()`, and calls `PlayerHandoffs.install` (`feat(cloudnet)`)
- [ ] 8.3 Build: add `compileOnly(project(":voyager:api"))` to `voyager/cloudnet-bridge/build.gradle.kts` and the task `verifyNoVoyagerApiInBridgeJar`, wired to `check`. Red first: a bridge jar that bundles `PlayerHandoffs.class` fails the task (`feat(cloudnet)`)
- [ ] 8.4 Red then green: in `CloudNetBoundaryTest`, `bridgeDependsOnlyOnJdkAndLibraries` must still fail for `api.race`; widen it to allow `net.elytrarace.voyager.api.handoff..` only (`feat(cloudnet)`)

## 9. Documentation (`docs`)

- [ ] 9.1 `docs/reference/config-check.md`: `VOYAGER_LOBBY_HOLOGRAM`, its rules and the two refusals
- [ ] 9.2 `docs/reference/hud.md`: the lobby sidebar, the hologram and the boss bar text naming the cup
- [ ] 9.3 `docs/guides/how-to-run-a-playtest.md`: what a racer sees in the lobby, the hub handoff after a cup, and the local disconnect message
- [ ] 9.4 `docs/guides/how-to-deploy-on-cloudnet.md`: the proxy needs a fallback for the game's group, otherwise `connectToFallback` has no target (the answer to Q4)
- [ ] 9.5 `docs/research/`: a research note on the handoff investigation (the RC17 call, the fork's class loader, the linger), as Lumen's entry
- [ ] 9.6 `docs/decisions/0033-lobby-cup-display-and-hub-handoff.md` status and consequences updated with the answers (`docs`)

## 10. Verification

- [ ] 10.1 `./gradlew build` for both trees, plus `:voyager:api:test`, `:voyager:platform:test`, `:voyager:server:test`, `:voyager:cloudnet-bridge:test` and `:voyager:fitness:test`. Confirm `verifyNoCloudNetInFatJars` and `verifyNoVoyagerApiInBridgeJar` pass, and that `CupSessionGoldenMasterTest` is unchanged apart from 4.5
- [ ] 10.2 `./gradlew :voyager:server:validateCatalog`, then `./gradlew :voyager:server:runServer` on the playtest server: a lobby sidebar, the hologram, the boss bar naming the cup, a cup end that leaves the racers in the hub, and the local disconnect message when no bridge runs. Owner-run (not CI); record the result in the PR description

## 11. Archive and sync

- [ ] 11.1 Sync the delta specs into `openspec/specs/` when the change ships with its implementation, and do not write a separate archive commit (`docs(openspec)`)

## 12. Ship

- [ ] 12.1 Open the pull request from this branch under the title `feat(race): show the cup in the lobby and hand finished racers to the hub`. Put the spike results (2.1, 2.2) and the playtest result (10.2) in the description. End the description with exactly two lines: first `https://claude.ai/referral/m5Ak2Sa7aQ`, then `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`
