# Tasks

Commit types per task group, all under the proposal's scope `server`: `test(server)` for characterisation and red tests,
`refactor(server)` for the green code and the fitness rules, `docs` for `docs/` pages and the ADR, `docs(openspec)` for
artifact edits. One type per commit. Every behaviour gets a failing test first (Red), then the code (Green). Every test
follows F.I.R.S.T.: no sleeps, no `System.setProperty`, `@TempDir` for files, one `Configuration` per test.

Haiku effort is the effort note for the implementation agent of each group: `low` for mechanical moves, `medium` for code with
a test contract, `high` for the spike and the golden comparison, where a wrong reading changes behaviour.

## 1. Decisions

- [x] 1.1 Owner accepts ADR-0036 (Q1) and its status changes from Proposed to Accepted (`docs`). Haiku effort: low.
- [x] 1.2 Owner answers Q2 (velocity secret order) and Q3 (report with sources); the answers are recorded in design.md, "Owner questions" (`docs(openspec)`). Haiku effort: low.

## 2. Spike and characterisation (before any production change)

- [x] 2.1 Spike, no commit: read the avaje-config 5.2 sources or javadoc for the builder (`includeResourceLoading`, environment and property inclusion, `put`), the environment-name mapping, and the enumerated-section caveat Titan documents. Record each finding in design.md D2 and D3. If the builder cannot include the environment, D3 falls back to the loader reading it. Haiku effort: high.
- [x] 2.2 Red: `ConfigCharacterisationTest` names the behaviours "a bind port that is not a number is refused and named", "a minimum racer count of zero is refused", "a lobby world without a spawn is refused and names the spawn", and "the velocity secret prefers the environment". It runs against the current `ServerSettings` and `ProxyForwardingSettings` and must pass there (`test(server)`). Haiku effort: medium.
- [x] 2.3 Golden matrix: a script under `voyager/server/src/test/resources/config-golden/` with about thirty inputs (command line, each key set and unset, invalid values, both secrets) and the expected output of the current `main` jar, generated once and committed (`test(server)`). Haiku effort: high.

## 3. platform: the loader and the call sites

- [x] 3.1 Red: `ConfigLoaderTest` names the behaviours "a system property beats the environment", "the environment beats a working-directory file", "a working-directory file sets a value when nothing else does", "the velocity secret takes the environment before the property", "a blank environment secret is refused, not printed", and "an absent key is absent, not empty" (`test(platform)`). Haiku effort: medium.
- [x] 3.2 Green: add the catalogue entry `avaje-config` 5.2 in `settings.gradle.kts`, the dependency in `voyager/platform/build.gradle.kts`, and `platform.config.ConfigLoader` with `lookup()`, `velocitySecret()` and the provenance helper of D7 (`refactor(server)`). Haiku effort: high.
- [x] 3.3 Green: move the four call sites: `ServerSettings.fromEnvironment` takes the loader's lookup, `VoyagerServer` reads `voyager.config.check` through the loader, `ConfigCheck.systemProperties()` is replaced by the loader's provenance view, and `ProxyForwardingSettings.fromEnvironment()` takes the loader. Existing unit tests change only where they used the JVM (`refactor(server)`). Haiku effort: medium.

## 4. Fitness

- [x] 4.1 Red: add `environmentAccessIsConfinedToTheEdge`, `avaje.config` confinement, and `apiDoesNotDependOnConfigLibrary` to `voyager/fitness` (`test(server)`); run `./gradlew :voyager:fitness:test` and record that the new rule fails on the four call sites. Haiku effort: medium.
- [x] 4.2 Green: confirm the rules pass after task 3.3, with no baseline store (`refactor(server)`). Haiku effort: low.

## 5. Compatibility verification

- [x] 5.1 Run the golden matrix (2.3) against the new `shadowJar` and diff it with the committed output. Any difference fails this task; a documented additive environment name is the only allowed difference, and it is listed in the reference page. Haiku effort: high.
- [x] 5.2 Smoke boot, mandatory: build `./gradlew :voyager:server:shadowJar`; run `java -Dvoyager.config.check=true -DVOYAGER_DATA_PATH=<run>/catalog -DVOYAGER_WORLDS_PATH=<run>/worlds -jar <jar>` (exit 0 expected), then boot without the check flag, wait for the "Done" line, and stop with `stop` on standard input (exit 0). Record both in the PR description. Haiku effort: medium.

## 6. Documentation

- [x] 6.1 Update `docs/guides/how-to-deploy-on-cloudnet.md`, section "Values the node provides": the environment names, the precedence in one table, and the velocity exception (`docs`). Haiku effort: low.
- [x] 6.2 Add `docs/reference/configuration.md` (Diataxis reference): every key with its system property, environment name, default and validation (`docs`). Haiku effort: medium.
- [x] 6.3 Mark ADR-0036 Accepted once task 1.1 is done, and link it from `docs/decisions/` index if one exists (`docs`). Haiku effort: low.
- [x] 6.4 Amend the greenfield design spec, section "Configuration": note that the hand-written resolver is replaced by avaje-config (ADR-0036), keep the four ranks, and add row D16 to the decision table (`docs`). Haiku effort: medium.
- [x] 6.5 Lumen's research note in `docs/research/`: the measured compatibility of the golden matrix and the spike results, in English (`docs`). Haiku effort: medium.

## 7. Verification and pull request

- [ ] 7.1 `./gradlew build` on both trees and `./gradlew :voyager:fitness:test`; both green before the change is marked done (`docs(openspec)` for the archive note only). Haiku effort: low.
- [ ] 7.2 `openspec validate adopt-avaje-config --strict` is clean. Haiku effort: low.
- [ ] 7.3 Open the pull request. Title: `refactor(server): load configuration through avaje-config`. Body: the smoke boot results (5.2), the golden diff result (5.1), and the footer with two lines: `https://claude.ai/referral/m5Ak2Sa7aQ` and `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`. Do not push without the owner's go-ahead. Haiku effort: low.
