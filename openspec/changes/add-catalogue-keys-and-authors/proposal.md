# Proposal

**Type and scope:** `feat(catalog)`. Ships as one code change under this title. It changes the catalogue model, its
reader, the setup server's writer and the shipped data. It does not change how a race is played, scored or shown to a
player; the player-facing display is the separate change `show-colored-map-and-cup-names` (`feat(race)`), which builds on
this one.

## Why

A map and a cup are known by a flat id such as `elytraraceblueandred`. That id is the key of the catalogue, the name
operators type and the name the statistics store. Players see it too, because a display name only exists when a file
states one. A map has no author, so the game cannot say who built the course a racer is flying. Owner decision: maps and
cups are identified by an Adventure `Key` in the namespace `elytrarace` (for example `elytrarace:blue_and_red`), and they
carry their authors as a UUID and a name. This change makes that the catalogue's model and migrates what is on disk.

Playtest finding that this change does not fix on its own: the v1.17.0 chat showed `elytraraceblueandred`, which is the
map's id, not a player name. The display-name fallback printed the id, so the shipped `displayName` "Blue & Red" was not
in the file the playtest server read. The setup server's writer cannot be the only cause, but it is a confirmed one:
`MapDraftJsonWriter` writes only the draft's fields, and `MapDraft` has no `displayName`, so every save made by the setup
server drops the name from a map file. This change makes the writer keep it.

## What Changes

- A map and a cup have an identifier `elytrarace:<value>` (an Adventure `Key`). The value is `[a-z0-9][a-z0-9_-]{0,31}`,
  the same rule the setup server already applies to a map id. Any other namespace is refused.
- Schema version 2 states the identifier in a `key` field. Schema version 1 (the flat `name`) still loads. Its name becomes
  `elytrarace:<name>`, so no file that loads today is refused. **BREAKING** for the file format only: a version 2 file
  is not read by a server that predates this change.
- A map or cup may state `authors`: a list of objects with a `uuid` and a `name`. Both are optional.
- Operator inputs accept the flat id and the key during the migration: the `VOYAGER_CUP` setting, the cup and map
  arguments of operator and statistics commands. Operator output prints the key.
- The setup server writes version 2 files, keeps `displayName` and `authors` when it saves a map it loaded, and writes a
  map's key from its folder name.
- The shipped catalogue is migrated: `alpha_cup`, `legacy_cup`, and the four maps are rewritten to version 2 with their
  keys, an explicit `world` in every map, and the `mapNames` of the cups rewritten to keys (see the design's migration
  table).
- Statistics rows already written with a flat map or cup id are rewritten to the key by a Flyway migration, so the
  leaderboards and personal bests keep their history. The layout version does not change, because it hashes geometry only.
- The API module depends on `net.kyori:adventure-key` and on nothing else from Adventure. Decision record: ADR-0040,
  Proposed, in `design.md` of this change (the precedent is ADR-0039, which was written in its change's design and moved
  to `docs/decisions/` by the code pull request).

## Capabilities

### New Capabilities

- `catalog/catalogue-keys`: the identifier of a map and a cup, its namespace and value rules, the reading of version 1 and
  version 2 files, the default world of a version 2 file, the operator inputs that accept both forms, and the output that
  prints the key.
- `catalog/authors`: the optional authors of a map or cup, their UUID and name rules, and the limits on their count.

### Modified Capabilities

- `catalog/file-schema`: the schema for version 1 is kept under its own file name, and a schema for version 2 describes the
  current format; the field list gains `key` and `authors`.
- `catalog/file-versioning`: the highest version this server reads becomes 2, so the refusal example moves to version 3.
- `catalog/map-file-format`: a version 2 map without `world` takes its world from the key value, while a version 1 map
  keeps its name as its world.
- `catalog/shipped-catalogue`: the shipped cups and maps are named by their keys.
- `setup/map-draft-storage`: the draft JSON is written in version 2 and keeps `displayName` and `authors` across a save.
- `persistence/postgres-storage`: statistics are keyed by the catalogue key, and the migration of existing rows is part of
  the schema.

## Out of scope

- How a name is shown to a player, its colour, the fallback palette and the placement screens. These are in
  `show-colored-map-and-cup-names`.
- The `tools/map-converter`. It still writes version 1 files, and version 1 files still load.
- New setup commands for editing a display name or authors. The setup server keeps and writes the fields, but a builder
  sets them by editing the file until the owner asks for commands (open question 3 in the design).
- Checking an author's UUID or name against the Mojang profile service. The catalogue load does no network I/O.
- Renaming world folders. A world keeps the folder name it has, because the map states it.

## Impact

- `voyager/api`: `CupDefinition` and `MapDefinition` carry a `key` (an Adventure `Key`) and `authors`; `MapDraft` carries
  the same fields. New dependency `net.kyori:adventure-key`. New fitness rule in `voyager/fitness`: the API module may use
  Adventure only through `net.kyori.adventure.key`.
- `voyager/platform`: the catalogue adapters (`CupDefinitionAdapter`, `MapDefinitionAdapter`, `MapDraftAdapter`), the
  schemas, `MapDraftJsonWriter`, `JsonDraftStore`, and `CatalogConsistency`, which resolves `mapNames` against keys.
- `voyager/server`: the shipped data under `src/main/resources/{cups,maps}/`, the `VOYAGER_CUP` read, and the golden masters
  that print a cup or map id (the design lists them; they change only in the key form).
- `voyager/persistence`: a Flyway migration that rewrites `map_key` and `cup_key` (`V2__…sql`).
- `voyager/setup`: the draft store and the map commands, which write version 2.
- Operators: the file format changes. A deployment that copies the shipped data gets version 2 files, and an existing
  flat-id deployment keeps loading until it is updated.
