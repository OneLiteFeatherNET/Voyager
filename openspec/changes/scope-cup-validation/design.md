# Design

## Context

Boot wires the cup in `ServerBeans.cup` (`voyager-server/.../inject/ServerBeans.java`):
1. `CatalogConsistency.requireEveryCupMapResolves(cups, maps)` walks every cup and throws one
   `UnresolvedCupMapException` for all of them.
2. `CupResolution.resolve(cups, settings.cupName())` picks the played cup.

The catalogue reads cups with `CatalogDirectory.readAll`, which throws on the first unparseable file. So step 0
(reading the directory) is also all-or-nothing. A fix has to change both the read and the check.

Dependency direction (Clean Architecture; arrows point at what is depended on):

    voyager-server  ServerBeans.cup, game/CupResolution   (composition root, selection, warning log)
          |
          v
    voyager-platform  JsonCupCatalog, CatalogConsistency, CatalogDirectory   (I/O, cross-catalogue check)
          |
          v
    voyager-api  CupDefinition, InvalidCupException   (pure records)

No new type crosses a boundary in the wrong direction. The check stays in `voyager-platform` and does not log.
Logging is done by the composition root, which owns the boot policy.

## Goals / Non-Goals

- Goal: a broken unplayed cup cannot stop boot; a broken played cup always does, with today's message quality.
- Goal: every warning and refusal is testable without Minestom, a network or a real server.
- Non-goal: map validation, the validate task, hot reload, mode defaulting (see proposal).

## Decisions

**D1. Resolve first, then check the selected cup.** `CupResolution.resolve` runs before the consistency check.
A typo in the name then reports "no such cup" with the list, and never reports an unrelated cup's problem. The
existing order (check everything, then resolve) is the reason one broken cup blocks boot.

**D2. "Only cup" counts files.** `CupResolution` today requires `cupNames().size() == 1`. Parsed names do not
count broken files, so a valid cup plus a broken one would resolve silently to the valid one. That is not
wanted: the operator should be told that a second cup file exists and choose. The snapshot therefore exposes
`fileCount()` (parsed plus problem files), and the ambiguity test uses it. The ambiguous message still lists the
parsed names, and also lists the problem file names.

**D3. Named broken file.** When the chosen name is absent from the parsed cups, check the problems for a file whose
stem equals the chosen name. If one exists, throw the malformed-file problem for that file. Otherwise throw
`noSuchCup`. The file stem is used because the name is not known from a file that did not parse.

**D4. Result shape.** `CatalogConsistency` gets a method that takes the selected `CupDefinition` and returns the
list of problems of the other cups, as a value. It does not throw for those, and it does not log. The composition
root logs one WARN from that list. The message is built with `String.formatted`:
`"%d cup(s) are not playable and were skipped: %s"`, where each problem reads `cup 'x' plays 'y'` or
`'<file>' is not a valid cup definition: <reason>`. This keeps the warning testable as a value and as a log event.

**D5. Dependency on `unify-catalog-loading`.** Collecting parse problems for unselected files needs a snapshot
returned from `CatalogDirectory`: `parsed` definitions by name, plus `problems` (file, reason). Today's
`readAll` throws. `unify-catalog-loading` has no proposal yet, so its shape is unknown. This change depends on it
explicitly and requires only:
- a per-file problem value carrying the file path and the reason, and
- a read that returns parsed definitions and problems together, and throws only for an unreadable or empty directory.

If `unify-catalog-loading` lands with another shape, adapt the snapshot to it. Do not duplicate the directory
reader in this change. Without that dependency, the change would need its own tolerant reader, a second copy of the
four error cases that `CatalogDirectory` exists to share.

**D6. Mode stays required.** A default of `RACE` changes data semantics: a missing key, and also a typo such as
`"moode"`, would silently become a race. Research 005 (D3) suggested the default. This change does not make it.
It is a separate concern: a `feat(platform)` change of its own, with an owner decision on whether a typo should
still be an error. Owner decision needed: keep required (recommended) or default to RACE.

**D7. Warning once per boot.** One line, not one per cup, so a broken catalogue does not flood the log. The line
lists every problem, because a renamed map usually breaks several cups at once.

## Risks / Trade-offs

- A broken cup that the operator does not know about is not reported as a refusal. Mitigation: the WARN line on
  every boot, and the planned `validate` task (Q6), which is out of scope.
- D2 changes behaviour for one case: a valid cup plus a broken cup, no name set, now refuses. This is intended and
  is listed in the proposal.

## Test Strategy (F.I.R.S.T.)

Tests are written before the production code (Red/Green). None uses a sleep, the system clock, or shared static
state. The warning is asserted through a captured log4j2 `ListAppender` attached per test to the `ServerBeans`
logger and removed in `@AfterEach`. Pass or fail comes from the list of events, not from console output.

## Fitness

No new module, package or dependency direction. Existing `voyager-fitness` rules still cover the packages. If
the `log4j-core` test dependency is missing from `voyager-server`, it is added at test scope only.

## Open Questions for the Owner

1. Mode: keep required (recommended, D6) or default to RACE in a separate change?
2. Ambiguity with a broken second file (D2): refuse (recommended) or resolve to the one valid cup with a warning?
