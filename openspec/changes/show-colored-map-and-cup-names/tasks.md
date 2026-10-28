# Tasks

Commit types and scope: `feat(race)` for the platform, race and api code, its tests, the palette and the fireworks; `feat(catalog)`
for the display-name parsing in the catalogue adapters; `docs` for `docs/` pages; `test` for test-only commits. Each behaviour gets
a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleep (the finale is driven by
`tick()` calls), an injected clock where a time is read, a fresh instance per test, no shared static palette state. This change
starts only after `add-catalogue-keys-and-authors` is merged, and after the owner answers the open questions in `design.md`. Task
checkboxes are ticked in the follow-up pull request, never in the code pull request.

## 1. Red: the name rules

- [ ] 1.1 Red: `DisplayNameTest` accepts `<blue>Blue</blue> & <gold>Gold</gold>` and refuses `<click:run_command:/stop>Alpha</click>`, naming the tag `click` (`feat(catalog)`)
- [ ] 1.2 Red: `DisplayNameTest` refuses `<red>Red</red>`, `<#e0201a>Rush</#e0201a>` and a gradient with a red stop; it accepts `<#ff8c42>Orange</#ff8c42>` (`feat(catalog)`)
- [ ] 1.3 Red: `DisplayNameTest` measures `<gradient:#00c2ff:#5bff8f>Blue and Red Rush</gradient>` as seventeen visible characters and refuses forty-one (`feat(catalog)`)
- [ ] 1.4 Red: `DisplayNameTest` shows `a < b` as the literal text (`feat(catalog)`)
- [ ] 1.5 Red: `NamePaletteTest` gives the same key the same hue twice, and no hue of the palette is red or a role colour of `Palette` (`feat(race)`)
- [ ] 1.6 Red: `MapDefinitionTest` and `CupDefinitionTest`: an absent name is the key's value in title case, "Dragon Lobby" for `elytrarace:dragon_lobby` (`feat(race)`)
- [ ] 1.7 Red: adapter test with `@TempDir`: a map file with the tag `click` is refused, and a map file with `displayName` of forty-one visible characters is refused (`feat(catalog)`)
- [ ] 1.8 Red: boot log test: a played map without a name logs one warning naming its key, through a captured appender (`feat(race)`)
- [ ] 1.9 Red: confirm 1.1 to 1.8 fail for the stated reason, and record the messages in the pull request

## 2. Green: the name rules

- [ ] 2.1 Green: `DisplayName` (parse, tag whitelist, visible length, red test) and `NamePalette` in `platform/text` with their `package-info.java` (`feat(catalog)`)
- [ ] 2.2 Green: `JsonFields.displayName` uses `DisplayName` and refuses by the new rules, with the file and the field named (`feat(catalog)`)
- [ ] 2.3 Green: the title-case fallback in `MapDefinition` and `CupDefinition` compact constructors, replacing the id fallback (`feat(race)`)
- [ ] 2.4 Green: the boot warning for each played map or cup without a name, in `CatalogLoader` or the boot check (`feat(race)`)
- [ ] 2.5 Refactor: the display-name rule message in `catalog/display-names` and its fixtures (`feat(race)`)

## 3. Red: the map surfaces

- [ ] 3.1 Red: `MessagesTest`: the announce line for `elytrarace:blue_and_red` names "Blue & Red" in its colour and contains no key (`feat(race)`)
- [ ] 3.2 Red: `MessagesTest`: the finish line "Finished Blue & Red in 1:44.3", and the result line, contain no enumeration name and use the mixed-case medal word in English and German (`feat(race)`)
- [ ] 3.3 Red: `CupAnnouncerTest`: the intro title carries the name, the subtitle "by Alice, Bob" in English and "von Alice, Bob" in German, and no subtitle without authors (`feat(race)`)
- [ ] 3.4 Red: `RaceHudTest`: the boss bar reads "Map 1/1 · Blue & Red · Gold · 12 s left" with the name coloured and the rest unchanged (`feat(race)`)
- [ ] 3.5 Red: `LobbySidebarTest`: the title "Alpha Cup" in its colour, the line "1. Blue & Red · by Alice +1", and at most fifteen lines for twelve maps (`feat(race)`)
- [ ] 3.6 Red: `TutorialPagesTest`: six pages, the sixth names the cup, its maps and their authors; pages one to five unchanged (`feat(race)`)
- [ ] 3.7 Red: confirm 3.1 to 3.6 fail for the stated reason, and record the messages in the pull request

