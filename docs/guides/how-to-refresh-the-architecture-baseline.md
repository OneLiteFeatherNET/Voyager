# Refresh the architecture baseline after a fix

The slice-boundary rules of `voyager-fitness` freeze their known violations in a committed baseline. The baseline lives in
`voyager/fitness/src/test/resources/archunit_store/`, one text file per frozen rule, and `stored.rules` indexes them. When
you fix a recorded violation, the baseline must shrink in the same change. This guide shows how.

The reasons for the baseline are in [ADR-0020](../decisions/0020-freeze-architecture-violations-as-baseline.md).

## Before you start

- You have fixed the code that a recorded violation describes, and the code compiles.
- You know which migration item the fix closes. The mapping is in the design of the change that introduced the baseline,
  in `openspec/changes/archive/2026-10-10-freeze-slice-boundary-violations/design.md`, section D7.

## Refresh the baseline

1. Run the fitness tests locally, without `CI`:

   ```bash
   ./gradlew :voyager:fitness:test
   ```

   ArchUnit removes the fixed violation from its store file during this run. The run passes.

2. Check the store diff:

   ```bash
   git diff voyager/fitness/src/test/resources/archunit_store/
   ```

   The diff must show removed lines only. An added line means a new violation, and the fix is not complete or the
   violation is new. A new violation needs an item of the design and the owner's approval before it is recorded. Do not
   commit it otherwise.

3. If a rule's store file is now empty, the rule has no known violations left. Make it a plain rule in the same commit:
   drop its `freeze(...)` wrapper and its `persistIn(...)`, keep `allowEmptyShould(false)`, delete its `.txt` file, and
   delete its line from `stored.rules`.

4. Run the same check the way CI runs it, to confirm the committed state passes:

   ```bash
   CI=true ./gradlew :voyager:fitness:test
   ```

5. Commit the fix and the shrunk store together. The commit type is the type of the fix, because the baseline update is
   part of the fix.

## Never do this

- Do not run with `freeze.refreeze=true`. Refreezing records the new violations as well, which hides exactly the
  regressions the baseline exists to catch.
- Do not edit a store line by hand to make a test pass.
- Do not add a store entry without a migration item that the owner has approved.

## What the CI messages mean

CI runs with `CI=true`, so the build refuses to create or update the store. Three failures can occur. Each message is the
text the test report shows.

**`Updating frozen violations is disabled (enable by configuration freeze.store.default.allowStoreUpdate=true)`**

The store still holds a violation that no longer occurs, or a frozen rule has no entry in `stored.rules`. Either you fixed
a violation and did not commit the shrunk store, or the description of a rule changed, or a new frozen rule was added
without its entry. Fix: run `./gradlew :voyager:fitness:test` locally without `CI`, check the diff as in step 2, and commit
the store with the change. For a new frozen rule, record its entry only with an approved item.

**`Creating new violation store is disabled (enable by configuration freeze.store.default.allowStoreCreation=true)`**

The committed store has no `stored.rules` at all. Nothing in the baseline is recorded any more. Fix: restore the directory
from git with `git checkout -- voyager/fitness/src/test/resources/archunit_store`. Do not regenerate it locally, because
regenerating would record every current violation, including new ones.

**A violation message such as `Architecture Violation ... was violated (1 times)`**

A new violation, which the baseline does not contain, names the rule and the class. Fix the class so that it no longer
depends on the forbidden package. Do not add a store entry for it.
