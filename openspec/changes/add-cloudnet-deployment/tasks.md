# Tasks

Every behaviour starts with a failing test (Red), then the production code (Green), then a refactor. Tests follow F.I.R.S.T.
(no sleeps, injected `Clock` where time matters, fresh fixtures, no shared static state). The fitness suite runs after every group.
This change starts only after `add-luckperms-permissions` is archived, because it uses that change's PermissionPolicy, its
Velocity forwarding, its bind properties and its stdin `stop`.

Aligned with Cygnus on 2026-10-11 (`docs(openspec): align add-cloudnet-deployment with cygnus`): see design.md, Decisions 3 and 8.
Each commit on the branch must leave `main` green, so a fitness rule that names a module lands in the same range as that module
(Red proofs of section 3 are run on the working tree before the commit that makes them green, and are not committed red).

## 1. Feasibility spike (throwaway, nothing merged)

- [ ] 1.1 Spike: resolve the CloudNet 4.0.0-RC16 artifacts Cygnus uses (`eu.cloudnetservice.cloudnet:bom:4.0.0-RC16`, `bridge-api`, `bridge-impl`, `driver-api`) in `voyager/cloudnet-bridge`, and boot the bridge's Minestom extension against Minestom `2026.09.12-26.2`. Verify: the artifacts resolve, and the spike server reports the bridge's extension as loaded or fails with the exact message. Record the result for Minecraft 26.2 in design.md, Risks, R8. If the bridge fails on 26.2 with RC16, stop and ask the owner; RC17 is a new decision.
- [x] 1.2 Spike: resolve `net.onelitefeather:minestom-extensions:2.2.0` with its bom and processor against `2026.09.12-26.2`, and boot the server with `ExtensionBootstrap.init(auth)` followed by `start(host, port)` (the 2.2.0 API; there is no `bootstrap()`). Verify: the boot log shows the bootstrap started, the port listens, and the JitPack proxy is the only extra repository needed (`com.github.Minestom:DependencyGetter`). Record the result in design.md, Risks, R2.
- [x] 1.3 Spike: in Minestom's test environment, set a `Player` subclass with `ConnectionManager.setPlayerProvider` and assert that `player.getOrDefault(PermissionChecker.POINTER, ...)` returns the subclass's checker. Verify: the assertion passes. (Folded into 4.1, which asserts the same thing on `VoyagerPlayer`.)
- [x] 1.4 Spike: inspect the RC16 `bridge-api` jar (`javap`) for the registration call Cygnus uses (`ServiceRegistry.registry().registerProvider(MinestomPermissionChecker.class, name, checker).markAsDefaultService()`). Verify: the methods exist; record any difference. **Result (2026-10-11, javap on `bridge-impl`, `bridge-api`, `driver-api` of 4.0.0-RC16 and 4.0.0-RC17 from Maven Central):** `MinestomPermissionChecker` is `boolean hasPermission(Player, String)` in both releases; `ServiceRegistry.registerProvider(Class, String, S)` returns `ServiceRegistryRegistration<S>`, which has `markAsDefaultService()`, in both releases. No difference for the calls used, so the RC16 compile target also serves RC17 for this extension. Recorded in design.md, Decision 6.
- [ ] 1.5 Checkpoint: write the four spike results into design.md, Risks, replacing the placeholders, and stop if 1.1 failed. (1.4 passed.)

## 2. Decision record (status Proposed)

- [x] 2.1 Write `docs/decisions/0026-cloudnet-bridge-as-minestom-extension-module.md` in MADR 4.0. Context: decision D15 of the greenfield design (deferred), the owner's decision to bring it forward, the `CloudNet_Bridge` dependency, the POINTER requirement, and the RC16 choice with its risk. Status: `Proposed`. ADR-0026 was free at apply time (0025 is the last one). Verify: the file exists and `## Status` reads `Proposed`.
- [x] 2.2 Update the greenfield design spec: the D15 row (CloudNet bridge as a ninth module, now in progress under this change and ADR-0026) and the "deliberately deferred" paragraph. Verify: `grep -n "D15" docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` shows the row, and the paragraph no longer says "deferred".

## 3. Fitness rules (Red first, committed green)

