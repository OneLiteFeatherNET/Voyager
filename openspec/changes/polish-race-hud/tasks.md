# Tasks

Commit type and scope: `feat(race)` for the platform text and HUD code, the bundles and their tests. `docs` for the reference
pages. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: the
translator is built per test, colours are read from `Palette` (no shared mutable state), and no test reads the clock. Task
checkboxes are ticked in the follow-up PR, never in the code PR.

## 1. Red: the contrast, colour, fill and text rules

- [ ] 1.1 Red: `PaletteContrastTest` asserts that each colour the HUD uses (`VALUE`, `OK`, `DIM`, the four medal colours, the warn colour) reads at 4.5 to 1 or more on `#101010` and on `#1E1E1E`, naming the colour that fails (`feat(race)`)
- [ ] 1.2 Red: `RaceHudBossBarTest` asserts that the boss bar colour is the same for every `MedalTier`, named "the boss bar colour does not depend on the medal" (`feat(race)`)
- [ ] 1.3 Red: `RaceHudFillTest` asserts that `fill(12, 35)` is 12 divided by 35, and that the fill is 0 with no ring passed (`feat(race)`)
- [ ] 1.4 Red: `MessagesBossBarTest` renders the title through the translator for `en_US` and `de_DE`, and asserts "Diamond · 57.9 s left" and "Diamant · noch 57.9 s" (`feat(race)`)
- [ ] 1.5 Red: `MessagesBossBarTest` asserts that the finish tier shows "Finish" in English and "Ziel" in German with no seconds (`feat(race)`)
- [ ] 1.6 Red: `MessagesFlightHudTest` asserts "Time 0:02.1 · Rings 0/35" for `en_US` and "Zeit 0:02.1 · Ringe 0/35" for `de_DE`, and that the ring count is the OK colour when a ring was just passed (`feat(race)`)
- [ ] 1.7 Red: a test "the boss bar names the map by its display name" for "elytraraceblueandred" as map 1 of 2 (depends on `add-display-names`; mark it skipped with a reason until that change merges) (`feat(race)`)
- [ ] 1.8 Red: confirm 1.1 to 1.7 fail for the stated reason, and record the messages in the PR

## 2. Green: the palette, the bar and the text

- [ ] 2.1 Green: replace `Palette.bossBarColour(tier)` by one fixed colour, `BossBar.Color.GREEN`, for every tier; update `RaceHud.updateBar` to use it (`feat(race)`)
- [ ] 2.2 Green: add the keys `voyager.hud.tier.{diamond,gold,silver,bronze,finish,dnf}`, `voyager.hud.medal.left` and `voyager.hud.medal.last` to `voyager_en_US.properties` and `voyager_de_DE.properties`, with the values in the design's key table (`feat(race)`)
- [ ] 2.3 Green: rewrite `voyager.hud.flight` and `voyager.hud.bossbar` (and `.last`) in both bundles to the templates in the design, keeping the four arguments of the flight line and the placeholder set of each key (`feat(race)`)
- [ ] 2.4 Green: make `Messages.bossBarTitle` build the medal phrase as one translatable component coloured with the tier's palette colour, and keep the results' `voyager.medal.*` keys unchanged (`feat(race)`)
- [ ] 2.5 Green: make `Messages.flightHud` keep its key and four arguments; the clock stays plain text and its colour comes from the template tag (`feat(race)`)
- [ ] 2.6 Refactor: remove the `medal(tier)` capitals form from the boss bar path only, and confirm nothing else changes (`feat(race)`)

## 3. Parity and golden masters

- [ ] 3.1 Run `MessageBundleParityTest` and confirm both bundles have the same key set and placeholders for every new key, and record in the PR that no key was missing before this change (the 91 and 104 figures are raw line counts) (`feat(race)`)
- [ ] 3.2 Run `CupSessionGoldenMasterTest` and confirm every golden transcript is byte-identical (`feat(race)`)

## 4. Checks

- [ ] 4.1 Grep the platform for any other caller of `Palette.bossBarColour` and of the capitals `medal(tier)` on the boss bar, and record the result (`feat(race)`)
- [ ] 4.2 Run `./gradlew :voyager:fitness:test` and `./gradlew build` on both trees (`feat(race)`)

## 5. Client check

- [ ] 5.1 Start the dev server, fly one map in `en_US` and one in `de_DE` (or switch the client locale), and take one screenshot of the boss bar and one of the action bar; attach both to the code PR and confirm the title layout assumed in the design (`feat(race)`)

## 6. Docs

- [ ] 6.1 Update the boss bar and action bar rows in `docs/reference/hud.md` to the new labels and colours (`docs`)
- [ ] 6.2 Update the colour table and the "medal words" rule in `docs/reference/message-style-guide.md` to say that the boss bar names the tier in words and that results keep the caps form (`docs`)

## 7. Ship

- [ ] 7.1 Open the code pull request under the title `feat(race): label the race boss bar and action bar and fix their contrast`. Put the screenshots (5.1) and the golden result (3.2) in the description, and write it as the racer sees the change. Do not add a session link (`feat(race)`)
