# Design

## Context

The rebuild's rules live in `voyager-fitness` (ArchUnit 1.5.0, `allowEmptyShould(false)` on every rule). They check
module direction, purity and DI placement. They do not check slices. The migration list in the archived `design.md` of
`define-clean-architecture-with-vertical-slices` (items 1 to 27, 22 open) records the slice violations that such rules
would flag. See proposal.md, Why, for the motivation.

Two facts about ArchUnit 1.5.0 shape the design. They were checked against the library sources, not from memory:

- `FreezingArchRule` stores violations per rule, keyed by the rule description. A violation is "known" when a stored
  violation matches it, and the default matcher ignores line numbers. Stored violations that no longer occur are removed
  from the store during evaluation, which needs `freeze.store.default.allowStoreUpdate=true`. Creating a store index needs
  `freeze.store.default.allowStoreCreation=true`, and its default is `false`.
- With `allowEmptyShould(false)`, a rule that matches no class throws an `AssertionError` before the store is written. A
  frozen rule therefore cannot pass vacuously.

## Goals / Non-Goals

**Goals:**
- Every open slice violation that an ArchUnit dependency rule can express is in a committed baseline, and mapped to its
  migration item.
- A new violation of any frozen rule fails the local and the CI build.
- A fixed violation leaves the baseline in the same change, and the baseline cannot be created or updated on CI.

**Non-Goals:**
- Fixing any violation. Those are the follow-ups, and `extract-cup-slice` is planned in parallel.
- Expressing the nine open items that are not dependency rules (see Decisions D7). They stay tracked by the migration list.
- The layered ring rule, the generated `internal`-package rule, the DI placement extensions and the mutable-static and
  clock rules. They stay in `add-architecture-slice-rules`.

## Decisions

### D1. Mechanism: ArchUnit `FreezingArchRule` with a text store

Each rule that has known violations is wrapped as `freeze(rule.allowEmptyShould(false)).persistIn(new
TextFileBasedViolationStore(name -> slug(name) + ".txt"))`. Rule descriptions are set with `as(...)` and are stable, because
the description is the key in the store.

Alternatives considered:
- **Plain rules, red on `main`.** Rejected: `main` must stay green, and an unexplained red suite is the failure mode the
  project already fixed (`FitnessCoverageTest` exists because rules silently checked nothing).
- **Excluding the packages or the classes.** Rejected: the architecture-enforcement spec forbids excluding a rule to make
  it pass, and an exclusion stops the violation from being visible at all.
- **A hand-written Java allow-list.** Rejected: it re-implements ArchUnit's matching and shrinking, and that code would
  need tests of its own.
- **`freeze.refreeze=true` for every refresh.** Rejected: refreezing stores the new violations too, so it hides exactly
  the regressions the baseline exists to catch.

### D2. Store location and format

The store is `voyager-fitness/src/test/resources/archunit_store/`. It holds `stored.rules` (ArchUnit's index, which maps
each rule description to its file) and one `<rule-slug>.txt` per frozen rule, one violation per line. Plain text gives
reviewable diffs. The directory name and the slug names are deterministic, so a diff shows which rule changed.

The path is set in `archunit.properties` as `freeze.store.default.path=src/test/resources/archunit_store`. It is relative
to the working directory of the test JVM, and `voyager-fitness/build.gradle.kts` pins that to the module directory with
`workingDir = projectDir`. ArchUnit's default path is `archunit_store` in that same directory, so a missing override would
write to the wrong place; the pin makes that a visible setting.

### D3. Configuration: committed defaults, CI overrides

`voyager-fitness/src/test/resources/archunit.properties` (committed) sets:

```
freeze.store.default.path=src/test/resources/archunit_store
freeze.store.default.allowStoreCreation=true
freeze.store.default.allowStoreUpdate=true
freeze.refreeze=false
```

The local defaults let a developer create and shrink the store. On CI (`CI=true`), `voyager-fitness/build.gradle.kts` sets
the test JVM's system properties `archunit.freeze.store.default.allowStoreCreation=false` and
`archunit.freeze.store.default.allowStoreUpdate=false`. ArchUnit's configuration reads system properties with the
`archunit.` prefix and lets them override `archunit.properties`. The build never sets `freeze.refreeze`, so it stays
`false` everywhere.

