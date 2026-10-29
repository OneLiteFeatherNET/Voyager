# Design

## Context

A cup is a `CupDefinition` (`voyager/api/.../api/race/CupDefinition.java:22`): a key, the keys of its maps in play order
(`mapNames`), a `GameMode`, a display name and authors. Its non-empty map list is required (`CupDefinition.java:44-46`), and
its map names are normalised to keys (`CupDefinition.java:51`).

The game server reads cups from `<dataDirectory>/cups/` and maps from `<dataDirectory>/maps/` (`CatalogLoader.java`, the
`read` method, which resolves both folders from the data directory). The setup server's data directory has the same two
folders for its own use: `maps/` holds the game-loadable drafts and `drafts/` the rest (`JsonDraftStore.java:48-49`). A cup
written into `<dataPath>/cups/` is therefore the file the game reads, once the operator copies it there (see ADR-0045 below).

Three existing rules shape the design:

1. **A cup names only maps the game can load.** `CatalogConsistency.unresolvedCupMaps` finds every entry that names a map the
   catalogue does not provide (`voyager/platform/.../catalog/CatalogConsistency.java:36-40, 60-70`). The game refuses a boot
   with such a cup, so the setup server must refuse the same save.
2. **Saves are atomic.** `JsonDraftStore.save` writes a temporary file in the target folder, forces it, moves it over the
   target, and deletes the stale copy only after the new copy exists (`JsonDraftStore.java:124-151`). A cup save uses the same
   sequence.
3. **The setup server creates only its data and worlds folders at boot.** `server-bootstrap` requires both to exist and does
   not create a `cups` folder (`openspec/specs/setup/server-bootstrap/spec.md`), so the first cup save creates it.

The shipped `cups/alpha_cup.json` carries a `notes` array of strings. `cup.schema.json` describes it as free text that the
loader ignores, and the cup reader does not read it. A save by this change keeps it (decision 9).

Claims come from change 5. `EditClaims` (`setup/mapsetup`) holds one holder per string key and reserves `cup:<id>` for cups
(`single-builder-map-claims`, spec `map-claims`). The permission node `voyager.setup.unlock` (`PermissionNode.VOYAGER_SETUP_UNLOCK`)
is added by that change too; this change only uses it.

## Goals / Non-Goals

Goals:

- A builder creates a cup, opens it, names it, sets its mode and orders its maps in the setup server.
- One builder edits a cup at a time: a cup is claimed when it is opened, as a map is.
- The setup server never writes a cup the game would refuse for an unknown or unloadable map.
- The file is the one the game reads: version 2, the key `elytrarace:<id>`, the same field names.
- Every change is saved before the reply, and a failed save keeps the previous file.
- A save keeps the `notes` the file holds, and refuses a file that states `authors`.

Non-Goals:

- Undo, authors on a cup, publishing to the game server, and the statistics. See the proposal.

## Decisions

