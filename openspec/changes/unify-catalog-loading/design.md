# Design

## Context

Today `voyager-platform` `catalog` has three entry points. `JsonMapCatalog` and `JsonCupCatalog` each call
`CatalogDirectory.readAll`, and each stops at the first bad file. `CatalogConsistency` is called by hand from the
`cup` bean in `voyager-server` `inject/ServerBeans`, which also takes the concrete `JsonCupCatalog` because it must
enumerate. `CupResolution` takes `JsonCupCatalog` for the same reason. The result is that the catalogue has no single
value and no single failure report.

## Goals / Non-Goals

**Goals**
- One operation turns a data directory into one immutable `CatalogSnapshot`.
- All problems in one load are reported together, deterministically.
- Consumers keep their current ports (`MapCatalog`, `CupCatalog`), so `voyager-race` and `voyager-api` are untouched.

**Non-Goals**
- No schema change, no new validation rule, no ring-index or default-world change (Q2, Q3).
- No cup scoping (Q4), validate task (Q6) or hot reload (Q7). The snapshot is shaped so they can build on it.
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
| `CatalogLoader` | `voyager-platform` `platform.catalog` | Final class with private constructor. Single static `load(Path dataDirectory)`. The only place that reads `maps/` and `cups/`. |
| `InvalidCatalogException` | `voyager-platform` `platform.catalog.exception` | `RuntimeException`. Message lists every problem; each underlying exception is added with `addSuppressed`, so the existing exception types keep their identity. |
| `CatalogDirectory` (changed) | same package | `readAll` parses every file, records each failure, and returns definitions plus problems instead of throwing on the first. |
| `CatalogConsistency` (changed) | same package | Takes plain name-to-definition maps and returns the dangling references; no longer takes `JsonCupCatalog`. Called by `CatalogLoader`. |
| `JsonMapCatalog`, `JsonCupCatalog` | removed | Replaced by the snapshot. |
| `ServerBeans` (changed) | `voyager-server` `inject` | One `CatalogSnapshot` bean; `MapCatalog` and `CupCatalog` beans as method references on it; `cup` bean calls `CupResolution`. |

Why a record and not a class with methods: the snapshot is a value that is compared, copied and passed to tests; the
record's compact constructor is where immutability is enforced once.

Why `CatalogLoader` and not a static factory on `CatalogSnapshot`: the snapshot stays a pure value, and the file system
access sits in the adapter, where a reader of the ring rules expects it.

### 2. Ports come from the snapshot, not from separate beans

`MapCatalog` and `CupCatalog` are `@FunctionalInterface`s whose only method is `byName`. A `@Bean` returning
`snapshot::mapByName` and one returning `snapshot::cupByName` register the ports, and the snapshot stays the single
owner of the data. Rejected: a snapshot that implements both ports. Both declare `byName(String)` with different return
types, so it cannot implement both. Rejected: keeping `JsonMapCatalog` and `JsonCupCatalog` as thin views. That keeps two
classes whose only job is to hold what the snapshot already holds.

### 3. Collecting problems without losing exception types

`CatalogDirectory` keeps its four messages and exception types. A failing file no longer aborts the directory read: the
exception is recorded and the loop continues. `CatalogLoader` gathers the problems from both directories and from the
consistency check. If the list is empty it returns a snapshot. Otherwise it throws `InvalidCatalogException`, with a
message of the form "N problems in the catalogue at <path>:" followed by one numbered line per problem, and with each
recorded exception added as suppressed. Rejected: one new exception type for every problem. It would duplicate the four
that already name the file well.

### 4. Reading order and missing directories

`maps/` and `cups/` are read independently; a missing or empty directory is one problem and does not stop the other
directory from being checked. Files within a directory are read in sorted filename order, as today. The consistency
check runs only when both directories produced definitions, because a dangling reference cannot be decided without
both lists; when one directory is broken, its problem is reported and the check is skipped, so no spurious
"plays unknown map" lines appear.

### 5. Server wiring

`VoyagerServer` already obtains `CupDefinition`, `MapCatalog` and `CupSession` from the graph and needs no change except
that `CupDefinition` still comes from the `cup` bean. `ServerBeans` javadoc is updated: the "catalogue beans" paragraph
and the "cross-catalogue consistency check" paragraph now describe the loader. `CupResolution.resolve` takes a
`CatalogSnapshot`, so the server still names only platform types and the port-for-consumers rule is not weakened.

### 6. Architecture enforcement

No new ArchUnit rule is needed. Covered by existing rules:
- DI annotations stay out of `voyager-platform` (fitness rule added in `5b89e6b`); `CatalogLoader` and `CatalogSnapshot` carry none.
- `ApiPurityTest` keeps Gson and the file system out of the modules that model a race; the loader remains in platform.
- `FitnessCoverageTest` still sees `voyager-platform` as covered because it imports ArchUnit-visible classes.
- `NullabilityConventionTest` applies to the new `exception` and `catalog` packages through their `package-info.java`.

If the implementation finds a rule it cannot satisfy from these, it stops and reports rather than adding one silently.

## Risks / Trade-offs

- **The boot message changes for invalid catalogues.** Mitigation: the exception family and the file names are unchanged; a scenario asserts the message contains each problem; the valid path is byte-identical in behaviour.
- **Collecting after a parse failure may surface follow-on noise** (for example, a cup whose map file failed to parse is reported as dangling). Mitigation: a map file that failed to parse contributes no name, so the dangling line is correct and is worded as the operator needs it; the test "malformed file and dangling reference together" pins this.
- **Snapshot holds all definitions in memory**, as the catalogues already do; no change in footprint.
- **Test migration**: `JsonMapCatalogTest`, `JsonCupCatalogTest`, `CatalogConsistencyTest` and `CatalogDirectoryTest` change to target the loader. Moved, not weakened.

## Migration Plan

1. Tests first (Red) for the snapshot and loader, then implement (Green), in `voyager-platform`.
2. Move the catalogue tests onto the loader and delete the two JSON classes.
3. Rewire `ServerBeans` and `CupResolution`; migrate `CupResolutionTest`, `CommittedMapDataTest`, `VoyagerGraphTest`.
4. Update docs: `docs/explanation/architecture.md` catalog row and research 005 Q1 status. No ADR: this is a refactor inside an existing decision (D10 unchanged).
5. Rollback: revert the single squash commit; no data or file format changed.

## Open Questions

- None blocking. If the owner prefers one exception per problem over an aggregate with suppressed causes, that changes Decision 3 only.
