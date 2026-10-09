# Tasks

Conventional Commits: one type, `fix(build)`, one commit. This is build logic with no Java test, so the Red and Green steps are
the reproducible Gradle sequence of `design.md`, decision 3, not a unit test. Every step's output goes into the PR body.

## 1. Red: reproduce the stale map on the current build

- [ ] 1.1 Confirm `run/` is ignored by git (`git check-ignore -v run/run/data/maps/x.json` names `.gitignore`). Verify: the command prints the rule.
- [ ] 1.2 Run `./gradlew :voyager-server:prepareRunData`, then create `run/run/data/maps/stale.json` with the content `{}`, then run `./gradlew :voyager-server:prepareRunData` again. Verify: `ls run/run/data/maps/stale.json` still lists the file. This is the bug; record the output.

## 2. Green: switch the task to Sync

- [ ] 2.1 In `voyager-server/build.gradle.kts`, change `prepareRunData` from `Copy::class` to `Sync::class`, keep the source, the includes and `into(runDataDir)`, and extend the `description` to say that the directory is a build-owned mirror and that other files in it are removed. Verify: `./gradlew :voyager-server:tasks --group voyager` shows the new description.
- [ ] 2.2 Repeat 1.2. Verify: `stale.json` is gone after the second run; `cmp run/run/data/maps/elytraraceblueandred.json voyager-server/src/main/resources/maps/elytraraceblueandred.json` and the same for `cups/test_cup.json` report no difference.
- [ ] 2.3 Run `./gradlew :voyager-server:prepareRunData` a third time without changes. Verify: the output reports `UP-TO-DATE` or the run is a no-op, and no file timestamp in `run/run/data` changes (`stat -c %Y` before and after).
- [ ] 2.4 Run `./gradlew :voyager-server:runServerDev --dry-run` and `./gradlew :voyager-server:runServer --dry-run`. Verify: `prepareRunData` is still listed before the run tasks; no JVM starts.

## 3. Verify and open the pull request

- [ ] 3.1 Run `./gradlew build` over both trees. Verify: exit code 0.
- [ ] 3.2 Run `openspec validate fix-run-data-sync --strict`. Verify: exit code 0.
- [ ] 3.3 Commit `fix(build): remove maps from the run directory once they leave the catalogue` (one commit, type `fix`, scope `build`, no Co-Authored-By line).
- [ ] 3.4 Open the pull request against `main` with the title `fix(build): remove maps from the run directory once they leave the catalogue`. Body: the Red and Green outputs of 1.2 and 2.2, the up-to-date result of 2.3, the BREAKING note for hand-made files in `run/run/data`, and the merge note that `add-catalog-validate-task` depends on this change. End the body with the referral footer `https://claude.ai/referral/m5Ak2Sa7aQ`. Verify: `gh pr view` shows the title and base `main`.
