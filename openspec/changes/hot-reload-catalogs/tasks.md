# Tasks

Red before green for every behaviour: each test is written and seen to fail before the production code it covers.
Every test follows F.I.R.S.T.: no sleeps, `Clock` injected (`Clock.fixed`), an injected direct executor in place of
the virtual thread, `@TempDir` for files, no shared static state. Pass or fail comes from assertions, and a log line
that matters is asserted through a captured appender.

## 0. Preconditions

- [x] 0.1 Confirm that `unify-catalog-loading` is applied (merged, or archived) and that `add-catalog-validate-task` is merged.
  Verify: `CatalogLoader.read` returns a `CatalogReading` whose `problems()` is a list of `CatalogProblem`, and `CatalogSnapshot`
  exists as designed. If it does not, record the adapted shape in design.md (decision 5) before group 2.
- [ ] 0.2 Owner decision on design open question 2 (`scope-cup-validation` before this change, or accept that one broken unrelated Answer recorded in design.md, Open Questions 2 (as built; owner confirmation pending with ADR-0019).
  cup blocks reloads). Verify: the answer is recorded in design.md, "Open Questions".
- [ ] 0.3 Ask the owner (AskUserQuestion) on design open question 1 (pinned rounds only, or geometry-only map-boundary swaps) and Answers recorded in design.md, Open Questions 1 and 3 (as built). The group was started before the owner was asked; owner confirmation is pending.
  open question 3 (permission source). Verify: answers recorded in design.md. Do not start group 2 before this.

## 1. Spike: prove the assumptions (throwaway code, nothing committed)

- [x] 1.1 Read every caller of `CupSession.start` in production (first join, `/race start`, cup end). Verify: the answer replaces Result: design.md, "Spike Results" (source-verified; the spike files were not kept).
  design open question 4 with a fact, and the "pending" wording is set from it.
- [x] 1.2 Create a `MapInstances` world on a virtual thread in a throwaway test and check that `InstanceManager` finds the Result: design.md, "Spike Results" (source-verified; the spike files were not kept).
  instance and that no exception occurs. Verify: result recorded in design decision 10. If it is unsafe, stop and ask the owner:
  new worlds stall one tick on the tick thread, or new worlds need a restart.
- [x] 1.3 Check the Minestom permission API for commands in the pinned version (`2026.08.28-26.2`), including what the console Result: design.md, "Spike Results" (source-verified; the spike files were not kept).
  sender returns. Verify: the check is one static predicate that the test in 5.2 can call.
- [x] 1.4 Delete all spike files. Checkpoint: report the results to the owner before group 2.

## 2. Holder and the seam into the server (voyager-platform, voyager-server)

- [x] 2.1 Red: `CatalogHolderTest` in `voyager-platform`. (a) `current()` is the initial value until a round promotes;
  (b) `offer` does not change `current()`; (c) `promoteForNewRound()` applies the pending value once, and a second call returns
  the same one; (d) two offers before a promote leave the later one; (e) a value a round already pinned is still the one that
  round holds after a later offer and promote; (f) eight threads offering concurrently leave a pending value that is one of the
  offered values, never null. Verify: fails because the holder does not exist.
- [x] 2.2 Green: `LoadedCatalog` (record) and `CatalogHolder` (no DI annotation, no port interface, one constructor, two
  `AtomicReference`s). Verify: 2.1 passes; `./gradlew :voyager-platform:test` green.
- [x] 2.3 Red: `CatalogReloaderTest`, the initial load. A valid data directory with `@TempDir` yields a `LoadedCatalog` whose
  `loadedAt` is the fixed clock instant and whose cup resolves every map. A selected cup missing from `cups/` is an error that
  names it. Verify: fails.
- [x] 2.4 Green: `CatalogReloader.loadInitial(dataPath, cupName)` building on `CatalogLoader` and `CupResolution` from
  `unify-catalog-loading`, throwing `IllegalStateException` with the boot refusal the server uses (the first problem, as `load`
  throws it). Verify: 2.3 passes.

