# Tasks

Commit types and scope: `feat(race)` for the platform, race and api code, its tests, the palette and the fireworks; `feat(catalog)`
for the display-name parsing in the catalogue adapters; `docs` for `docs/` pages; `test` for test-only commits. Each behaviour gets
a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleep (the finale and the intro are
driven by `tick()` calls), an injected clock where a time is read, a fresh instance per test, no shared static palette state. This
change starts only after `add-catalogue-keys-and-authors` is merged. Task checkboxes are ticked in the follow-up pull request, never
in the code pull request.

Archive order: archive `add-display-names` before this change. Nothing is archived by this change or by its code pull request.

## 1. Red: the name rules

- [ ] 1.1 Red: `DisplayNameTest` accepts `<blue>Blue</blue> & <gold>Gold</gold>` and refuses `<click:run_command:/stop>Alpha</click>`, naming the tag `click` (`feat(catalog)`)
- [ ] 1.2 Red: `DisplayNameTest` accepts `<red>Red</red>`, `<#e0201a>Rush</#e0201a>`, a gradient with a red stop and `<rainbow>Rush</rainbow>` (`feat(catalog)`)
- [ ] 1.3 Red: `DisplayNameTest` measures `<gradient:#00c2ff:#5bff8f>Blue and Red Rush</gradient>` as seventeen visible characters and refuses forty-one (`feat(catalog)`)
- [ ] 1.4 Red: `DisplayNameTest` shows `a < b` as the literal text (`feat(catalog)`)
- [ ] 1.5 Red: `NamePaletteTest` gives the same key the same hue twice, and asserts that the six hexadecimal values are `2EE6D6`, `B47CFF`, `FFD23F`, `5CC8FF`, `FF7AC6` and `B8FF4D` (`feat(race)`)
- [ ] 1.6 Red: `NamePaletteTest` asserts each hue reaches a contrast ratio of at least 6:1 against `#1A1A1A` (`feat(race)`)
- [ ] 1.7 Red: `MapDefinitionTest` and `CupDefinitionTest`: an absent name is the key's value in title case, "Dragon Lobby" for `elytrarace:dragon_lobby` (`feat(race)`)
- [ ] 1.8 Red: adapter test with `@TempDir`: a map file with the tag `click` is refused, and a map file with `displayName` of forty-one visible characters is refused (`feat(catalog)`)
- [ ] 1.9 Red: boot log test: a played map without a name logs one warning naming its key, through a captured appender (`feat(race)`)
- [ ] 1.10 Red: confirm 1.1 to 1.9 fail for the stated reason, and record the messages in the pull request

## 2. Green: the name rules

- [ ] 2.1 Green: `DisplayName` (parse, tag whitelist, visible length) and `NamePalette` in `platform/text`, with `package-info.java` (`feat(catalog)`)
- [ ] 2.2 Green: `JsonFields.displayName` uses `DisplayName` and refuses by the new rules, with the file and the field named (`feat(catalog)`)
- [ ] 2.3 Green: the title-case fallback in the `MapDefinition` and `CupDefinition` compact constructors, replacing the id fallback (`feat(race)`)
- [ ] 2.4 Green: the boot warning for each played map or cup without a name (`feat(race)`)

## 3. Red: the map surfaces

- [ ] 3.1 Red: `MessagesTest`: the announce line for `elytrarace:blue_and_red` names "Blue & Red" in its colour and contains no key (`feat(race)`)
- [ ] 3.2 Red: `MessagesTest`: the finish line "Finished Blue & Red in 1:44.3" and the result line contain no enumeration name, and the medal word is "Finish" in English and "Ziel" in German, in sentence case (`feat(race)`)
- [ ] 3.3 Red: `CupAnnouncerTest` or `RaceFeedbackTest`: the intro title fades in for 10 ticks, stays for 60 and fades out for 20; its subtitle is "by TheMeinerLP" in English and "von TheMeinerLP" in German; no subtitle without authors (`feat(race)`)
- [ ] 3.4 Red: `RaceHudTest`: the boss bar reads "Map 1/1 · Blue & Red · Gold · 12 s left" with the name coloured and the rest unchanged (`feat(race)`)
- [ ] 3.5 Red: `LobbySidebarTest`: the title "Alpha Cup" in its colour, the line "1. Blue & Red · by TheMeinerLP", and at most ten map lines and fifteen lines in total (`feat(race)`)
- [ ] 3.6 Red: `TutorialPagesTest`: six pages, the sixth names the cup, its maps and their authors; pages one to five unchanged (`feat(race)`)
- [ ] 3.7 Red: `StatsRepliesTest`: a leaderboard reply names the map in its colour and prints no `elytrarace:` (`feat(race)`)
- [ ] 3.8 Red: confirm 3.1 to 3.7 fail for the stated reason, and record the messages in the pull request

## 4. Green: the map surfaces

