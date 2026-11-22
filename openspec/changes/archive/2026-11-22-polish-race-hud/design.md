# Design

## Context

The two lines a racer reads in a map are built here.

- **Boss bar.** `RaceHud.updateBar` (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/hud/RaceHud.java`, around
  `:200-211`) builds the title with `Messages.bossBarTitle(mapNumber, mapCount, mapName, outlook)`, the fill with the static
  `fill(ringsPassed, ringCount)` just above it (already the ring fraction), and the colour with `Palette.bossBarColour(tier)`.
  `Messages.bossBarTitle` (`.../platform/text/Messages.java:153-170`) uses the keys `voyager.hud.bossbar` and
  `voyager.hud.bossbar.last` and renders the tier through `medal(tier)`, which is the capitals form ("DIAMOND") shared with
  the results. `Palette.bossBarColour` (`.../platform/text/Palette.java:153-160`) maps DIAMOND to `BossBar.Color.BLUE`
  (`:155`), GOLD to YELLOW, SILVER to WHITE, BRONZE to PINK, FINISH and DNF to PURPLE.
- **Action bar.** `Messages.flightHud` (`Messages.java:135-143`, key `FLIGHT_HUD` at `:41`) passes four arguments to
  `voyager.hud.flight`: the clock as plain text, the ring count coloured `Palette.OK` when a ring was just passed and
  `Palette.VALUE` otherwise, the ring total as plain text and a run of spaces as the fourth argument. `RaceHud` calls it at
  `:116`.
- **Bundles.** `voyager.hud.flight` is `<c-dim><arg:0>   <arg:1>/<arg:2>   <arg:3>` in English (`voyager_en_US.properties:21`)
  and the same template in German (`voyager_de_DE.properties:9`). `voyager.hud.bossbar` (`:24`) and `.last` (`:25`) render
  "Map i/n · name · tier · time" in dim with the time in value colour.

The owner's audit describes the boss bar title as cyan text on a BLUE bar. In the client the title is drawn above the bar
rather than on its fill, so the title sits over the scene. The design does not rely on that layout: it removes the medal
colour from the bar, and it limits every HUD text colour to one that reads at 4.5 to 1 or more on a dark backdrop. A
screenshot check in the client confirms the layout (task 6.1).

## Decisions

1. **The bar colour is fixed.** `Palette.bossBarColour(tier)` is replaced by one fixed colour, `BossBar.Color.GREEN`, for
   every tier. The bar is the ring progress; the medal is named by the title. This removes the cyan-on-blue pairing the audit
   names, without choosing a text colour that depends on the bar.
2. **The medal is named in words, and the phrase is one coloured component.** The tier name comes from new keys
   `voyager.hud.tier.<tier>` (for example "Diamond" and "Diamant"). The phrase "<tier> · <time> s left" (English) and
   "<tier> · noch <time> s" (German) is one translatable component, `voyager.hud.medal.left`, coloured with the tier's palette
   colour. The "· " separator is dim. The finish tier uses `voyager.hud.medal.last` (the name alone). Results keep
   `voyager.medal.*` in capitals; those keys are not changed.
3. **Decimal separator: `.` in both locales.** The owner's example for German reads "57,9 s". ADR-0038 (finding 4, owner
   decision D4) records that race times read "12.4 s" in both locales. This change follows D4 and writes "57.9 s" in German.
   If the owner wants a German comma, D4 changes first, and that change touches every time string, not this one.
4. **No emoji in the action bar.** The owner's example "⏱ 0:02.1 · Ringe 0/35" is not used. The vanilla default font
   coverage of the stopwatch glyph was not verified, and a missing glyph would show as a box mid-flight. The two labels
   carry the meaning in words.
5. **One accent per line.** `message-style` allows one accent tag besides the dim structure tag. The action bar's values
   are the accent (`VALUE` white, or `OK` green just after a ring); its labels and separator are dim. The boss bar's
   tier phrase is the accent; the map position and name are dim. The dim labels read at 6.13 to 1 on `#101010`, so they
   stay legible.
6. **The action bar keeps its key and its arguments.** The golden transcripts record `voyager.hud.flight` with its four
   arguments, and not the rendered text (`checkpoint-reset.txt` and `lobby-and-start.txt`). The change edits the template
   only: the clock is shown in value colour through a tag in the template (`<c-value><arg:0>`), not by colouring the
   argument, so the recorded argument stays a plain string. The golden transcripts therefore stay byte-identical.
7. **The boss bar is not in any golden transcript.** Its text can change freely; the test is a unit test on
   `Messages.bossBarTitle`.

## Colour check

Measured WCAG contrast of the palette tags the HUD uses, against the two backdrops `#101010` and `#1E1E1E`, computed in the
design step (`r = (L1 + 0.05) / (L2 + 0.05)`):

| Tag | Colour | On `#101010` | On `#1E1E1E` | Used for |
|---|---|---|---|---|
| `c-value` | `#FFFFFF` | 19.03 | 16.67 | action bar values, boss bar counter |
| `c-dim` | `#8A93A0` | 6.13 | 5.37 | labels, separators, map position |
| `m-diamond` | `#66E0E0` | 12.05 | 10.56 | boss bar tier, Diamond |
| `m-gold` | `#FFCC33` | 12.63 | 11.06 | boss bar tier, Gold |
| `m-silver` | `#CFD6DE` | 12.98 | 11.37 | boss bar tier, Silver |
| `m-bronze` | `#C87A3E` | 5.72 | 5.01 | boss bar tier, Bronze |
| `Palette.OK` | `#5BFF8F` | 14.63 | 12.82 | action bar ring count just after a ring (set in code, not a bundle tag) |
| `c-warn` (bundle tag) | `#FF3DA1` | 5.83 | 5.11 | DNF label (not in the bar while racing) |

Every tag clears 4.5 to 1 on both backdrops. The test in task 1.1 reads these colours from `Palette`, so a palette change that
drops one below 4.5 fails the build.

## Bundle keys

| Key | English | German |
|---|---|---|
| `voyager.hud.flight` | `<c-dim>Time <c-value><arg:0> <c-dim>· Rings <arg:1>/<arg:2>   <arg:3>` | `<c-dim>Zeit <c-value><arg:0> <c-dim>· Ringe <arg:1>/<arg:2>   <arg:3>` |
| `voyager.hud.bossbar` | `<c-dim>Map <arg:0>/<arg:1> · <arg:2> · </c-dim><arg:3>` | the same template |
| `voyager.hud.bossbar.last` | `<c-dim>Map <arg:0>/<arg:1> · <arg:2> · </c-dim><arg:3>` | the same template |
| `voyager.hud.medal.left` | `<arg:0> · <arg:1> s left` | `<arg:0> · noch <arg:1> s` |
| `voyager.hud.medal.last` | `<arg:0>` | `<arg:0>` |
| `voyager.hud.tier.diamond` | `Diamond` | `Diamant` |
| `voyager.hud.tier.gold` | `Gold` | `Gold` |
| `voyager.hud.tier.silver` | `Silver` | `Silber` |
| `voyager.hud.tier.bronze` | `Bronze` | `Bronze` |
| `voyager.hud.tier.finish` | `Finish` | `Ziel` |
| `voyager.hud.tier.dnf` | `DNF` | `DNF` |

Parity holds today: both bundles have 67 keys, and the same set. The "91 and 104" in the owner's note are raw line counts,
which include comments and blank lines. No key is missing from `de_DE`. The task list adds the new keys to both bundles
together, so the parity test (`MessageBundleParityTest`) keeps holding.

The `<arg:N>` placeholders stay zero-based, and the `{N}` form stays refused in every locale. A `voyager.hud.medal.left`
argument carries the time as text and the tier name as a translated component, so the German "noch" is in the German line.

## Architecture

| Type or change | Module | Package | Note |
|---|---|---|---|
| `Messages.flightHud`, `bossBarTitle` (edited) | `voyager-platform` | `platform.text` | the only class that builds HUD text |
| `Palette.bossBarColour` (replaced by one colour) | `voyager-platform` | `platform.text` | no caller outside the HUD |
| `RaceHud.updateBar` (edited) | `voyager-platform` | `platform.hud` | passes the display name from `add-display-names` |
| bundle keys | `voyager-server` resources | | parity-checked |

No type in `voyager-api` changes. No Minestom type enters a new package. The literal-text rule (`ApiPurityTest`) and the
message-fitness rules are unchanged, because the labels are bundle keys, not literals in code. The behaviour is covered by
the platform tests and the palette test; `voyager-fitness` is run to confirm the rules still hold.

## Out of scope

- The rocket count (owner decision: not picked for this change).
- The lobby boss bar and action bar (`cygnus-style-lobby`).
- The results text and the caps medal words (`voyager.medal.*`).
- Sounds and titles (`race-feedback`).

## Verification plan

- Unit tests: `Messages` rendered through `VoyagerTranslator` for `en_US` and `de_DE`, the palette contrast test, and the
  fill and colour tests on `RaceHud`.
- Golden: `CupSessionGoldenMasterTest` must pass unchanged, which proves the template-only edit (Decision 6).
- Client: one screenshot of the boss bar and the action bar on the dev server, attached to the code PR. It confirms the
  title layout assumed above.
