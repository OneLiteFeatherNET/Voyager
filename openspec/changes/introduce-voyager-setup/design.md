# Design

## Context

Research 005 (`docs/research/005-simpler-map-and-cup-setup.md`) measured the setup cost of the sample map at 354 in-game
actions, 350 of them ring placements. The owner approved it as the E6.0 input on 2026-10-09 (its section 7.4) with three
decisions: the ring stays a disc (center, normal, radius); FAWE polyhedra are an import source only, through
`tools/map-converter`; and issue #120 uses the converter path. This design builds the first slice of `voyager-setup` on
that basis.

Facts verified in `main` for this design:

- `voyager-setup` is not in `settings.gradle.kts` yet. The greenfield design fixes its edges: `voyager-setup -> api, platform`.
  `voyager-platform` declares `api(minestom 2026.08.28-26.2)`, so Minestom reaches setup through platform's `api` edge.
- `Ring` (`api.race.Ring`) requires index >= 0, radius > 0, points >= 0 and a unit-length normal (tolerance 1e-9).
- `MapDefinition` refuses an empty ring list, a blank world, ring indices that differ from list position, and a
  non-positive reference time. A draft with no rings or no spawn therefore cannot be a `MapDefinition`.
- `MapDefinitionAdapter` (game side) requires `name`, `world`, `spawn`, `referenceTimeSeconds`, `boostConfig`, `guideLine`
  and `rings`, and ignores unknown fields.
- `CatalogDirectory.jsonFilesIn` lists only regular files ending in `.json` directly under `maps/`. A folder `maps/<id>/` is
  skipped by the game server, so the game's layout is the flat file `maps/<id>.json` with its world at `worldsPath/<world>`.
  The owner chose this layout on 2026-10-09 (open question O1).
- `MapDefinitionAdapter` requires `spawn` and `MapDefinition` requires at least one ring. A game-loadable file therefore needs
  both, and that rule decides where a draft lives (section 4).
- `MapInstances` refuses a world directory with no region data (`holdsRegionData`).
- The 35-ring sample (`voyager-server/src/main/resources/maps/elytraraceblueandred.json`) uses radius
  3.605551275463989 (sqrt 13) on every ring, points 10 and type `STANDARD`. Its reference time (60.0), boost (30/40) and
  guide line (lookAheadRings 2, particleSpacing 1.0) are documented there as provisional seeds.
- `ApiPurityTest.onlyServerDependsOnDiContainer` forbids `io.avaje.inject..` and `jakarta.inject..` outside `server..`.
  `onlyTheTextPackageBuildsAUserFacingString` exempts only `RaceCommand`. `onlyPlatformDependsOnGson` keeps Gson out of
  every module except platform.
- `FitnessCoverageTest` requires every `voyager-*` module with `src/main/java` to be listed in `PACKAGE_PREFIX_BY_PROJECT`
  and on the fitness classpath. A new module with sources turns it red until both are done.
- `VoyagerServer` is the model composition root: settings first, `MinecraftServer.init()`, eager `BeanScope`, then listen.

## Goals / Non-Goals

**Goals**
- A Minestom setup server that a builder can use to create a map, place and remove disc rings from their pose, and see
  the rings.
- A complete draft is written where the game server reads it, in the game server's format, so no publish step or conversion exists.
- All decision logic is pure and tested with `new`. Minestom is only in the adapter and the root.
- Each architecture boundary of this change is a fitness rule.

**Non-Goals** (each is a named follow-up in the table at the end)
- Test-fly, publish gate, cup commands, undo, Interaction handles, dialog forms, presets, design lint, lap recording,
  legacy import, persistent action-bar checklist.
- Saving terrain. Terrain stays in the builder's external editor (research 005, section 1.3). Block placement and breaking
  are cancelled in the setup world (spec `setup/ring-placement`).
- Any change to the game server, the tree being replaced, or a publish path (none exists in this change).
- Polar (research spike X4) and the FAWE operation inventory (spike X1). This change decides neither.

