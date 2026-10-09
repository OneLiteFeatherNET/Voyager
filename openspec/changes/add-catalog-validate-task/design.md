# Design

## Dependency direction

```
voyager-server  (composition root, ring 4)
   ├── server.config.ConfigCheck  ──┐  settings checks, entry for voyager.config.check
   │                                 ▼
voyager-platform (ring 3, adapters)
   ├── catalog.CatalogValidation ─── uses the aggregate loader of unify-catalog-loading
   └── world.WorldFolders        ─── layout rule, shared with MapInstances
                                 ▼
voyager-api  (ring 1-2, no I/O)
   └── api.config.ConfigProblem(key, source, message, severity)
```

Arrows point inward. `voyager-platform` never imports `voyager-server`. `ConfigProblem` has no I/O, so it lives in `voyager-api`.

## Where each type lives

| Type | Module | Package | Why there |
|---|---|---|---|
| `ConfigProblem` (record, severity enum nested) | voyager-api | `net.elytrarace.voyager.api.config` | Pure record named by NFR-007; used by platform and server. |
| `WorldFolders` | voyager-platform | `net.elytrarace.voyager.platform.world` | Path rule only, no Minestom. `MapInstances.holdsRegionData` delegates to it. |
| `CatalogValidation` | voyager-platform | `net.elytrarace.voyager.platform.catalog` | Needs Gson (catalogue) and the filesystem; `onlyPlatformDependsOnGson` already allows it. |
| `ConfigCheck` | voyager-server | `net.elytrarace.voyager.server.config` | Reads system properties; the only place that knows the three property names. |

## Decisions

1. **One entry point, two triggers.** `VoyagerServer.main` checks `voyager.config.check` first and returns before any Minestom class is touched. The Gradle task `validateCatalog` is a `JavaExec` on the runtime classpath with that property set. No second main class.
   - Alternative: a separate `ConfigCheckMain`. Rejected: two entry points for one check drift apart.
2. **Collect, do not throw.** The catalogue half reuses the aggregate report of `unify-catalog-loading`. The settings half is a new non-throwing function. `ServerSettings`' compact constructor is not changed; it keeps the refusal for the normal boot path.
   - Consequence: the throwing constructor and the check must agree on what is an error. A shared private predicate, not two copies, is required (task 3.1).
3. **Shallow by default.** The world check tests for the folder and the region files, which is what `MapInstances` tests before it throws `UnknownWorldException`. No region file is opened. The deep check (`WorldHealth`) is a Could-have, opt-in, and needs Falco's loader. Open risk: Falco may need a Minestom registry, so the deep check may not be runnable without starting Minestom. If so, it is dropped from this change.
4. **Exit codes.** 0 for no errors, including warnings. 1 for at least one error. No other codes, so CI can rely on the two values.
5. **Output.** One line per problem: `ERROR|WARN <absolute source> <key>: <message>`, sorted by source then key. Stdout for the report, stderr for nothing else.
6. **Run tasks.** `runServer` and `runServerDev` depend on `validateCatalog`. The check depends on `prepareRunData`. The run tasks keep `dependsOn(prepareRunData)` through the check, so the Copy still runs first.
   - Cost: one extra JVM start per run (roughly a second or two). Accepted, because the alternative is a warning that the operator must read.
   - No escape hatch in this change. Open question for the owner (see below).
7. **Path constants.** The Gradle task reads `runDataDir` and `runWorldsDir` from `voyager-server/build.gradle.kts`, the same values the run tasks use, so the build and the runtime cannot disagree on the doubled path.

## Risks

- `unify-catalog-loading` changes `JsonMapCatalog` and `JsonCupCatalog`. This change must rebase onto it, not onto the current classes.
- Two cup-validation rules (this change's report and Q4's scoped rule) could disagree on which cups are errors. Until Q4 lands, all cups are validated as errors, matching `CatalogConsistency` today.
- `prepareRunData` still uses `Copy`, so stale maps remain visible to the check. Separate change (see proposal).

## Open questions for the owner

- Deep world check: keep it as a Could-have opt-in in this change, or drop it and create a follow-up?
- Escape hatch for run tasks (for example `-PskipCatalogCheck=true`): none in this design. Add one, or keep none?
- Should `fix-run-data-sync` be created now, and land before this change's PR?

## ADR

None. The choices above follow ADR-free conventions already in the repository (the composition root reads system properties; the run tasks depend on prepare tasks). If the owner wants the exit-code and run-gating decision recorded, it becomes a MADR in `docs/decisions/`.
