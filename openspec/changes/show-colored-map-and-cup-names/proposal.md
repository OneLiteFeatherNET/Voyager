# Proposal

**Type and scope:** `feat(race)`. Ships as one code change under this title. It changes what a player reads and sees: the
map and cup names, the authors, the colours, the placement screen at the end of a cup. It does not change scoring, flight,
the start countdown or the hand-off to the hub, except that the cup's score gains the rings a racer passed.

**Depends on:** `add-catalogue-keys-and-authors` (`feat(catalog)`), which provides the key, the authors and the migrated
catalogue. This change is merged after it.

## Why

A player sees the cup and the map by their ids. The v1.17.0 playtest showed `elytraraceblueandred` at the end of every map,
and the owner found the end of a cup "not cool" and "meagre": a grey heading with the id, and one plain line per racer, then
the server hands the player on within seconds. The cup is the unit a player experiences, so the end of the cup should be the
moment that rewards it. The owner asked for more colour, the authors on the screens where a map is named, and a placement
moment for every racer.

## What Changes

- A display name is MiniMessage, restricted to colour and decoration tags. A name without a colour gets a hue from a palette
  that never includes red, and a name that claims red is refused. **BREAKING** for a file that used a tag outside the
  allowed set; no shipped file does.
- The map announce line, the finish line, the map result line, a new map intro title, the boss bar, the lobby sidebar and the
  hologram name the map in its colour. The map intro title and the lobby name the authors.
- The tutorial book gets a sixth page with the cup, its maps and their authors.
- When a cup ends, each racer gets a title for their place ("Victory!", "2nd place", "3rd place", "Place n" and the German
  forms), a sound that fits the place, and fireworks for the first three places. The winner gets the largest celebration.
- The standings heading uses the coloured cup name, and a line lists the maps. Places one to three are gold, silver and
  bronze. Each line shows points, best time and rings hit "x/total". The viewer's own line is bold with a marker.
- A map or cup without a display name is shown as its key's value in title case, never as the raw key, and each one played
  without a name is warned about once at boot.

## Capabilities

### New Capabilities

- `catalog/display-names`: the MiniMessage rule and its tag set, the forty-character limit on visible text, the red rule,
  the name palette and its fallback, the title-case fallback, and the rule that names are never translated.
- `race/map-presentation`: where a map or cup is named, in which colour, with which authors: the announce line, the finish
  and result lines, the intro title, the boss bar, the sidebar, the hologram and the tutorial page.
- `race/cup-finish`: the placement titles, their sounds, the fireworks, the timing against the results linger, the coloured
  standings, the rings hit, the highlight of the viewer's line, and what is not added to the end screen.

### Modified Capabilities

- `message-style`: a display name's colour is not counted as an accent, and red stays forbidden, also in a name.

### Related capabilities that are not changed here

- `add-display-names` (not yet archived) introduced the plain-text rule that this change replaces. The archive order is:
  `add-display-names` is archived before this change, and this change's `catalog/display-names` requirement then replaces
  its requirement "A display name is plain text and is refused when it is unusable" (see the design, Migration).
- `polish-race-hud` (shipped as #347) owns the boss bar layout. This change keeps it.
- `cygnus-style-lobby` and `refine-lobby-countdown-feedback` (not yet archived) own the lobby lines and the tutorial book. The
  sixth tutorial page is added on top of their five pages; the tutorial change is rebased on them before code starts.

## Out of scope

- A longer pause before the hand-off, a play-again item, and a per-racer breakdown on hover. The owner did not choose them.
- Showing names or authors in `/stats` and `/leaderboard`. The owner's list does not name them (open question 6 in the design).
- Translating a display name. Names are proper nouns.
- Changing the start countdown, the medal bands, the scoring, or the boss bar layout.
- Setup commands for the display name and authors. That is `add-catalogue-keys-and-authors`' open question 3.

## Impact

- `voyager/api`: `MapDefinition.displayName` is read as MiniMessage; the record's shape does not change.
- `voyager/race`: `CupScore` (`race/scoring`) and the cup standings accumulate the rings a racer passed; no scoring rule
  changes.
- `voyager/platform`: `text/Messages`, the new name parser and palette in `platform/text`, `cup/CupAnnouncer`,
  `cup/CupSession`, `hud/RaceHud`, `hud/RaceFeedback`, `lobby/LobbyBoard`, `lobby/LobbySidebar`, `lobby/LobbyHologram`,
  `tutorial/TutorialPages` and `tutorial/TutorialBook`, and a new fireworks helper and placement sounds.
- `voyager/server`: the shipped data's display names (`alpha_cup`, `blue_and_red` and the three legacy maps keep their
  names, with their colour tags removed as the rule requires); the golden transcripts that print a name. Every changed
  transcript is listed in the pull request, with the reason.
- `docs/reference/message-style-guide.md` and `docs/reference/hud.md`: the name colours, the placement titles and the new
  sixth tutorial page.
