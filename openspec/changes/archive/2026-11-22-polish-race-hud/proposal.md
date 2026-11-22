# Proposal

**Type and scope:** `feat(race)`. Ships as one code change under this title. It changes the race HUD text, colours and
translations. It does not change timing, scoring, rings, rockets or the lobby.

## Why

During a map the racer's two HUD lines are the only feedback they read while flying, and both are hard to use. The boss bar
shows the medal as a word in capitals ("DIAMOND") with a time and no label saying what the time means, and it is coloured
with the medal's own colour, so the title and the bar compete. The action bar shows "0:02.1  0/35" in dim grey: the racer
cannot tell the clock from the ring count without counting, and the grey draws the eye away from the course. The racer
reads the numbers in the middle of a turn, so the lines must say what they are at a glance.

## What Changes

- **Boss bar label.** The medal is named with a translated tier name and a "left" phrase: "Diamond · 57.9 s left" in
  English and "Diamant · noch 57.9 s" in German (the number format follows ADR-0038, see the design).
- **Boss bar contrast.** The bar colour no longer follows the medal. It is one fixed colour, so the title colour is
  free to be the medal's colour. Every HUD text colour is checked against a dark backdrop at 4.5:1 or more.
- **Boss bar name.** The title shows the map display name from `add-display-names` in place of the id.
- **Boss bar fill.** The fill is the fraction of rings passed. This is already how the bar is computed; the spec now
  states it.
- **Action bar labels.** The action bar names its two values: "Time 0:02.1 · Rings 0/35" in English and "Zeit 0:02.1 ·
  Ringe 0/35" in German. The values are bright; the labels are dim but readable.
- **Translations.** Every new HUD string exists in `voyager_en_US` and `voyager_de_DE` with the same placeholders. The
  German text uses "du" tone where it addresses the racer.
- **Out of scope.** The rocket count. The owner did not pick it for this change.

## Capabilities

### New Capabilities

- `race/race-hud`: what the boss bar and the action bar of a racer in a map say, how they are labelled, the colour rules
  for their text and bar, and the translation of their labels.

### Modified Capabilities

None. `race-feedback` (the missed-ring line and the GO title), `message-style` (one accent per line, the channel rules) and
`player-messages` (translation fallback) are not changed; this change follows them.

## Out of scope

- The rocket count and any rocket HUD.
- The lobby boss bar and action bar, which `cygnus-style-lobby` replaces.
- The results text and the medal words in results (`voyager.medal.*`), which keep their caps form.
- Sounds.

## Impact

- `voyager/platform` text (`Messages.flightHud`, `Messages.bossBarTitle`, `Palette.bossBarColour`), HUD (`RaceHud`).
- Bundles `voyager_en_US.properties` and `voyager_de_DE.properties`.
- Tests in `voyager/platform` for the title, the bar colour and the action bar, and a contrast test over the palette.
- `docs/reference/hud.md` and `docs/reference/message-style-guide.md`.
- No golden master changes. The cup-session transcripts record the action bar as its translation key and its four
  arguments (`voyager.hud.flight`, for example `lobby-and-start.txt`) and record no boss bar. The labels live in the
  bundle, so the key, the argument list and the argument colours stay the same; the design states this rule.