## Decisions

### 1. Module graph and dependency rule

```
                 voyager-setup  (net.elytrarace.voyager.setup..)
   ring 4  +-----------------------------------------------+
           |  setup            SetupServer (main)          |
           |  setup.inject     SetupBeans (@Factory)       |  avaje: only here and in SetupServer
           +-----------------------------------------------+
   ring 3  |  setup.adapter    WandListener, SetupCommands,|  Minestom, Messages; no DI annotation
           |                   MapSession, RingPreview     |
   ring 2  |  setup.mapsetup   RingFromPose, RingPicker,   |  pure: api + JDK only; no Minestom, no DI
           |                   DraftEditor, MapStatus,     |
           |                   RingOrientation, RingDefaults|
           +-----------------------------------------------+
                         |                     |
                         v                     v
              voyager-platform            (api types)
              catalog.JsonDraftStore      api.mapsetup.DraftStore (port)
              catalog.writer.MapDraftJsonWriter   api.mapsetup.MapDraft, MapId
              catalog.adapter.MapDraftAdapter     api.mapsetup.exception.*
              world.VoidWorldTemplate     api.race.Ring, api.math.Vec3
              convert.Vectors (Minestom <-> Vec3)
                         |
                         v
                   voyager-api
```

Allowed edges: `voyager-setup -> voyager-api, voyager-platform` (declared with `implementation`). `voyager-platform`
gains no edge. Ring 2 and ring 3 share one module, as ring 3 and ring 4 already do in `voyager-platform`/`voyager-server`:
the module line is enforced, the package line is enforced by the rules in decision 8.

Why a pure `mapsetup` package inside setup and not in `voyager-race`: setup needs no race run, and the rules of the
greenfield design say setup depends on api and platform only. Ring placement is authoring logic about one disc, not a race.

### 2. Types in `voyager-api`, slice `mapsetup`

The slice's contract lives in `net.elytrarace.voyager.api.mapsetup` because platform implements the store and setup calls it.
The contract holds only records, an interface and exceptions (`ApiPurityTest` rules apply).

- `record MapId(String value)`: compact constructor accepts `[a-z0-9][a-z0-9_-]{0,31}`; otherwise `InvalidMapIdException`.
  The id is a folder name, so this check runs before any file system call.
- `record MapDraft(MapId id, String world, @Nullable Vec3 spawn, List<Ring> rings, double referenceTimeSeconds,
  BoostConfig boostConfig, GuideLine guideLine)`: compact constructor copies the list and checks that `world` is not blank,
  that each ring's index equals its list position (`InvalidDraftException`), and that the reference time is finite and
  positive. Zero rings and a missing spawn are legal here; they are the draft's problems, not the draft's invariants.
- `interface DraftStore` (port, called by setup, implemented by platform):
  `MapDraft create(MapDraft skeleton)` (refuses an existing folder with `DraftAlreadyExistsException`),
  `MapDraft load(MapId id)` (`DraftNotFoundException`, `InvalidDraftException`),
  `void save(MapDraft draft)` (`DraftWriteFailedException`),
  `Path worldDirectory(MapId id)`.
  Setup builds the skeleton itself and passes it in, because platform must not depend on setup.
  `worldDirectory` returns `<worldsPath>/<id>`, the game's own world location.
- Exceptions in `api.mapsetup.exception`, each with `package-info.java`, extending `RuntimeException`.

`MapDraft` is deliberately not `MapDefinition`. A draft becomes game-loadable when it has a spawn and a ring (section 4); the
problems that keep it from being loadable are the ones `MapStatus` lists.

### 3. Pure logic in `voyager-setup`, package `setup.mapsetup` (ring 2)

Every class is a `final` utility or a record, has a private constructor where it is static, and is tested with `new`.
No `java.time.Clock` is needed: nothing here reads the time.

