# Design

## Context

Where a cup or map is named today (`origin/main`, after `add-display-names` #346 and `polish-race-hud` #347):

| Surface | Site | Prints now |
|---|---|---|
| Finish line | `platform/cup/CupAnnouncer.java` `report` (`Messages.finished`) | the display name, plain, through `Component.text` |
| Map result and DNF | `CupAnnouncer.announceMapScore` (`Messages.mapResult`, `mapResultDnf`) | the display name, plain |
| Map announce line | `platform/cup/CupSession.java:702` (`Messages.mapBanner`) | the display name, plain |
| Cup heading and standings rows | `CupAnnouncer.announceCupResult` (`Messages.cupHeading`, `cupRow`) | the cup name, plain; racer names |
| Cup title for a place | `CupAnnouncer.announceCupPlace` (`RaceFeedback.cupResult`) | "CUP COMPLETE" and "place n · points" |
| Boss bar | `platform/hud/RaceHud.java:203` (`Messages.bossBarTitle`) | the map name, plain |
| Lobby sidebar and hologram | `lobby/LobbyBoard.java`, `LobbySidebar`, `LobbyHologram` | cup and map names, plain |
| Tutorial | `tutorial/TutorialPages.java` (`COUNT = 5`) | five pages, no catalogue content |

The end of a cup, from the code as read on this branch, not yet measured on a server:

- `RaceTimings.DEFAULT` gives the last map's results screen `endAfterLastMap` = 20 s.
- The cup's standings are announced when `CupSession.cupFinished()` becomes true (`CupSession.java:530`).
- `WaitingRoom.tick()` starts the linger on that tick (`WaitingRoom.java:236`). The linger is `RESULTS_LINGER_TICKS` = 200
  ticks, so every racer is handed to the hub 10 s after the standings appear.

So the standings and the placement title are on screen for about 10 s before the hand-off, and the owner asked for no longer
pause. The celebration in this change is planned to fit in those 10 s. Open question 6 asks what triggers the server restart
the owner saw.

## Decisions

1. **A display name is parsed once, in `platform.text`.** `DisplayName` (in `net.elytrarace.voyager.platform.text`)
   parses MiniMessage with a restricted `TagResolver`. It accepts the colour tags, the decoration tags and `reset`, and refuses
   any other tag by name. The catalogue adapters call it at load, so a file with an unusable name is refused with the file
   and the field named. `MiniMessage.deserialize` is called only in `platform.text`, which `message-fitness` already requires.
2. **Rainbow is not accepted.** A rainbow is a sequence of hues that includes red, so the red rule would refuse it anyway.
   The allowed tag list therefore leaves it out.
3. **The red rule is a hue test on the parsed component.** `DisplayName` walks the parsed component tree and reads each
   `TextColor`. A colour whose hue is within 15 degrees of 0 (red) is refused, and so is the named colour `red` and
   `dark_red`. A gradient or a rainbow is refused by the same walk, because its stops are colours of the children. Pink and
   magenta are outside the band, as the palette's magenta alarm colour (`WARN`) already is.
4. **Visible length is measured on the stripped text.** `MiniMessage.stripTags` gives the characters a player sees, and the
   limit of forty code points applies to them. This replaces the raw-length check in `JsonFields.displayName`.
5. **A name without colour takes a palette hue.** `NamePalette` holds the hues names may use. None is red, and none is a role
   colour of `Palette` (`SPEED`, `OK`, `WARN`, `CAUTION`, `VALUE`, `DIM`, `PERSONAL_BEST`, `DIAMOND`, `GOLD`, `SILVER`,
   `BRONZE`). The hue is `hues[Math.floorMod(key.asString().hashCode(), hues.length)]`. `String.hashCode` is fixed by the Java
   specification, so the same key gets the same hue on every server. The palette's hues are open question 1.
6. **A missing name is the key's value in title case.** `MapDefinition` and `CupDefinition` store, when the name is absent,
   the key's value with `_` and `-` replaced by spaces and each word capitalised: `elytrarace:dragon_lobby` is "Dragon Lobby".
   This replaces the #346 fallback to the id. The boot check logs one warning per played map or cup without a name, in
   English, with the key. Golden transcripts that used a synthetic id change (listed below).
7. **The map name is a `Component` argument, never a key.** `Messages` takes the parsed name component and passes it as a
   translation argument. The name is coloured by its own tags or by the palette hue, and the surrounding text keeps its
   translated colour tags.
8. **The intro title is the countdown's first title.** The countdown digits already use the title channel. The intro title
   shows the name and the authors for the countdown's first second (`RaceFeedback`), then the digits follow, and the
   countdown subtitle "map · rings · target" is dropped because the announce line holds those figures. The timing is open
   question 4.
9. **Authors are plain text, joined with ", ".** The sidebar shows the first author and " +n" for the rest. A map line whose
   text would exceed forty visible characters drops the author list, so the name is never cut. The sidebar keeps its ten map
   lines, so title, racers, ten maps, status and footer make fourteen lines under the fifteen-line cap.
10. **The tutorial gets a sixth page.** `TutorialPages.COUNT` becomes 6. The page is built per racer from the cup the room is
    waiting for, and it is the only page that depends on the cup. The `cygnus-style-lobby` tutorial spec ("five pages in a
    fixed order") is replaced by this change's sixth page once that spec is archived.
11. **The placement title and its sound are chosen by place.** `Messages.placeTitle(place)` returns a translated key:
    `voyager.cup.place.1.title` ("Victory!" / "Sieg!"), `.2` ("2nd place" / "Platz 2"), `.3` ("3rd place" / "Platz 3") and
    `voyager.cup.place.other` ("Place <arg:0>" / "Platz <arg:0>"). The subtitle is the points.
12. **Fireworks are launched from the tick loop, not from a timer.** A small `CupFinale` schedule lists the bursts by tick
    offset from the tick the cup ends, and the tick loop launches each one when its offset is reached. Winner: five bursts
    over four seconds. Second and third: two bursts each. Other places: none. The fireworks are firework rocket entities with
    an explosion, or, if the Minestom API used here cannot set the explosion, particle bursts; an apply spike decides which.
13. **Rings hit are counted per racer across the cup.** `CupRound.record` gains the rings a racer passed on a map, and
    `CupScore` (`race/scoring`) gains `ringsPassed`. The total is the sum of the ring counts of every map of the cup, read from
    the cup's loaded maps when the standings are announced. A racer's line reads "passed/total".
14. **The standings colours and the viewer's line.** Places one to three use `Palette.GOLD`, `SILVER` and `BRONZE`, the medal
    colours the game already has. The viewer's line is `<bold>` with a "»" marker. No other colour is added.
15. **Nothing else is added to the end screen.** No play-again item, no longer pause, no hover breakdown.

## Golden masters

These transcripts change, and the pull request lists each one with its reason:

- Every transcript that prints a map or cup name with no colour tag now shows a palette hue. The fixtures use synthetic ids
  (`grand_tour`, `ridge-run`, `checkpoint_cup`, `com_tour`) and no display name, so each name also changes to its title-case
  form ("Grand Tour").
- The standings transcript changes its rows (points, best time, rings hit) and gains the heading colour and the map line.
- The lobby board transcript changes its sidebar title and map lines.
- Operator output (`config-golden`) keeps the key from `add-catalogue-keys-and-authors`. It does not change in this change.

## Architecture

| Type or change | Module | Package |
|---|---|---|
| `DisplayName` (parse, refusal reasons, visible length, red test) | `voyager-platform` | `net.elytrarace.voyager.platform.text` |
| `NamePalette` (hues, hash choice) | `voyager-platform` | `net.elytrarace.voyager.platform.text` |
| `CupFinale` (placement titles, sounds, fireworks schedule) | `voyager-platform` | `net.elytrarace.voyager.platform.cup` |
| `TutorialPages` sixth page | `voyager-platform` | `net.elytrarace.voyager.platform.tutorial` |
| `CupScore.ringsPassed`, `CupRound.record` | `voyager-race` | `net.elytrarace.voyager.race.scoring`, `…race.cup` |
| Title-case fallback in `MapDefinition`, `CupDefinition` | `voyager-api` | `net.elytrarace.voyager.api.race` |

Dependency direction holds: `api` ← `race` ← `platform`. `race` does not import `platform.text`, and the parsed component
never enters `race`.

### Fitness rules

- `message-fitness`: no `Component.text` literal outside `platform.text`, and `MiniMessage.deserialize` only in
  `platform.text`. Names reach the rest of the platform as parsed components.
- `message-style` (modified): the one-accent test ignores a name's colour and still rejects red in every bundle value.
- New, if the apply finds the rule is not yet in `voyager-fitness`: `DisplayName` and `NamePalette` must not reference a
  role colour by a literal hex, only `Palette` constants (`allowEmptyShould(false)` with a seeded violation).

## Migration

- `add-display-names` (#346) is not yet archived. Its requirement "A display name is plain text and is refused when it is
  unusable" is replaced by `catalog/display-names`. Archive `add-display-names` first; then this change's delta replaces the
  plain-text requirement. Until then the delta names the replaced requirement in its scenario text.
- `add-catalogue-keys-and-authors` changes the same `file-schema` requirement that `add-display-names` modifies. Whichever
  change is archived second rebases its delta.
- No shipped display name changes. The values that #346 seeded are plain text, which the rule accepts. The shipped names
  gain a colour only from the palette fallback, unless the owner adds a tag (open question 2).

## Open questions

These are for the owner before the code pull request is opened.

1. **The name palette.** Four candidates that are not red and not a role colour: blue `5B7CFF`, lime `C6FF3D`, yellow
   `FFF06B` and light violet `A98BFF` (not the reserved `C77DFF`). They are not checked on a dark backdrop yet. Owner picks the
   list; the test checks contrast of each against the boss bar's backdrop.
2. **The owner's example `<red>Red</red>`.** The red rule refuses it. Owner chooses the example's replacement, for instance
   "<blue>Blue</blue> & <gold>Gold</gold>", and whether a shipped name with a gradient to red is recoloured.
3. **One accent per line.** The finish line now has the name's colour and the medal word's accent. The proposal says the
   name's colour is not counted. Owner confirms, or the medal word goes dim.
4. **Intro title timing.** The title replaces the first countdown second. Owner confirms, or the intro gets its own two
   seconds before the digits.
5. **Medal word.** "Finish" and "Ziel" in mixed case replace "FINISH" and "ZIEL". Owner confirms, and whether the finish
   word keeps its slate colour, which is the dimmest colour in the palette.
6. **The server restart.** The owner saw the ElytraRace service stop and restart right after the standings, and a landing on
   Lobby-2. The code path read here hands racers to the hub after the 10 s linger. If the restart is triggered by the cup's
   end instead, the celebration is cut. Confirm what restarts the service and when. This is the blocking question for the
   timing of section "Why".
7. **Statistics commands.** `/stats` and `/leaderboard` print the stored map key today. The owner's list does not name them.
   Recommendation: include them in this change as the display name and its colour, because the raw key is now
   `elytrarace:blue_and_red`.
8. **The maps count in the standings.** The row drops "maps finished" to keep one line per racer. Owner confirms the drop or
   asks for both.
9. **Rings total.** "x/total" counts every ring of the cup's maps, not only the maps a racer played. A late joiner shows a
   lower x. Confirm.
10. **Sidebar map lines.** `openspec/specs/race/lobby-cup-display` says twelve map lines, and `LobbyBoard.MAX_MAP_LINES` is
    ten. Ten keeps the sidebar at fourteen lines. The spec is stale and is corrected in this change's apply, unless the owner
    wants twelve, which would overflow the fifteen-line cap with the current four fixed lines.
11. **Sixth tutorial page.** The owner said "tutorial book (authors there too)". This design reads that as a new page. Confirm,
    or put the authors on page 5 instead.
12. **Fireworks API.** Whether the Minestom version pinned in `voyager/platform` can set a firework explosion on a rocket
    entity. The apply's first task is a spike; the fallback is particles.
13. **Pulse review.** The colour, the fanfare and the timing have not yet been reviewed by the game psychologist role, as
    `CLAUDE.md` asks for every gameplay and UI decision. The owner decides whether that review happens before the code PR.
