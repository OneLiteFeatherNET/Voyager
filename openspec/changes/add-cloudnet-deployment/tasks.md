# Tasks

Every behaviour starts with a failing test (Red), then the production code (Green), then a refactor. Tests follow F.I.R.S.T.
(no sleeps, injected `Clock` where time matters, fresh fixtures, no shared static state). The fitness suite runs after every group.
This change starts only after `add-luckperms-permissions` is archived, because it uses that change's PermissionPolicy, its
Velocity forwarding, its bind properties and its stdin `stop`.

## 1. Feasibility spike (throwaway, nothing merged)

- [ ] 1.1 Spike: resolve the CloudNet 4.0.0-RC16 artifacts Cygnus uses (`eu.cloudnetservice.cloudnet:bom:4.0.0-RC16`, `bridge-api`, `bridge-impl`, `driver-api`) in `voyager/cloudnet-bridge`, and boot the bridge's Minestom extension against Minestom `2026.08.28-26.2`. Verify: the artifacts resolve, and the spike server reports the bridge's extension as loaded or fails with the exact message. Record the result for Minecraft 26.2 in design.md, Risks, R8. If the bridge fails on 26.2 with RC16, stop and ask the owner; RC17 is a new decision.
- [ ] 1.2 Spike: resolve `net.onelitefeather:minestom-extensions:2.2.0` with its bom and processor against `2026.08.28-26.2`, and boot the server with `ExtensionBootstrap.bootstrap()` followed by `start(host, port)`. Verify: the boot log shows the bootstrap started, the port listens, and the JitPack proxy is the only extra repository needed (`com.github.Minestom:DependencyGetter`). Record the result in design.md, Risks, R2.
- [ ] 1.3 Spike: in Minestom's test environment, set a `Player` subclass with `ConnectionManager.setPlayerProvider` and assert that `player.getOrDefault(PermissionChecker.POINTER, ...)` returns the subclass's checker. Verify: the assertion passes.
- [ ] 1.4 Spike: inspect the RC16 `bridge-api` jar (`javap`) for the registration call Cygnus uses (`ServiceRegistry.registry().registerProvider(MinestomPermissionChecker.class, name, checker).markAsDefaultService()`). Verify: the methods exist; record any difference.
- [ ] 1.5 Checkpoint: write the four spike results into design.md, Risks, replacing the placeholders, and stop if 1.1 or 1.4 failed.

## 2. Decision record (status Proposed)

- [ ] 2.1 Write `docs/decisions/0026-cloudnet-bridge-as-minestom-extension-module.md` in MADR 4.0. Context: decision D15 of the greenfield design (deferred), the owner's decision to bring it forward, the `CloudNet_Bridge` dependency, the POINTER requirement, and the RC16 choice with its risk. Status: `Proposed`. Re-check at apply time that 0026 is still free and renumber if not. Verify: the file exists and `## Status` reads `Proposed`.
- [ ] 2.2 Update the greenfield design spec: the D15 row (CloudNet bridge as a ninth module, now in progress under this change and ADR-0026) and the "deliberately deferred" paragraph. Verify: `grep -n "D15" docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` shows the row, and the paragraph no longer says "deferred".

## 3. Fitness rules (Red first)

- [ ] 3.1 Red: add `voyager/fitness/src/test/java/net/elytrarace/fitness/CloudNetBoundaryTest.java` with `cloudNetIsConfinedToBridge` (classes depending on `eu.cloudnetservice..` must reside in `net.elytrarace.voyager.cloudnet.bridge..`), `rootsAndPlatformDoNotDependOnCloudNet` (classes in `..server..`, `..setup..`, `..platform..` and `..api..` must not depend on `eu.cloudnetservice..` or `net.elytrarace.voyager.cloudnet..`) and `bridgeDependsOnlyOnApi` (classes in `..cloudnet.bridge..` must not depend on `..platform..`, `..server..` or `..setup..`), all with `allowEmptyShould(false)`. Verify: `./gradlew :voyager:fitness:test` fails with "failed to check any classes" for the bridge rules, because the module has no classes yet.
- [ ] 3.2 Red: add `:voyager:cloudnet-bridge` to `PACKAGE_PREFIX_BY_PROJECT` in `FitnessCoverageTest`, and add the module to `voyager/fitness/build.gradle.kts`. Verify: `FitnessCoverageTest` fails until the bridge module has a class under its prefix and a rule names it.
- [ ] 3.3 Red proof: temporarily import `eu.cloudnetservice.cloudnet.driver.ServiceDriver` into `RaceCommand` on the scratch branch. Verify: `cloudNetIsConfinedToBridge` fails and names `RaceCommand`. Revert.

## 4. Pointer-carrying player in voyager-platform (Red then Green)

