# Design

## Context

Boot wires the cup in `ServerBeans.cup` (`voyager-server/.../inject/ServerBeans.java`):
1. `CatalogConsistency.requireEveryCupMapResolves(cups, maps)` walks every cup and throws one
   `UnresolvedCupMapException` for all of them.
2. `CupResolution.resolve(cups, settings.cupName())` picks the played cup.

Since `unify-catalog-loading`, the catalogue is read with `CatalogLoader.read`, which returns the parsed cups and every
per-file problem as data and throws nothing for a bad file. `CatalogLoader.load` (the boot policy for everything but cups)
still throws the first problem. This change applies its own boot policy to cups on top of `read`.

Dependency direction (Clean Architecture; arrows point at what is depended on):

    voyager-server  ServerBeans.cup, game/CupResolution   (composition root, selection, warning log)
          |
          v
    voyager-platform  CatalogLoader.read, CatalogConsistency, CatalogDirectory   (I/O, cross-catalogue check)
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

**D2. "Only cup" counts files; a broken second file refuses boot.** `CupResolution` today requires `cupNames().size() == 1`.
Parsed names do not count broken files, so a valid cup plus a broken one would resolve silently to the valid one. Owner
decision (2026-10-09): boot refuses. The operator is told that a second cup file exists and chooses. The reading exposes
`fileCount()` (parsed plus problem files), and the ambiguity test uses it. The ambiguous message is the existing text
of `UnresolvedCupException.ambiguous`, extended with the file names of every cup file in the directory (parsed and
broken, sorted), so it names both cup files in the reported case. The switch wording (`-Pcup=<name>` and
`-DVOYAGER_CUP=<name>`) comes from `name-both-cup-switches`. Built with `String.formatted`.

**D3. Named broken file.** When the chosen name is absent from the parsed cups, check the problems for a file whose
stem equals the chosen name. If one exists, throw the malformed-file problem for that file. Otherwise throw
`noSuchCup`. The file stem is used because the name is not known from a file that did not parse.

**D4. Result shape.** `CatalogConsistency` gets a method that takes the selected `CupDefinition` and returns the
list of problems of the other cups, as a value. It does not throw for those, and it does not log. The composition
root logs one WARN from that list. The message is built with `String.formatted`:
`"%d cup(s) are not playable and were skipped: %s"`, where each problem reads `cup 'x' plays 'y'` or
`'<file>' is not a valid cup definition: <reason>`. This keeps the warning testable as a value and as a log event.

**D5. Dependency on `unify-catalog-loading`.** The shape is now final. `CatalogLoader.read(dir)` returns a `CatalogReading`:
the parsed `CatalogSnapshot` and `problems` (`CatalogProblem`: source path and the exception it would raise). It throws
only for an unreadable or empty directory, as today. This change uses `read` directly for cups:
- the selected cup is resolved and checked (D1, D3) against the snapshot;
- every problem whose source is not the selected cup's file is returned to the composition root as the warning list (D4).
- A problem in `maps/` is not a cup problem: it still refuses boot through `load`'s first-problem policy (unify decision 3).

No second copy of the directory reader is written in this change.

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

## Owner decisions (2026-10-09)

- D2: with no cup selected and a broken second cup file, boot **refuses**, and the message names both files and both
  switches (`-Pcup` and `VOYAGER_CUP`).
- Still open, owner's call: D6, the cup `mode` default. The spec keeps `mode` required until the owner answers. It is
  not part of this change's scope.
