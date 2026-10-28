# Design

## Context

The catalogue names a cup and a map by a flat string. The records carry it as `name()`:
`CupDefinition(String name, List<String> mapNames, GameMode mode, String displayName)`
(`voyager/api/.../api/race/CupDefinition.java`) and `MapDefinition(String name, String world, ...)`
(`voyager/api/.../api/race/MapDefinition.java`). The name is used in four roles at once:

1. the key of the catalogue (`CatalogDirectory` keys each definition by `nameOf`, and rejects two files with one name);
2. the reference from a cup to its maps (`mapNames`, resolved by `CatalogConsistency`);
3. the operator's handle (`VOYAGER_CUP`, `/leaderboard MAP`, the `UnresolvedCupException` hint);
4. the statistics key (`map_key` and `cup_key` in `voyager/persistence/src/main/resources/db/migration/V1__baseline.sql`,
   lines 38, 50 and 146; `RaceCompletions.of(map.name(), ...)` in `CupSession`).

The layout version (`LayoutVersion`) hashes the geometry only (spawn and rings, see `MapDefinitionCanonicalWriter`), so
a rename changes no layout and resets no leaderboard, as long as the geometry is unchanged.

The setup server writes the catalogue. `MapDraft` (`voyager/api/.../api/mapsetup/MapDraft.java`) carries the id as
`MapId`, a value object for a folder name (`[a-z0-9][a-z0-9_-]{0,31}`). `MapDraftJsonWriter` writes only the draft's
fields, so a save of a loaded map drops `displayName`, which `MapDraft` does not carry. `JsonDraftStore.save` writes the
game-loadable file through that writer.

Versioning: `SchemaVersion.CURRENT = 1` (`voyager/platform/.../catalog/adapter/SchemaVersion.java`). The schemas cap
`schemaVersion` at 1 (`openspec/specs/catalog/file-schema`), and a later version gets a new schema file name.

## Decisions

1. **The identifier is an Adventure `Key` in the namespace `elytrarace`.** The API records carry `Key key` in place of
   `String name`. The namespace is fixed to `elytrarace`; the value is `[a-z0-9][a-z0-9_-]{0,31}`. A value is written in
   the file as the full string, `"key": "elytrarace:blue_and_red"`. Decision record: ADR-0040 below.

2. **Schema version 2 replaces `name` with `key`.** A version 2 file has `"schemaVersion": 2` and `"key"`. The adapters
   read both versions. Version 1 (absent or `1`) reads the flat `name` and makes the key `elytrarace:<name>`. The name must
   already be a valid value, so a version 1 name with capitals or spaces is refused with the file and field named. Every
   shipped file that loads today has a valid value, so none is refused. Hence "a file that loads today keeps loading".

3. **The default world follows the file's version.** A version 1 map without `world` keeps its name as its world (the
   rule already in `map-file-format`). A version 2 map without `world` takes the key's value. The migration states `world`
   explicitly in every rewritten map, so no shipped map depends on a default.

4. **Authors are optional data on maps and cups.** `"authors": [{"uuid": "<8-4-4-4-12 hex>", "name": "<name>"}]`. The
   UUID is parsed to a `java.util.UUID`, so it is compared by value and written in lower case. The name is
   `[A-Za-z0-9_]{1,16}`, the Minecraft username shape, shown as written. The list has at most eight entries and no UUID
   twice. The name is not checked against Mojang, because a load does no I/O. Authors are data, so they are not MiniMessage
   and not translated; the display change decides how they are shown.

5. **Operator inputs accept both forms until the owner removes the flat form.** `VOYAGER_CUP=alpha_cup` and
   `VOYAGER_CUP=elytrarace:alpha_cup` name the same cup, and `/leaderboard blue_and_red` and
   `/leaderboard elytrarace:blue_and_red` name the same map. A flat argument is read as `elytrarace:<flat>`. Operator and
   log output prints the key. The setting is read through the avaje-config loader (ADR-0036), not `System.getProperty`.

6. **The setup server keeps what it loads.** `MapDraft` gains `displayName` and `authors`. `MapDraftAdapter` reads them,
   and `MapDraftJsonWriter` writes them when set. The draft's key is `elytrarace:<MapId.value()>`, computed in one place.
   A save of a map the setup server loaded therefore changes no field the builder did not touch. This is the fix for
   the playtest's dropped name, as far as the code can show. Setup commands that edit the new fields are open question 3.