- [ ] 4.1 Red: `VoyagerPlayerTest` in `voyager/platform/src/test` (Minestom test environment, fresh `Env` per test, explicit ticks) asserts that a player created by `VoyagerPlayerProvider` answers `player.getOrDefault(PermissionChecker.POINTER, ...)` with a checker, and that the checker's `value("voyager.command.race.reload")` follows a fake `PermissionPolicy` (granted gives TRUE, denied gives FALSE). Verify: the test fails because the classes do not exist.
- [ ] 4.2 Green: add `VoyagerPlayer` (extends `Player`, implements the pointer through the policy, using the string form of the port) and `VoyagerPlayerProvider` in `net.elytrarace.voyager.platform.player`. Verify: `4.1` passes.
- [ ] 4.3 Green: both composition roots install `VoyagerPlayerProvider` with `setPlayerProvider` before the port binds. Verify: the boot tests in groups 9 of `add-luckperms-permissions` still pass, and a new boot assertion shows the provider is installed.

## 5. Bridge module (Red then Green)

- [ ] 5.1 Red: create `voyager/cloudnet-bridge/build.gradle.kts` (plugin `voyager.java-conventions`, `compileOnly` on the RC16 coordinates from 1.1, `compileOnly` on `voyager-api` and Minestom and Adventure) and add the include in `settings.gradle.kts`. Then add `CloudNetPermissionCheckerTest`: the checker maps `test(permission)` to the policy's string form and returns the policy's answer as a `TriState`; the test uses a fake policy and fake player and imports no CloudNet type. Verify: the test fails because the class does not exist.
- [ ] 5.2 Green: add `CloudNetPermissionChecker` in `net.elytrarace.voyager.cloudnet.bridge`, with no CloudNet import. Verify: `5.1` passes.
- [ ] 5.3 Red then Green: `VoyagerCloudNetExtensionTest` asserts that the `@ExtensionInfo` annotation of `VoyagerCloudNetExtension` names `CloudNet_Bridge` among its dependencies (reflection on the annotation, no CloudNet runtime). Then add `VoyagerCloudNetExtension extends Extension`, whose `initialize()` registers the checker through `ServiceRegistry` with `markAsDefaultService()`, as Cygnus does, and whose `terminate()` does nothing else. Verify: the test passes.
- [ ] 5.4 Verify: `./gradlew :voyager:cloudnet-bridge:build` generates `extension.json` (through `minestom-extensions-processor`, with `-Aminestom.extension.version` as Cygnus does) that lists `CloudNet_Bridge`. Verify: the generated file is read and lists the dependency.

## 6. Extension bootstrap in both roots (Red then Green)

- [ ] 6.1 Red: add `ListenerStartTest` in `voyager/platform/src/test` with a recording fake of the bootstrap seam (`start(host, port)` recorded in order), asserting that the root's start sequence calls the bootstrap after the graph is wired and before the port is bound. Verify: the test fails because the seam does not exist.
- [ ] 6.2 Green: add the seam interface in `net.elytrarace.voyager.platform.lifecycle` and the production implementation that calls `ExtensionBootstrap.bootstrap()` and `start(host, port)`. Replace `server.start(host, port)` in `VoyagerServer` and `SetupServer` with it. Verify: `6.1` passes; the existing `startup-errors` behaviour still exits with a non-zero status when the start fails.
- [ ] 6.3 Verify: a local run without `extensions/` starts, and the log names the skipped `CloudNet_Bridge` extension (the fork logs it; the test is the acceptance run in group 10 and a log capture in the boot test is not used, because the fork owns that line).

## 7. Dependencies, fat jars and the no-CloudNet rule (Red then Green)

- [ ] 7.1 Pin in `settings.gradle.kts`, programmatically and as Cygnus does: `cloudnet` 4.0.0-RC16 (bom, `bridge-api`, `bridge-impl`, `driver-api`), `minestom-extensions` 2.2.0 (bom, library, processor) and the JitPack proxy repository `https://repo.onelitefeather.dev/onelitefeather-proxy`. No `gradle/libs.versions.toml`. Verify: `./gradlew :voyager:cloudnet-bridge:dependencies --configuration compileClasspath` lists the four CloudNet artifacts.
- [ ] 7.2 Red: add the Gradle task `verifyNoCloudNetInFatJars` in the root build script. It opens each `voyager-server` and `voyager-setup` shadow jar and fails if any entry starts with `eu/cloudnetservice/`, naming the jar, and it is wired into `check`. Verify: it fails because `voyager-setup` has no shadow jar yet.
- [ ] 7.3 Green: in `voyager/setup/build.gradle.kts` add `alias(libs.plugins.shadow)` with the same configuration as `voyager/server` (`Main-Class: net.elytrarace.voyager.setup.SetupServer`, `mergeServiceFiles()`, signature-file exclusions). Verify: `./gradlew :voyager:setup:shadowJar`, then `unzip -p` on the jar's `META-INF/MANIFEST.MF` shows the `Main-Class`, and `verifyNoCloudNetInFatJars` passes.
- [ ] 7.4 Verify: `./gradlew :voyager:server:dependencies --configuration runtimeClasspath` lists no `cloudnet` artifact, and the server's shadow jar passes `verifyNoCloudNetInFatJars`. Verify: both commands exit 0.
- [ ] 7.5 Verify: the `minestom-loader` is still excluded from `testRuntimeClasspath` in both roots (no regression of `add-luckperms-permissions` task 10.2). Verify: the dependency listing shows no loader on the test classpath.

