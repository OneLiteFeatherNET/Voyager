# Proposal

Conventional Commits: `test(fitness): freeze the open slice-boundary violations`

## Why

The rebuild's architecture rules check dependency direction between modules, but nothing checks that a class sits in the
right ring or slice. "Clean Architecture" and "Vertical Slice Architecture (VSA)" are therefore only partly enforced: 22 open
items of the migration list (archived `design.md` of `define-clean-architecture-with-vertical-slices`, items 1 to 27) break
slice rules and no rule fails when a 23rd appears. Adding the slice rules as plain failing rules would make `main` red, and
excluding the packages would hide the violations. ArchUnit's `FreezingArchRules` record the known violations as a committed
baseline, so the open items stay visible, new violations fail the build, and fixing an item shrinks the baseline.

This is a test-only change. It ships under `test(fitness)`: it changes no production code, no slice and no public type.

## What Changes

- Add `SliceBoundaryRulesTest` in `voyager-fitness` with eight `@ArchTest` rules. Each rule sets `allowEmptyShould(false)`
  before it is frozen, and each has a stable `as(...)` description, because the description is the baseline key.
  - Frozen, because each has known violations on `main`:
    - R1 the server does not depend on `race.scoring` (migration items 1 to 3);
    - R2 the server's `game` package does not depend on Minestom (items 6 to 9 and addendum item 28, see design);
    - R3 the platform infrastructure packages `text`, `convert`, `world`, `tick` and `render` do not depend on `race` (items 11, 13, 15, and 20, which is acceptable and frozen as it stands);
    - R4 the platform slice packages are free of cycles (item 27);
    - R5 platform classes that depend on `api.mapsetup` live in `platform.mapsetup` (item 24 and addendum item 29, see design);
    - R6 `voyager-setup` does not depend on Minestom (item 26).
  - Plain, because they pass today: R7 `race` slices are free of cycles; R8 `api` slices are free of cycles.
- On CI the fitness test task refuses to create or update a baseline. A new frozen rule without a committed store, and a
  fixed violation whose store entry was not removed, both fail the CI build. No public API or runtime behaviour changes.
- Commit the baseline under `voyager-fitness/src/test/resources/archunit_store/`, with `archunit.properties` beside it.
- Record in `design.md` which migration item each stored violation maps to. The rules cover 13 of the 22 open items. The other
  nine (items 5, 10, 12, 14, 16, 17, 18, 23 and 25) are not expressible as ArchUnit dependency rules, and they stay tracked only
  by the migration list.
- Add ADR-0020 (MADR 4.0, status Proposed until the owner accepts it) for the choice of a frozen baseline.
- Add a how-to guide for refreshing the baseline after a fix. Update `docs/explanation/architecture.md` (its enforcement
  table, its "a rule enters the suite only after the violations are fixed" sentence, and its stale link to the moved change
  `design.md`). Fix the same stale link in ADR-0017.

## Capabilities

### New Capabilities

None. The rules belong to the existing architecture capability.

### Modified Capabilities

- `architecture/architecture-enforcement`: rules may enter the suite frozen, with their known violations in a baseline,
  instead of only after the violations are fixed. The requirements for slice cycles, Minestom in setup and the server's
  scoring import now name their frozen baseline. Requirements are added for the baseline mechanism, the CI behaviour, the
  infrastructure and server-game boundaries, and the mapsetup classification.

## Slices and scope

- Slices the rules touch, read-only: `race` (ring, run, flow, scoring), `platform` (catalog, world, tick, render, text,
  convert, mapsetup, hud), the composition root `server` (game package), and `setup` (adapter package).
- Out of scope: fixing any violation. The fixes are the follow-up `extract-cup-slice`, which is being planned in parallel
  and which supersedes the name `move-cup-flow-out-of-server` recorded in the archived migration list, and the follow-ups
  `regroup-platform-by-slice`, `flatten-api-by-slice` and `regroup-setup-adapters`.
- Out of scope: the layered ring rule, the generated `internal`-package rule, the DI placement extensions, and the
  mutable-static and clock rules. They remain for `add-architecture-slice-rules`.
- No greenfield design decision (D1 to D10) changes. The change alters how rules enter the suite, which
  `architecture-enforcement` already governs.

## Impact

- `voyager-fitness/src/test/java/net/elytrarace/fitness/SliceBoundaryRulesTest.java` (new), and the existing
  `FitnessCoverageTest`, which must stay green because every rebuild module keeps a named rule.
- `voyager-fitness/build.gradle.kts`: CI override of the store flags; `workingDir` pinned to the module directory.
- `voyager-fitness/src/test/resources/archunit.properties` and `archunit_store/` (new, committed).
- No new dependency: `FreezingArchRule` is in `archunit` 1.5.0, which `archunit-junit5` already brings in.
- `docs/decisions/0020-*.md` (new), `docs/guides/how-to-refresh-the-architecture-baseline.md` (new),
  `docs/explanation/architecture.md`, `docs/decisions/0017-clean-architecture-with-vertical-slices.md` (stale link).