7. **Statistics follow the key, with a migration of the rows.** A Flyway migration `V2__catalogue_keys.sql` rewrites
   `map_key` and `cup_key` from the flat id to the key using the migration table below, in every table that holds them.
   The migration is one transaction. After it, the writer stores the key, and the reader accepts a key or a flat id.
   `layout_version` is unchanged.

8. **The API keeps Adventure to the key package.** `voyager-api` gets `net.kyori:adventure-key` (already resolved
   through `adventure-api` 5.2.0, the version the platform uses). A new `ApiPurityTest` rule forbids `net.kyori..`
   classes in `net.elytrarace.voyager.api..` except `net.kyori.adventure.key..`. The rule is new and has to hold on its
   own, so it gets a seeded violation in `ApiPurityRulesSeededTest` (the `allowEmptyShould(false)` discipline).

## ADR-0040 (proposed): Catalogue identifiers are Adventure Keys

Status: **Proposed**. It is accepted only when the owner accepts it. The text below moves to
`docs/decisions/0040-catalogue-keys.md` in the code pull request, with the status it then has.

Number: 0040 is the next free number. `docs/decisions/` on `origin/main` ends at 0039.

### Context and problem statement

A map and a cup are identified by a flat string that is also the name operators type, the key of the statistics and
the reference between a cup and its maps. A flat string cannot say which namespace it belongs to, so it cannot be
told apart from another source of the same name. The owner decided that a map and a cup are identified by an Adventure
`Key` with the namespace `elytrarace`, and that the key is what players, operators and the statistics see.

### Decision drivers

- One identifier format for the whole game, shared with Adventure's own keys: the tutorial dialogs already use
  `net.kyori.adventure.key.Key` (`TutorialPages.CLOSE_KEY`).
- The API module stays free of platform and of text libraries (`ApiPurityTest`, ADR-0017).
- No file that loads today stops loading.
- Statistics history is kept.

### Considered options

1. **Adventure `Key` in `voyager-api`** (chosen). The records use `net.kyori.adventure.key.Key` directly.
2. **An own record `CatalogKey(namespace, value)` in `voyager-api`**, with the same string form, converted to an
   Adventure `Key` in the platform.
3. **Keep a flat `String` and prefix it in the platform**, with no type in the domain.

### Decision outcome

Option 1. The key is a value type from a library that is already on the classpath of every module that needs it, and it
is the type Adventure's APIs take for translation and dialog identifiers. Option 2 keeps the API free of the library,
but it duplicates the validation Adventure already does and needs a converter in every place a key reaches Adventure.
Option 3 keeps the domain string-typed, which is the problem this change exists to remove.

### Consequences

- Good: a key is a single value with one string form, and the same type is used in the API, the platform and the
  translation layer.
- Good: a flat id from before this change maps to a key by one rule, `elytrarace:<id>`.
- Bad: `voyager-api` gains a library dependency. It is a single class package of Adventure, so the cost is small, and the
  fitness rule keeps it at that.
- Bad: the file format changes, and statistics rows are rewritten once. Both are done by the migration in this change.
- Neutral: operators type the flat form during the transition. The owner decides when it is removed.

### Confirmation

`ApiPurityTest` gains the rule for `net.kyori..`, with a seeded violation. `CatalogLoaderTest` and the setup writer tests
read the migrated shipped catalogue and a version 1 file, and assert the key of each.

## Migration

### Shipped catalogue

The owner approved this table (keys and authors). The world of each map is written explicitly, so the rename does not move
a world folder. Author UUIDs are the dashed form of the public Mojang profile lookup
`GET https://api.mojang.com/users/profiles/minecraft/<name>`, resolved on 2026-10-28.

| Current file and id | Key | `world` (explicit, unchanged) | Authors |
|---|---|---|---|
| `cups/alpha_cup.json`, `alpha_cup` | `elytrarace:alpha_cup` | — | none |
| `cups/legacy_cup.json`, `legacy_cup` | `elytrarace:legacy_cup` | — | none |
| `maps/elytraraceblueandred.json`, `elytraraceblueandred` | `elytrarace:blue_and_red` | `ElytraraceBlueAndRed` | TheMeinerLP, `05bf52c6-7bb0-4f13-8951-0e1fd803df35` |
| `maps/palmbeach.json`, `palmbeach` | `elytrarace:palm_beach` | `PalmBeach` | Seelenretterin, `e1dff87a-1059-47f1-bc7d-533f072bf8c7` |
| `maps/dragonlobby.json`, `dragonlobby` | `elytrarace:dragon_lobby` | `DragonLobby` | Seelenretterin, `e1dff87a-1059-47f1-bc7d-533f072bf8c7` |
| `maps/memoria.json`, `memoria` | `elytrarace:memoria` (unchanged) | `Memoria` | Seelenretterin, `e1dff87a-1059-47f1-bc7d-533f072bf8c7` |