- `RingDefaults`: `RADIUS = 3.605551275463989` (the radius of every ring in the sample), `POINTS = 10`, `TYPE = STANDARD`,
  `REACH_BLOCKS = 32.0`, `REFERENCE_TIME_SECONDS = 60.0`, `BOOST = new BoostConfig(30, 40)`,
  `GUIDE_LINE = new GuideLine(List.of(), 2, 1.0)`. The last four are the sample's provisional seeds (see Open questions).
- `RingFromPose.create(Vec3 eye, Vec3 look, int index)`: normalises `look` with `Vec3`'s length; a zero or non-finite look
  throws `InvalidPoseException` (setup exception package). Returns `new Ring(index, eye, unit, RADIUS, POINTS, TYPE)`.
  The eye position is the center: a builder who stands in the ring and looks along the flight line places the ring
  where they are.
- `RingPicker.nearestCrossed(List<Ring> rings, Vec3 origin, Vec3 direction, double reach)` returns `OptionalInt` (the
  index). For each ring: `denom = normal . direction`; skip when `|denom| < 1e-9`; `t = normal . (center - origin) / denom`;
  skip unless `0 < t <= reach`; the hit point `h = origin + t * direction` must satisfy `|h - center| <= radius` (rim
  inclusive, as `Ring`'s documentation states). The smallest `t` wins.
- `DraftEditor`: `skeleton(MapId)`, `withRingAppended(MapDraft, Ring)` (the ring's index must equal the current count),
  `withoutRing(MapDraft, int index)` (removes and renumbers every later ring so that index equals position), `withSpawn(MapDraft, Vec3)`.
  All return a new record; none mutates.
- `MapStatus.of(MapDraft)` returns `MapStatus(boolean spawnSet, int ringCount, List<String> blockingProblems)`, where
  the blocking problems are "no spawn set" and "no rings".
- `RingOrientation.fromNormal(Vec3 normal)` returns a unit quaternion (x, y, z, w) that rotates a base axis onto `normal`.
  It is pure math, so the spike's transform code in the adapter only copies the numbers. Degenerate case: normal equal to
  the negative base axis picks an explicit perpendicular axis, and the test covers it.

### 4. Storage in `voyager-platform`, and where a draft lives

Owner decision (2026-10-09): write maps in the game's current layout, with no folder bundle and no publish step.

**Location rule.** A draft is *game-loadable* when it has at least one ring and a spawn. Only a game-loadable draft may sit where
the game reads, because `MapDefinitionAdapter` refuses a file without `spawn` and `MapDefinition` refuses an empty ring list.
- game-loadable: `<dataPath>/maps/<id>.json`, the flat file the game reads;
- otherwise: `<dataPath>/drafts/<id>.json`, a folder the game never reads.
- The world is `<worldsPath>/<id>` in both cases, created by `/map new`. The file names it `world: "<id>"` explicitly.

This extends the owner's rule ("zero rings stay in `drafts/`") with the spawn. Without it, a builder who places rings before a
spawn would write a file the game refuses at boot. Alternative considered and rejected: a marker field (`"draft": true`) that
the game skips. It needs a change to the game's loader, and it leaves a file the game parses in `maps/` before it is ready.

- `catalog.JsonDraftStore implements DraftStore`, constructed by a `@Bean` method in setup with the `dataPath`, the `worldsPath`
  and a `FileMover` functional interface (default `Files::move`), so a test can make the move fail without a real file-system
  fault. This is the Injectable Creator rule (ManisGame rule 7) applied to a file operation.
- `catalog.writer.MapDraftJsonWriter` (final, package-private helpers) writes the keys in the order of the shipped map, with the
  schema version first: `schemaVersion` (1), `name` (the id), `world`, `spawn` (omitted while null), `referenceTimeSeconds`,
  `boostConfig`, `guideLine`, `rings`. Each ring is `index, center, normal, radius, points, type`, with `index` always written.
  It writes explicitly, not by reflective record serialisation, so the omission of `spawn` and the key order are visible in code.
- `catalog.adapter.MapDraftAdapter` (Gson `JsonDeserializer`, `final`, `@ApiStatus.Internal`) reads a draft from either location.
  It reuses `JsonFields` and the existing `Vec3Adapter`/`RingAdapter` for rings, but accepts a missing `spawn` and zero rings
  (those are draft problems, not file errors), and it reports a missing key with the file name, as the catalog does.
- `world.VoidWorldTemplate` copies the classpath directory `templates/void-world/` into `<worldsPath>/<id>`. Its content is
  fixed by spike 1.3.

**Create** (`JsonDraftStore.create`): refuses with `DraftAlreadyExistsException` when `<id>.json` exists in either location or
`<worldsPath>/<id>` exists. Otherwise writes the skeleton to `drafts/<id>.json` (a skeleton has no rings, so it is never
game-loadable).

**Save** (`JsonDraftStore.save`), in this order:
1. Choose the target location from the draft by the rule above, and the other location.
2. Create a temp file in the target folder (`Files.createTempFile`); write UTF-8; `FileChannel.force(true)`;
   `FileMover.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`. On any failure, delete the temp file only and throw
   `DraftWriteFailedException`. If the file system does not support `ATOMIC_MOVE`, a plain `REPLACE_EXISTING` move is allowed.
3. If the other location holds a file for this id, delete it. It is stale now: the target holds the newer content. Deleting the
   old copy after the new one exists is not the forbidden delete-then-write; the forbidden order never happens for the file being
   saved.

**Crash window.** A crash between steps 2 and 3 leaves a copy in each location. `load` and `/map open` then refuse with
`DraftLocationConflictException`, which names both files. The server never picks one of them silently, because either choice can
lose the last action. The operator removes the stale copy (the how-to says which one to keep).

**Move back.** Removing the last ring, or the spawn, makes the draft incomplete again. The same save moves the file from `maps/`
to `drafts/`, so `maps/` never holds an incomplete draft.

### 5. Minestom adapter and composition root in `voyager-setup`

- `setup.adapter.MapSession`: one per builder (`Map<UUID, MapSession>` held by an adapter bean). It holds the open `MapId`, the
  current `MapDraft`, the opened world instance (`MapInstances` with the map folder as root, world name `world`), and the
  preview handles. Every mutation is: compute with `DraftEditor`, `DraftStore.save`, then update the session and previews.
  If the save throws, the session keeps the old draft.
- `setup.adapter.WandListener`: right-click with the wand calls `RingFromPose` with the player's eye position and look
  direction (converted by `platform.convert.Vectors`), then the placement path above. Left-click calls `RingPicker` with the
  same ray and removes the ring. The events that carry the left-click (on air and on a block) are fixed by spike 1.2.
- `setup.adapter.SetupCommands`: a Minestom `Command` tree for `/map new <id>`, `/map open <id>`, `/map spawn`, `/map status`,
  using the same command API as `server.command.RaceCommand`. Map-id parsing goes through `MapId`.
- `setup.adapter.RingPreview`: spawns and removes display entities. Its transform call is written after spike 1.1.
- User-facing strings go through `platform.text.Messages` with a setup bundle, because `onlyTheTextPackageBuildsAUserFacingString`
  exempts only `RaceCommand`.
- `setup.inject.SetupBeans` (`@Factory`): `SetupSettings` is external; `DraftStore`, `InstanceManager`, the session registry
  and the listeners are `@Bean` methods that call constructors. No class in `setup.adapter` or `setup.mapsetup` carries an
  annotation. The ring-from-pose pipeline is one method, not a list.
- `net.elytrarace.voyager.setup.SetupServer` (`main`): settings, data-directory check, `MinecraftServer.init()`, eager
  `BeanScope` (`shutdownHook(false)`, closed in a shutdown task), events, commands, then `start`. Same order as `VoyagerServer`.
- `SetupSettings` (record in `setup.config`): `host`, `port` (default 25566, so a developer can run both servers), `dataPath`
  (`VOYAGER_DATA_PATH`, default `run-setup/data`), `worldsPath` (`VOYAGER_WORLDS_PATH`, default `run-setup/worlds`). It reads
  three system properties and nothing else. Both directories must exist at start, as the game server requires. The platform resolver the
  greenfield design describes does not exist yet; see Risks.

Threading: player events run on Minestom's main thread, so `MapSession` needs no lock. The design depends on that and says so
in the class comment.

### 6. Data flow

```
 builder right-click (wand)
   -> WandListener: eye = Vectors.fromMinestom(player.getPosition().add(0, eyeHeight, 0)); look = direction
   -> RingFromPose.create(eye, look, index = session.draft.rings().size())      [pure]
   -> DraftEditor.withRingAppended(draft, ring)                                [pure]
   -> DraftStore.save(draft)                                                   [platform]
        -> MapDraftJsonWriter -> temp file -> force -> atomic move -> maps/<id>.json (game-loadable)
           or drafts/<id>.json (not yet); then the stale copy in the other folder is deleted
   -> session.draft = newDraft; RingPreview.spawn(ring)                        [adapter]
   -> Messages: "Ring 12 placed"  (on DraftWriteFailedException: old draft kept, "not saved")

 builder left-click (wand)
   -> RingPicker.nearestCrossed(draft.rings(), eye, look, REACH_BLOCKS)        [pure]
   -> DraftEditor.withoutRing(draft, index) -> save -> RingPreview.remove(index)
```

### 7. Preview (blocked by spike 1.1)

Each ring is a thin `BlockDisplay` or `ItemDisplay`, positioned at its center, rotated by `RingOrientation.fromNormal(normal)`
and scaled to the radius. Previews live in the setup world only and are never written to a file. Which Minestom 26.2 API
sets rotation and scale is what spike 1.1 records. Until it does, the placement and removal path works without previews,
and the preview task does not start.

### 8. Fitness rules (voyager-fitness)

- `FitnessCoverageTest`: add `":voyager-setup" -> "net.elytrarace.voyager.setup"`. Once `voyager-setup` has sources it is
  picked up automatically, so this entry is the red step.
- `voyager-fitness/build.gradle.kts`: `testImplementation(project(":voyager-setup"))`.
- `ApiPurityTest`, each with `allowEmptyShould(false)` and a description that names the `net.elytrarace.voyager.setup` package:
  - `setupDomainOnlyDependsOnApiAndJdk`: classes in `setup.mapsetup..` `onlyDependOnClassesThat()` reside in `net.elytrarace.voyager.api..`,
    `java..` or `org.jetbrains..`. This rule has the strongest effect: it makes Minestom, Gson, DI and platform all forbidden in ring 2.
  - `setupDoesNotDependOnServer`: `setup..` must not depend on `server..`.
  - `serverAndPlatformAndApiDoNotDependOnSetup`: `api..`, `physics..`, `race..` and `platform..` must not depend on `setup..`.
  - `onlySetupCompositionDependsOnDiContainer`: in `setup..`, `io.avaje.inject..` and `jakarta.inject..` may be used only in
    `setup.inject..` and in the class `setup.SetupServer`. Together with `onlyServerDependsOnDiContainer`, narrowed to exempt
    `server..` and `setup..` as the two composition roots, this keeps DI out of ring 2 and ring 3.
  - `setupDoesNotDependOnBukkit`: no `org.bukkit..` in `setup..`.
- Existing rules that already cover setup without change: `onlyPlatformDependsOnGson`, `onlyTheTextPackageBuildsAUserFacingString`,
  `DesignRuleTest` exception rules (setup exceptions go in `..exception`), `NullabilityConventionTest` (every package has a
  `package-info.java` with `@NotNullByDefault`).

### 9. Testing

All tests follow F.I.R.S.T. (`openspec/config.yaml`): no sleeps, no `System.currentTimeMillis`, fresh instances per test,
`@TempDir` for files, Minestom ticks driven by `env.tick()`.

- Pure (`voyager-setup`, `new`, milliseconds): `RingFromPoseTest`, `RingPickerTest`, `RingOrientationTest`, `DraftEditorTest`,
  `MapStatusTest`.
- API (`voyager-api`): `MapIdTest`, `MapDraftTest`.
- Platform: `MapDraftAdapterTest`, `MapDraftJsonWriterTest` (including the round trip through the existing
  `MapDefinitionAdapter`), `JsonDraftStoreTest` (fake `FileMover` for the failure path, `@TempDir` otherwise; covers the location
  rule in both directions, the stale-copy deletion, and `DraftLocationConflictException` for two copies).
- Setup integration (Minestom `Env`, fresh per test, `minestom.inside-test=true` as in `voyager-platform`): `SetupCommandsTest`,
  `WandListenerTest`, `RingPreviewTest` (after spike 1.1), `SetupGraphTest` (builds the `BeanScope` with a `@TempDir` data path
  and asserts that every bean resolves).

### 10. ADR-0018 (authoring model, MADR 4.0)

Options: (A) pose placement of disc rings, chosen; (B) record one lap and derive gates (follow-up `add-setup-record-one-lap`);
(C) fly-through capture (later, research 005 section 4.1); (D) polyhedral FAWE selections stored in the game format (rejected:
`Ring` has no polyhedral form; FAWE polyhedra are import-only through `tools/map-converter`, owner decision 2026-10-09).
The ADR records the draft folder layout and the `MapDraft`/`MapDefinition` split. It is `proposed` until the owner accepts
it, and it names Polar and the FAWE inventory as open, not decided.

### 11. Risks

- **A complete draft in `maps/` must boot the game.** Mitigation: the completeness rule (section 4) is the only writer's rule, the
  round-trip test reads each written file with the game's adapter, and the void template holds region data (spike 1.3). The
  validate task (`add-catalog-validate-task`) checks the same files on every run.
- **Two copies after a crash.** Mitigation: `DraftLocationConflictException` refuses to open the map and names both files
  (section 4). No silent choice.
- **A region-less void world fails `MapInstances`.** Mitigation: spike 1.3 decides the template; the template ships with one
  empty region file if needed.
- **No undo.** A left-click removes a ring for good, short of re-placing it. Mitigation proposed as an owner question (O2).
- **Settings duplication.** `SetupSettings` duplicates three of `ServerSettings`' properties until a shared resolver exists.
  Recorded as debt. The greenfield design says duplicated resolution is how the two roots drift; the debt is small and visible.
- **`apiDoesNotPerformFileIo` and `Path` in the port.** If ArchUnit flags the `Path` return of `worldDirectory`, the accessor
  moves out of the port into `JsonDraftStore`, and setup uses the platform class directly.
- **Left-click on air.** If Minestom 26.2 sends no event for a left-click on air, removal works only on blocks. Spike 1.2 decides.
- **Time-based claims.** None. The 10-second test-fly target of research 005 belongs to S4, not to this change.

## Open questions (owner, AskUserQuestion before apply)

| # | Question | Options and trade-offs | Recommendation |
|---|---|---|---|
| O1 | Draft layout | **Resolved 2026-10-09:** flat `maps/<id>.json` with world `<worldsPath>/<id>`, as the game reads today. Drafts without a ring or spawn wait in `drafts/` (section 4). No publish step, no `align-map-folder-with-game-catalog`. | Owner decision |
| O2 | Removal gesture before undo | Plain left-click as specified (fast, unrecoverable) vs sneak plus left-click (one extra key; fewer accidents until S8) | Sneak plus left-click, if the owner agrees to change the spec |
| O3 | Settings | Duplicate two properties now (debt recorded) vs extract a resolver to `voyager-platform` first (refactor(platform), delays this change) | Duplicate now; extract before the second setup feature |
| O4 | Terrain in the setup world | Cancel block edits (no silent loss; builders use the external editor) vs allow edits (lost on restart without a save path) | Cancel block edits |
| O5 | Defaults | Radius sqrt(13), reach 32 blocks, provisional reference time, boost and guide seeds | Designer confirms (Drift); seeds stay provisional as in the sample |
| O6 | US-6.01 closure | Research 005 lists spike X1 (FAWE inventory, Must) as not yet recorded. Close US-6.01 now on the owner's 2026-10-09 approval, or after X1 is in `docs/research/` | Close after X1 is recorded; task 2.3 checks it |

## Migration Plan

The change ships as one squash commit, `feat(setup)`, after the commits in `tasks.md`. Rollback is one revert: the module is
removed from `settings.gradle.kts`, and the platform additions have no caller outside setup. The game server is untouched,
and no data migrates.

## Follow-up changes (not part of this change)

| Change | Type(scope) | Research ID | MoSCoW | Depends on |
|---|---|---|---|---|
| `add-setup-test-fly` | feat(setup) | S4 | Must | this change; race run API |
| `add-setup-draft-publish-gate` | feat(setup) | S5 | Must | this change; Q4; `add-catalog-validate-task`. Scope reduced: the publish copy is gone (owner, 2026-10-09). The gate is test-fly evidence and the checks of `add-catalog-validate-task` |
| `add-catalog-validate-task` (validate-and-exit, NFR-007) | feat(build) | Q6 | Must | `fix-run-data-sync`; `unify-catalog-loading` |
| `add-setup-cup-commands` | feat(setup) | S6 | Should | Q3; the publish gate |
| `add-setup-persistent-checklist` (action bar) | feat(setup) | S3 remainder | Should | this change |
| `add-setup-ring-handles` (Interaction, select/move/delete) | feat(setup) | S7 | Should | spike 1.1; X2 |
| `add-setup-undo` (persisted, bounded) | feat(setup) | S8 | Should | this change; O2 |
| `add-setup-record-one-lap` | feat(setup) | T1 | Should | test-fly; live FlightTick stream; owner approval of the approach |
| `add-setup-dialog-forms` | feat(setup) | S9 | Could | X3 |
| `add-setup-ring-presets` | feat(setup) | S10 | Could | this change |
| `add-setup-design-lint` | feat(setup) | S11 | Could | persistent checklist; playtest data |
| `add-map-folder-bundle` (`maps/<id>/` bundles with their own world) | feat(platform) | (new) | Could | a game loader change; owner approval; not started |
| `add-setup-legacy-world-import` | feat(setup) | S12 | Won't (this cycle) | E6.4 |
| `fix-run-data-sync` (Copy to Sync for `prepareRunData`) | fix(build) | (new, prerequisite of the validate task) | Must | none |
| Phase 0 items Q1 to Q7 of research 005 | refactor/feat/fix(platform) | Q1 to Q7 | Must/Should | none (independent of E6) |
| Spike X1 FAWE inventory; X4 Polar round trip | docs(research) | X1, X4 | Must, Should | none |

## Documentation

- `docs/decisions/0018-pose-placement-authoring-model-for-setup.md` (MADR 4.0, status `proposed`); see task 2.1. It records the
  location rule of section 4 and the two-copy refusal.
- `docs/guides/how-to-build-a-map-with-setup.md` (Diataxis how-to; written after the implementation, task 8.x).
- `docs/research/006-minestom-26-2-setup-spikes.md` (spike record, task 1.4).
- `STATUS.md` (E6 section), `docs/requirements/user-stories-stufe-6.md` (US-6.01 to US-6.05 status column), the greenfield
  epics (E6.0 acceptance criteria), and a cross-reference from the greenfield design's "Setup" section to ADR-0018.