- [x] 3.1 Red: add `voyager/fitness/src/test/java/net/elytrarace/fitness/CloudNetBoundaryTest.java` with `cloudNetIsConfinedToBridge` (classes depending on `eu.cloudnetservice..` must reside in `net.elytrarace.voyager.cloudnet.bridge..`), `rootsAndPlatformDoNotDependOnCloudNet` (classes in `..server..`, `..setup..`, `..platform..` and `..api..` must not depend on `eu.cloudnetservice..` or `net.elytrarace.voyager.cloudnet..`) and `bridgeDependsOnlyOnJdkAndLibraries` (classes in `..cloudnet.bridge..` must depend only on `java..`, Minestom, Adventure, `eu.cloudnetservice..`, JetBrains annotations and the bridge package; no `voyager` package at all, because the pointer is resolved in the platform). All with `allowEmptyShould(false)`, and written so each one is non-empty before the bridge module exists, except the bridge rule, which is added in the module's commit. Verify: `./gradlew :voyager:fitness:test` is green with the module absent.
- [x] 3.2 Coverage: `:voyager:cloudnet-bridge` is added to `PACKAGE_PREFIX_BY_PROJECT` in `FitnessCoverageTest` in the same commit as the module (the map and the module are checked against each other, so neither lands alone). Verify: `FitnessCoverageTest` passes with the module present and a rule naming its prefix.
- [x] 3.3 Red proof (local, not committed): temporarily import `eu.cloudnetservice.cloudnet.driver.ServiceDriver` into `RaceCommand`. Verify: `cloudNetIsConfinedToBridge` fails and names `RaceCommand`. Revert.

## 4. Pointer-carrying player in voyager-platform (Red then Green)

- [x] 4.1 Red: `VoyagerPlayerTest` in `voyager/platform/src/test` (Minestom test environment, fresh `Env` per test, explicit ticks) asserts that a player created by `VoyagerPlayerProvider` answers `player.getOrDefault(PermissionChecker.POINTER, ...)` with a checker, and that the checker's `test("voyager.command.race.reload")` follows a fake `PermissionPolicy` (granted gives TRUE, denied gives FALSE). Verify: the test fails because the classes do not exist. (This is also spike 1.3.)
- [x] 4.2 Green: add `VoyagerPlayer` (extends `Player`, overrides `pointers()` with `withDynamic(POINTER, ...)` as Cygnus's `PermissionAwarePlayer` does, and answers through the policy's string form) and `VoyagerPlayerProvider` (`PlayerProvider`, holds the `PermissionPolicy`) in `net.elytrarace.voyager.platform.player`. Verify: `4.1` passes.
- [ ] 4.3 Green: both composition roots install `VoyagerPlayerProvider` with `ConnectionManager.setPlayerProvider` before the port binds. Verify: the boot tests of `add-luckperms-permissions` still pass, and a new boot assertion shows the provider is installed.

## 5. Bridge module (Red then Green)

- [x] 5.1 Red: create `voyager/cloudnet-bridge/build.gradle.kts` (plugin `voyager.java-conventions`, the RC16 `compileOnly` coordinates from the catalogue, `compileOnly` on Minestom, Adventure, and the `minestom-extensions` processor for `@ExtensionInfo`) and add the include in `settings.gradle.kts`. Then add `CloudNetPermissionCheckerTest`: the checker reads `PermissionChecker.POINTER` from a fake `Pointered` and returns the pointer's `test(node)`, and false when no pointer is set; the test uses no Minestom `Player` and no CloudNet type. Verify: the test fails because the class does not exist.
- [x] 5.2 Green: add `CloudNetPermissionChecker` in `net.elytrarace.voyager.cloudnet.bridge`, with no CloudNet import. Verify: `5.1` passes.
- [x] 5.3 Red then Green: `VoyagerCloudNetExtensionTest` reads the generated `extension.json` from the module's classes (the `@ExtensionInfo` annotation has source retention, so reflection cannot see it) and asserts that its dependencies contain `CloudNet_Bridge`. Then add `VoyagerCloudNetExtension extends Extension`, whose `initialize()` registers `(player, permission) -> CloudNetPermissionChecker.allows(player, permission)` through `ServiceRegistry.registry().registerProvider(MinestomPermissionChecker.class, "voyager-permissions", checker).markAsDefaultService()` as Cygnus does, and whose `terminate()` does nothing. Verify: the test passes.
- [x] 5.4 Verify: `./gradlew :voyager:cloudnet-bridge:build` generates `extension.json` (through `minestom-extensions-processor`, with `-Aminestom.extension.version` as Cygnus does) that lists `CloudNet_Bridge`. Verify: the generated file is read and lists the dependency.

## 6. Extension bootstrap in both roots (Red then Green; 6.1 and 6.2 deviate, see design.md Decision 3: the roots call ExtensionBootstrap directly, so no seam is added)

- [ ] 6.1 Red: add `ServerListenerTest` in `voyager/platform/src/test` with a recording fake of the seam (`init` and `start(host, port)` recorded in order), asserting that the seam's `start` binds the host and port it was given and that the composition root's order is init, graph, start. Verify: the test fails because the seam does not exist.
- [ ] 6.2 Green: add the seam in `net.elytrarace.voyager.platform.lifecycle`: a production implementation over `ExtensionBootstrap.init(auth)` (or `init()` when the auth is null) and `bootstrap.start(host, port)`. Replace `MinecraftServer.init(auth)` and `server.start(host, port)` in `VoyagerServer` and `SetupServer` with it. Verify: `6.1` passes; the existing `startup-errors` behaviour still exits with a non-zero status when the start fails.
- [ ] 6.3 Verify: a local run without `extensions/` starts, and the log names the skipped `CloudNet_Bridge` extension (the fork logs it; the test is the acceptance run in group 10 and a log capture in the boot test is not used, because the fork owns that line).