1. **The setup server writes cups into its data directory; the operator copies them to the game server (ADR-0045).** The
   maps work the same way today. Rejected: a publish command that sends the cup to the game server (a network protocol outside
   this change's scope); writing into the game server's folder (the two servers do not share a directory by default,
   `server-bootstrap`).

2. **A cup id is a `CupId` with the same rule as a map id.** `[a-z0-9][a-z0-9_-]{0,31}`, refused before any file is touched.
   The value object is `CupId` in `net.elytrarace.voyager.api.cupsetup`, and a test asserts that `CupId` and `MapId` accept
   and refuse the same strings. Rejected: reusing `MapId` for a cup (its name says map, and a later rule for one kind would
   silently change the other).

3. **The mode is `RACE` or `PRACTICE`, in either letter case.** The builder types lower case as often as not. Stored as the
   `GameMode` constant name. Rejected: free text (a typo would become a boot refusal).

4. **Map positions are counted from 1.** `/cup open` prints the positions from 1, so a builder types the number the list shows.
   Rejected: 0-based positions (the first map would be position 0, which no list shows).

5. **A map argument is a flat id or a key.** As in `add-catalogue-keys-and-authors` (decision 5), a flat `blue_and_red` is read
   as `elytrarace:blue_and_red`, and the file stores the key. Rejected: storing the flat form (the file's `mapNames` are keys).

6. **The game-loadable check uses the catalogue's own rule.** The store reads the maps the game can load from `maps/` with
   `CatalogLoader.read`, takes the parsed maps, and calls `CatalogConsistency.unresolvedCupMaps` with the one cup. A map file
   that does not parse is therefore not loadable, and a cup naming it is refused, which is what the game would do at boot.
   Rejected: a file-exists check (it would accept a malformed map that fails at boot), and checking `drafts/` (a draft is not
   game-loadable). The cost is one read of `maps/` per cup save; a setup data directory holds tens of maps, so that is acceptable.

7. **The cup writer writes a fixed set of keys, and no `authors`.** `CupDefinitionJsonWriter` in `platform.catalog.writer`
   writes `schemaVersion` (2), `key`, `displayName` (when set), `mode`, `mapNames`, and then the `notes` array when the stored
   file has one (decision 9). It writes no `$schema` pointer, which is an editor hint, and no `authors` key (decision 8). It is a
   streaming JSON writer, as `MapDraftJsonWriter` is. Rejected: reflective serialisation of the record (it would write fields the
   format does not name).

8. **Cups carry no authors.** The setup server neither adds nor removes cup authors (owner decision C6). A save of a cup whose
   stored file states an `authors` key, whatever its value, is refused with `CupAuthorsRefusedException`, which names the file
   and the key, so no author data is lost silently. The check runs in the store, before the save. Rejected: dropping the authors
   on save (a silent loss of data a person wrote); writing them back (a cup author would then be data the setup server carries
   without any command that sets it).

9. **A save keeps the file's `notes` array, and `CupDefinition` gains no field (owner decision C7).** The mechanism: after the
   check of decision 12 has confirmed that the stored file still equals `expected`, `JsonCupStore` reads the stored file's
   `notes` array as raw JSON strings and writes them back verbatim, in the same order, after `mapNames`. The strings never pass
   through `CupDefinition` or the `CupStore` port, so the API does not change and no command holds notes in memory. A file
   without `notes` gets no `notes` key. A notes-only edit made by hand is kept as it stands on disk, and it does not make the
   check of decision 12 refuse, because that check compares the record only. Rejected: a `notes` field on `CupDefinition` (an API
   change for text the game ignores); dropping the notes on save (it loses text an operator wrote for the person editing the file).

10. **A cup is claimed like a map, under the key `cup:<id>` (owner decisions O4 and C8).** The claim is held in change 5's
    `EditClaims` registry. The rules:
    - `/cup new` checks the id, the file and the maps, saves the cup, and only then takes the claim, so a failed save takes none.
      A new id has no holder; a holder of the key is refused with its name, as for an open.
    - `/cup open <id>` refuses a missing cup. It refuses, naming the holder, when another builder holds `cup:<id>`. The builder
      who already holds it changes nothing. Otherwise it takes the claim first and releases the cup the builder held before,
      the order change 5 uses for maps, so a refused open leaves the builder on the cup they had.
    - `name`, `mode`, `map add`, `map remove` and `map move` apply only when the builder holds the claim. Without it, the
      refusal says to open the cup first; with another holder, it names the holder.
    - `/cup unlock <id>` requires `voyager.setup.unlock` and releases the claim whoever holds it. Its gate is that node alone.
    - A claim is released when the builder opens another cup, when the builder disconnects, and by unlock. A builder holds at
      most one cup claim. A cup claim does not touch the builder's map claim, so a builder can hold a map and a cup at once.
    - There is no `/cup close`. C8 lists none, and the two events that end a session already end a claim.

    The open cup of each builder is held by `CupCommandService` (decision 13), not by `BuilderSessions`, because `BuilderSessions`
    is bound to Minestom. The Minestom wrapper forwards the disconnect to the service. Each command reads, changes, saves and
    replies within one main-thread step, so two builders in one process cannot tear a file. Decision 12 covers a write from
    outside the process. Rejected: edits with no claim (two builders would edit one cup in turn without a word); a `/cup close`
    (not in C8, and redundant with the other release paths).

11. **Save before reply; no undo for cups (owner decision C3).** Every command saves the cup before it replies, as the `/map`
    commands do. Cups are not covered by undo: `undo-draft-edits` covers map edits only. Rejected: a history for cups (C3 leaves
    cups out, and a mistyped position is fixed by a second command).

12. **A cup save is guarded like a draft save, with the same mechanism.** ADR-0044 (proposed in `single-builder-map-claims`) asks
    that the rule cover the cups of this change "without a second mechanism". `CupStore` therefore has `load`, `create` and
    `saveIfUnchanged(CupDefinition expected, CupDefinition next)`, and no plain save. `JsonCupStore` implements the guard by comparing
    the stored file with `expected` on the canonical JSON that `CupDefinitionJsonWriter` writes, on both sides. A difference, or a
    missing file, refuses with `CupChangedException`, and the file keeps its bytes. Each command reads the cup at its start and passes
    that copy as `expected`, so the guard catches a write from another process between the read and the save. Rejected: a plain
    `save` (it would replace an outside edit without a word); a second guard type with its own comparison (the second mechanism
    ADR-0044 rules out).

13. **The command logic is pure; the Minestom command is a thin wrapper (owner decision C9).** The port `CupCommandPort` (an
    interface) and its class `CupCommandService` live in `net.elytrarace.voyager.setup.cupsetup`, with no Minestom or Adventure
    type. The port takes a `BuilderRef` (the builder's UUID and name) and plain arguments, and returns a `CupReply`, a record of a
    message key and its arguments. The service asks the `PermissionPolicy` port of `api.permission` for `voyager.setup.use` on each
    subcommand except `unlock`, and for `voyager.setup.unlock` on `unlock`. `CupCommand` extends Minestom's `Command`. It converts the
    sender and the arguments into the port's calls and renders each reply with `SetupMessages`.

    **Placement deviates from C9 in one point.** C9 puts the wrapper in the root package `net.elytrarace.voyager.setup`. The rule
    `SliceBoundaryRulesTest.r6_setupAdapterDoesNotUseMinestom` exempts only `net.elytrarace.voyager.setup.SetupServer` and the
    `setup.inject..` package, so a Minestom class in the root package would be a new violation, which C9 forbids. The wrapper therefore
    lives in `net.elytrarace.voyager.setup.inject`. That package is the setup server's composition root, which the rule's own comment
    names as wiring. A nested class in `SetupServer` would not match the exemption either, because the exemption compares the full
    name. Rejected: the wrapper in `SetupServer` (the composition root would grow a command body); a freeze-store entry (C9).

**ADR-0045 (proposed): the setup server writes cups into its data directory, and the operator copies them to the game server**

Status: **Proposed**. It is accepted only when the owner accepts it. The text below moves to `docs/decisions/0045-cup-files-from-the-setup-server.md`
in the code pull request, with the status it then has.

### Context and problem statement

A map is written by the setup server into its data directory and copied by the operator to the game server. A cup is still
written by hand. A cup is a rotation of maps, so the setup server can check it, and it should write it the way it writes maps.

### Decision drivers

- One way for every catalogue file: the setup server writes, the operator copies, the game reads.
- The setup server and the game server do not share a directory by default (`server-bootstrap`).
- No new network protocol for publishing in this change.

### Considered options

1. **Write into `<dataPath>/cups/`; the operator copies the file** (chosen).
2. **Publish the cup to the game server through a command** (rejected: a network protocol, and a later change by the proposal).
3. **Write into the game server's `cups/` folder directly** (rejected: the two servers do not share a folder by default, and the
   game server would read a file the setup server is still writing).

### Decision outcome

Option 1. It repeats the path the maps already take, so the operator's copy step is the same for both kinds of file.

### Consequences

- Good: no new deployment step; the game reads the file it is given.
- Good: the same folder layout in the setup data directory for maps and cups.
- Bad: an operator must copy each cup by hand. A publish command would remove the step, and it is later work.
- Neutral: a cup copied before its maps are copied fails the game's cross-check at boot. The cup save check cannot see the game
  server's folder, so the operator copies the maps first; `/cup open` shows which maps the setup server holds as loadable.

### Confirmation

`JsonCupStoreTest` writes a cup into `@TempDir` and reads it with the game's `CupDefinitionAdapter`. The `docs/guides` section
"Build a cup" names the copy step.

## Dependency direction

| Type | Module | Package | Note |
|---|---|---|---|
| `CupStore` (port: `load`, `create`, `saveIfUnchanged`) | `voyager-api` | `net.elytrarace.voyager.api.cupsetup` | pure port, no I/O in the API, no plain save |
| `CupId` | `voyager-api` | `net.elytrarace.voyager.api.cupsetup` | value object, the map id's rule |
| `CupNotFoundException`, `CupAlreadyExistsException`, `CupWriteFailedException`, `CupChangedException`, `CupMapNotLoadableException`, `CupAuthorsRefusedException`, `InvalidCupIdException` | `voyager-api` | `net.elytrarace.voyager.api.cupsetup.exception` | `RuntimeException`, domain-named |
| `JsonCupStore` (implements `CupStore`) | `voyager-platform` | `net.elytrarace.voyager.platform.catalog` | beside `JsonDraftStore`, same folder layout; reads the stored `notes` at save |
| `CupDefinitionJsonWriter` | `voyager-platform` | `net.elytrarace.voyager.platform.catalog.writer` | streaming writer, no `authors`, `notes` passed in verbatim |
| `AtomicJsonFile` | `voyager-platform` | `net.elytrarace.voyager.platform.catalog` | extracted from `JsonDraftStore.save` |
| `CupEditor` (pure: rename, mode, add, remove, move) | `voyager-setup` | `net.elytrarace.voyager.setup.cupsetup` | `@ApiStatus.Internal`, like `DraftEditor` |
| `CupCommandPort` (interface) and `CupCommandService` (pure class; holds each builder's open cup) | `voyager-setup` | `net.elytrarace.voyager.setup.cupsetup` | no Minestom or Adventure type; asks `PermissionPolicy` |
| `BuilderRef`, `CupReply` (records) | `voyager-setup` | `net.elytrarace.voyager.setup.cupsetup` | the port's arguments and replies |
| `CupCommand` (Minestom `Command` wrapper) | `voyager-setup` | `net.elytrarace.voyager.setup.inject` | converts senders and arguments; renders replies; outside r6 by the rule's own exemption |
| `PermissionPolicy` (port) | `voyager-api` | `net.elytrarace.voyager.api.permission` | existing port; `voyager.setup.use` and `voyager.setup.unlock` |
| `PermissionNode.VOYAGER_SETUP_UNLOCK` | `voyager-api` | `net.elytrarace.voyager.api.permission` | added by change 5, used here |
| `MapName` (name parser of change 7) | `voyager-setup` | `net.elytrarace.voyager.setup.mapsetup` | reused by the cup name command |
| `EditClaims` (change 5's registry, key `cup:<id>`) | `voyager-setup` | `net.elytrarace.voyager.setup.mapsetup` | reused; change 5 must be merged first |

Dependency direction: `voyager-setup` depends on `voyager-api` and `voyager-platform`, as now; `voyager-platform` depends on
`voyager-api`; `voyager-api` depends on nothing here. The cup store reads the maps through the platform's catalog classes, and
the setup slice sees only the port.

### Fitness rules

- Covered by existing rules: `ApiPurityTest` (the port, `CupId`, the records and the exceptions have no I/O and no platform type);
  `NullabilityConventionTest` (the new `api.cupsetup` and `setup.cupsetup` packages need `package-info.java`); `DesignRuleTest`
  (exceptions are runtime exceptions with domain names); `PermissionBoundaryTest.commandsAskOnlyThePolicy` (the service asks the
  `PermissionPolicy` port and imports no LuckPerms type).
- `SliceBoundaryRulesTest.r6_setupAdapterDoesNotUseMinestom` is frozen and stays clean without a change to it. `CupCommandService`
  and `CupEditor` import no Minestom type; `CupCommand` sits in `setup.inject`, which the rule exempts. No freeze-store entry is added,
  and the store under `voyager/fitness/src/test/resources/archunit_store` is not changed by this pull request.
- Not covered: `r5_mapsetupContractsLiveInPlatformMapsetup` names `api.mapsetup..` only. `JsonCupStore` depends on `api.cupsetup`,
  so the rule does not reach it, and `JsonDraftStore` sits in `platform.catalog` for the same reason. No new rule is proposed.

## Testing

Every test follows F.I.R.S.T.:

- **Fast and Repeatable:** the store tests write into `@TempDir` and never read the shipped `cups/` or `maps/`; the maps and cups the
  tests need are written by the test. The editor and service tests are plain JUnit with no I/O beyond a `@TempDir` store.
- **Independent:** each test builds its own `JsonCupStore`, `EditClaims` and `CupCommandService` over a fresh `@TempDir` data directory.
  No static state, no shared catalogue.
- **Self-validating:** assertions name the file bytes, the reply key, or the exception's keys, never a log line.
- **Timely:** each behaviour starts with a failing test (Red/Green TDD).
- **Pyramid:** `CupEditorTest` and `CupIdTest` are unit tests; `JsonCupStoreTest` is the adapter test; `CupCommandServiceTest` covers the
  claims and the replies; `SetupPermissionTest` covers each `/cup` subcommand through the wrapper.
- The save guard is tested as change 5 tests its draft guard: two `JsonCupStore` instances over one `@TempDir` play two setup servers,
  and the second save is refused with the file keeping the first writer's bytes.
- Tests named in `tasks.md` cover: a round trip through the game's cup adapter; a cup naming a draft map refused; move and remove
  renumber; the last map not removable; the guard refusal; the authors refusal; the notes kept across a save; the claim refused and
  released by open, by disconnect and by unlock; and the edit refused without a claim.

## Open questions

1. **A map twice in one cup.** Recommendation: allowed, because `CupDefinition` allows it. Option B: refused. Pro of allowing it: a
   rotation may play one map twice on purpose. Con: the statistics count both plays. The answer changes only the editor's check
   in `CupEditor`, and no other part of the design.
