# Tasks

Each task that changes files is one commit of one Conventional Commits type, named in its text. A task marked
"(no commit)" is a check. Red/Green TDD applies to every task that adds behaviour: the failing test is written and
seen to fail first, then the code. Every commit leaves `./gradlew build` green. The golden files under
`voyager-server/src/test/resources/golden/cup-session/` never change after task 1.2.

**Frozen store rule.** A task that closes a migration item removes that item's entries from the frozen store
(`voyager-fitness/src/test/resources/archunit_store/`) in the same commit, as `freeze-slice-boundary-violations` design D4
requires. No task adds a store entry. Design D6 lists the effect on each frozen rule.

## 0. Prerequisite and baseline

- [ ] 0.1 Confirm `freeze-slice-boundary-violations` is archived on `main`, and record the path of the frozen store it creates. Confirm that the store holds the entries of design D6 of this change: R1 (items 1, 2, 3), R2 (items 6, 7, 8, 9 and addendum item 28) and the open items of R3 to R6. Confirm the owner has approved the addendum numbers 28 and 29 (freeze task 1.2). Stop the change if any of this is missing (verify: `openspec list` shows it archived; `./gradlew :voyager-fitness:test` is green on `main`). (no commit)
- [ ] 0.2 Record the baseline on `main`: `./gradlew build` for both trees, and the test counts of `voyager-server`, `voyager-race`, `voyager-platform` and `voyager-fitness` (verify: build is green; counts are noted in the PR description). (no commit)

## 1. Characterization first (Golden Master)

- [ ] 1.1 Spike the two capture mechanisms for chat and title lines per racer, Minestom Testing connection packets and a recording wrapper, on unchanged `main`, and keep the cheaper one that yields each racer's messages in order (verify: the spike result is named in the commit body of 1.2; no spike code is committed). (no commit)
- [ ] 1.2 `test(server)`: add `CupSessionGoldenMasterTest`, its transcript helper, and the four scenarios `lobby-and-start`, `racing-boost-disconnect`, `skip-finish`, `restart-pending-reload`, with their golden files generated from unchanged `main` (verify: `./gradlew :voyager-server:test --tests '*CupSessionGoldenMasterTest*'` is green, and every golden file contains a `describe()` block per tick and no logger name).
- [ ] 1.3 Prove the harness is not vacuous with two uncommitted probes on unchanged production code: swap the boost and flight steps, then change one message character. Each probe must turn at least one scenario red. Revert both and check the production tree is clean (verify: both probes red, `git status` shows no production change). (no commit; the result goes in the commit body of 1.2)

## 2. Pure cup slice in race

- [ ] 2.0 `test(fitness)`: add `ClosedViolationsAreGoneTest` in `voyager-fitness`. It reads the frozen store and fails when a stored line names the source file of a closed item. Its list of closed files starts empty, so the test is green (verify: `./gradlew :voyager-fitness:test` is green; the test class exists and names the store path it reads). The list is extended by tasks 2.2, 3.1 and 4.6.
- [ ] 2.1 `refactor(race)`: add `race.cup.CupRound` with `CupRoundTest`, written red first. The test pins that `recordMap` scores through `MapScorer`, that `closeMap` uses the cup's mode, that `scoreOn` returns the recorded score, and that `reset` clears the board (verify: `./gradlew :voyager-race:test` is green; the test was seen failing before `CupRound` existed). Add `race.cup/package-info.java`.
- [ ] 2.2 `refactor(race)`: move `CupStanding` and `CupStandings` with `CupStandingsTest` from `server.game` to `race.cup`, and `UnresolvedCupException` from `platform.catalog.exception` to `race.cup.exception` with its `package-info.java`. Update the imports of `CupSession`, `CupResolution` and their tests.
  - Red: append the source files of `CupStanding.java` and `CupStandings.java` (items 2 and 3) to the list of `ClosedViolationsAreGoneTest`. The test fails, because the R1 store still holds their lines.
  - Green: remove those R1 lines from the frozen store in this commit.
  - Verify: `./gradlew :voyager-race:test :voyager-platform:test :voyager-server:test :voyager-fitness:test` green; golden files byte-identical; items 2, 3 and 5 closed (item 5 has no store entry).