- [ ] 4.1 Green: `Messages` takes the parsed name components for the announce line, finish, result, DNF, boss bar, sidebar, hologram and tutorial; the medal words in sentence case (`feat(race)`)
- [ ] 4.2 Green: the intro title in `RaceFeedback` and `CupSession` with the 10, 60, 20 tick timing, replacing the countdown subtitle; the countdown is checked to be at least 90 ticks long in the development and production timings (`feat(race)`)
- [ ] 4.3 Green: the sixth tutorial page in `TutorialPages` and `TutorialBook`, with `COUNT` at 6 (`feat(race)`)
- [ ] 4.4 Green: the author rule and the forty-character line rule in `LobbyBoard` and `LobbySidebar` (`feat(race)`)
- [ ] 4.5 Green: the stats replies name the map by its coloured name (`feat(race)`)
- [ ] 4.6 Green: the German and English bundle keys, each checked by the parity test (`feat(race)`)

## 5. Red: the cup finale

- [ ] 5.1 Red: `CupFinaleTest`: place 1 gets "Victory!" and "Sieg!", place 4 gets "Place 4" and "Platz 4", and each subtitle gives the points (`feat(race)`)
- [ ] 5.2 Red: `CupFinaleTest`: a schedule with five bursts for place 1, two for places 2 and 3, none for others, all within eighty ticks, driven by `tick()` (`feat(race)`)
- [ ] 5.3 Red: `CupFinaleTest`: a racer who left gets no title and is listed as "(left)" (`feat(race)`)
- [ ] 5.4 Red: `CupScoreTest`: `ringsPassed` accumulates across maps, and the total is the sum of the ring counts of every map of the cup, including maps the racer did not play (`feat(race)`)
- [ ] 5.5 Red: `CupStandingsTest`: each row carries points, best time, maps finished and "passed/total", and the viewer's line is bold with the marker (`feat(race)`)
- [ ] 5.6 Red: confirm 5.1 to 5.5 fail for the stated reason, and record the messages in the pull request

## 6. Green: the cup finale

- [ ] 6.1 Green: `CupScore.ringsPassed` and `CupRound.record` with the rings passed (`feat(race)`)
- [ ] 6.2 Green: `CupFinale` with the placement titles, the sounds and the tick schedule of bursts (`feat(race)`)
- [ ] 6.3 Green: the bursts, as firework rocket entities with an explosion where the pinned Minestom API can set it, otherwise as particles with the same sound and timing (`feat(race)`)
- [ ] 6.4 Green: the standings heading, the map line, the coloured rows and the viewer's line in `CupAnnouncer` (`feat(race)`)
- [ ] 6.5 Refactor: remove the old "CUP COMPLETE" title use if no other code path reads it (`feat(race)`)

## 7. Error colour

- [ ] 7.1 Red: `PaletteTest` asserts that the error colour is the fixed red of the owner's confirmed hex, and that no other role takes it (`feat(race)`)
- [ ] 7.2 Green: set the error colour in `Palette` to the confirmed red, and update `message-style-guide.md` (`feat(race)`)

## 8. Verification tasks for the code pull request

- [ ] 8.1 Verify firework support: a Minestom test or a short spike shows whether a firework rocket entity can carry an explosion on the pinned Minestom version. Record the result and which path (entities or particles) the code uses, in the pull request (`test`)
- [ ] 8.2 Verify the results linger: on a local run with a short cup, confirm that the 10 s linger completes, with the title, the sound and the bursts finished, before the hub hand-off. Record the observed times in the pull request (`test`)
- [ ] 8.3 Confirm that the countdown of the development and production timings is at least 90 ticks, so the intro finishes before the first digit (`test`)

## 9. Golden masters and checks

- [ ] 9.1 Run the golden masters, and record each changed transcript in the pull request with its reason, as `design.md` lists them (`test`)
- [ ] 9.2 Run `./gradlew :voyager:fitness:test` and `./gradlew build` on both trees, and the bundle parity test for the new keys (`test`)

## 10. Docs

- [ ] 10.1 Update `docs/reference/message-style-guide.md`: the name colours, the one-accent exception for names, the error red, the placement titles and the medal words (`docs`)
- [ ] 10.2 Update `docs/reference/hud.md`: the intro title and its timing, the boss bar name, the sidebar lines (ten map lines) and the sixth tutorial page (`docs`)
- [ ] 10.3 Update the catalogue file reference: the MiniMessage display name, its tag set, its forty visible characters and the colour rule (`docs`)

## 11. Smoke boot

- [ ] 11.1 Build the server shadow jar, boot it in a scratch directory with the shipped data copied and `VOYAGER_CUP=alpha_cup`, drive a short cup with a test racer, and quote the chat lines of the finish, the result and the standings in the pull request (`test`)

## 12. Ship

- [ ] 12.1 Open the code pull request under the title `feat(race): show coloured map and cup names with authors and a placement finale`. Its description lists each changed golden transcript, the firework path chosen in 8.1, the linger timings from 8.2, and the error colour hex from 7.1. It ends with the referral line of the repository's pull request rule. Do not add a session link (`feat(race)`)
