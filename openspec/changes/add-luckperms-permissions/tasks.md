# Tasks

Every behaviour starts with a failing test (Red), then the production code (Green), then a refactor. Each test follows F.I.R.S.T.
(no sleeps, injected `Clock` where time matters, fresh fixtures, no shared static state). The fitness suite runs after every group.

## 1. Feasibility spike (throwaway, nothing merged)

Spike code lives on a scratch branch and is deleted before group 2. Nothing from this group is merged.

- [x] 1.1 Spike: in a scratch copy of `voyager-server`, add `net.luckperms:minestom-loader:5.6-SNAPSHOT` as runtimeOnly, start `MinestomLoader.get().load().registerShutdownHook().start()` after `MinecraftServer.init()`, and let one offline-mode test player join. Verify: the LuckPerms start line appears in the log and `LuckPermsProvider.get().getUserManager().getUser(uuid)` returns a user for a user created by the spike. Fallback if this fails: ship `LevelPermissionPolicy` only and follow the fallback described in design.md, Risks; stop before group 2 and report to the owner.
- [x] 1.2 Spike: record the directory LuckPerms uses for its config and its H2 file on the Minestom loader, and whether it can be set to a path that Voyager chooses. Verify: the path is written in the spike notes, and is the value `add-cloudnet-deployment` will use for `<service>/data`.
- [x] 1.3 Spike: compare the `net/luckperms/api` classes inside the loader jar with `net.luckperms:api:5.5` (`javap` on `LuckPerms`, `ContextManager`, `CachedPermissionData`, `UserManager`). Verify: the list of methods the adapter calls exists in both; record any difference (design.md, Risk R2).
- [x] 1.4 Spike: start the server with `MinecraftServer.init(new Auth.Velocity("spike-secret"))` and connect once with a Velocity-style forwarded login through Minestom's test environment (or, if the harness cannot forge the handshake, record that the check moves to the acceptance group 12). Verify: the player's UUID equals the forwarded UUID.
- [x] 1.5 Checkpoint: write the four spike results into design.md "Risks", replacing the placeholders in R3, and stop. If 1.1 failed, ask the owner before continuing, because the approach changes.

## 2. Decisions recorded as ADRs (status Proposed)

- [ ] 2.1 Write `docs/decisions/0024-permission-port-with-luckperms-adapter.md` in MADR 4.0. Context: the permission port in `voyager-api`, the LuckPerms adapter in `voyager-platform`, the level-based fallback and the rejected Cygnus fail-open fallback. Status: `Proposed`. Re-check at apply time that 0024 is still free in `docs/decisions/` and renumber if not. Verify: the file exists, `## Status` reads `Proposed`, and every MADR section is present.
- [ ] 2.2 Write `docs/decisions/0025-velocity-modern-forwarding-with-secret.md` in MADR 4.0. Context: Minestom `Auth.Velocity`, the secret sources, the offline fallback, and the CloudNet RC16 note that proxy authentication moved to the Minestom implementation. Status: `Proposed`. Verify: the file exists and `## Status` reads `Proposed`.

## 3. Fitness rules (Red first)

- [ ] 3.1 Red: add `voyager/fitness/src/test/java/net/elytrarace/fitness/PermissionBoundaryTest.java` with `luckPermsIsConfinedToItsAdapter` (classes depending on `net.luckperms..` or `me.lucko..` must reside in `net.elytrarace.voyager.platform.permission.luckperms..`) and `commandsAskOnlyThePolicy` (classes in `..server.command..` and `..setup.adapter..` must not depend on `..platform.permission.luckperms..`), both with `allowEmptyShould(false)`. Verify: `./gradlew :voyager:fitness:test` fails on `luckPermsIsConfinedToItsAdapter` with "failed to check any classes", because no class uses LuckPerms yet.
- [ ] 3.2 Red proof for the second rule: temporarily add an import of `net.elytrarace.voyager.platform.permission.luckperms.LuckPermsPolicy` to `RaceCommand` on the scratch branch. Verify: `commandsAskOnlyThePolicy` fails and names `RaceCommand`. Revert the import.
- [ ] 3.3 Green: the rules pass once groups 5 and 6 exist and no class outside the adapter package names LuckPerms. Verify: `./gradlew :voyager:fitness:test` passes; `FitnessCoverageTest` still accepts `voyager-platform`.