Behaviour on CI, all of it failing the build:
- a frozen rule with no entry in an existing committed `stored.rules` fails with "Updating frozen violations is
  disabled", because ArchUnit reaches that message when it saves the new violation, and the entry must be committed with its
  migration item (confirmed on the running build by task 3.2);
- a committed store with no `stored.rules` at all fails with "Creating new violation store is disabled", the only case
  that message describes;
- a violation that was fixed but is still in the committed store makes the update fail with "Updating frozen violations is
  disabled", so the shrunk store must be committed with the fix;
- a new violation fails the rule, as it does locally.

Decision taken without asking, for the owner to confirm: CI fails on a stale store (the strict option). The alternative
lets CI shrink the store silently, which passes the build and leaves the checkout dirty. The strict option is what makes
"fixed violations shrink the baseline" a property of the repository and not of a developer's machine.

### D4. Refresh procedure after a fix

1. Fix the code.
2. Run `./gradlew :voyager-fitness:test` locally, without `CI`. ArchUnit removes the fixed violation from its file.
3. Check `git diff voyager-fitness/src/test/resources/archunit_store/`. It must show removed lines only. An added line needs
   an item in this change's mapping table (D7) and the owner's approval.
4. If a rule's store file is now empty, make that rule plain in the same commit: drop the `freeze(...)` wrapper and the
   `persistIn`, and keep `allowEmptyShould(false)`.
5. Commit the fix and the store change together. The commit type is the type of the fix, because the baseline update is part
   of the fix.

Never refresh with `freeze.refreeze=true` and never edit a store line by hand to make a test pass. The how-to guide,
`docs/guides/how-to-refresh-the-architecture-baseline.md`, states these steps for developers.

### D5. Which rules are frozen

Only rules with known violations on `main` are frozen. A rule without violations is a plain `@ArchTest` with
`allowEmptyShould(false)`, which is the simplest form and fails at once on the first violation. The Red step (tasks 1.1 and 1.2)
decides which rules are frozen by running them unfrozen.

### D6. Rules, module and coverage

All rules live in one new test class, `voyager-fitness/src/test/java/net/elytrarace/fitness/SliceBoundaryRulesTest.java`,
with package `net.elytrarace.fitness`. The module is test-only and sits outside the rings, so the change adds no production
type and no Gradle dependency. `FitnessCoverageTest` needs no change, because every rule names a package that is already
mapped to a module. Its check is re-run in task 3.5.

| Id | Rule (description via `as`) | Package scope | Status on `main` | Migration items (expected) | MoSCoW |
|---|---|---|---|---|---|
| R1 | server does not depend on race scoring | `net.elytrarace.voyager.server..` to `net.elytrarace.voyager.race.scoring..` | frozen | 1, 2, 3 | Should (spec: "Server holds no scoring...") |
| R2 | server game package does not use Minestom | `net.elytrarace.voyager.server.game..` to `net.minestom..` | frozen | 6, 7, 8, 9, 28 | Must |
| R3 | platform infrastructure does not depend on race | `platform.{text,convert,world,tick,render}..` to `race..` | frozen | 11, 13, 15, and 20 (acceptable, frozen as it stands) | Must |
| R4 | platform slices are free of cycles | `net.elytrarace.voyager.platform.(*)..` | frozen | 27 | Must |
| R5 | platform mapsetup contracts live in platform mapsetup | classes in `platform..` depending on `api.mapsetup..`, must reside in `platform.mapsetup..` | frozen | 24, 29 | Should |
| R6 | setup classes outside the composition root do not use Minestom | `net.elytrarace.voyager.setup..` minus `setup.SetupServer` and `setup.inject..` to `net.minestom..` | frozen | 26 (seven classes of `setup.adapter`, 153 violations) | Must |
| R7 | race slices are free of cycles | `net.elytrarace.voyager.race.(*)..` | plain (expected to pass) | none expected | Must |
| R8 | api slices are free of cycles | `net.elytrarace.voyager.api.(*)..` | plain (expected to pass) | none expected | Must |

