# Proposal

**Type and scope:** `feat(race)`. Ships as one code change under this title. It adds catalogue data and rendering; it
does not change scoring, flight or the lobby layout.

## Why

Players see internal ids. The boss bar, the map banner in chat, the lobby sidebar, the lobby hologram and the results
show the catalogue id, so the shipped cup reads `alpha_cup` and the map reads `elytraraceblueandred`, and the legacy
cup reads `legacy_cup` with maps named `palmbeach`, `dragonlobby` and `memoria`. The names players know are written
only in the notes of the catalogue files, where no code reads them. Today the notes say "The rebuild's CupDefinition
has no display-name field, so the name is recorded here until a schema change adds one." This change is that schema
change.

## What Changes

- Cups and maps get an optional `displayName` string in the catalogue JSON. When it is absent, the id is shown, as
  today.
- The cup and map schemas (`cup.schema.json`, `map.schema.json`) describe the field. The api records `CupDefinition`
  and `MapDefinition` carry it, and the catalogue adapters read it.
- Every player-facing place that shows a cup or map shows its display name instead of its id: the boss bar, the map
  banner in chat, the lobby sidebar, the lobby hologram and the results.
- The shipped catalogue is seeded: `alpha_cup` is "Alpha Cup", the map `elytraraceblueandred` is "Blue & Red", the
  legacy cup `legacy_cup` is "Testing Cup", and its maps are "PalmBeach", "DragonLobby" and "Memoria". The names come
  from the notes already in those files, with the MiniMessage gradient and rainbow tags removed.
- Display names are proper nouns. They are not translated, so a German and an English player see the same name.
- Operator and log output keeps the id, so an operator can find the file. (Console output stays English, see
  `player-messages`.)

## Capabilities

### New Capabilities

- `catalog/display-names`: the optional player-facing name of a cup or map, its fallback to the id, the rule that it is
  shown as plain text and not translated, the surfaces that show it, and the seeded names of the shipped catalogue.

### Modified Capabilities

- `catalog/file-schema`: the requirement "The schema accepts exactly the fields the adapters read" gains `displayName`
  as an optional field of cups and maps. The schema still describes the current version only.

The lobby, boss bar, banner and results requirements in other specs say "cup name" or "map name". Under
`catalog/display-names` those words mean the display name, so no other requirement is edited by this change. The lobby
change (`cygnus-style-lobby`) and the race HUD change (`polish-race-hud`) add their own surfaces on top of this rule.

## Out of scope

- Translating names, or a German name for a map. Names are proper nouns.
- A display name for the catalogue's internal `name` used in the operator commands and in file names.
- Renaming any file or id. The id stays the key of the catalogue and of the golden files.
- Setup-server output (`setup/*`), which shows the map name as the operator types it.

## Impact

- `voyager/platform` schemas `cup.schema.json` and `map.schema.json`, and the catalogue adapters
  `platform/catalog/adapter/CupDefinitionAdapter` and `MapDefinitionAdapter`; the `voyager-api` records
  `CupDefinition` and `MapDefinition` (`voyager/api/.../api/race/`).
- Every construction site of the two records in tests (the design lists the two shared fixtures).
- Shipped catalogue files under `voyager/server/src/main/resources/cups/` and `maps/`, including the note at
  `cups/alpha_cup.json:10` that records the name until a schema change adds the field.
- Golden masters in `voyager/server/src/test/resources/golden/cup-session/`: none change. The fixtures use synthetic
  ids with no display name, which fall back to the id. The design explains it.