## 4. Permission port in voyager-api (Red then Green)

- [ ] 4.1 Red: `PermissionNodeTest` in `voyager/api/src/test` asserts that the enum holds exactly the five node values in the spec (`voyager.command.race.reload`, `voyager.command.race.start`, `voyager.command.race.skip`, `voyager.command.stop`, `voyager.setup.use`), that every value starts with `voyager.`, that `byValue` returns null for an unknown value, and that the values are unique. Verify: the test fails to compile because the enum does not exist.
- [ ] 4.2 Green: add `PermissionNode`, the sealed `PermissionSubject` (records `Console` and `Player`) and the interface `PermissionPolicy` (abstract `allows(PermissionSubject, String node)`, default `allows(PermissionSubject, PermissionNode)` that delegates with `node.value()`), in `net.elytrarace.voyager.api.permission`, with `package-info.java` carrying `@NotNullByDefault`. Verify: `4.1` passes; `ApiPurityTest` passes.
- [ ] 4.4 Red then Green: `PermissionPolicyFormsTest` uses a recording fake policy and asserts that the enum form passes `node.value()` to the string form, for every `PermissionNode` constant. Verify: the test fails before 4.2 and passes after it.
- [ ] 4.3 Red then Green: `PermissionSubjectPlayerTest` asserts that `Player` rejects an operator level below 0 and above 4 with `IllegalArgumentException` and accepts 0 and 4. Verify: the test fails first, then passes after the compact constructor is written.

## 5. Fallback policy and player mapping in voyager-platform (Red then Green)

- [ ] 5.1 Red: `LevelPermissionPolicyTest` asserts: the console is allowed every node; a player at level 4 is allowed every node; a player at level 3 and a player at level 0 are denied every node. Verify: the test fails because the class does not exist.
- [ ] 5.2 Green: add `LevelPermissionPolicy` in `net.elytrarace.voyager.platform.permission`. Verify: `5.1` passes.
- [ ] 5.3 Red then Green: `PlayerSubjectsTest` uses Minestom's test environment (fresh `Env` per test, explicit ticks) and asserts that `PlayerSubjects.of(player)` carries the player's UUID and `getPermissionLevel()`. Verify: the test passes on the Green step and the test environment reports no leaked connection.

## 6. LuckPerms adapter in voyager-platform (Red then Green)

- [ ] 6.1 Red: `LuckPermsPolicyTest` uses a fake `LuckPermsGateway` and asserts: no user for the UUID means deny; node TRUE means allow; node UNDEFINED means deny; node FALSE means deny. Verify: the test fails because the classes do not exist. The test imports no `net.luckperms` type.
- [ ] 6.2 Green: add `LuckPermsGateway` (interface, in `permission.luckperms`) and `LuckPermsPolicy`. Verify: `6.1` passes.
- [ ] 6.3 Red then Green: `NetLuckPermsGateway` is the only class in `permission.luckperms` that imports `net.luckperms`. Add it and `LuckPermsBootstrap` (`isPresent()` by `Class.forName` on `me.lucko.luckperms.minestom.loader.MinestomLoader` without initialisation; `start()` as in spike 1.1; a failed start throws `PermissionBackendStartException` in `permission.luckperms.exception`). Test `LuckPermsBootstrapTest` runs with the loader off the test class path and asserts `isPresent()` is false and `start()` is not reached. Verify: the test passes, and `./gradlew :voyager:fitness:test` passes `luckPermsIsConfinedToItsAdapter`.

## 7. Proxy forwarding and bind settings (Red then Green)

