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

The shipped `cups/alpha_cup.json` carries a `notes` array, which `cup.schema.json` allows (`notes`, `additionalProperties:
false`), but which the cup reader does not read. A save by this change therefore drops it (decision 9).

## Goals / Non-Goals

Goals:

- A builder creates a cup, names it, sets its mode and orders its maps in the setup server.
- The setup server never writes a cup the game would refuse for an unknown or unloadable map.
- The file is the one the game reads: version 2, the key `elytrarace:<id>`, the same field names.
- Every change is saved before the reply, and a failed save keeps the previous file.

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

4. **Map positions are counted from 1.** `/cup info` prints the positions from 1, so a builder types the number the list shows.
   Rejected: 0-based positions (the first map would be position 0, which no list shows).

5. **A map argument is a flat id or a key.** As in `add-catalogue-keys-and-authors` (decision 5), a flat `blue_and_red` is read
   as `elytrarace:blue_and_red`, and the file stores the key. Rejected: storing the flat form (the file's `mapNames` are keys).

6. **The game-loadable check uses the catalogue's own rule.** The store reads the maps the game can load from `maps/` with
   `CatalogLoader.read`, takes the parsed maps, and calls `CatalogConsistency.unresolvedCupMaps` with the one cup. A map file
   that does not parse is therefore not loadable, and a cup naming it is refused, which is what the game would do at boot.
   Rejected: a file-exists check (it would accept a malformed map that fails at boot), and checking `drafts/` (a draft is not
   game-loadable). The cost is one read of `maps/` per cup save; a setup data directory holds tens of maps, so that is acceptable.

7. **A cup writer writes the keys in a fixed order and no `notes`.** `CupDefinitionJsonWriter` in `platform.catalog.writer`
   writes `schemaVersion` (2), `key`, `displayName` (when set), `mode`, `mapNames`, and `authors` (when set), as a streaming
   JSON writer, as `MapDraftJsonWriter` does. Rejected: reflective serialisation of the record (it would write fields the
   format does not name).

8. **The atomic write is one helper, shared by both stores.** The temporary-file, force and replace sequence of
   `JsonDraftStore.save` moves into a helper `AtomicJsonFile` in `platform.catalog`, which `JsonDraftStore` and `JsonCupStore`
   both call. The extraction is a refactor with its own commit, and the existing `JsonDraftStoreTest` must pass unchanged.
   Rejected: copying the sequence into the cup store (two copies of the one rule that makes a crash safe).

9. **An edit keeps the display name and the authors, and drops the `notes`.** A save of an existing cup keeps what the record
   holds, so the authors and the display name survive an edit. The `notes` array is not part of the record, so it is not
   kept. Open question 2 asks the owner whether the notes of the shipped cups should be carried through the record.

10. **A cup has no open session in this change; the claim is a question for the owner.** Change 5 provides the registry
    `EditClaims` in `setup/mapsetup`, keyed by string, and it reserves the key `cup:<id>` for this change
    (`single-builder-map-claims`, spec `map-claims`, scenario on `map:alpha` and `cup:alpha`). The brief places the cup id under
    that registry while the cup is open, but the command list has no `/cup open`. Without it, no command holds a claim, and the
    claim requirement in `specs/setup/cup-commands` takes effect only if the owner accepts `/cup open` and `/cup close` (open
    question 1). Each command reads, changes, saves and replies within one main-thread step, so two builders cannot tear a file,
    even though they can edit one cup in turn. Decision 12 covers the case that one step does not: a write from outside the
    process.

11. **Save before reply, and no undo in the first version.** Every command saves the cup before it replies, as the `/map`
    commands do. Undo for cups is open question 3.

12. **A cup save is guarded like a draft save, with the same mechanism.** ADR-0044 (proposed in `single-builder-map-claims`) asks
    that the rule cover the cups of this change "without a second mechanism". `CupStore` therefore gains `saveIfUnchanged(CupDefinition expected, CupDefinition
    next)`, which `JsonCupStore` implements by comparing the stored file with `expected` on the canonical JSON that
    `CupDefinitionJsonWriter` writes, on both sides. A difference, or a missing file, refuses with `CupChangedException`, and the
    file keeps its bytes. Each command reads the cup at its start and passes that copy as `expected`, so the guard catches a
    write from another process between the read and the save. Rejected: a plain `save` (it would replace an outside edit
    without a word); a second guard type with its own comparison (the second mechanism ADR-0044 rules out).

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
  server's folder, so the operator copies the maps first; `/cup info` shows which maps the setup server holds as loadable.

### Confirmation

`JsonCupStoreTest` writes a cup into `@TempDir` and reads it with the game's `CupDefinitionAdapter`. The `docs/guides` section
"Build a cup" names the copy step.

## Dependency direction

| Type | Module | Package | Note |
|---|---|---|---|
| `CupStore` (port: `load`, `create`, `save`, `saveIfUnchanged`) | `voyager-api` | `net.elytrarace.voyager.api.cupsetup` | pure port, no I/O in the API |
| `CupId` | `voyager-api` | `net.elytrarace.voyager.api.cupsetup` | value object, the map id's rule |
| `CupNotFoundException`, `CupAlreadyExistsException`, `CupWriteFailedException`, `CupChangedException`, `CupMapNotLoadableException`, `InvalidCupIdException` | `voyager-api` | `net.elytrarace.voyager.api.cupsetup.exception` | `RuntimeException`, domain-named |
| `JsonCupStore` (implements `CupStore`) | `voyager-platform` | `net.elytrarace.voyager.platform.catalog` | beside `JsonDraftStore`, same folder layout |
| `CupDefinitionJsonWriter` | `voyager-platform` | `net.elytrarace.voyager.platform.catalog.writer` | streaming writer, no `notes` |
| `AtomicJsonFile` | `voyager-platform` | `net.elytrarace.voyager.platform.catalog` | extracted from `JsonDraftStore.save` |
| `CupEditor` (pure: rename, mode, add, remove, move) | `voyager-setup` | `net.elytrarace.voyager.setup.cupsetup` | `@ApiStatus.Internal`, like `DraftEditor` |
| `CupCommands` (the `/cup` root) | `voyager-setup` | `net.elytrarace.voyager.setup.adapter` | Minestom, like `SetupCommands` |
| `MapName` (name parser of change 7) | `voyager-setup` | `net.elytrarace.voyager.setup.mapsetup` | reused by the cup name command |
| `EditClaims` (change 5's registry, key `cup:<id>`) | `voyager-setup` | `net.elytrarace.voyager.setup.mapsetup` | reused only when open question 1 is answered with option A |

Dependency direction: `voyager-setup` depends on `voyager-api` and `voyager-platform`, as now; `voyager-platform` depends on
`voyager-api`; `voyager-api` depends on nothing here. The cup store reads the maps through the platform's catalog classes, and
the setup slice sees only the port.

### Fitness rules

- Covered by existing rules: `ApiPurityTest` (the port, `CupId` and the exceptions have no I/O and no platform type);
  `NullabilityConventionTest` (the new `api.cupsetup` packages need `package-info.java`); `DesignRuleTest` (exceptions are
  runtime exceptions with domain names); `PermissionBoundaryTest.commandsAskOnlyThePolicy` (the `/cup` subcommands ask the policy
  and nothing else).
- **Frozen debt, stated:** `r6_setupAdapterDoesNotUseMinestom` (`SliceBoundaryRulesTest`) is a frozen rule. `SetupCommands` is
  already one of its recorded violations. `CupCommands` is a new Minestom `Command` in `setup.adapter`, so it is a new violation,
  and the freeze store needs an entry for it. The code pull request adds that entry and names it in the pull request, so the
  owner sees the debt being added. Rejected: putting the `/cup` root in `SetupServer` (the composition root would grow a
  command body).
- Not covered: `r5_mapsetupContractsLiveInPlatformMapsetup` names `api.mapsetup..` only. `JsonCupStore` depends on `api.cupsetup`,
  so the rule does not reach it, and `JsonDraftStore` sits in `platform.catalog` for the same reason. No new rule is proposed; if
  a freeze store needs an entry, the apply stops and reports, as `add-catalogue-keys-and-authors` requires.

## Testing

Every test follows F.I.R.S.T.:

- **Fast and Repeatable:** the store tests write into `@TempDir` and never read the shipped `cups/` or `maps/`; the maps the
  tests need are written by the test. The editor tests are plain JUnit with no I/O.
- **Independent:** each test builds its own `JsonCupStore` and `CupCommands` over a fresh `@TempDir` data directory. No static
  state, no shared catalogue.
- **Self-validating:** assertions name the file bytes, the reply, or the exception's keys, never a log line.
- **Timely:** each behaviour starts with a failing test (Red/Green TDD).
- **Pyramid:** `CupEditorTest` and `CupIdTest` are unit tests; `JsonCupStoreTest` is the adapter test; `CupCommandsTest` drives
  the commands like `SetupCommandsTest` does; `SetupPermissionTest` covers each `/cup` subcommand.
- The save guard is tested as change 5 tests its draft guard: two `JsonCupStore` instances over one `@TempDir` play two
  setup servers, and the second save is refused with the file keeping the first writer's bytes.
- Tests named in `tasks.md` cover: a round trip through the game's cup adapter; a cup naming a draft map refused; move and
  remove renumber; the last map not removable; the guard refusal.

## Open questions

Each question is for the owner. The recommendation is the option in the decision text above, or the one stated here.

1. **Claim on a cup (decision 10).** The brief wants a cup id held in the claim registry while it is open. Option A (recommended):
   add `/cup open <id>` and `/cup close`, with the same claim semantics as `/map open` and `/map close`, so the claim requirement
   takes effect. Option B: keep per-command edits and drop the claim requirement. Pro of A: the registry is used as the brief
   intends. Con of A: two more commands to document and gate. Option A also needs `BuilderSessions` (change 5's class, which
   owns the claim lifecycle) to hold a builder's open cup beside the open map, so that a disconnect releases both; that change
   to change 5's design is part of the owner's decision.
2. **Notes of the shipped cups (decision 9).** Option A (recommended): accept the loss of `notes` in a setup save, and keep the
   notes of shipped cups in version control, editing them by hand. Option B: carry `notes` through `CupDefinition`, an API change
   that grows this change. Pro of A: no API change. Con of A: an edit made in the setup server drops the notes until the owner
   restores them.
3. **Undo for cups.** Option A (recommended): none in the first version; each command saves. Option B: a history as for maps in
   `undo-draft-edits`. Pro of A: smaller. Con of A: a mistyped position needs a second command to fix.
4. **Authors on a cup.** Option A (recommended): a later change adds `/cup author`, as `map-name-and-authors` does for maps. Option B:
   include it now. Pro of A: the cup commands ship first.
5. **A map twice in one cup.** Option A (recommended): allowed, because `CupDefinition` allows it. Option B: refused. Pro of A: a
   rotation may play one map twice on purpose. Con of A: the statistics count both plays.