`R3` includes `render` although `architecture.md` does not list it as an infrastructure package. The package holds only
`GuideLineRenderer`, which renders the racing line. It is a technical name that holds slice content, and the rule treats it
as infrastructure for that reason.

The follow-up `extract-cup-slice` empties the stores of R1 and R2 and retargets R2 when it deletes `server.game`. Its design
records the effect on each frozen rule.

The expected items were checked by the Red run (D7, "Red run"). The mapping table in D7 is the confirmed one.

### D7. Mapping of baseline entries to migration items

ArchUnit's violation text names the source file and line, and the default matcher ignores the line. So the mapping is
many-to-one by source file: each stored line maps to the item that names its file. The stored file names carry no item
number, and this table is the only record of the mapping.

Covered open items (13 of 22): 1, 2, 3, 6, 7, 8, 9, 11, 13, 15, 24, 26, 27. The addenda 28 and 29 are added below.

Items not covered, each with the reason no dependency rule can express it:

| Item | Why it stays list-only |
|---|---|
| 5 | An exception sits in the wrong package. There is no import to forbid. |
| 10 | The tick order is a test of one `@Bean` method, not a dependency. |
| 12 | `MapTransition` imports only `api.race.MapDefinition` and Minestom. Its cup behaviour is not visible in imports. |
| 14 | `FlightTick` in `platform.tick` depends on `platform.flight`, which is a slice-to-slice rule that R3 does not cover. |
| 16 | `MinestomCollisionSpace` sits in its own package. Placement only. |
| 17 | The `ring` slice is split over three race packages, with no cycle. Placement only. |
| 18 | The flat `api.race` package holds six slices. A single package has no cycle, so a rule would need a list of slice names. |
| 23 | Cup-selection rules in `platform.catalog`. A behaviour, not an import. |
| 25 | `SetupMessages` in `platform.text`. Placement only. |

Items 4, 19, 20 (except as frozen by R3), 21 and 22 are resolved or acceptable and need no entry.

**Red run (2026-10-10, `SliceBoundaryRulesTest` with all rules unfrozen).** R1 to R6 fail, R7 and R8 pass. Counts are
the header counts of the ArchUnit report, and each equals the sum of its per-file lines.

| Rule | Violations | Source files (violations) | Item |
|---|---|---|---|
| R1 | 39 | `server.game.CupSession` (24), `CupStandings` (12), `CupStanding` (3) | 1, 2, 3 |
| R2 | 94 | `server.game.CupSession` (29), `Racers` (19), `Rockets` (23), `LivePlayerSampler` (16), `CurrentMapBlocks` (7) | 6, 7, 8, 9, 28 |
| R3 | 52 | `platform.world.RaceRuns` (7), `platform.tick.XerusPhaseDriver` (33), `platform.tick.RacePhaseListener` (2), `platform.render.GuideLineRenderer` (6), `platform.text.Messages` (4) | 11, 13, 15, 20 |
| R4 | 1 cycle | `catalog` to `world` (`CatalogValidation` to `WorldFolders`, `WorldHealth`; `MapInstances` to `WorldOpener`) and `JsonDraftStore` to `VoidWorldTemplate` | 27; the `JsonDraftStore` edge goes with item 24 |
| R5 | 46 | `catalog.JsonDraftStore` (30), `catalog.writer.MapDraftJsonWriter` (12), `catalog.MapDraftAdapter` (4) | 24, 29 |
| R6 | 170 raw (153 in scope) | `setup.adapter` (153 across seven classes: `WandListener` 44, `SetupCommands` 50, `RingPreviews` 15, `TerrainGuard` 18, `Wand` 16, `BuilderSessions` 7, `MapSession` 3) and the setup composition root (17: `setup.SetupServer` 10, `setup.inject.SetupBeans` 3, its avaje-generated `SetupBeans$DI` 3, `setup.inject.DInjectModule` 1), which decision B excludes from R6 | 26 for the adapter; the composition root is outside R6 |

