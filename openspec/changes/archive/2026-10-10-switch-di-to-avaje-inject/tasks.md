# Tasks

## 1. Spike: prove the wiring assumptions before anything is replaced

Throwaway code only; delete all spike files before group 2. Nothing from this group is merged.

- [x] 1.1 Red: in a scratch `@Factory` in `voyager-server`, declare a bean whose constructor needs a type no bean provides. Verify `./gradlew :voyager-server:compileJava` fails and the failure names the missing type (spec: missing or ambiguous wiring).
- [x] 1.2 Verify that a second bean providing the same type makes compilation fail with the type named (spec: ambiguous wiring). Verify: build output names the type.
- [x] 1.3 Verify that a `jakarta.inject.@Inject` constructor on a class compiled in `voyager-server` is used by the generated wiring without a `@Bean` method. If it is not, record that `@Inject` is documentary and the `@Bean` method is the wiring (design decision 3). Verify: generated source under `build/generated` constructs the class.
- [x] 1.4 Verify that singletons are constructed when `BeanScope` is built, not on first lookup, using a constructor side-effect counter in a throwaway test. Verify: counter is 1 before any `get` call (design decision 4). If it is 0, record the fallback: `VoyagerServer` resolves every bean at startup.
- [x] 1.5 Verify that the build works without `module-info.java` and that `./gradlew :voyager-server:shadowJar` keeps avaje's generated entries (check the jar contents), with the jar reaching the graph-build log line. Verify: the jar lists the generated classes and the boot log shows graph construction.
- [x] 1.6 Checkpoint: write the four results into the pull request description (task 8.4) and stop. If 1.1 or 1.5 fails, ask the user before continuing, because the spec scenarios and the approach would change. Passed, user approved 2026-10-09; implementation continues with groups 2 to 8.

Spike results (2026-10-09), throwaway code deleted, nothing from group 1 committed:

- 1.1 Missing bean: holds. `compileJava` fails with `No dependency provided for net.elytrarace.voyager.server.spike.SpikeUnprovided on ...SpikeNeedsMissing`, followed by `Dependencies [...SpikeUnprovided] are not provided - there are no @Singleton, @Component, @Factory/@Bean that currently provide this type`. Exit 1. The type is named.
- 1.2 Ambiguous bean: fails at compile time, but the message names the methods, not the type: `@Bean method second() returns the same type as with method first() without a unique name qualifier. Add @Named ...`. The spec scenario asks for the type to be named; the spec wording needs a small change, or the scenario accepts the method names as identifiers.
- 1.3 `@Inject`: only wired when the class also carries `@Singleton` and is compiled in voyager-server. Generated `SpikeInjectedSingleton$DI.java` contains `new SpikeInjectedSingleton(builder.get(SpikeDep.class,"!dep"))`. A plain `@Inject` class without `@Singleton` fails with "No dependency provided". A `@Singleton @Inject` class in voyager-platform is not wired from voyager-server (`No dependency provided for net.elytrarace.voyager.platform.spike.PlatformInjected`) and compiles once a `@Bean` method exists in voyager-server. Design decision 3 holds: cross-module wiring needs `@Bean`.
- 1.4 Eager construction: holds. A constructor counter reads 1 after `BeanScope.builder().build()` and before any `get`. A singleton never requested by the test is also constructed at build (counter 1 before `get`). No fallback needed.
- 1.5 No module-info needed (compiles). `./gradlew :voyager-server:shadowJar` succeeds and the jar contains the `$DI.class` files, `SpikeModule.class` and `META-INF/services/io.avaje.inject.spi.InjectExtension` (both `ObserverManagerPlugin` and `SpikeModule` listed). `jshell --class-path <jar>` running `BeanScope.builder().build()` logs "Wired beans in 30ms" and resolves the spike bean, so ServiceLoader discovery works from the fat jar. No `mergeServiceFiles()` is configured anywhere; this run needed no merge, so the entry is generator output, not shadow output. Adding `mergeServiceFiles()` is a cheap guard for later. This was not a full `runServer` boot; the real server still uses Guice.

## 2. Dependencies