- [ ] 7.1 Red: `ProxyForwardingSettingsTest` asserts: the environment value wins over the property; the property is used when the environment is absent; a blank value throws; neither set gives an offline result; `toString` contains no part of the secret. Verify: the test fails because the record does not exist.
- [ ] 7.2 Green: add `ProxyForwardingSettings` in `net.elytrarace.voyager.platform.proxy`, with the value masked in `toString`. Verify: `7.1` passes.
- [ ] 7.3 Red: `ServerSettingsBindTest` (in `voyager-server`) and `SetupSettingsBindTest` (in `voyager-setup`) assert the precedence: positional arguments, then `service.bind.host` and `service.bind.port`, then `0.0.0.0` and 25565; and that a port of `70000` is refused with an error that names `service.bind.port`. Verify: the tests fail on the current `fromProperties`.
- [ ] 7.4 Green: change `ServerSettings.fromProperties` and `SetupSettings.fromProperties` to read the properties in that order. Verify: `7.3` passes; the existing `config-check-mode` tests still pass.

## 8. Stdin stop and /stop (Red then Green)

- [ ] 8.1 Red: move the existing `ConsoleCommandReaderTest` from `voyager/server` to `voyager/platform` (`git mv` of the test and the class, package `net.elytrarace.voyager.platform.lifecycle`) and add cases: the line `stop` requests shutdown; the line `end` is ignored; end of input keeps running without an error. Verify: the moved tests fail on the `stop` case before 8.3.
- [ ] 8.2 Red: `ServiceShutdownTest` injects the shutdown action and an `Executor`, and asserts that a request runs the action on a thread other than the caller and that the action runs once when two requests arrive. Verify: the test fails because `ServiceShutdown` does not exist. No real `MinecraftServer.stopCleanly()` runs in the test.
- [ ] 8.3 Green: add `ServiceShutdown` (its shutdown runs on a platform thread, then `System.exit(0)`), register `stop` in `ConsoleCommandReader`'s dispatcher, and add `StopCommand` (`/stop`) that asks the policy for `voyager.command.stop` and allows the console. Test `StopCommandTest` asserts a player without the node is denied and the console is allowed. Verify: `8.1`, `8.2` and `StopCommandTest` pass.

## 9. Composition roots and command gating (Red then Green)

- [ ] 9.1 Red: `VoyagerGraphTest` (server) and the setup boot test assert that a `PermissionPolicy` bean resolves to `LevelPermissionPolicy` when the loader is absent, that the WARN line naming the fallback is logged (captured through `LogCapture`, not read from the console), and that `RaceCommand` denies a level-3 player with the denial message. Verify: the tests fail before the bean exists.
- [ ] 9.2 Green: in `ServerBeans` and `SetupBeans`, add the `PermissionPolicy` bean (`LuckPermsPolicy` when `LuckPermsBootstrap.isPresent()`, else `LevelPermissionPolicy`). Replace the `ReloadPermission` call in `RaceCommand` with `policy.allows(...)` for `voyager.command.race.reload`, and gate dev-mode `start` and `skip` with their nodes. Delete `ReloadPermission` if no caller remains. Verify: `9.1` passes; `RaceCommand` tests still pass.
- [ ] 9.3 Red then Green: gate every `SetupCommands` subcommand and the `WandListener` behind `voyager.setup.use`. Test: a player without the node gets the denial message and no draft is changed. Verify: the test passes; `SetupCommands` tests with the console still pass.
- [ ] 9.4 Green: in `VoyagerServer` and `SetupServer`, call `LuckPermsBootstrap.start()` when present, before the port binds; map `ProxyForwardingSettings` to `MinecraftServer.init(Auth)`; register `/stop` and start the `ConsoleCommandReader` after listening. Verify: the boot tests from `9.1` pass; a failed start in a test seam exits with a non-zero status (the `startup-errors` pattern).

## 10. Build (Gradle)