- [ ] 2.3 `refactor(race)`: add `race.cup.MapFigures` with `MapFiguresTest`, written red first. The test pins the in-flight figures (a finished racer shows the clock they finished on; the last ring tick is the last passed ring's tick, or 0) and the starting figures (game tick 0, nothing flown, the medal outlook at zero elapsed) (verify: `./gradlew :voyager-race:test` green; the test was seen failing before `MapFigures` existed).

## 3. Platform adapters (Minestom)

- [ ] 3.1 `refactor(platform)`: move `Racers` and `Rockets` with their tests from `server.game` to `platform.flight`, `CurrentMapBlocks` with its test to `platform.world`, and `LivePlayerSampler` with its test to `platform.cup` (creating `platform.cup/package-info.java`). Widen visibility only where a caller moved. `LivePlayerSampler` goes to `platform.cup`, not `platform.flight`, because it imports `platform.tick`, and `platform.tick` already imports `platform.flight` (design D2).
  - Red: append the source files `Racers.java`, `Rockets.java`, `CurrentMapBlocks.java` and `LivePlayerSampler.java` (items 6, 7, 9 and 8) to the list of `ClosedViolationsAreGoneTest`. The test fails, because the R2 store still holds their lines.
  - Green: remove those R2 lines from the frozen store in this commit.
  - Verify: `./gradlew :voyager-platform:test :voyager-server:test :voyager-fitness:test` green. The R4 cycle rule stays green, which shows that no flight-to-tick edge was added. Golden files byte-identical. Items 6, 7, 8 and 9 closed.
- [ ] 3.2 `refactor(platform)`: add `platform.hud.HudStates` with `HudStatesTest`, written red first. The test pins that each field of `HudState` equals the matching `MapFigures` component, using the figures of the racing-boost scenario. `CupSession` maps through `HudStates` (verify: `./gradlew :voyager-platform:test` green; golden files byte-identical).

## 4. Tick order and the cup adapter

- [ ] 4.1 `refactor(platform)`: add `platform.cup.TickStep` and `platform.cup.TickPipeline` with `TickPipelineTest`, written red first. The test pins that steps run in the declared order, that the built list is unmodifiable, that an empty list is refused, and that each step keeps its name (verify: `./gradlew :voyager-platform:test` green; the test was seen failing before the classes existed).
- [ ] 4.2 `refactor(server)`: rename `RaceBeans` to `CupBeans`, and construct `CupSession` through a public constructor instead of `CupSession.create`. The constructor creates its own `CupRound`, so `CupBeans` imports no `race.cup` or `race.scoring` type. `VoyagerGraphTest` and the tests that call `create` move to the constructor (verify: `./gradlew :voyager-server:test` green; golden files byte-identical).
- [ ] 4.3 `refactor(server)`: declare the cup tick order once in `CupBeans` as a `TickPipeline` of `flight sample`, `boost burn` and `phase advance`, with `CupSession.sampleFlight()` and `advancePhase()` as the step bodies. `VoyagerServer.scheduleTick` runs the pipeline and `CupSession.tick()` is removed. Tests that drove `tick()` build the same pipeline through `CupBeans.tickPipeline` (verify: golden files byte-identical; `CupSessionTest` green; item 10 closed, list-only, no store entry).
- [ ] 4.4 `refactor(platform)`: extract `CupAnnouncer` from `CupSession` directly into `platform.cup`, not into `server.game`: `report`, `announceMapScore`, `announceCupResult`, `announceCupPlace`, `broadcast` and the fallback clock. It is public, so `CupSession` in `server.game` calls it (verify: golden files byte-identical; `CupSessionTest` green; the R1 store gains no line, because the announcer is outside `server`).
- [ ] 4.5 `refactor(server)`: route `CupSession` scoring through `CupRound` and HUD figures through `MapFigures` and `HudStates`, so that `CupSession` computes no score and no figure itself (verify: golden files byte-identical; the R1 store has no new line, and its line count for `CupSession` does not grow).
- [ ] 4.6 `refactor(platform)`: move `CupSession` from `server.game` to `platform.cup`.
  - Red: append `CupSession.java` (items 1 and 28) to the list of `ClosedViolationsAreGoneTest`. The test fails, because the R1 and R2 stores still hold its lines.
  - Green: remove the R1 and R2 lines of `CupSession.java` from the stores. Both stores are now empty, so make R1 and R2 plain in this commit: remove their `freeze(...)` wrapper and `persistIn`, delete their `.txt` store files and their lines in `stored.rules`, and keep `allowEmptyShould(false)`. Extend R1 to `race.cup`: no class in `..server..` depends on `race.scoring..` or `race.cup..`.
  - Verify: `./gradlew :voyager-server:test :voyager-fitness:test` green; golden files byte-identical; the logger category changes and the golden transcripts do not; items 1 and 28 closed.

## 4a. Retarget the server Minestom rule

- [ ] 4.7 `test(fitness)`: retire R2 and add R2' from design D6, `allowEmptyShould(false)`.
  - Red: add a scratch class `net.elytrarace.voyager.server.ProbeMinestom` in the `server` root that imports a `net.minestom` type. R2' must fail on it, and R2 would not. Record the result. Then remove the probe.
  - Green: commit R2' (the `server.game` package still exists, with `CatalogReloadService` in it, so the rule matches a class). Delete R2 (its store file is already gone since 4.6).
  - Verify: `./gradlew :voyager-fitness:test` green with the probe absent; the probe was red.

## 5. Composition root

- [ ] 5.1 `refactor(server)`: move `CatalogReloadService` from `server.game` to the `net.elytrarace.voyager.server` root, delete the `server.game` package and its `package-info.java`, and build `RaceCommand` as a `@Bean` in `ServerBeans`. `VoyagerServer` takes it with `graph.get(RaceCommand.class)` and registers it at the same point (verify: `./gradlew :voyager-server:test` green, including `VoyagerGraphTest`, `RaceCommandReloadTest` and `VoyagerStartupTest`; `grep -rn "server.game" voyager-server/src` is empty; R2' stays green).
- [ ] 5.2 `test(fitness)`: add the race-command rule of design D6, `allowEmptyShould(false)`: no class outside `..server.inject..` calls a `RaceCommand` constructor.
  - Red: add a scratch class that calls `new RaceCommand(...)` outside `..server.inject..`. The rule must fail. Then remove the probe.
  - Green: commit the rule. Verify: `./gradlew :voyager-fitness:test` green with the probe absent; the probe was red.

## 6. Verification of the store and the rules

- [ ] 6.1 Check the frozen store at the end of phase 5 (no commit): it holds exactly the open entries (R3: items 11, 13, 15, 20; R4: item 27; R5: items 24 and 29; R6: item 26), and no line for items 1, 2, 3, 6, 7, 8, 9 or 28. R1 and R2 have no store file. `ClosedViolationsAreGoneTest` lists the source files of items 2, 3, 6, 7, 8, 9, 1 and 28. Items 5 and 10 have no store entry. Verify: `git status` shows no unexpected store change; `./gradlew :voyager-fitness:test` green.
- [ ] 6.2 Run the fitness suite as CI does (no commit): `CI=true ./gradlew :voyager-fitness:test` on the final tree. Verify: exits 0, and `git status` shows no change under `archunit_store/` (a shrink on CI would fail the build, so none is expected).

## 7. Documentation

- [ ] 7.1 `docs(architecture)`: update `docs/explanation/architecture.md`: migration status (items 1, 2, 3, 5, 6 to 10, and addendum 28 resolved; counts recalculated), the cup row (`CupSession` and the announcer in `platform.cup`; `race.cup` holds `CupRound`, `CupStandings`, `MapFigures`), and the flight row (`server.game` is gone) (verify: `grep -n "server.game" docs/explanation/architecture.md` finds only historical statements).
- [ ] 7.2 `docs(reference)`: add a row for "Golden Master" to `docs/reference/semantic-anchors.md`, with how Voyager applies it: the cup transcript, and the rule that golden files are never regenerated by a move (verify: the anchor is listed, and the row names `CupSessionGoldenMasterTest`).
- [ ] 7.3 `docs(adr)`: add `docs/decisions/0021-cup-use-cases-in-race-cup.md` as a MADR 4.0 record with status **proposed**, covering D1 to D4 of design.md, with the alternatives named there (verify: the file has the MADR sections; status is proposed; the owner's approval is requested in the PR). The number is 0021: ADR-0020 belongs to `freeze-slice-boundary-violations`.

## 8. Integration and pull request

- [ ] 8.1 Integration check across the change (no commit): `./gradlew build` for both trees; `openspec validate extract-cup-slice --strict`; the four golden scenarios green; the frozen store holds none of the closed items; R1 is plain and covers `race.cup`; R2' is in place; `grep -rn "server.game" voyager-*/src docs/explanation` shows only historical text.
- [ ] 8.2 Open the pull request with the title `refactor(race): extract the cup slice from the server module`. The body carries the verification results of 8.1, the baseline counts of 0.2, the probe results of 1.3, 4.7 and 5.2, and the footer: the body ends with the footer line `https://claude.ai/referral/m5Ak2Sa7aQ`, followed by the session link `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz` as the last line. Verify: `gh pr view` shows the title, and the CI status is recorded in the PR description. The owner merges locally and archives without a pull request, as in `define-clean-architecture-with-vertical-slices` task 3.5. This task stays as written; after a local merge, record the merge commit here in place of the PR reference and mark the task done. This is the last task.

## Workflow follow-up

- After the owner approves ADR-0021 and merges (locally, or by pull request), archive the change with the commit `docs(openspec): archive extract-cup-slice`. The archive applies the RENAMED and MODIFIED blocks to `openspec/specs/architecture/architecture-enforcement/spec.md`, which requires `freeze-slice-boundary-violations` to be archived first.
- Sync the three delta specs and the new `cup-session-characterization` capability into `openspec/specs/` as part of the archive, and fill that capability's Purpose if it is still a placeholder.
- Follow-up 3 (`regroup-platform-by-slice`) and follow-up 4 (`flatten-api-by-slice`) start from the updated migration status. Follow-up 3 also decides whether `FlightSample` and `FlightSampler` move to `platform.flight` (design, Open Questions).