## 8. Service template and deployment guide (docs, Red as a check)

- [ ] 8.1 Check first: list every path, property and file name the guide will name, and grep the code for each one (`VOYAGER_DATA_PATH`, `VOYAGER_WORLDS_PATH`, `service.bind.host`, `service.bind.port`, `VOYAGER_VELOCITY_SECRET`, the stop line). Verify: each grep finds the name in the code, or the task stops and fixes the guide's text.
- [ ] 8.2 Write `docs/guides/how-to-deploy-on-cloudnet.md` (Diátaxis how-to), adapted from Cygnus's removed `docs/cloudnet-deployment.md` (`git show c216431f^:docs/cloudnet-deployment.md` in the Cygnus clone). It covers the artifacts, the service layout of `cloudnet/service-template`, the JVM options that set `VOYAGER_DATA_PATH` and `VOYAGER_WORLDS_PATH`, the stop sequence, the Velocity secret, the verification steps, and the RC16 version with its 26.2 risk. Verify: every command in the guide runs as written on a local service directory (8.3).
- [ ] 8.3 Emulate a service in a temporary directory outside the repository: copy the setup fat jar, the bridge jar and the `catalog/` and `worlds/` directories into it, start with `-DVOYAGER_DATA_PATH=catalog -DVOYAGER_WORLDS_PATH=worlds -Dservice.bind.port=25566`, and write `stop` to stdin. Verify: the process exits with status 0, the port is free afterwards, and `data/` appears under the service directory only.
- [ ] 8.4 Update the CLAUDE.md module table with a row for `voyager-cloudnet-bridge` (`:voyager:cloudnet-bridge`, the CloudNet extension) and the Key Decisions line that names CloudNet 4.0.0-RC16. Verify: `grep -n cloudnet CLAUDE.md` shows both.
- [ ] 8.5 Add a note to `docs/reference/permission-nodes.md` (created by `add-luckperms-permissions`) that CloudNet's permission checks, including `cloudnet.bridge.maintenance`, are answered from the same port through the bridge. Verify: the note is present and links to the deployment guide.

## 9. Build verification

- [ ] 9.1 Run `./gradlew :voyager:fitness:test :voyager:cloudnet-bridge:test :voyager:setup:test :voyager:server:test`. Verify: exit 0 in each.
- [ ] 9.2 Run `./gradlew build --continue` from the repository root. Verify: exit 0 across both trees, and `verifyNoCloudNetInFatJars` runs under `check`.

## 10. Node acceptance on the owner's CloudNet network

- [ ] 10.1 Check the application file name on the node against Cygnus's template (the name is set by the node's service environment). Confirm open questions 1 and 2 in design.md with the owner, or record that the default is used. Verify: the name and both answers are written into the guide's artifact table.
- [ ] 10.2 Copy the service root JAR, `extensions/CloudNet-Bridge.jar` (from the node) and `voyager-cloudnet-bridge.jar` into a game service's template by hand, as Cygnus does, and start a game service. Verify: the log shows `CloudNet_Bridge` loaded and the checker registered, and it does not show `requires an extension called CloudNet_Bridge`.
- [ ] 10.3 Run `stop <service>` on the node. Verify: the process exits on its own, and the log ends with the shutdown message (not a kill).
- [ ] 10.4 Put the task into maintenance. Verify: a player with `cloudnet.bridge.maintenance` granted in LuckPerms joins, and a player without it is kicked.
- [ ] 10.5 Repeat 10.2 to 10.4 for a setup service with the setup fat jar. Verify: the same three results.
- [ ] 10.6 Record the results, including the RC16 result for Minecraft 26.2, in design.md, Risks, R8.

## 11. Pull request

- [ ] 11.1 Open the pull request titled `feat(cloudnet): add the CloudNet bridge and service deployment`. Supporting commits on the branch: `build(setup)` for the setup shadow jar and its check, `docs(cloudnet)` for the guide, decision record and CLAUDE.md, and `docs(openspec)` only if the change is archived in the same branch. Note: PRs are merged locally per the owner's decision, not squash-merged on GitHub. The title stays a valid Conventional Commit because it is what the local merge lands on `main`. The body lists the spike results, the RC16 risk and the acceptance results from group 10. The body ends with two footer lines, in this order: `https://claude.ai/referral/m5Ak2Sa7aQ` and `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`, per the owner's memory rule. Verify: `gh pr view` shows the title, the base `main`, and the footer.

## Workflow follow-up

- Archive with `docs(openspec): archive add-cloudnet-deployment` after the PR is merged locally.