## 4. Green: the map surfaces

- [ ] 4.1 Green: `Messages` takes the parsed name components for the announce line, finish, result, DNF, boss bar, sidebar, hologram and tutorial; the medal word uses its mixed-case translation (`feat(race)`)
- [ ] 4.2 Green: the intro title in `RaceFeedback` and `CupSession`, replacing the countdown subtitle (`feat(race)`)
- [ ] 4.3 Green: the sixth tutorial page in `TutorialPages` and `TutorialBook`, with `COUNT` at 6 (`feat(race)`)
- [ ] 4.4 Green: the sidebar author rule and the forty-character line rule in `LobbyBoard` (`feat(race)`)
- [ ] 4.5 Green: the German and English bundle keys, each line checked by the parity test (`feat(race)`)

## 5. Red: the cup finale

- [ ] 5.1 Red: spike, not production code: a Minestom test that a firework rocket entity can carry an explosion; the result decides between entities and particles, recorded in the pull request (`test`)
- [ ] 5.2 Red: `CupFinaleTest`: place 1 gets the title "Victory!" and "Sieg!", place 4 gets "Place 4" and "Platz 4", and each subtitle gives the points (`feat(race)`)
- [ ] 5.3 Red: `CupFinaleTest`: a schedule with five bursts for place 1, two for places 2 and 3, none for others, all within eighty ticks, driven by `tick()` (`feat(race)`)
- [ ] 5.4 Red: `CupFinaleTest`: a racer who left gets no title and is listed as "(left)" (`feat(race)`)
- [ ] 5.5 Red: `CupScoreTest`: `ringsPassed` accumulates across maps, and the total is the sum of the ring counts of the cup's maps (`feat(race)`)
- [ ] 5.6 Red: `CupStandingsTest`: each row carries points, best time and "passed/total", and the viewer's line is bold with the marker (`feat(race)`)
- [ ] 5.7 Red: confirm 5.2 to 5.6 fail for the stated reason, and record the messages in the pull request

## 6. Green: the cup finale

- [ ] 6.1 Green: `CupScore.ringsPassed` and `CupRound.record` with the rings passed (`feat(race)`)
- [ ] 6.2 Green: `CupFinale` with the placement titles, the sounds and the tick schedule of bursts (`feat(race)`)
- [ ] 6.3 Green: the fireworks on the chosen Minestom API (entities or particles, from 5.1) (`feat(race)`)
- [ ] 6.4 Green: the standings heading, the map line, the coloured rows and the viewer's line in `CupAnnouncer` (`feat(race)`)
- [ ] 6.5 Refactor: remove the old "CUP COMPLETE" title use if no other code path reads it, and keep the translated key if one does (`feat(race)`)

## 7. Shipped data and checks

- [ ] 7.1 Confirm the shipped display names pass the rule, and that no tag is needed for them (`feat(catalog)`)
- [ ] 7.2 Run the golden masters, and record each changed transcript in the pull request with its reason, as `design.md` lists them (`test`)
- [ ] 7.3 Run `./gradlew :voyager:fitness:test` and `./gradlew build` on both trees, and the bundle parity test for the new keys (`test`)

## 8. Docs

- [ ] 8.1 Update `docs/reference/message-style-guide.md`: the name colours, the one-accent exception for names, the placement titles (`docs`)
- [ ] 8.2 Update `docs/reference/hud.md`: the intro title, the boss bar name, the sidebar lines and the sixth tutorial page (`docs`)
- [ ] 8.3 Update the catalogue file reference: the MiniMessage display name, its tag set, its forty visible characters and the red rule (`docs`)

## 9. Smoke boot

- [ ] 9.1 Build the server shadow jar, boot it in a scratch directory with the shipped data copied and `VOYAGER_CUP=alpha_cup`, drive a short cup with a test racer, and quote the chat lines of the finish, the result and the standings in the pull request (`test`)

## 10. Ship

- [ ] 10.1 Open the code pull request under the title `feat(race): show coloured map and cup names with authors and a placement finale`. Its description lists each changed golden transcript and the open-question answers it relies on, and ends with the referral line of the repository's pull request rule. Do not add a session link (`feat(race)`)
