# Tasks

Every behaviour is test-first (Red, then Green, then Refactor). Each test name states one behaviour. Commits follow
Conventional Commits; all production work is `feat(platform)`, so there is one type.

## 0. Prerequisite

- [ ] 0.1 Confirm `unify-catalog-loading` is merged (or archived) and that `CatalogLoader.read` returns `CatalogReading` with `CatalogProblem`s as designed in D5. Verify: `CatalogLoaderTest` of that change is green on `main`.
- [ ] 0.2 Confirm `name-both-cup-switches` is merged, so the `ambiguous` text already names both switches. Verify: `grep -n "DVOYAGER_CUP" voyager-server/src/main/java/net/elytrarace/voyager/server/game/exception/UnresolvedCupException.java` shows the Gradle form too.

## 1. Platform: per-file problems and the cross-catalogue check

- [ ] 1.1 Red: `CatalogLoaderTest.unparseableCupFileIsReportedAndDoesNotAbortTheRead`. A directory with one valid and one malformed cup file gives a reading with the valid cup and one problem naming the file.
- [ ] 1.2 Red: `CatalogLoaderTest.emptyOrUnreadableCupDirectoryStillAbortsTheRead`. Keeps today's `UnreadableCatalogException`.
- [ ] 1.3 Green: expose `fileCount()` on `CatalogReading` (parsed plus problem files in `cups/`). Verify: 1.1 and 1.2 pass.
- [ ] 1.4 Red: `CatalogConsistencyTest.selectedCupWithUnresolvedMapIsRefusedListingOnlyItsEntries`.
- [ ] 1.5 Red: `CatalogConsistencyTest.unselectedCupWithUnresolvedMapIsReturnedNotThrown`.
- [ ] 1.6 Red: `CatalogConsistencyTest.consistentCupsReturnNoProblems`.
- [ ] 1.7 Green: implement the selected-cup check and the returned list of other-cup problems (D4). Message built with `formatted`.
- [ ] 1.8 Refactor: remove the old all-cups `requireEveryCupMapResolves` once no caller remains. Verify: `unify-catalog-loading` `load` still throws the same first problem for maps.

## 2. Server: selection rules

- [ ] 2.1 Red: `CupResolutionTest.unnamedSelectionIsAmbiguousWhenAMalformedSecondFileExists`, asserting that the message names both cup file names, `-Pcup=` and `-DVOYAGER_CUP=`. (D2, owner decision 2026-10-09)
- [ ] 2.2 Red: `CupResolutionTest.namedSelectionOfAMalformedFileNamesThatFile`. (D3)
- [ ] 2.3 Red: `CupResolutionTest.unknownNamedSelectionKeepsTheNoSuchCupMessage`.
- [ ] 2.4 Green: implement D2 and D3 in `game/CupResolution` and `game/exception/UnresolvedCupException`. Append the cup file names to the existing `ambiguous` text; keep the switch wording from `name-both-cup-switches`.

## 3. Server: boot wiring and the warning

- [ ] 3.1 Add `log4j-core` test dependency to `voyager-server` if it is missing. Test scope only.
- [ ] 3.2 Red: `CupBootValidationTest.bootWarnsOnceWithEveryUnplayableCup`. A captured `ListAppender` sees exactly one WARN event containing both problems.
- [ ] 3.3 Red: `CupBootValidationTest.bootIsSilentWhenEveryCupIsConsistent`. No WARN event.
- [ ] 3.4 Red: `CupBootValidationTest.aBrokenUnselectedCupDoesNotRefuseBoot`.
- [ ] 3.5 Red: `CupBootValidationTest.aBrokenSelectedCupRefusesBootWithItsEntriesOnly`.
- [ ] 3.6 Red: `CupBootValidationTest.onlyTheSelectedCupIsPlayed`. Every map played belongs to the selected cup.
- [ ] 3.7 Green: rewrite `ServerBeans.cup` in the order of D1: resolve, check the selected cup, log one WARN from the returned list (D7). Use `LogManager.getLogger(ServerBeans.class)`.
- [ ] 3.8 Refactor: keep `ServerBeans` free of new annotations. Only the composition root may carry DI annotations.

## 4. Verification and docs

- [ ] 4.1 Add a `docs/` operations note: what the boot warning means and how to fix an unplayable cup. Diátaxis how-to, English.
- [ ] 4.2 Run `./gradlew :voyager-platform:test :voyager-server:test :voyager-fitness:test`, then `./gradlew build`, both trees. All must pass.
- [ ] 4.3 Record the owner's answers (2026-10-09: D2 refuse, see `design.md`). Ask the owner only D6 (cup `mode` default); leave it open in the PR text.

## 5. Ship

- [ ] 5.1 Open the pull request. Title: `feat(platform): refuse to boot only for the cup that will be played`. Body: link this change, the research 005 Q4 entry, and the referral footer `https://claude.ai/referral/m5Ak2Sa7aQ`.
- [ ] 5.2 After merge, archive with the commit `docs(openspec): archive scope-cup-validation`, unless the owner asks to ship it with the implementation.