Steps, each its own commit under `feat(catalog)` or `docs`:

1. Rewrite each shipped file to `"schemaVersion": 2`, `"key"`, the explicit `world`, and the `$schema` of the version 2
   schema. The file name becomes the value (`blue_and_red.json`, `palm_beach.json`, `dragon_lobby.json`; `memoria.json` and
   the two cup files keep their names). `displayName` stays as it is.
2. Rewrite the `mapNames` of `alpha_cup` and `legacy_cup` to the keys.
3. Add the `authors` of the table to the map files. Cups have no authors in the shipped data.
4. `CatalogLoader` boot check: the shipped catalogue loads with no problem, and `CatalogConsistency` finds each `mapNames`
   entry.

### Statistics

`V2__catalogue_keys.sql` maps each flat value in `map_key` and `cup_key` to its key, with the mapping above, in the
tables that hold them (`V1__baseline.sql` lines 38, 50 and 146). Rows with an id that is not in the mapping are left
alone, and the migration logs nothing for them, because a deployed database may hold maps that were removed from the
catalogue. Open question 4 asks whether any production database holds rows that matter.

### Config golden masters

The 14 files under `voyager/server/src/test/resources/config-golden/expected/` that print a cup or map id change in the
key form (`elytrarace:alpha_cup`), and `run-matrix.sh` is updated with them. Each change is listed in the PR body.

## Architecture

| Type or change | Module | Package | Note |
|---|---|---|---|
| `Key key` on `CupDefinition`, `MapDefinition`, `MapDraft` | `voyager-api` | `net.elytrarace.voyager.api.race`, `…api.mapsetup` | replaces `name` as the identifier; `name()` is kept as a deprecated accessor for one change only if a caller needs it |
| `Author` record (`UUID uuid`, `String name`) | `voyager-api` | `net.elytrarace.voyager.api.race` | compact constructor checks the name shape |
| Version 2 adapters and key rules | `voyager-platform` | `…platform.catalog.adapter` | reads both versions |
| Writer of version 2 and authors | `voyager-platform` | `…platform.catalog.writer` | keeps `displayName` and `authors` |
| `V2__catalogue_keys.sql` | `voyager-persistence` | `db/migration` | Flyway |
| Operator input parsing | `voyager-platform` (catalog) and `voyager-server` (`ServerSettings`) | — | flat or key; the server reads it through the loader |

Dependency direction: `voyager-api` ← `voyager-platform` ← `voyager-server` and `voyager-setup`. The API does not depend on
the platform. The new `Author` and `Key` types are API types, so no platform type is introduced.

### Fitness rules

- New: `ApiPurityTest`, Adventure only through `net.kyori.adventure.key..` in the API (decision 8).
- Existing and unchanged: `ApiPurityTest` (no Minestom, no Paper, no DI, no file I/O in the API); `PersistenceBoundaryTest`
  (the migration sits in `persistence`); `NullabilityConventionTest` (every new package gets `package-info.java` with
  `@NotNullByDefault`).
- The ArchUnit store is not changed. If a new rule needs a store entry, the apply stops and reports.

## Open questions

These are not settled by the code and the owner decides them before the code PR is opened.

1. **Shipped key values. Resolved by the owner:** `blue_and_red`, `palm_beach` and `dragon_lobby`; `memoria`, `alpha_cup`
   and `legacy_cup` stay unchanged.
2. **Authors of the shipped maps. Resolved by the owner:** TheMeinerLP for `blue_and_red`; Seelenretterin for `palm_beach`,
   `dragon_lobby` and `memoria`. The UUIDs are in the migration table. The cups have no authors.
3. **Setup commands for the new fields.** The setup server can keep and write `displayName` and `authors`, but nothing in
   the setup commands sets them. Recommendation: a follow-up change adds `/map name` and `/map author`. Including them
   now grows this change by a command set and two bundle parity sets.
4. **Production statistics.** Does any deployed database hold `map_key` or `cup_key` rows from the stats writer? If
   none does, the migration is a no-op there and the owner accepts the key form at once.
5. **Flat form removal.** When are flat ids refused at the operator inputs (`VOYAGER_CUP`, the statistics commands)? The
   proposal keeps them until the owner says otherwise.
6. **Multiple authors.** Is eight the right limit? A cup of a collaborative build may have more.
7. **Version 1 names with capitals.** No shipped file has one. Refusing them is the rule; confirm that no operator copy
   of the catalogue has one.