- [x] 2.1 Add `io.avaje:avaje-inject:12.7` (implementation), `io.avaje:avaje-inject-generator:12.7` (annotationProcessor) and `io.avaje:avaje-inject-test:12.7` (testImplementation) to `voyager-server/build.gradle.kts`, pinned in `settings.gradle.kts` the way other versions are declared there. Verify: `./gradlew :voyager-server:dependencies --configuration runtimeClasspath` lists `io.avaje:avaje-inject:12.7`; no `gradle/libs.versions.toml` is created.
- [x] 2.2 Add `jakarta.inject:jakarta.inject-api:2.0.1` to `voyager-server` only (compile). Do not add it to `voyager-platform`, `voyager-api`, `voyager-physics` or `voyager-race`. Verify: `./gradlew :voyager-server:dependencies` lists it; `./gradlew :voyager-platform:dependencies` and `./gradlew :voyager-api:dependencies` do not.
- [x] 2.3 Configure `mergeServiceFiles()` on the `shadowJar` task of `voyager-server` as a defensive guard, so avaje's `META-INF/services/io.avaje.inject.spi.InjectExtension` is merged rather than overwritten by another module's service file. Verify: `./gradlew :voyager-server:shadowJar`, then `unzip -l voyager-server/build/libs/*.jar | grep services` lists `META-INF/services/io.avaje.inject.spi.InjectExtension`, and `unzip -p voyager-server/build/libs/*.jar META-INF/services/io.avaje.inject.spi.InjectExtension` lists the `ObserverManagerPlugin` entry and the `$DI`-generated module entry.

## 3. Composition root: graph tests first, then the factories

- [x] 3.1 Red: write `VoyagerGraphTest` in `voyager-server` (fresh `ServerSettings` built in the test, `@TempDir` data directory, no shared state). It asserts that a `BeanScope` built from the settings resolves each of the 13 types `VoyagerModule` provides today, plus `ServerSettings`, and that each process-wide service resolves to the same instance twice. Verify: test fails because no factory exists yet (spec: bean lifetimes and identities).
- [x] 3.2 Red: write `VoyagerStartupTest` asserting that building the graph with a cup that names an unknown map throws before any server binds a port, and that a missing world directory is refused with the message naming the path. Verify: test fails (spec: startup refuses to listen on an incomplete graph).
- [x] 3.3 Green: extract `VoyagerServer.openGraph(ServerSettings)` returning the `BeanScope` and throwing `IllegalStateException` on refusal, without `System.exit`. Verify: `VoyagerStartupTest` from 3.2 still fails only on the factories, not on the extraction.
- [x] 3.4 Green: add `inject/ServerBeans` (`@Factory`) for `InstanceManager`, the JSON catalogues, the ports, the cup with its consistency check, `MapInstances`, `RaceRuns`, `MapTransition`, `FlightTracker`, `RaceTimings`, and the online-players supplier. Each platform and race class is constructed by a `@Bean` method that calls its constructor; no annotation is added to those classes. Each `@Provides` from `VoyagerModule` maps to one `@Bean` with the same semantics; the mapping is listed in the PR description. Verify: `VoyagerGraphTest` and `VoyagerStartupTest` pass. Accepted deviation (design decision 8): no separate `@Bean` for the `MapCatalog` and `CupCatalog` ports. avaje registers `JsonMapCatalog` and `JsonCupCatalog` under their interfaces; a second `@Bean` returning the same instance made resolution ambiguous. `VoyagerGraphTest` asserts that each port and its implementation are the same singleton.
- [x] 3.5 Green: add `inject/RaceBeans` (`@Factory`) for `CupSession`, using the same `Duration.ofMillis(MinecraftServer.TICK_MS)` as before. Verify: `VoyagerGraphTest` passes for `CupSession`.
- [x] 3.6 Green: switch `VoyagerServer.main` to `openGraph(settings)` and resolve its beans from the `BeanScope`. Close the scope in the existing shutdown task. Verify: `./gradlew :voyager-server:test` green.
- [x] 3.7 Refactor: delete `inject/VoyagerModule.java`, the Guice imports in `VoyagerServer`, `io.airlift:guice` and `guiceVersion` from `voyager-server/build.gradle.kts`, and the Guice comment in `game/CupSession.java`. Verify: `grep -rn "com.google.inject\|io.airlift\|VoyagerModule" voyager-server` returns nothing.

## 4. System pipeline order (Moved)

(Moved) Removed from this change by user decision, 2026-10-09. `CupSession.tick()` is not a list of uniform systems:
the flight result is folded into `lastSimulated`, boosts advance after it, a null driver returns early, and
`onUpdate()` is followed by a conditional post-step that mutates `currentMap`. Turning it into a list is a redesign.
Follow-up: the cup-slice refactor (moving `CupSession` to `race.cup`) in change `define-clean-architecture-with-vertical-slices`
declares the per-tick step order once and tests it. Tasks 4.1 and 4.2 are not done in this change.

## 5. Architecture rules

