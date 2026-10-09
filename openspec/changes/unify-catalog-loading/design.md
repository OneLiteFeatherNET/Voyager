# Design

## Context

Today `voyager-platform` `catalog` has three entry points. `JsonMapCatalog` and `JsonCupCatalog` each call
`CatalogDirectory.readAll`, and each stops at the first bad file. `CatalogConsistency` is called by hand from the
`cup` bean in `voyager-server` `inject/ServerBeans`, which also takes the concrete `JsonCupCatalog` because it must
enumerate. `CupResolution` takes `JsonCupCatalog` for the same reason. The result is that the catalogue has no single
value and no single entry point.

## Goals / Non-Goals

**Goals**
- One operation turns a data directory into one immutable `CatalogSnapshot`.
- Every problem a load finds is available as data, in a deterministic order, for the consumers that need more than the
  first one (`scope-cup-validation`, `add-catalog-validate-task`, `hot-reload-catalogs`).
- Boot behaviour is unchanged: the first problem refuses boot with today's exception and message.
- Consumers keep their current ports (`MapCatalog`, `CupCatalog`), so `voyager-race` and `voyager-api` are untouched.

**Non-Goals**
- No schema change, no new validation rule, no ring-index or default-world change (Q2, Q3).
- No all-at-once report and no aggregate exception. That is `add-catalog-validate-task` (Q6), which consumes
  `CatalogReading`.
- No cup scoping (Q4) and no hot reload (Q7). The reading is shaped so they can build on it.
- No move of `CupResolution` into `race.cup` (follow-up 2 of the slice roadmap).

## Decisions

### 1. Dependency direction and placement

```
voyager-server (ring 4, @Factory ServerBeans)
   |  calls CatalogLoader.load(path), wires ports
   v
voyager-platform catalog (ring 3, no DI annotations)  --implements-->  api.race.MapCatalog, CupCatalog (ring 1 ports)
   |
   v
voyager-api api.race (MapDefinition, CupDefinition)
```

| Type | Module and package | Role |
|---|---|---|
| `CatalogSnapshot` | `voyager-platform` `platform.catalog` | Record: `maps` and `cups` by name, unmodifiable, insertion-ordered. Accessors `mapByName`, `cupByName`, `mapNames`, `cupNames`. |
| `CatalogProblem` | same package | Record: `Path source`, `RuntimeException cause`. `message()` is the cause's message. The cause is today's exception object (`MalformedCatalogFileException`, `DuplicateCatalogEntryException`, `UnreadableCatalogException`, `UnresolvedCupMapException`). |
| `CatalogReading` | same package | Record: `CatalogSnapshot snapshot` (what parsed) and `List<CatalogProblem> problems` (unmodifiable, sorted). `problems().isEmpty()` means the catalogue is clean. |
| `CatalogLoader` | same package | Final class with private constructor. `read(Path)` returns a `CatalogReading` and never throws for a bad file. `load(Path)` returns the snapshot or throws the first problem's cause. The only place that reads `maps/` and `cups/`. |
| `CatalogDirectory` (changed) | same package | `readAll` parses every file, records each failure as a `CatalogProblem`, and continues. |
| `CatalogConsistency` (changed) | same package | Takes plain name-to-definition maps and returns the dangling references as one `UnresolvedCupMapException`, as today. Called by `CatalogLoader`. |
| `JsonMapCatalog`, `JsonCupCatalog` | removed | Replaced by the snapshot. |
| `ServerBeans` (changed) | `voyager-server` `inject` | One `CatalogSnapshot` bean from `CatalogLoader.load`; `MapCatalog` and `CupCatalog` beans as method references on it. |

Why a record and not a class with methods: the snapshot and the reading are values that are compared, copied and passed
to tests; the compact constructor is where immutability is enforced once.

Why `CatalogLoader` and not a static factory on `CatalogSnapshot`: the snapshot stays a pure value, and the file system
access sits in the adapter, where a reader of the ring rules expects it.

### 2. Ports come from the snapshot, not from separate beans

`MapCatalog` and `CupCatalog` are `@FunctionalInterface`s whose only method is `byName`. A `@Bean` returning
`snapshot::mapByName` and one returning `snapshot::cupByName` register the ports, and the snapshot stays the single
owner of the data. Rejected: a snapshot that implements both ports. Both declare `byName(String)` with different return
types, so it cannot implement both. Rejected: keeping `JsonMapCatalog` and `JsonCupCatalog` as thin views. That keeps two
classes whose only job is to hold what the snapshot already holds.

