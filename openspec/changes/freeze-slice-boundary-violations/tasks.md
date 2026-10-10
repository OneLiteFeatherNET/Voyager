# Tasks

Red/Green TDD applies: the Red group adds the rules unfrozen and proves they fail for the known violations; the Green
group freezes them. Every test follows F.I.R.S.T. (no sleeps, no clock, no shared static state). No task edits production
code or existing tests.

## 1. Red: rules fail on the known violations

- [ ] 1.1 Add `voyager-fitness/src/test/java/net/elytrarace/fitness/SliceBoundaryRulesTest.java` with rules R1 to R8 from design D6, each an `@ArchTest` with `allowEmptyShould(false)` and a stable `as(...)` description, none frozen yet. Verify: `./gradlew :voyager-fitness:test --tests 'net.elytrarace.fitness.SliceBoundaryRulesTest'` exits non-zero, and the failure output names the rules R1 to R6 and R4, and no rule fails with the empty-should error (the package patterns match classes).
- [ ] 1.2 Reconcile the Red output with design D6 and D7. Replace the expected item numbers with the confirmed ones, record the violation count per rule, and add an addendum item for each violation that no migration item names. Ask the owner to confirm the addendum numbers before group 2. Verify: every violation in the Red output appears in the reconciled D7 table, and the R7 and R8 rows show zero violations or are changed to frozen.

## 2. Green: freeze the rules and commit the baseline

- [ ] 2.1 Wrap each rule with known violations as `freeze(rule.allowEmptyShould(false)).persistIn(new TextFileBasedViolationStore(name -> <slug> + ".txt"))`, add `voyager-fitness/src/test/resources/archunit.properties` with the four keys of design D3, and pin `workingDir = projectDir` plus the `CI`-conditional `archunit.freeze.store.default.allowStoreCreation` and `allowStoreUpdate` overrides in `voyager-fitness/build.gradle.kts`. Verify: `./gradlew :voyager-fitness:test` (CI unset) exits 0, and `voyager-fitness/src/test/resources/archunit_store/` contains `stored.rules` and one `.txt` file per frozen rule.
- [ ] 2.2 Check the committed store against the reconciled D7 table: count the lines of each `.txt` file and confirm each line names a source file that an item of the table covers. Verify: each count equals the table's count, and no line is unmapped.

## 3. Verify the failure modes

- [ ] 3.1 In a throwaway worktree branched from the up-to-date remote `main`, with this change's files copied in by absolute path, add a forbidden `net.minestom` import to a new class in `server.game` that is not in the baseline and run `./gradlew :voyager-fitness:test`. Verify: the run exits non-zero and the output names the rule R2 and the new class. Remove the worktree afterwards.
- [ ] 3.2 In the same worktree, with `CI=true`, delete one frozen rule's entry from `stored.rules` and run the tests. Verify: the run exits non-zero with the message "Creating new violation store is disabled". Record the exact message for the how-to guide.
- [ ] 3.3 In the same worktree, with `CI=true`, remove one baselined import from its class without shrinking the store and run the tests. Verify: the run exits non-zero with the message "Updating frozen violations is disabled". Record the exact message for the how-to guide.
- [ ] 3.4 In the main checkout, with `CI=true` and the committed store unchanged, run `./gradlew :voyager-fitness:test`. Verify: it exits 0, `git status` shows no change under `archunit_store/`, and the test classes `FitnessCoverageTest`, `ApiPurityTest`, `DesignRuleTest` and `NullabilityConventionTest` all pass.

## 4. Documentation

- [ ] 4.1 Write `docs/decisions/0020-freeze-architecture-violations-as-baseline.md` in MADR 4.0, in the layout of ADR-0017, with status Proposed and the options and consequences of design D1 to D3. Verify: the file exists, its status reads "Proposed", and the ADR index lists it. Acceptance by the owner is a separate step.
- [ ] 4.2 Write `docs/guides/how-to-refresh-the-architecture-baseline.md` as a Diátaxis how-to with the five steps of design D4 and the two CI messages recorded in tasks 3.2 and 3.3, each with its fix. Verify: the commands in the guide run as written in the main checkout, and the messages in the guide match the output of tasks 3.2 and 3.3.
- [ ] 4.3 Update `docs/explanation/architecture.md`: point its links (line 6 and the references to the migration list) to `openspec/changes/archive/2026-10-10-define-clean-architecture-with-vertical-slices/`; replace "A rule enters the suite only after the violations it would flag are fixed" with the frozen-baseline policy and a link to the how-to guide; add the R1 to R8 rows to the enforcement table with their frozen or plain status; and record the nine list-only items in the migration status. Verify: every relative link in the page resolves with `test -e`, and the test class and rule names on the page match `SliceBoundaryRulesTest`.
- [ ] 4.4 Point the link in `docs/decisions/0017-clean-architecture-with-vertical-slices.md` (line 164) to the archive path of the change. Verify: `test -e` on the new path succeeds, and `grep -rn 'openspec/changes/define-clean-architecture-with-vertical-slices' docs` finds no path that is absent.

## 5. Validation and pull request

- [ ] 5.1 Run `openspec validate freeze-slice-boundary-violations --strict`. Verify: the command exits 0.
- [ ] 5.2 Open the pull request against `main` with the title `test(fitness): freeze the open slice-boundary violations`. The body summarises rules R1 to R8, the frozen baseline and its refresh guide, and the nine list-only items. The body ends with the footer line `https://claude.ai/referral/m5Ak2Sa7aQ`, followed by the session link `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz` as the last line. Verify: `gh pr view --json title,baseRefName` returns that exact title and base `main`. The owner merges locally and archives without a pull request, as in `define-clean-architecture-with-vertical-slices` task 3.5. This task stays as written; after a local merge, record the merge commit here in place of the PR reference and mark the task done.

## Workflow follow-up

- After the owner merges, archive the change with the commit `docs(openspec): archive freeze-slice-boundary-violations`, unless it shipped with the implementation.
- The follow-up `add-architecture-slice-rules` keeps the rules that this change does not add (layered ring rule, `internal` packages, DI placement, mutable statics, clock reads). Name it in its own proposal.
- Each migration follow-up that fixes a frozen violation shrinks the baseline in its own pull request, as design D4 describes.
