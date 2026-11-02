# Design

## Context

Today a finished map leaves the setup server by hand. The draft is `run-setup/data/maps/<id>.json` (`JsonDraftStore`, which
already writes the game's own map format), and its world is `run-setup/worlds/<id>`. The guide says so: "A map in
`run-setup/data/maps/` is loadable by the game server once you copy that file, and its world folder ... into the game
server's directories" (`docs/guides/how-to-build-a-map-with-setup.md`, "Run the game server with the map"). A cup is written
by `cup-authoring` into the same data directory and is copied the same way (ADR-0045 Proposed, in that change's design).

The game reads what it is given and checks it at two points. Boot calls `CatalogReloader.loadInitial`, which reads through
`CatalogLoader.read` and refuses on the problems of the played cup (`voyager/platform/.../catalog/CatalogReloader.java:59-75`). A reload calls `CatalogReloader.reload`, which reads the whole
directory, runs the cross-check of cups against maps, and requires region data for every world the played cup needs
(`holdsRegionData`, `CatalogReloader.java:109-115`). Both checks already exist. The setup server can run them on a candidate
before anything reaches the game.

What the game does with a changed world is the risk. A round pins its catalogue (ADR-0019, `hot-reload-catalogs`), so a
reload never changes the rings of a running round. Terrain is different. A world that is open is read from its region files
as chunks load, and `hot-reload-catalogs` already says that a change to the region files of an open world needs a restart
(`CatalogReloader.java:138-140`). Spike 0.1 confirms the lazy read. This design assumes it and does not rely on it: a
published world is never changed in place.

The live topology (from the brief and `docs/guides/how-to-deploy-on-cloudnet.md`): one node runs the game task `ElytraRace`
and the setup service `Voyager-Setup-1`. The game reads its catalogue from `run/data` and its worlds from `run/worlds`, paths
its task passes explicitly. Templates are stored in S3 (`s3:ElytraRace/<template>`). The greenfield design puts maps and cups
into a `Voyager/maps` template layer that ships on the content cadence (its section "CloudNet v4", "Template cut"). Services
with `autoDeleteOnStop` lose their service directory at stop, so anything that must survive a stop lives outside it. ADR-0046
already uses a node path outside the service directory for the rotation state.

Constraints that shape the options:

- Only `voyager-cloudnet-bridge` may name `eu.cloudnetservice..` (ADR-0026; `CloudNetBoundaryTest`). A publish that calls
  CloudNet needs a port in the bridge, and ADR-0026 names the packages the bridge may use.
- The freeze store records the Minestom classes that sit outside `setup.inject` (`SliceBoundaryRulesTest.r6`). A new Minestom
  class outside `setup.inject` adds a store entry, and the apply rules stop on that.
- Configuration is read through `ConfigLoader` (ADR-0036). `SetupSettings` reads `System::getProperty` today; this change
  does not copy that pattern.
- Blocking work is off the tick (`TickBoundaryTest`); the reply goes back on the main thread, as the import does.

## Goals / Non-Goals

Goals:

- A builder publishes a map that passes the same checks as `/map status` and as the game's catalogue check, and nothing
  else reaches the game.
- The game picks up a published revision without a manual copy, at its next reload or start.
- A half-written publish is never visible to the game, and a failed publish changes nothing the game reads.
- A running race never sees its terrain change, and a rollback is possible.

Non-goals:

- A CloudNet template or S3 upload (option B), a second node, and the game polling for revisions.
- Any change to how a cup is played or scored, or to the game's code.
- Automatic assignment of a map to a cup, retention and pruning.

## Options

Quality attributes at stake: correctness of what the game plays (atomicity, no torn catalogue); operability on the live
single node; deployability (no manual copy); reversibility; the security of who may write the catalogue (the setup service's
user, behind `voyager.setup.publish`); and maintainability (no new coupling to CloudNet).

| | A. Node-local revisions (recommended) | B. CloudNet template through a bridge port | C. Export bundle that an operator imports |
|---|---|---|---|
| What it is | The setup server writes numbered, complete revisions under a node path. The game's data path points at the `current` revision. | The setup server builds an archive of the maps, cups and changed worlds, and the bridge uploads it as a version of the `Voyager/maps` template in S3. The game picks it up at its next service start. | The setup server writes a bundle with a manifest and checksums. An operator imports it into the game's template. |
| Gains | No bridge change, no S3 call, no CloudNet API. A publish is one local move. Rollback is a new revision. The game's code does not change. The layout is the same content as a template layer, so moving to B later is an upload. | Keeps maps in the template the greenfield design names. CloudNet keeps template versions. Works across nodes. | No coupling between the two services. The bundle is the backup and the audit trail. Testable without CloudNet. |
| Costs | Setup and game must share one node path (node affinity). Maps become node state, not template content, so the catalogue root needs a backup. The game task's data and worlds paths change once. | Depends on a CloudNet 4 RC17 capability this repository does not verify: a service uploading a template through the bridge (Scout to verify). Needs an ADR-0026 amendment and a new bridge port. A broken bridge stops the service at start (ADR-0026). Each publish uploads whole worlds. The game sees the change only at a new service start. Publishing needs the bridge, so a node without it cannot publish. | Still a manual step, which is the problem this change exists to remove. The operator places the bundle. The live catalogue is a second copy until the bundle is imported. Needs an import tool. |
| Irreversible parts | Maps leave the template for node state. The revision layout and the add-only world names (`<id>.r<k>`) become part of the saved map files, through their `world` values. | The ADR-0026 amendment. Template versions created in S3 become CloudNet history. | None. The bundle format can change. |
| Reversibility | Medium. Moving to B is an upload of the same layout. | Low. Versions accumulate in S3 and the bridge port is in the extension. | High. |
| Cognitive load | Low: one folder with a pointer, and one command per action. | High: a second API surface, S3 retention, and the bridge's failure modes. | Low, but the operator must learn the import step. |

Rejected: writing straight into the game's live `maps/` and `worlds/` folders with no revisions. A half-written map would reach
a reload, a changed world would reach a running race, and there would be no rollback. Option A is the brief's "shared
node-local directory" with revisions added, and without the direct write.

Recommendation: **A**, because it removes the manual copy with no unverified CloudNet call and no bridge amendment. It keeps
every later move open: its layout is the content of a template layer, so B is an upload when a second node is needed. The
price is node affinity and a catalogue root that needs a backup. Those costs are visible and can be paid down later. B
stays the target when the owner needs more than one node, and it waits for the Scout check.

## Decisions

1. **The publish root holds the revisions, the worlds and the pointer (option A).**

   ```
   <publish root>                      VOYAGER_PUBLISH_PATH (setup); the game's data and worlds paths point into it
     worlds/<world name>/              add-only: written once, never changed
     revisions/<n>/data/maps/<id>.json complete, written before the pointer moves
     revisions/<n>/data/cups/<id>.json
     revisions/<n>/manifest.json       who, when, what, and the fingerprint of each world it names
     current -> revisions/<n>          the one pointer; moved by an atomic rename
   ```

   The game task runs with `VOYAGER_DATA_PATH=<publish root>/current/data` and `VOYAGER_WORLDS_PATH=<publish root>/worlds`.
   The game's code does not change: `CatalogLoader` and `CatalogReloader` read the path they are given. The manifest sits
   outside `maps/` and `cups/`, so the game's reader never sees it.

2. **A revision is complete before it is visible, and the pointer move is the commit point.** A publish writes the candidate
   into a staging folder, checks it (decision 5), creates `revisions/<n>` with `Files.createDirectory` (which fails when the
   number is taken), writes the manifest, and only then points `current` at it. The pointer is a symbolic link written as
   `current.tmp` and moved over `current` with `ATOMIC_MOVE`. A failure before the move leaves `current` on the revision the
   game already reads, and the staging folder is removed. Rejected: copying the files over the live folder (a reader sees a
   half-written set) and a lock file (a crash leaves a stale lock, as ADR-0044 found).

3. **A world is add-only and named by its revision.** A published world is copied to `worlds/<id>.r<k>`, where `k` counts the
   revisions of that map's world, starting at 1. A world folder is never modified after it is written; a copy that would reuse
   an existing name is refused. The map's `world` is written explicitly in the published file, so the game never derives it
   from the key. A ring-only change does not copy a world: the fingerprint (decision 7) matches the world the map already
   publishes, and the new file names that world. Rejected: writing the world in place (it would change terrain under a running
   race, and the game has no way to notice). Rejected: a hash in the name (it would make a file name that a builder cannot
   read; the counter is enough, and the manifest carries the fingerprint).

   The name `<id>.r<k>` uses a dot, which `MapId` forbids (`[a-z0-9][a-z0-9_-]{0,31}`), so a map can never be named like a
   revision of another map. `MapDefinition` takes any non-blank `world` string (`MapDefinitionAdapter.java:98-106`).

4. **The snapshot comes from the setup working copy, under the claim and off the tick.** The source is
   `run-setup/worlds/<id>`. The setup server never saves a world (the guide: breaking and placing blocks in an open map is
   cancelled), so the folder on disk changes only when an external editor copies a new one in. A publish copies under the
   builder's claim, on a worker thread. The copy is a point-in-time copy; the design does not detect an editor that writes
   while the copy runs, and the guide says a builder copies a world in between edits, not during a publish.

5. **The candidate check is the game's own check, applied to every map and cup in the candidate.** The candidate passes when:

   - the draft has a spawn and at least one ring (`MapStatus`, the same two blocking problems as `/map status`);
   - the stored copy equals the session's copy (the save guard of `single-builder-map-claims`, applied to a read);
   - `CatalogLoader.read` on the candidate reports no problem (every problem in one reply, not the first);
   - `CatalogConsistency.unresolvedCupMaps` finds no cup that names a map the candidate lacks, for every cup in it;
   - every world named by any map in the candidate holds region data (`holdsRegionData`, the reloader's cheap check, not an
     open). The check covers all worlds, not only the changed one, because a published revision must load as a whole.

   The check is strict for every cup in the publish root, not only for the cup the game plays. Boot warns about a broken cup
   that it does not play (`scope-cup-validation`). Publish is the gate that keeps a broken cup out of the root, so it refuses.
   The cost is that one broken cup blocks every publish until it is fixed. That is intended, and owner question 4 asks whether
   it should be.

6. **Numbers and concurrency.** Two setup servers on one root are the case the save guard already covers for drafts. Each
   publish records the revision it read; before the pointer moves, it reads `current` again, and when the target differs it
   refuses and removes its staging folder. `createDirectory` keeps two numbers from being created twice. The gap between the
   re-read and the move is a few milliseconds of local file access, which ADR-0044 (Proposed) accepts for the save guard, and this design
   accepts it too.

7. **World fingerprint.** The fingerprint is SHA-256 over the sorted list of region and level file names, their sizes and their
   bytes. It is computed off the tick, and the manifest stores it per world name. A world whose fingerprint matches a world the
   map already publishes reuses that name. Rejected: file size and modification time (a copy changes modification times, so a
   change in content would be missed or a match would be lost).

8. **Cups are published by `/cup publish <id>`, which uses the same candidate rule.** The candidate is the previous revision
   plus the cup file as the builder's `cup-authoring` file holds it. The cup's bytes are copied only after the check, so the
   notes and the absence of authors that `cup-authoring` guarantees are kept. This command needs `cup-authoring` merged, and
   its tasks are ordered after that change.

9. **Assignment: a published map is played only when a published cup names it.** Publish does not add a map to any cup. The
   reply to `/map publish` says when no published cup names the map, so the builder knows the next step. The builder's path is
   `/cup map add` (cup-authoring), then `/cup publish`. A cup that names an unpublished map is refused by decision 5.

10. **Permissions.** `voyager.setup.publish` (`PermissionNode.VOYAGER_SETUP_PUBLISH`) gates `/map publish`, `/cup publish` and
    `/catalogue rollback`, and each of them also needs `voyager.setup.use`. `/catalogue history` needs `voyager.setup.use`
    alone. The node is granted to the release group, not to every builder. Owner question 3 asks whether rollback needs its
    own node.

11. **Configuration.** `VOYAGER_PUBLISH_PATH` is read through `ConfigLoader` (ADR-0036). An unset path leaves the server running,
    and each publish command replies that publishing is not configured. A set path must exist when a command runs; the server
    does not create it, which is the rule of the import folder. A set path that does not exist refuses the command and names
    the path. The setup server's existing `SetupSettings` reads the system properties directly; this change does not move it.

12. **Messages.** New keys go into `SetupMessages` (`platform/text`) and both bundles. German uses the informal "du", and
    placeholders are `<arg:N>`. `MessageBundleParityTest` checks the keys.

## What happens to a map that is being played

- **A round that is running** keeps its pinned catalogue (`hot-reload-catalogs`). A publish never changes it, and its terrain
  cannot change, because its world folder is add-only.
- **A changed ring or spawn of a played map** is read at the next reload or boot. It plays from the next round. The running
  round keeps its rings. The reply says so.
- **A changed terrain** is published under a new world name. The game opens that world at the next reload, after it checks
  its region data. The old world stays open until the game restarts, which is the known limit of `hot-reload-catalogs`, and
  the reply says a restart releases it. On a 1024 MB heap, each new terrain revision is one more open world until restart;
  `restart-service-after-cup` (proposed) would release it at the next cup.
- **A rollback during a round** behaves the same way: the round keeps its catalogue, and the next round reads the revision
  rolled back to.
- **A boot during a publish** reads either the old or the new revision in full, because the pointer moves last.
- **A map removed from a revision** is gone from the next reload. Its world stays open until restart, as the reload rule says.

## Versioning and rollback

- Each publish creates revision `n+1`, and numbers are never reused. `revisions/<n>/manifest.json` holds the revision number,
  the time from the injected `Clock` in ISO-8601 UTC, the builder's name as recorded at the claim, the action (`publish-map`,
  `publish-cup` or `rollback`), the items it changed, and the fingerprint of every world the revision names.
- `/catalogue rollback <n>` writes revision `N+1` whose data is revision `n`'s data. It runs the same candidate check, so a
  rollback to a revision whose worlds are gone is refused. History is never rewritten and the pointer never moves backwards.
- `/catalogue history` lists the last ten revisions (number, time, builder, action and items).
- Voyager deletes nothing in this change. Disk growth is one world copy per terrain publish; the reply shows the size of each
  copy. Retention is owner question 6.

## Dependency direction

| Type | Module | Package | Note |
|---|---|---|---|
| `CataloguePublisher` (port: `publishMap`, `publishCup`, `rollback`, `history`) | `voyager-api` | `net.elytrarace.voyager.api.publish` | pure port, no file I/O in the API |
| `RevisionNumber`, `Revision`, `PublishedItem` (records) | `voyager-api` | `net.elytrarace.voyager.api.publish` | values only |
| `PublishRefusedException`, `RevisionNotFoundException`, `CatalogueChangedException`, `CataloguePublishFailedException` | `voyager-api` | `net.elytrarace.voyager.api.publish.exception` | `RuntimeException`, domain names, `package-info` |
| `PermissionNode.VOYAGER_SETUP_PUBLISH` | `voyager-api` | `net.elytrarace.voyager.api.permission` | value `voyager.setup.publish` |
| `FileCataloguePublisher` (implements the port) | `voyager-platform` | `net.elytrarace.voyager.platform.catalog.publish` | the candidate check, the world copy, the fingerprint, the revision store, the pointer move |
| `WorldFingerprint`, `WorldSnapshot`, `CandidateCheck`, `RevisionStore` | `voyager-platform` | `net.elytrarace.voyager.platform.catalog.publish` | `RevisionStore` takes a `FileMover` seam, as `JsonDraftStore` does |
| `PublishPlanner` (pure: which checks to ask for, the guard order) | `voyager-setup` | `net.elytrarace.voyager.setup.publish` | depends on `api` and the JDK only |
| `PublishCommand`, `CatalogueCommand` (Minestom wrappers) | `voyager-setup` | `net.elytrarace.voyager.setup.inject` | converts senders and arguments, renders replies; outside r6 by the rule's own exemption |
| `SetupMessages` keys | `voyager-platform` | `net.elytrarace.voyager.platform.text` | both bundles |
| `VOYAGER_PUBLISH_PATH` | `voyager-platform` | `net.elytrarace.voyager.platform.config` | read through `ConfigLoader` |

The API holds the port and the values. The platform implements the port and does the file work. The setup server depends on
the port, not on the implementation, and `SetupBeans` (the composition root) wires the implementation. No new edge between
two slices is added. The bridge is not touched.

### Fitness rules

- Covered by existing rules: `ApiPurityTest` (the API port and records have no file I/O and no platform type),
  `DesignRuleTest` (exception names and hierarchy), `NullabilityConventionTest` (every new package has `package-info.java`),
  `PermissionBoundaryTest.commandsAskOnlyThePolicy` (the command wrappers ask the policy, not LuckPerms),
  `CloudNetBoundaryTest` (no `eu.cloudnetservice` anywhere new), `PersistenceBoundaryTest` (setup does not reach persistence),
  `TickBoundaryTest.noBlockingOnFuturesOnTheTick` (the publish work is not joined on the tick), and `MessageBundleParityTest`.
- `SliceBoundaryRulesTest.r6_setupAdapterDoesNotUseMinestom` stays frozen and unchanged. The new Minestom wrappers sit in
  `setup.inject`, as `cup-authoring` decided. A Minestom class anywhere else adds a freeze-store entry and stops the apply.
- `ApiPurityTest.setupDomainOnlyDependsOnApiAndJdk` covers `setup.mapsetup..` only. This change proposes adding
  `setup.publish..` to its list of allowed dependencies, so the pure planner cannot grow a platform or Minestom import. If
  that extension creates a violation, the apply stops and reports it. No freeze entry is added.
- The platform's infrastructure packages (`text`, `convert`, `world`, `tick`, `render`) must not depend on `race`. The new code
  lives in `platform.catalog.publish`, which has no such rule, and it needs no `race` type.

## Testing

Every test follows F.I.R.S.T. and Red/Green TDD. Each test builds its own `@TempDir` data directory and publish root; no
test reads the shipped `catalog/` or `worlds/`; no test sleeps; the revision time comes from an injected `Clock` (`Clock.fixed`).

- Pure (plain JUnit, no I/O): `PublishPlannerTest`; the order of the checks; the refusal lists.
- Platform (`@TempDir`): `WorldFingerprintTest`, `RevisionStoreTest` (staging, the atomic move, a failing move through a fake
  `FileMover`, two stores over one root, add-only worlds), `CandidateCheckTest` (every world checked, the cross-check, the
  game's own refusal), `LayoutCompatibilityTest` (the game's `CatalogLoader.read` on `current/data` sees a revision, and a
  moved pointer changes what it reads).
- Setup (`@EnvTest`, one `Env` per test, `env.tick()` driven): a builder without `voyager.setup.publish` is refused and nothing
  is written; a publish with the claim and the node writes revision 1 and replies with the key; an unclaimed publish is refused.
- Rollback and history: `RollbackTest` (a new revision with revision `n`'s data), `HistoryTest` (the lines, from a fixed clock).
- Configuration: unset path reply; a missing directory is refused, named, and not created.
- Messages: `MessageBundleParityTest`.

## ADR-0048 (proposed): Publish checked maps and cups as numbered revisions under a node path

Status: **Proposed**. It is accepted only when the owner accepts it. The text moves to
`docs/decisions/0048-publish-catalogue-revisions.md` in the code pull request, with the status the owner gives it.

Number: 0048 is the next number after 0047, which `restart-service-after-cup` reserves, and 0045 is reserved by
`cup-authoring`. The number is provisional until the proposal's check against `origin/main`.

### Context and problem statement

A finished map reaches the game by copying files. A cup reaches it the same way. The copy is where a mistake enters the game,
and the game refuses it only at boot or at reload. The setup server can apply the game's rules first, but the game must still
read a set of files that is complete and never half-written, and a running race must not see its terrain change.

### Decision drivers

- The game never reads a partial or unchecked catalogue.
- A running race's terrain never changes, and a rollback is possible.
- No manual copy, and no new dependency on CloudNet for the publish path on the live single node.
- Reversible to a template-based route when a second node is needed.

### Considered options

1. **Numbered, complete revisions under a node path with an atomic pointer (chosen).**
2. **A CloudNet template upload through a bridge port (ADR-0026 amendment).** Rejected for now: it needs a CloudNet capability
   this repository does not verify, a new bridge port, and a publish path that depends on the most fragile extension.
3. **An export bundle that an operator imports.** Rejected: the manual step remains.
4. **Writing directly into the game's live folders.** Rejected: no atomic unit, no rollback, and a changed world reaches a
   running race.

### Decision outcome

Option 1. It meets the drivers with the components the repository already has (the game's reader, the reload's checks, the
atomic-write pattern of the draft store, and the node path of ADR-0046). Its costs are node affinity and a catalogue root
that needs a backup, and both are written down.

### Positive consequences

- No manual copy. A publish is checked, complete and atomic, and a rollback is a new revision.
- The game's code is unchanged.
- A running race keeps its terrain and its catalogue.

### Negative consequences / accepted trade-offs

- The setup service and the game service must share one node path, so the game cannot move to another node without a
  migration.
- Maps become node state. The catalogue root is the only copy of the published maps, so the operator must back it up.
- The live task's data and worlds paths change once.
- An accepted ADR-0048 supersedes the template cut for maps and cups in the greenfield design, and the copy step of ADR-0045.

### Confirmation

`LayoutCompatibilityTest` reads a published revision through the game's loader. `RevisionStoreTest` and `CandidateCheckTest`
pin the commit point and the checks. The live acceptance run, which the owner performs, is recorded in the pull request.

## Risks

| Risk | Impact | Response |
|---|---|---|
| The game reads region files lazily, and a changed file reaches a running race | Terrain changes mid-race | Add-only worlds (decision 3). Spike 0.1 confirms the lazy read; the design does not rely on it |
| The catalogue root is the only copy of published maps | Loss of the live maps on node failure | Backup of the root; an export bundle (option C) can be added as a second copy |
| Node affinity: the game task is scheduled on another node | The game cannot read the revisions and refuses to boot | The boot names the path; `validateCatalog` checks it; the operator's task runs on the node that holds the root |
| A saved world with no terrain (a void world from `/map new`) passes the region check | An empty course is published | Spike 0.3 decides; owner question 8 |
| Disk growth from one world copy per terrain publish | The node runs out of space | Free-space check before the copy (Should); retention is owner question 6 |
| A broken cup blocks every publish | Publishing stalls | Intended (decision 5); owner question 4 |
| The setup task is dynamic with `autoDeleteOnStop` | Drafts in its service directory are lost at stop | Spike 0.4 checks the task; the publish root is outside the service directory in every case |

## Open questions

These are owner decisions. Each needs an answer before the code pull request, and the spikes answer what they can.

1. **Option A, not B or C?** The recommendation is A. Owner decision. Option B needs the Scout check of the CloudNet API first.
2. **Accept the node affinity and the backup duty of the catalogue root?** The alternative is option B, with its bridge amendment.
3. **Does rollback need its own node?** The recommendation is one node, `voyager.setup.publish`, for publish, cup publish and rollback.
4. **Should one broken cup block every publish?** The recommendation is yes, strict for every cup in the root. The alternative is the boot policy, which warns for a cup the game does not play.
5. **How does a game pick up a revision?** Today: at the next reload or start. Options: a poll of the pointer (the follow-up of `hot-reload-catalogs`), or `restart-service-after-cup`. The recommendation is to pair A with `restart-service-after-cup`, which the owner must enable on the live task.
6. **Retention of revisions and worlds.** Voyager deletes nothing in this change. Who prunes, and at what free-space threshold?
7. **Seeding the live catalogue.** The live maps and worlds of `run/data` and `run/worlds` become revision 1 by a documented one-time copy. Should a command do it, and who runs it?
8. **Should a publish refuse a world that holds only the void template?** Spike 0.3 decides whether a `/map new` world has region data.
9. **Does a world's new name change what the statistics count?** Vault confirms that the statistics key maps by map key and layout version (geometry), not by world name, so a terrain revision does not split a leaderboard.
10. **Is the setup task static?** The brief names the setup service `Voyager-Setup-1`. If it is dynamic with `autoDeleteOnStop`, drafts in its service directory are lost at stop. Hangar confirms the task.
11. **Whether the greenfield template cut for maps may be amended** (decision 1 and ADR-0048). Owner decision.
