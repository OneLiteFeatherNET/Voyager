# Tasks

Conventional Commits: one type, `fix(server)`. Test first (Red, then Green), per the project's F.I.R.S.T. rules.

## 1. Red: assert the message text

- [ ] 1.1 In `voyager-server/src/test/java/net/elytrarace/voyager/server/game/CupResolutionTest.java`, add `.hasMessageContaining("-Pcup=")` to `refusesToGuessWhenTheCatalogueHoldsMoreThanOneCupAndNothingChose`. Keep the existing `VOYAGER_CUP` assertion.
- [ ] 1.2 In `ServerSettingsTest.java`, add a test per missing directory (data and worlds) asserting the message contains `-PdataPath=` and `-PworldsPath=` respectively, next to the existing `VOYAGER_DATA_PATH` assertion. One behaviour per test, named after it.
- [ ] 1.3 Run `./gradlew :voyager-server:test` and confirm the new assertions fail for the expected reason (message lacks the Gradle form). Do not change production code yet.

## 2. Green: change the messages

- [ ] 2.1 `UnresolvedCupException.ambiguous`: change the text to `set -Pcup=<name> (Gradle) or -DVOYAGER_CUP=<name> (java -jar)`, built with `String.formatted`.
- [ ] 2.2 `MissingServerDirectoryException`: map the purpose to its Gradle property (`data` to `dataPath`, `worlds` to `worldsPath`) and name both forms. Keep the property name derivation for the system form.
- [ ] 2.3 Run `./gradlew :voyager-server:test` until the new and existing tests pass.

## 3. Verify

- [ ] 3.1 Run `./gradlew build` (both trees) and confirm it is green.
- [ ] 3.2 Grep `docs/` for the old hint text and update any user-facing guide that quotes it. Scribe-style edit only; no research paper changes.
- [ ] 3.3 Run `openspec validate name-both-cup-switches --strict`.

## 4. Pull request

- [ ] 4.1 Open the pull request against `main` with the title `fix(server): name the gradle property next to the system property in startup errors`. Body: the two messages before and after, and the referral footer `https://claude.ai/referral/m5Ak2Sa7aQ`. Verify with `gh pr view` that the title and base are correct.
