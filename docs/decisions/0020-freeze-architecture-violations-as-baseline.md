# ADR-0020: Freeze the known slice-boundary violations in a committed baseline

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. The change that
introduces the baseline is `freeze-slice-boundary-violations`, which does not mark this record Accepted.

## Date

2026-10-10

## Decision makers

- TheMeinerLP (project owner). Decided on 2026-10-10 that CI fails on a stale baseline (strict), and that the composition
  roots of the setup server are excluded from the Minestom rule as wiring (option B).
- Drafted with the change `freeze-slice-boundary-violations`.

## Context and problem statement

The rebuild's architecture rules check the direction of dependencies between modules. They do not check slices. The
migration list of the archived change `define-clean-architecture-with-vertical-slices` records 22 open items that break
slice rules, and no rule fails when a new one appears. Three ways to add the slice rules are obvious, and each fails a
requirement:

- A plain rule that is red on `main` makes `main` red, and an unexplained red suite is the failure the project already
  fixed (`FitnessCoverageTest` exists because rules silently checked nothing).
- Excluding the violating packages, or narrowing a package pattern, hides the violations.
- A rule that is green only once every violation is fixed cannot be added before the follow-up changes.

The known violations must stay visible, every new one must fail the build, and a fixed one must leave the record in the
same change.

## Decision drivers

- `main` stays green, and the suite stays honest: no excluded rule, no narrowed pattern.
- A new violation fails the local and the CI build, and the failure names the rule and the class.
- A fixed violation is removed from the record in the same change that fixes it.
- Each recorded violation maps to a migration item, so the record cannot grow silently.
- No new dependency and no hand-written matching code.

## Considered options

1. **Plain rules, red on `main`.** Simple, but `main` is red until the follow-ups land.
2. **Exclusions or narrowed patterns.** Green, but the violations are no longer visible, and the architecture-enforcement
   spec forbids it.
3. **A hand-written Java allow-list.** Green and visible, but it re-implements ArchUnit's matching and shrinking, and that
   code would need tests of its own.
4. **ArchUnit `FreezingArchRule` with a committed text store, and CI that refuses to create or update the store.**
   Green, visible, reviewable as a text diff, and ArchUnit does the matching and the shrinking.
5. **`freeze.refreeze=true` for every refresh.** Refreezing records the new violations as well, so it hides exactly the
   regressions the baseline exists to catch.

## Decision outcome

Chosen option: **4**, because it keeps `main` green without excluding anything, and ArchUnit already implements matching
and shrinking. The decision has four parts.

**Mechanism.** A rule with known violations on `main` is frozen: `FreezingArchRule.freeze(rule.allowEmptyShould(false))`
with a `TextFileBasedViolationStore`. Its store is committed under `voyager-fitness/src/test/resources/archunit_store/`.
The rule description, set with `as(...)`, is the key of its entry, so it must stay stable. A rule with no violations is a
plain rule with `allowEmptyShould(false)`, so any violation fails it at once.

**Local and CI behaviour.** Locally, a developer may create the store and shrink it, and `freeze.refreeze` stays `false`.
On CI (`CI=true`), `voyager-fitness/build.gradle.kts` sets `archunit.freeze.store.default.allowStoreCreation=false` and
`archunit.freeze.store.default.allowStoreUpdate=false`. The build then fails when a frozen rule has no committed entry, and
when a fixed violation is still in the committed store. A new violation fails the rule everywhere.

**Refresh.** A fix commits its code change and the shrunk store together. The store diff of a fix shows removed lines only.
A store entry is never added by hand, and never added by a refreeze run, without a migration item approved by the owner.

**Scope.** The rules cover the slices of the rebuild, and the composition roots are outside them. The setup server's
composition root (`SetupServer` and `setup.inject`) imports Minestom for its bootstrap and bean wiring. It is excluded from
the Minestom rule, as the server's bootstrap is outside the server-game rule. The migration list treats both as wiring.

## Consequences

Positive:

- The known violations are visible in the repository, and each one maps to a migration item in the change's design.
- A new violation fails the build locally and on CI, and the failure names the rule and the class.
- Fixing a violation shrinks the record in the same change, and CI refuses a fix whose record was not shrunk.
- No new dependency. `FreezingArchRule` is part of ArchUnit 1.5.0, which `archunit-junit5` already brings in.

Negative:

- A store can be edited by hand to hide a violation. Review is the defence: the store diff must show only removals, and
  every added line needs an approved item.
- A changed rule description creates a new key, which CI rejects until the entry is committed with its item.
- Nine open items are not expressible as dependency rules, and they have no guard. They stay in the migration list.
- The store's file names are long, because they are derived from the rule descriptions.

## Confirmation

- `SliceBoundaryRulesTest` holds rules R1 to R8. R1 to R6 are frozen, R7 and R8 are plain.
- The CI behaviour was verified with `CI=true` in a throwaway worktree: a missing entry and a stale entry each fail the
  build with the messages in the how-to guide, and a new violation fails with the rule and the class named.
- `FitnessCoverageTest` checks that every rebuild module keeps a named rule, so a frozen rule cannot leave a module
  unconstrained.

## More information

- Change: `openspec/changes/archive/2026-10-10-freeze-slice-boundary-violations/` (the design lists the baseline entries
  and their migration items).
- How to refresh the baseline after a fix: [how-to-refresh-the-architecture-baseline](../guides/how-to-refresh-the-architecture-baseline.md).
- [ADR-0017](0017-clean-architecture-with-vertical-slices.md): the rings and slices the rules enforce.
