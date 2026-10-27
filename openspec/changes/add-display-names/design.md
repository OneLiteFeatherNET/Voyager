# Design

## Context

The catalogue identifies a cup and a map by its id. The records carry it as `name()`: `CupDefinition(String name, List<String>
mapNames, GameMode mode)` at `voyager/api/src/main/java/net/elytrarace/voyager/api/race/CupDefinition.java:8`, and
`MapDefinition(String name, String world, Vec3 spawn, ...)` at `MapDefinition.java:40-41`, with a seven-argument convenience
constructor at `:46`. The JSON adapters build them at `voyager/platform/src/main/java/net/elytrarace/voyager/platform/catalog/
adapter/CupDefinitionAdapter.java:44` and `MapDefinitionAdapter.java:74`. Both schemas set `additionalProperties: false`
(`cup.schema.json:7`, `map.schema.json:7`), so a new field is refused until the schema names it.

The name players read is written only in the notes. `MapDefinitionAdapter.java:47` says `notes` is "deliberately not read",
and `alpha_cup.json:10` records "Display name: Alpha Cup ... until a schema change adds one." This change is that schema change.

Player-facing sites that print the id today, verified at `origin/main`:

| Surface | Site | Prints |
|---|---|---|
| Race boss bar | `platform/hud/RaceHud.java:203` (`bossBarTitle(..., state.mapName())`, rendered by `Messages.bossBarTitle`) | map id |
| Lobby boss bar | `platform/lobby/WaitingRoom.java:305,325`, `LobbyBoard.java:43` (`new LobbyBoard(cup.name(), cup.mapNames())`) | cup id |
| Lobby sidebar | `LobbySidebar.java:92-94` (lines from the board) | cup title and map ids |
| Lobby hologram | `LobbyHologram.java:71-76` (text from the same board) | cup and map ids |
| Map banner in chat | `platform/cup/CupSession.java:702` (`map.name()`) | map id |
| Cup heading in chat | `platform/cup/CupAnnouncer.java:148` (`cup.name()`) | cup id |
| Map and cup results | `CupAnnouncer.java:75`, `:94`, `:99` (`map.name()`) | map id |

The race boss bar already carries the map name (`Messages.bossBarTitle` takes `mapName` and prints it as the
second part of "Map i/n · name · tier · time"). This change only swaps the id for the display name. The label and
layout of that bar are changed by `polish-race-hud`.

## Decisions

1. **The display name is part of the record.** `CupDefinition` and `MapDefinition` gain a `displayName` component, placed
   last. The compact constructor stores `name` when the given value is null or blank, so `displayName()` never returns
   null. The existing convenience constructors (`CupDefinition` with three arguments, `MapDefinition` with seven) keep
   their meaning and set the display name to the id. Test fixtures therefore compile without edits, and their output
   does not change.
2. **The loader validates the field.** A blank value means "absent". A value longer than 40 characters refuses the file,
   and the error names `displayName` and the file. The 40 keeps the map banner and the sidebar line
   readable; a longer name is a file error, not a silent truncation.
3. **The name is plain text.** It is passed to the translation layer as a `Component.text` argument, so MiniMessage does
   not read a `<` in it. The task list checks this for the banner, the heading and the results, where `Messages` already
   takes arguments (`Messages.mapBanner`, `cupHeading`, `mapResult`).
4. **No translation key.** A display name is data. The lobby lines that print it keep their translated frame text
   (for example "Cup <arg:0>"), but the name argument is never looked up.
5. **Lobby lines resolve maps by id.** `LobbyBoard.of(cup)` only sees the cup's `mapNames()` (ids). Its factory takes the
   catalogue snapshot as well, so each map id is resolved to its map's display name. A map id missing from the snapshot
   keeps the id as its label, so a board never fails.
6. **Operator output keeps the id.** Command output, the catalogue validation report and log lines keep the id. The
   cost is one more word to read in a log; the benefit is that an operator can find the file. This matches the
   `player-messages` rule that console output stays English.

## Seed values

| File | Field | Value | Source |
|---|---|---|---|
| `voyager/server/src/main/resources/cups/alpha_cup.json` | `displayName` | "Alpha Cup" | `:10` note |
| `.../maps/elytraraceblueandred.json` | `displayName` | "Blue & Red" | owner suggestion; the world is named `ElytraraceBlueAndRed` |
| `.../cups/legacy_cup.json` | `displayName` | "Testing Cup" | `:11` note, `<rainbow>Testing Cup</rainbow>` with the tag removed |
| `.../maps/palmbeach.json` | `displayName` | "PalmBeach" | `:423` note, `<rainbow>PalmBeach</rainbow>` |
| `.../maps/dragonlobby.json` | `displayName` | "DragonLobby" | `:487` note, `<gradient:#ff55ff:blue:-1>DragonLobby</gradient>` |
| `.../maps/memoria.json` | `displayName` | "Memoria" | `:439` note, `<gradient:gold:blue:yellow:-1>Memoria</gradient>` |

The note in `alpha_cup.json:10` that says the name is recorded there until the schema changes is removed in the code
PR, because it is no longer true. The other notes stay.

## Golden masters

Nothing in `voyager/server/src/test/resources/golden/cup-session/` changes. The golden fixtures build their cups and maps
in the test code with synthetic ids (`grand_tour`, `ridge-run`, `checkpoint_cup`, `com_tour`) and the three-argument
constructors, so their display name equals their id and every line stays byte for byte the same. The config golden
files under `voyager/server/src/test/resources/config-golden/expected/` print the shipped ids as operator output, which
Decision 6 keeps. A test for the new behaviour therefore asserts the display name directly, not through a golden file.

If a later change gives a golden fixture a display name, that change regenerates only the lines that print it.

## Architecture

| Type or change | Module | Package | Note |
|---|---|---|---|
| `CupDefinition.displayName`, `MapDefinition.displayName` | `voyager-api` | `api.race` | pure record component; no implementation |
| schema fields | `voyager-platform` resources | `schema/` | |
| adapters read the field | `voyager-platform` | `platform.catalog.adapter` | the refusal of a long name is in the adapter, with the file name |
| banner, heading, results | `voyager-platform` | `platform.cup` | |
| lobby board resolves map names | `voyager-platform` | `platform.lobby` | takes the snapshot |

Dependencies point inward: the api record knows nothing of Minestom or of the catalogue loader. The platform adapter
depends on the api record, never the reverse. No DI annotation is added. `voyager-fitness` is unchanged: the api
purity rules still hold because the record is pure data, and the literal-text rule (`ApiPurityTest`) is not affected,
because a display name is not a literal string in code.

## Out of scope

- Translating names, a German name for a map, or a per-locale name.
- Renaming an id. Ids stay the catalogue keys, the file names and the golden keys.
- The race boss bar's map name (`polish-race-hud`) and the lobby layout (`cygnus-style-lobby`), which read the name
  this change provides.
- Setup-server output, which shows the map name the operator typed.

## Limits recorded

- The 40-character limit is a decision of this change. A longer name is refused at load, so no player ever sees a
  truncated name.
- Decision 3 is checked by the task list (a test asserts that `<rainbow>` is shown literally), not assumed.