## 7. Dependencies, fat jars and the no-CloudNet rule (Red then Green)

- [x] 7.1 Pin in `settings.gradle.kts`, programmatically and as Cygnus does: `cloudnet` 4.0.0-RC16 (bom, `bridge-api`, `bridge-impl`, `driver-api`) and `minestom-extensions` 2.2.0 (bom, library, processor). No JitPack proxy repository is needed (design.md, Decision 3, corrected at apply time). No `gradle/libs.versions.toml`. Verify: `./gradlew :voyager:cloudnet-bridge:dependencies --configuration compileClasspath` lists the four CloudNet artifacts.
- [x] 7.2 Red: add the Gradle task `verifyNoCloudNetInFatJars` in the root build script. It opens each `voyager-server` and `voyager-setup` shadow jar and fails if any entry starts with `eu/cloudnetservice/`, naming the jar, and it is wired into `check`. Verify: it fails for a jar that holds a CloudNet entry (proof with a temporary entry), and passes for the shipped jars.
- [x] 7.3 Green: in `voyager/setup/build.gradle.kts` add `alias(libs.plugins.shadow)` with the same configuration as `voyager/server` (`Main-Class: net.elytrarace.voyager.setup.SetupServer`, `mergeServiceFiles()`, signature-file exclusions). Verify: `./gradlew :voyager:setup:shadowJar`, then `unzip -p` on the jar's `META-INF/MANIFEST.MF` shows the `Main-Class`, and `verifyNoCloudNetInFatJars` passes. *(already on `main` at apply time; the verifyNoCloudNetInFatJars check is still open under 7.2/7.4)*
- [x] 7.4 Verify: `./gradlew :voyager:server:dependencies --configuration runtimeClasspath` lists no `cloudnet` artifact, and the server's shadow jar passes `verifyNoCloudNetInFatJars`. Verify: both commands exit 0.
- [x] 7.5 Verify: the `minestom-loader` is still excluded from `testRuntimeClasspath` in both roots (no regression of `add-luckperms-permissions` task 10.2). Verify: the dependency listing shows no loader on the test classpath.

## 8. Service template and deployment guide (docs, Red as a check)

- [x] 8.1 Check first: list every path, property and file name the guide will name, and grep the code for each one (`VOYAGER_DATA_PATH`, `VOYAGER_WORLDS_PATH`, `service.bind.host`, `service.bind.port`, `VOYAGER_VELOCITY_SECRET` and `voyager.velocity.secret`, the stop line). Verify: each grep finds the name in the code, or the task stops and fixes the guide's text.
- [x] 8.2 Write `docs/guides/how-to-deploy-on-cloudnet.md` (Diátaxis how-to), adapted from Cygnus's removed `docs/cloudnet-deployment.md` (`git show c216431f^:docs/cloudnet-deployment.md` in the Cygnus clone). It covers the artifacts, the service layout of `cloudnet/service-template`, the launch form (`-cp` with the Main-Class, the wrapper as `-javaagent`), the JVM options that set `VOYAGER_DATA_PATH` and `VOYAGER_WORLDS_PATH`, the stop sequence, the Velocity secret under Voyager's names, the verification steps, and the RC16 version with its 26.2 risk. Verify: every command in the guide runs as written on a local service directory (8.3).
- [x] 8.3 Emulate a service in a temporary directory outside the repository: copy the setup fat jar, the bridge jar and the `catalog/` and `worlds/` directories into it, start with `-DVOYAGER_DATA_PATH=catalog -DVOYAGER_WORLDS_PATH=worlds -Dservice.bind.port=25566`, and write `stop` to stdin. Verify: the process exits with status 0, the port is free afterwards, and `data/` appears under the service directory only.
- [x] 8.4 Update the CLAUDE.md module table with a row for `voyager-cloudnet-bridge` (`:voyager:cloudnet-bridge`, the CloudNet extension) and the Key Decisions line that names CloudNet 4.0.0-RC16. Verify: `grep -n cloudnet CLAUDE.md` shows both.
- [x] 8.5 Add a note to `docs/reference/permission-nodes.md` (created by `add-luckperms-permissions`) that CloudNet's permission checks, including `cloudnet.bridge.maintenance`, are answered from the same port through the bridge. Verify: the note is present and links to the deployment guide.