- [x] 5.1 Red: before editing `ApiPurityTest`, add a temporary `io.avaje.inject` annotation to a type in `voyager-api`, and a temporary `jakarta.inject` annotation to a type in `voyager-platform`. Verify: `./gradlew :voyager-fitness:test` does not yet fail on them, which shows the rules are still the old ones; then revert both temporary annotations.
- [x] 5.2 Green: in `ApiPurityTest`, replace `com.google.inject..` and `io.airlift..` with `io.avaje.inject..` in `apiDoesNotDependOnADiContainer`, `physicsDoesNotDependOnADiContainer` and `onlyServerDependsOnDiContainer`; keep `jakarta.inject..` in the api and physics rules; make `onlyServerDependsOnDiContainer` forbid both `io.avaje.inject..` and `jakarta.inject..` outside `net.elytrarace.voyager.server..` across every rebuild module (design decision 6). Verify: the 5.1 temporary annotations now make `:voyager-fitness:test` fail, each naming its type; after reverting them the suite is green.
- [x] 5.3 Add a rule forbidding `jakarta.inject..` and `io.avaje.inject..` in `voyager-race` and in `voyager-platform` (if no such rule exists yet), with `allowEmptyShould(false)`. Verify: a temporary `@Inject` in `voyager-race` and a temporary `@Singleton` in `voyager-platform` each fail the rule, then are reverted; `FitnessCoverageTest` still passes.

## 6. (Removed) Constructor annotations in the platform

Removed 2026-10-09 by user decision: `voyager-platform` stays annotation-free. The platform classes that `ServerBeans` wires are constructed by `@Bean` methods (tasks 3.4 and 3.5), so no constructor is annotated and no platform test changes.

## 7. Documentation

- [x] 7.1 Write `docs/decisions/0016-replace-guice-with-avaje-inject.md` in MADR 4.0. It cites ADR-0013 on `refactor/architecture-ratchet` as prior art and notes the number is 0016 because 0012 to 0015 are taken there. Status is Accepted (the user approved the choice on 2026-10-09). Verify: file exists and follows the MADR 4.0 sections. Done: `## Status` reads Accepted; sections present.
- [x] 7.2 Update `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`: the D10 row (line ~42), the "Dependency injection" section (line ~192), and the risk row (line ~1288). The D10 text states that DI annotations appear only in the composition root and that the platform carries none. Verify: `grep -n "airlift" docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` shows only the historical rejection note, and the D10 row names avaje-inject 12.7.
- [x] 7.3 Update the "Key Decisions" Dependency injection line in `CLAUDE.md` to avaje-inject 12.7 with the rule that `jakarta.inject` and avaje appear only in `voyager-server`. Verify: `grep -n "guice\|avaje" CLAUDE.md` shows avaje only.

## 8. Integration and pull request

- [x] 8.1 Run `./gradlew build --continue` from the repository root. Verify: exit code 0 across both trees. Result: `BUILD SUCCESSFUL`, exit 0 (most tasks up-to-date from an earlier run with identical inputs).
- [x] 8.2 Smoke boot of `./gradlew :voyager-server:shadowJar` (`voyager-server-1.12.0.jar`), run with `-Dminestom.automatic-component-translation=true` (the flag the Gradle run tasks set), a data directory holding the shipped `maps/` and `cups/`, and the worlds directory from `VOYAGER_WORLDS_PATH`. Result, empty worlds directory: "Wired beans in 23ms", refusal `UnknownWorldException: world 'ElytraraceBlueAndRed' holds no region data`, exit 1, no listener on the port. Result, real world `ElytraraceBlueAndRed` copied from `run/run/worlds` (copy, so the repository data is untouched): "Wired beans in 22ms", "Opened world ... (35 rings)", "Listening on 127.0.0.1:25600", "Voyager started. cup 'test_cup' (1 map(s))", stopped by timeout after 60 s, no listener left. Verify: both outcomes match the spec scenarios.
- [x] 8.3 Record `./gradlew :voyager-server:compileJava --rerun-tasks --no-build-cache` wall time on this branch and on `main` (temporary detached worktree, removed afterwards). Result: branch 2.5 s and 2.5 s; main 3.4 s and 2.3 s (warm daemon, two runs each; small sample, so the difference is within noise). Verify: the numbers are in the PR body draft.
- [x] 8.4 Open the pull request against `main` with the title `refactor(server): replace guice with avaje-inject`. Body: the spike results (1.1 to 1.5), the bean mapping from 3.4, ADR-0016, the commit list by type, and the build time. End the body with the referral footer `https://claude.ai/referral/m5Ak2Sa7aQ`. Verify: `gh pr view` shows the title and the base `main`. Result: opened as https://github.com/OneLiteFeatherNET/Voyager/pull/269 stacked on `chore/openspec-setup` (#267, which is stacked on #266), at the user's direction, so the base is not `main`.

## Workflow follow-up

- Archive the change after merge with `docs(openspec): archive switch-di-to-avaje-inject`, or include the archive in the implementation PR if the project's review requires it.
- Confirm that the ADR-0013 and ADR-0015 numbers on `refactor/architecture-ratchet` have not moved before merging ADR-0016.