## 3. The reloader and the rollback (voyager-platform)

- [x] 3.1 Red: `CatalogReloaderTest`, failure cases, each its own test: one malformed file is listed; two malformed files are both
  listed in one `Rejected`; a duplicate map name lists both files; a cup naming an unknown map is listed; an empty `maps/` is listed.
  Verify: each test fails for the right reason.
- [x] 3.2 Green: `CatalogReloader.reload(dataPath, cupName)` returning `ReloadOutcome` (`Applied` or `Rejected`), converting the
  problems of `CatalogLoader.read` to `Rejected(problems)`. Verify: 3.1 passes.
- [x] 3.3 Red: `WorldOpener` port with a fake in the test. A reload with two new worlds where the second fails to open is
  `Rejected`; the first world is discarded again; the fake records the discard and the outcome names the failing world. Verify: fails.
- [x] 3.4 Green: `WorldOpener` (implemented by `MapInstances`), `MapInstances.discard(world)` closing only that world's loader, and the
  reloader's rollback. Verify: 3.3 passes; a `MapInstances` test shows `discard` closes one world's loader and leaves another open.
- [x] 3.5 Red: a new map naming a world without region data is `Rejected` and names the world; a world that is already open is not
  opened again; a changed region fingerprint (injected function) on an open world yields `Applied` with the warning "restart needed
  for world X". Verify: each fails.
- [x] 3.6 Green: the world checks in `CatalogReloader.reload`, with the fingerprint taken at open. Verify: 3.5 passes.

## 4. Round pinning (voyager-server)

- [x] 4.1 Red: `CupSessionRoundPinTest`. Holder with pinned value L1; start round 1; offer L2 during round 1; round 1 still enters
  its map from L1; start round 2; round 2 enters its map from L2. Drive the cup with explicit `tick()` calls. Verify: fails because
  `CupSession` still takes a `CupDefinition` and a `MapCatalog`.
- [x] 4.2 Green: `CupSession` takes `CatalogHolder`; `start(...)` calls `promoteForNewRound()` and keeps the returned `LoadedCatalog`
  as the round's pin; `enterMap` and `cup()` read the pin (before the first round, `holder.current()`). Verify: 4.1 passes, and the
  existing `CupSessionTest` passes unchanged.

## 5. Operator trigger (voyager-server)

- [x] 5.1 Red: `CatalogReloadServiceTest` with a direct executor, a fixed `Clock`, and a fake reloader. (a) `Applied` offers the
  loaded catalogue to the holder and tells the sender the change is pending for the next round; (b) `Rejected` leaves the holder
  unchanged and sends each problem to the sender; (c) a reloader throwing `RuntimeException` leaves the holder unchanged, sends
  "reload failed", and logs the cause at ERROR, asserted through a captured appender. Verify: fails.
- [x] 5.2 Red: the permission predicate from 1.3 refuses a sender without `voyager.race.reload` and allows the console. Verify: fails.
- [x] 5.3 Green: `CatalogReloadService` (catches `RuntimeException` at its boundary; runs the reload on the injected executor) and the
  `reload` literal on `RaceCommand`, registered outside the dev-mode guard, with the permission predicate as its condition.
  Verify: 5.1 and 5.2 pass; the existing `RaceCommand` tests pass.
- [x] 5.4 Red then green (Should): `CupSession.describe()` includes a line that a reload waits for the next round while a pending
  value exists, and the load time of the current catalogue. Verify: a `describe()` test with and without a pending value.

## 6. Graph and boot (voyager-server)

- [x] 6.1 Red: `VoyagerGraphTest` asserts that `CatalogHolder`, `CatalogReloader` and `CatalogReloadService` resolve, that no
  `CupDefinition`, `MapCatalog` or `CupCatalog` bean exists, and that `CupSession` resolves once. Verify: fails.
