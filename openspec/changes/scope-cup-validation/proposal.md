# Proposal

**Conventional Commits:** `feat(platform)`. Squash-merge PR title: `feat(platform): refuse to boot only for the cup that will be played`.

**Depends on:** `unify-catalog-loading` (in flight, no proposal yet). This change needs per-file problems
collected into a catalogue snapshot instead of the first malformed file aborting the read. See `design.md`
D5 for the seam it requires and the fallback if that change lands differently. Merge order: `unify-catalog-loading`
first.

## Why

`CatalogConsistency` checks every cup in the directory at boot (`CatalogConsistency.java:40-54`), and
`JsonCupCatalog` aborts on the first malformed cup file (`CatalogDirectory.readAll`). One broken cup that
nobody plays therefore stops every server from starting. Research 005 records this as finding P6 and roadmap item Q4.

## What Changes

- Boot resolves the selected cup first, with the existing rule: the cup named by `-DVOYAGER_CUP` or `-Pcup`, or
  the catalogue's only cup when none is named.
- The selected cup must be fully consistent: its file parses, and every map it names resolves. If not, boot
  refuses with the existing exception and message shape, listing only that cup's entries.
- Every other cup is not validated for play. Cups with problems (unparseable file, unresolved map) do not stop
  boot. They produce one aggregated `WARN` log line listing each problem, and they are never played.
- "Exactly one cup" for an unnamed selection counts cup files, not parsed cups. A broken second file still makes
  the selection ambiguous, so the operator is told to choose.
- A named selection that matches a broken file refuses boot and names that file, instead of "no cup named".
- The `mode` field stays required. Defaulting it to `RACE` is not part of this change (D6).

No change to: map validation, map directory handling, property names, Gradle mapping, the wire format, or the
`ambiguous` and `noSuchCup` message text.

**BREAKING**: none for valid data. A server that today refuses to boot because of an unrelated broken cup will now
boot and log a warning. This is the intended change.

## Capabilities

### New Capabilities

- `cup-validation`: which cup a server plays, what must be consistent for that cup to boot, and how problems in
  the other cups are reported. Durable behaviour, so it is not named after this change.

### Modified Capabilities

- None. `openspec/specs/` holds no capability yet.

## Impact

- Slices: the cup slice of the rebuild and the platform catalogue. Out of scope: map slice, setup slice.
- Code (voyager-platform): `catalog/CatalogConsistency`, `catalog/JsonCupCatalog`, `catalog/CatalogDirectory`
  (collecting per-file problems via the `unify-catalog-loading` seam).
- Code (voyager-server): `game/CupResolution` (ambiguity counts files; named broken file), `inject/ServerBeans.cup`
  (order of checks; warning emitted here, at the composition root).
- Tests: `CatalogConsistencyTest`, `JsonCupCatalogTest`, `CupResolutionTest`, new boot test with a captured
  log4j2 appender. No fitness rule changes (no new module or package boundary).
- Docs: `docs/` operations note for the boot warning. Research 005 status is updated by Lumen, not in this change.
- Dependencies: none new in production. A log4j-core test dependency is added to `voyager-server` if missing.

## Out of Scope

- The `validate` task and validate-and-exit mode (Q6, NFR-007).
- Map file problems: a malformed map still aborts boot, as today.
- Defaulting `mode` to `RACE` (D6, separate change).
- Hot reload (Q7), cup authoring commands (D5), cup `select{}` rules (D4).
- The retired tree (`server`, `plugins/*`, `shared/*`).
- Coordination: `name-both-cup-switches` edits the `ambiguous` message text in `UnresolvedCupException`. This change
  does not touch that text, so both can land in either order.