## 8A. Zero-touch runtime configuration (owner requirement, 2026-10-11; design.md "Runtime configuration")

(Status at the end of the apply run: 8A.1, 8A.2, 8A.3, 8A.5 and 8A.7 are done; 8A.4 and 8A.6 are open. Commits `959b8d0` and `5b1fc6e`.)

- [x] 8A.1 Read Cygnus's mechanisms for every value in design.md's table (done in design.md; the table cites the Cygnus paths). Verify: the table is present and no secret value is recorded.
- [x] 8A.2 Red then Green (`5b1fc6e`): production defaults of `ServerSettings` and `SetupSettings` become `catalog` and `worlds` under the working directory (Decision 9). The tests that pin the defaults change with them. Verify: a bare run in a directory holding `catalog/` and `worlds/` reads them with no property set.
- [x] 8A.3 (done in `5b1fc6e`; `validateCatalog` itself was not run in this apply pass) Keep the Gradle run tasks on the `run/` layout by passing `VOYAGER_DATA_PATH` and `VOYAGER_WORLDS_PATH` explicitly in `voyager/server/build.gradle.kts` and `voyager/setup/build.gradle.kts`, with the doubled `run/run` convention documented in the same place. Verify: `./gradlew :voyager:server:validateCatalog` still finds the shipped catalogue.
- [ ] 8A.4 Velocity secret: confirm that `VOYAGER_VELOCITY_SECRET` from the task environment is enough (no file, no `-D`). Verify: the guide names the environment variable and no file holds the secret.
- [x] 8A.5 Spike (translation flag, Decision 10): a launcher that sets `minestom.automatic-component-translation` before any Minestom class loads makes the flag unnecessary on 26.2 with the fork. Recorded in design.md, Decision 10, with a control run. Implementing it is a separate change (its own proposal), because it changes both roots' Main-Class.
- [x] 8A.6 LuckPerms storage (Owner decision 1, decided 2026-10-11 "like Cygnus": `data/config.yml` placed by hand in the template; guide, "LuckPerms"): the owner decides option A, B or C. Until the decision, the MariaDB and Redis settings stay hand-placed and group 10 records that. Verify: the decision is written into design.md before acceptance.
- [x] 8A.7 OTel: not applicable to Voyager today (no OpenTelemetry). Verify: the design table says so, and no OTel dependency is added.

## 9. Build verification

- [x] 9.1 Run `./gradlew :voyager:fitness:test :voyager:cloudnet-bridge:test :voyager:setup:test :voyager:server:test`. Verify: exit 0 in each.
- [x] 9.2 Run `./gradlew build --continue` from the repository root. Verify: exit 0 across both trees, and `verifyNoCloudNetInFatJars` runs under `check`.

## 10. Node acceptance on the owner's CloudNet network (owner-run, left open by the apply agent)

- [ ] 10.1 Check the application file name on the node against Cygnus's template (the name is set by the node's service environment). Confirm open questions 1 and 2 in design.md with the owner, or record that the default is used. Verify: the name and both answers are written into the guide's artifact table.
- [ ] 10.2 Copy the service root JAR, `extensions/CloudNet-Bridge.jar` (from the node) and `voyager-cloudnet-bridge.jar` into a game service's template by hand, as Cygnus does, and start a game service. Verify: the log shows `CloudNet_Bridge` loaded and the checker registered, and it does not show `requires an extension called CloudNet_Bridge`.
- [ ] 10.3 Run `stop <service>` on the node. Verify: the process exits on its own, and the log ends with the shutdown message (not a kill).
- [ ] 10.4 Put the task into maintenance. Verify: a player with `cloudnet.bridge.maintenance` granted in LuckPerms joins, and a player without it is kicked.
- [ ] 10.5 Repeat 10.2 to 10.4 for a setup service with the setup fat jar. Verify: the same three results.
- [ ] 10.6 Record the results, including the RC16 result for Minecraft 26.2, in design.md, Risks, R8.

## 11. Pull request

- [ ] 11.1 Open the pull request titled `feat(cloudnet): add the CloudNet bridge and service deployment`. Supporting commits on the branch: `build(cloudnet)` for the dependency pins and the fat-jar check, `docs(cloudnet)` for the guide, decision record and CLAUDE.md, and `docs(openspec)` for the alignment. Note: PRs are merged locally per the owner's decision, not squash-merged on GitHub. The title stays a valid Conventional Commit because it is what the local merge lands on `main`. The body lists the spike results, the RC16 risk and the acceptance results from group 10. The body ends with two footer lines, in this order: `https://claude.ai/referral/m5Ak2Sa7aQ` and `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`, per the owner's memory rule. Verify: `gh pr view` shows the title, the base `main`, and the footer.

## Workflow follow-up

- Archive with `docs(openspec): archive add-cloudnet-deployment` after the PR is merged locally.
