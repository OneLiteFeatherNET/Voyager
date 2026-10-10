## 1. Red: the closed-item guard

- [x] 1.1 Red: append `RaceRuns.java` (item 11) to the closed-item list of `ClosedViolationsAreGoneTest` in `voyager-fitness`.
  Local only, not committed on its own. Verify: the test fails and names the seven `RaceRuns` lines still held by the R3 store.

## 2. Move the run holder and the transition (refactor(platform))

- [x] 2.1 Move `RaceRuns` and `MapTransition` from `platform.world` to `platform.cup` with `git mv`, and `UnstartedRunException`
  to `platform.cup.exception` with a new `package-info.java` (`@NotNullByDefault`). Move `RaceRunsTest` and `MapTransitionTest`
  to `platform.cup`. Update package declarations and imports; the test assertions do not change.
  Verify: `./gradlew :voyager-platform:compileJava :voyager-platform:compileTestJava` succeeds.
- [x] 2.2 Update the imports only in `CupSession`, `WaitingRoomTest`, `world/package-info.java` (javadoc paragraph) and the
  `voyager-server` files `CupBeans`, `ServerBeans`, `CupSessionGoldenMasterTest`, `VoyagerGraphTest`, `RaceCommandReloadTest`,
  `CupSessionLobbyTest`, `CupSessionRoundPinTest`, `CupSessionTest`, `CupWiring`. Verify: `git diff` of those files changes
  import lines only.
- [x] 2.3 Refresh the R3 store as `docs/guides/how-to-refresh-the-architecture-baseline.md` describes: run
  `./gradlew :voyager-fitness:test` without `CI`, then `git diff voyager-fitness/src/test/resources/archunit_store/`. The diff
  must show removed lines only, seven in the R3 store and none elsewhere. Edit no store line by hand.
- [x] 2.4 Verify: `./gradlew :voyager-platform:test :voyager-server:test` green; `git status` shows no change under
  `voyager-server/src/test/resources/` (golden files byte-identical).
- [x] 2.5 Commit `refactor(platform)` with the moves, the import changes and the shrunk store, and no other path.

## 3. Guard entry (test(fitness))

- [x] 3.1 Keep the entry from 1.1, which now passes, and verify `CI=true ./gradlew :voyager-fitness:test` green.
- [x] 3.2 Commit `test(fitness)`: add `RaceRuns.java` (item 11) to the closed-item list of `ClosedViolationsAreGoneTest`.

## 4. Documentation (docs(architecture))

- [x] 4.1 Update `docs/explanation/architecture.md`: the `run` and `cup` rows of the slice table (the holder and the transition
  are in `platform.cup`); the infrastructure row; the enforcement row for R3 (frozen items 13, 15 and 20); the named gap for the
  placement; the migration status (items 11 and 12 resolved, counts adjusted by two, follow-up 3 notes the closure).
- [x] 4.2 Add `docs/decisions/0023-run-holder-with-map-transition-in-platform-cup.md` in MADR 4.0 format, status Proposed,
  recording decision 1 and the alternatives of `design.md`.
- [x] 4.3 Commit `docs(architecture)` with the two files and no other path.

## 5. Verify and merge

- [x] 5.1 `CI=true ./gradlew :voyager-fitness:test` green.
- [x] 5.2 `./gradlew build` green for both trees; record the test count.
- [x] 5.3 `openspec validate move-race-run-holder --strict` passes.
- [x] 5.4 Merge `refactor/move-race-run-holder` into `main` with `git merge --no-ff`, then archive with
  `openspec archive move-race-run-holder --yes`, check `openspec validate --specs --strict`, and commit
  `docs(openspec): archive move-race-run-holder`.

## 6. Pull request title

- [x] 6.1 The last task opens the pull request under the title `refactor(platform): move the race-run holder out of the world package`.
  No pull request is opened from here; the owner merges locally.
