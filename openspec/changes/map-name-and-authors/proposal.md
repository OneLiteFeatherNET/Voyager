# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under this title. It adds three commands to the setup server's `/map` root: a display name, the authors, and the reference time. It does not change the game server, how a race is played or scored, or how a name is shown to a player.

## Why

A builder who finishes a map has no way to name it or credit anyone in the setup server. The catalogue already carries a display name and authors (`add-catalogue-keys-and-authors`, merged as code in #363), and the setup server keeps them when it saves a map it loaded. Nothing in the setup server sets them, so a map can only be named or credited by editing its JSON file by hand. The reference time, the number of seconds a map's reference lap is measured against, is also fixed at the provisional default of 60 seconds, because no command sets it.

Today a save drops the display name and the authors on every ring edit or spawn set, because `DraftEditor.with` builds the draft through the constructor that resets both (`voyager/setup/.../mapsetup/DraftEditor.java:76-79`). That defect is fixed by `keep-draft-metadata-on-edit` (change 6), which must land first: a name set by this change would otherwise be lost by the next ring the builder places.

## What Changes

- `/map name <MiniMessage>` sets the map's display name. `/map name clear` removes it. A name is parsed with MiniMessage and may use only the colour, rainbow, decoration and `reset` tags of the display-name rule; any other tag, and a parse error, is refused with its position. Its visible text, the characters a player sees with the tags not counted, has at most 40 characters.
- `/map author add <name>` and `/map author remove <name>` edit the map's authors, at most eight, each named by a Minecraft username and identified by its UUID. An author who is online is taken from the server. An author who is not online is looked up through the public Mojang profile service, off the tick thread and with a timeout. A failed lookup changes nothing.
- `/map time <seconds>` sets the reference time: a finite decimal above zero, at most 3600 seconds.
- Each command saves the draft before it replies, as every other `/map` command does. A failed save keeps the previous draft.
- The player-facing messages of the new commands are in `voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties`.

## Capabilities

### New Capabilities

- `setup/map-metadata`: the display name, the authors and the reference time that a builder sets with `/map name`, `/map author` and `/map time`, their limits, the order in which an author is resolved, and the rule that these edits keep the rest of the draft.

### Modified Capabilities

None. The `/map` command set grows, but the existing `setup/map-commands` requirements still hold unchanged, and the draft format is unchanged by this change (change 6 and `add-catalogue-keys-and-authors` own the format).

## Out of scope

- Undo and redo of these edits. They become undoable when `undo-draft-edits` (change 4) lands, because every edit goes through `MapSession.commit`; this change does not build the history.
- Claims on a map. `single-builder-map-claims` (change 5) decides who may hold a map; these commands act only on the map the builder has open.
- Cup names and modes. They are change 8 (`cup-authoring`). Cups carry no authors at all (decided in change 8); this change adds authors to maps only.
- How a name is rendered in the game. That is `show-colored-map-and-cup-names`. This change stores the MiniMessage source as written, and the game renders it as that change defines.
- A cache of resolved authors, and checking an existing author against the profile service. Each `add` makes one lookup.
- Editing an author's UUID by hand.

## Impact

- Vertical slices: `setup` (the new subcommands, a pure metadata editor and parsers, the messages), `api` (a `PlayerLookup` port and its exception under `net.elytrarace.voyager.api.mapsetup`, and the 40-character visible limit as a constant), `platform` (the profile-service adapter in a new `profile` package, using the JDK HTTP client). The `race` slice is not touched.
- `voyager/setup`: `SetupCommands` gets four subcommands; `SetupMessages` and both bundles get the new keys.
- `voyager/server`: none. The game server reads the same files through the same adapters, so a name or author set here is read by the game without a change there.
- Docs: `docs/guides/how-to-build-a-map-with-setup.md` gets a section "Name and credit the map"; `docs/reference/map-and-cup-files.md` names the setup commands that write the fields.
- Operators: a builder with `voyager.setup.use` can use the new commands. No permission node is added.
- Depends on `keep-draft-metadata-on-edit` (change 6), which must land first, and on `show-colored-map-and-cup-names`, which sets the visible-length rule the catalogue reader applies. The code of this change ships after that change. Uses change 4 and change 5 without requiring them.