- [ ] 10.1 Pin `net.luckperms:api:5.5` and `net.luckperms:minestom-loader:5.6-SNAPSHOT` in `settings.gradle.kts`, programmatically, with the Sonatype snapshot repository added for the loader. No `gradle/libs.versions.toml`. Verify: `./gradlew :voyager:platform:dependencies --configuration compileClasspath` lists `net.luckperms:api:5.5`.
- [ ] 10.2 Add `compileOnly` for the API (excluding `net.kyori.adventure`) in `voyager/platform/build.gradle.kts`, and `runtimeOnly` for the loader (excluding `net.kyori.adventure`) in `voyager/server` and `voyager/setup`, excluded from their `testRuntimeClasspath`, as Cygnus does. Verify: `./gradlew :voyager:server:dependencies --configuration runtimeClasspath` lists `minestom-loader`; `--configuration testRuntimeClasspath` does not.
- [ ] 10.3 Verify the loader does not reach the API classpath of `voyager-api`: `./gradlew :voyager:api:dependencies` lists no `net.luckperms`. Verify: the command output shows no match.

## 11. Documentation

- [ ] 11.1 Reference page `docs/reference/permission-nodes.md` (Diátaxis reference): the five nodes, what each gates and by which command, the console rule, the fallback table, and that `/race status` stays open. Verify: every node in the enum appears in the page, and the page is linked from `docs/reference/`.
- [ ] 11.2 How-to `docs/guides/how-to-grant-permissions.md`: grant a node in LuckPerms, and use operator level 4 when LuckPerms is absent. Verify: each command in the guide runs as written on the spike server.
- [ ] 11.3 How-to `docs/guides/how-to-run-behind-a-proxy.md`: set `VOYAGER_VELOCITY_SECRET` (or the property), match the proxy's forwarding secret, and what the WARN line means. Verify: the secret is never printed in the guide's sample output.
- [ ] 11.4 Update the decision table in `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` with rows D13 (permission port, ADR-0024) and D14 (Velocity forwarding, ADR-0025), and add the `stop` line to the "Extensions must be bootstrapped explicitly" section where it describes stdin. Verify: `grep -n "D13\|D14" docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` shows both rows.

## 12. Integration checks and acceptance

- [ ] 12.1 Run `./gradlew :voyager:api:test :voyager:platform:test :voyager:server:test :voyager:setup:test :voyager:fitness:test`. Verify: exit 0 in each.
- [ ] 12.2 Run `./gradlew build --continue` from the repository root. Verify: exit 0 across both trees.
- [ ] 12.3 Acceptance, with LuckPerms absent: start `voyager-server`, join as a level-3 player and as a level-4 player, and run `/race reload` as each. Verify: the level-3 player gets the denial message; the level-4 player gets the reload report.
- [ ] 12.4 Acceptance, with LuckPerms present (spike environment): grant `voyager.command.race.reload` to one user only, and run `/race reload` as that user and as another level-4 user. Verify: only the granted user is allowed.
- [ ] 12.5 Acceptance: start with `-Dservice.bind.port=25571` and no arguments, then write `stop` to stdin. Verify: the server binds 25571, the process exits with status 0 and the log ends with the shutdown message.
- [ ] 12.6 Acceptance: start with `VOYAGER_VELOCITY_SECRET` set and connect through a Velocity proxy that uses the same secret. Verify: the player's UUID matches the account's UUID. Record the proxy version used.

## 13. Pull request

- [ ] 13.1 Open the pull request titled `feat(server): add LuckPerms permissions, Velocity forwarding and stdin stop`. Note: PRs are merged locally per the owner's decision, not squash-merged on GitHub. The title is still the commit message that the local merge lands on `main`, so it must stay a valid Conventional Commit. Supporting commits on the branch use `refactor(server)` for the `ConsoleCommandReader` move and `docs(...)` for the docs. The body lists the spike results, the fallback decision, and the acceptance results from group 12. The body ends with two footer lines, in this order: `https://claude.ai/referral/m5Ak2Sa7aQ` and `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`, per the owner's memory rule. Verify: `gh pr view` shows the title, the base `main`, and the footer.

## Workflow follow-up

- Archive with `docs(openspec): archive add-luckperms-permissions` after the PR is merged locally.
- `add-cloudnet-deployment` depends on this change and starts only after this change is archived.