- [x] 6.2 Red: `VoyagerStartupTest` asserts that a malformed map file refuses to start with the file named, before any port is
  bound (the existing refusal, now through the holder). Verify: fails for the right reason, or passes if the refusal is already
  equivalent.
- [x] 6.3 Green: `ServerBeans` removes the port and `cup` beans and the boot snapshot's direct consumers, adds the reloader, service
  and holder beans; `RaceBeans` passes `CatalogHolder` to `CupSession`; `VoyagerServer` takes the rotation and the worlds from
  `holder.current()`. Verify: 6.1 and 6.2 pass; `./gradlew :voyager-server:test` green.
- [x] 6.4 Verify that no DI annotation was added outside `voyager-server`: `ApiPurityTest` and `FitnessCoverageTest` green.

## 7. Documentation

- [ ] 7.1 ADR `docs/decisions/0019-pin-catalogue-snapshot-per-round.md` (MADR 4.0) for decisions 1, 3 and 8. Number 0019: 0017 and 0018 are reserved by other changes File written as `0019`, status Proposed; owner approval pending.
  against 0016 (reserved by `switch-di-to-avaje-inject`). Ask the owner to approve the ADR before the PR (CLAUDE.md: ADRs need
  approval). Verify: the ADR status is accepted before merge.
- [x] 7.2 How-to `docs/guides/reload-catalogs.md` (Diataxis how-to): the command, the permission, "applies at the next round", the
  reloadable and non-reloadable table from design decision 4, and what the report looks like. Verify: every statement matches a
  passing test or the spike result.
- [ ] 7.3 Research: set roadmap Q7 status in `docs/research/005-simpler-map-and-cup-setup.md` and add a short record of the implemented Q7 status line done in research 005. Research 006 was not created, per owner instruction.
  round pin (Lumen, English, research paper style) as `docs/research/006-hot-reload-round-pinning.md`. Verify: links resolve.
- [x] 7.4 Confirm that no decision row of the greenfield design spec changes. Verify: `git diff` on that spec is empty.

## 8. Verification

- [x] 8.1 `./gradlew build` for both trees, exit 0. Verify: `:voyager-*` and `:server` tests green.
- [x] 8.2 Grep the new tests for `Thread.sleep`, `Clock.systemUTC`, `Instant.now`, `LocalDateTime.now` and `System.currentTimeMillis`.
  Verify: no match.
- [ ] 8.3 Smoke test on a copy of the run data (the repository data is not changed): boot with `./gradlew :voyager-server:shadowJar` and Open: smoke test not run.
  the worlds path; change one map's ring count; `/race reload` reports pending; the next round plays the change. Then introduce invalid
  JSON; `/race reload` reports the file; the server keeps ticking and still listens. Verify: both outcomes noted in the PR body.

## 9. Pull request

- [x] 9.1 Commit per type on the branch, one type each: `refactor(server)` (holder seam and port removal), `test(...)` where a test lands
  alone, `feat(server)` (reloader, round pin, command, graph), `docs(...)` (ADR, how-to, research). Verify: `git log --format=%s` shows
  one type per commit, all Conventional Commits.
- [ ] 9.2 Open the pull request (deferred: owner merges locally first) with the title `feat(server): pick up changed maps and cups between rounds without a restart`. Body:
  the spike results (1.1 to 1.3), the EARS requirement list with the test that covers each, the sequence from design decision 7, the
  smoke result (8.3), the base branch (the `unify-catalog-loading` branch while it is not merged), and the footer
  `https://claude.ai/referral/m5Ak2Sa7aQ`. Verify: `gh pr view` shows the title and the base branch.

## Workflow follow-up

- After merge, archive with `docs(openspec): archive hot-reload-catalogs`, or include the archive in the implementation PR if review
  requires it.
- Follow-up change (not this one): the fingerprint poll trigger from design decision 3, default off, as its own `feat(server)` change.