### 3. Problems are data; boot refuses on the first one

This decision was settled by the project owner on 2026-10-09.

- `CatalogDirectory.readAll` no longer aborts on the first bad file. It records the failure as a `CatalogProblem` and reads
  on. Each failure keeps its exception type and message, so `CatalogProblem.cause()` is exactly what today's code throws.
- `CatalogLoader.read` returns the definitions that parsed and the problems, sorted by source file name as today. Nothing
  is thrown.
- `CatalogLoader.load` is `read` followed by a boot policy that is today's policy: if there is a problem, throw the cause
  of the first one in today's order (the `maps/` problems, then the `cups/` problems, then the cross-catalogue check).
  The thrown object is the original exception, so the type and the message are unchanged.
- The cross-catalogue check keeps today's shape: one `UnresolvedCupMapException` that lists the dangling entries it found.
  That is one problem with several entries, because that is what the check throws today; the behaviour is preserved, not
  changed to one exception per entry.
- Rejected: one aggregate `InvalidCatalogException` for every problem. It changes boot's observable behaviour, which the
  owner ruled out for this change. It is kept for `add-catalog-validate-task`, which owns the all-at-once report.

### 4. Reading order and missing directories

`maps/` and `cups/` are read independently; a missing or empty directory is one problem and does not stop the other
directory from being read. Files within a directory are read in sorted filename order, as today. The cross-catalogue
check runs only when both directories produced no problem, because a dangling reference cannot be decided without
both lists and a broken directory would produce spurious "plays unknown map" lines. Under `load`, this gives today's
result: a broken directory is the first problem, and the check is never reached.

### 5. Server wiring

`VoyagerServer` already obtains `CupDefinition`, `MapCatalog` and `CupSession` from the graph and needs no change except
that `CupDefinition` still comes from the `cup` bean. `ServerBeans` javadoc is updated: the "catalogue beans" paragraph
and the "cross-catalogue consistency check" paragraph now describe the loader. `CupResolution.resolve` takes a
`CatalogSnapshot`, so the server still names only platform types and the port-for-consumers rule is not weakened.

### 6. Architecture enforcement

No new ArchUnit rule is needed. Covered by existing rules:
- DI annotations stay out of `voyager-platform` (fitness rule added in `5b89e6b`); `CatalogLoader`, `CatalogReading`
  and `CatalogSnapshot` carry none.
- `ApiPurityTest` keeps Gson and the file system out of the modules that model a race; the loader remains in platform.
- `FitnessCoverageTest` still sees `voyager-platform` as covered because it imports ArchUnit-visible classes.
- `NullabilityConventionTest` applies to the `catalog` package through its `package-info.java`.

If the implementation finds a rule it cannot satisfy from these, it stops and reports rather than adding one silently.

## Risks / Trade-offs

- **Collecting after a parse failure may surface follow-on noise** (for example, a cup whose map file failed to parse is
  reported as dangling). Mitigation: the cross-catalogue check is skipped when a directory has a problem (decision 4), so
  no dangling line appears in `read`; the test "malformed map and dangling cup reference together" pins this.
- **Snapshot holds all definitions in memory**, as the catalogues already do; no change in footprint.
- **Test migration**: `JsonMapCatalogTest`, `JsonCupCatalogTest`, `CatalogConsistencyTest` and `CatalogDirectoryTest` change
  to target the loader. Moved, not weakened.

## Migration Plan

1. Tests first (Red) for the snapshot, the reading and the loader, then implement (Green), in `voyager-platform`.
2. Move the catalogue tests onto the loader and delete the two JSON classes.
3. Rewire `ServerBeans` and `CupResolution`; migrate `CupResolutionTest`, `CommittedMapDataTest`, `VoyagerGraphTest`.
4. Update docs: `docs/explanation/architecture.md` catalog row and research 005 Q1 status. No ADR: this is a refactor inside an
   existing decision (D10 unchanged).
5. Rollback: revert the single squash commit; no data or file format changed.

## Open Questions

- None. The owner's decision of 2026-10-09 (decision 3) resolves the boot behaviour. If the owner later wants one exception
  per problem on boot, that is a change of `add-catalog-validate-task` or a new change, not this one.