**Decision (owner, 2026-10-10): option B.** The setup composition root imports `net.minestom` (`SetupServer` for
`MinecraftServer` and `ServerFlag`; `SetupBeans` for `MinecraftServer` and `InstanceManager`). No migration item names it,
and item 26 names only `setup.adapter`. The owner treats the composition root as wiring, as it treats the bootstrap of
`VoyagerServer`, and excludes it from R6: R6 covers `..setup..` minus `setup.SetupServer` and `setup.inject..`, including
the avaje-generated classes of that package. The 17 composition-root violations are therefore not in the baseline, and no
addendum 30 is created. The baseline of R6 is the 153 violations of the seven `setup.adapter` classes, all mapped to item
26. The spec's premise "the seven classes of `setup.adapter` that the baseline records" is now true.

The Red run is recorded above. The D7 table is final for the baseline; the Red run's own numbers are unchanged.

Addenda, provisional until the owner confirms the numbers:
- **Item 28 (addendum, provisional):** `server/game/CupSession.java` imports Minestom. It is in the R2 baseline, and the migration
  list names it only for the scoring import and the tick order.
- **Item 29 (addendum, provisional):** `platform/catalog/adapter/MapDraftAdapter.java` depends on `api.mapsetup`. It is in the R5
  baseline (4 violations, confirmed by the Red run). Item 28 is closed by `extract-cup-slice`, which moves `CupSession` out of `server.game`.
  Item 29 is outside that change's scope.

The Red step (task 1.2) adds any further violation to this table as an addendum. The archived migration list is not
edited, because it is part of an archived change.

### D8. Decision record

The choice of a frozen baseline over red rules and over exclusions changes how architecture rules enter the suite. It is
recorded as ADR-0020 in MADR 4.0 with status Proposed. It becomes Accepted only when the owner accepts it, and the ADR
is not Accepted by this change.

## Risks / Trade-offs

- **A hand-edited store hides a violation.** → D4 step 3 and the review requirement in the spec; the mapping table in D7
  is the reference for what each line means.
- **The baseline grows without an item.** → The review requirement. A mechanical count check was considered and left as an
  open question, because it would need a second file to update on every fix.
- **A description change creates a new baseline key.** → On CI this fails at once, because the new key cannot be saved while updates are disabled. The
  how-to guide tells the developer to keep the description stable or to commit the new entry with its item.
- **A renamed violating class is a new violation.** → Intended. Moving a class out of the violating package removes the
  violation, and the store shrinks on the next local run.
- **Nine open items have no guard.** → They stay in the migration list and in `architecture.md`, and they are named as
  list-only in this design. A regression there is caught by review, not by a rule.
- **The ArchUnit property names or the system-property override do not behave as read.** → Verified in the Red and Green
  tasks, by running the build with `CI=true` against a stale store in a throwaway worktree (tasks 3.1 to 3.4).

## Migration Plan

The change deploys as tests and documentation only. No production code changes.

1. Red: add `SliceBoundaryRulesTest` with R1 to R8, unfrozen. The run fails with the violations listed in D6 and D7. The
   reconciled table replaces the expected items.
2. Green: freeze the rules with known violations (R1 to R6), write the store with a local run, and confirm the stored lines match the reconciled table.
3. Verify the failure modes in a throwaway worktree, then remove that worktree.
4. Docs: ADR-0020, the how-to guide, the `architecture.md` changes, and the ADR-0017 link fix.
5. Open the pull request (last task).

Rollback: delete `SliceBoundaryRulesTest`, the `archunit_store` directory and the `archunit.properties` file, and revert the
`build.gradle.kts` lines. No production behaviour depends on them.

## Open Questions

- **Name of the cup follow-up (settled).** The follow-up is `extract-cup-slice`. It supersedes the name
  `move-cup-flow-out-of-server` recorded in the archived migration list. This change names `extract-cup-slice` in its
  proposal, design and spec, and the spec does not depend on the name beyond that.
