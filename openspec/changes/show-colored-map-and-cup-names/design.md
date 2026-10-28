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
| Stats replies | `platform/stats/StatsReplies.java` | the stored map key |
| Tutorial | `tutorial/TutorialPages.java` (`COUNT = 5`) | five pages, no catalogue content |

### The end of a cup, from the code as read on this branch

Not measured on a server yet.

- `RaceTimings.DEFAULT` gives the last map's results screen `endAfterLastMap` = 20 s.
- The standings are announced when `CupSession.cupFinished()` becomes true (`CupSession.java:530`).
- `WaitingRoom.tick()` starts the linger on that tick (`WaitingRoom.java:236`). `RESULTS_LINGER_TICKS` is 200, so every racer is
  handed to the hub 10 s after the standings appear.

The celebration is planned to fit in those 10 s, and the owner chose no longer pause. The owner checked read-only on mc01 that
nothing stops the ElytraRace service after a cup: the cup finished at 13:55:38, the racer was handed off 10 s later, and the service
kept running for over an hour. The restarts the owner saw were deploy restarts. The linger is still verified in the code PR (task 8.2).

## Decisions

1. **A display name is parsed once, in `platform.text`.** `DisplayName` (in `net.elytrarace.voyager.platform.text`) parses
   MiniMessage with a restricted `TagResolver`. It accepts the colour tags (named colours, hexadecimal colours, `gradient`,
   `rainbow`), the decoration tags and `reset`, and refuses any other tag by name. The catalogue adapters call it at load, so a
   file with an unusable name is refused with the file and the field named. `MiniMessage.deserialize` is called only in
   `platform.text`, which `message-fitness` already requires.
2. **Any colour is allowed, including red and rainbow.** The owner's decision reverses the earlier red rule. A rainbow is accepted
   with the other colour tags.
3. **Visible length is measured on the stripped text.** `MiniMessage.stripTags` gives the characters a player sees, and the
   limit of forty code points applies to them. This replaces the raw-length check in `JsonFields.displayName`.
4. **A name without its own colour takes a hue from the default palette.** `NamePalette` holds the six hues in this order:
   turquoise `#2EE6D6`, violet `#B47CFF`, gold `#FFD23F`, sky blue `#5CC8FF`, pink `#FF7AC6`, lime `#B8FF4D`. The hue is
   `hues[Math.floorMod(key.asString().hashCode(), hues.length)]`. `String.hashCode` is fixed by the Java specification, so the same
   key gets the same hue on every server. Measured against the dark chat background `#1A1A1A` the contrast ratios are: turquoise
   11.1:1, violet 6.0:1, gold 12.1:1, sky blue 9.3:1, pink 7.3:1, lime 14.5:1. The test asserts each is at least 6:1.
   Gold is close to the medal gold `#FFCC33` of `Palette.GOLD`; a line that shows a gold name and a gold medal word is accepted,
   because the owner chose gold for names (open risk 1).
5. **A missing name is the key's value in title case.** `MapDefinition` and `CupDefinition` store, when the name is absent, the
   key's value with `_` and `-` replaced by spaces and each word capitalised: `elytrarace:dragon_lobby` is "Dragon Lobby". This
   replaces the #346 fallback to the id. The boot check logs one warning per played map or cup without a name, in English, with
   the key. Golden transcripts that used a synthetic id change (listed below).
6. **The map name is a `Component` argument, never a key.** `Messages` takes the parsed name component and passes it as a
   translation argument. The name is coloured by its own tags or by the palette hue, and the surrounding text keeps its
   translated colour tags.
7. **The intro title is shown at the start of the countdown, before the digits.** The title fades in for 10 ticks, stays for 60
   and fades out for 20, so it takes 90 ticks (4.5 s). The countdown digits follow it. The subtitle "map · rings · target" of
   the countdown is dropped, because the announce line holds those figures. The countdown must be at least 90 ticks long for
   the intro to finish before the first digit; the production lobby countdown is 20 s and the development one is shorter, and
   the dev timings are checked in the code PR.
8. **The medal word is in sentence case.** "Finish" in English and "Ziel" in German replace "FINISH" and "ZIEL" in the medal
   translations. The word keeps its existing colour.
9. **A name's colour is not an accent.** The one-accent rule of `message-style` does not count a display name's colour, so the
   finish line carries the name's colour and one accent tag for the medal word.
10. **Authors are plain text, joined with ", ".** The sidebar shows the first author and " +n" for the rest. A map line whose text
    would exceed forty visible characters drops the author list, so the name is never cut. The sidebar keeps ten map lines, so
    title, racers, ten maps, status and footer make fourteen lines under the fifteen-line cap.
11. **The tutorial gets a sixth page with the authors.** `TutorialPages.COUNT` becomes 6. The page is built per racer from the cup
    the room is waiting for, and it is the only page that depends on the cup. The `cygnus-style-lobby` tutorial spec ("five pages in
    a fixed order") is replaced by this change's sixth page once that spec is archived.
12. **The stats commands name the map in its colour.** `StatsReplies` takes the catalogue name of each stored key from the
    snapshot and falls back to the title-case value when the map is no longer in the catalogue. This is a SHOULD.
13. **Placement titles and sounds are chosen by place.** `Messages.placeTitle(place)` returns a translated key:
    `voyager.cup.place.1.title` ("Victory!" / "Sieg!"), `.2` ("2nd place" / "Platz 2"), `.3` ("3rd place" / "Platz 3") and
    `voyager.cup.place.other` ("Place <arg:0>" / "Platz <arg:0>"). The subtitle is the points. The sound is a fanfare for first
    place, a shorter one for second and third, and a soft chime for the rest; the sound keys are picked in the code PR and checked
    against the sound rules of `message-style`.
14. **Bursts are launched from the tick loop, not from a timer.** A small `CupFinale` schedule lists the bursts by tick offset from
    the tick the cup ends, and the tick loop launches each one when its offset is reached. Winner: five bursts over four seconds.
    Second and third: two bursts each. Other places: none.
15. **Fireworks: verified in the code PR, with a fallback.** Firework rocket entities with an explosion are used when the pinned
    Minestom API can set the explosion. Otherwise the same bursts are drawn as particles with the same sound and timing. The first
    task of the code PR verifies which one applies, and records it in the pull request.
16. **The error colour is unchanged.** `Palette.WARN` stays `#FF3DA1`. A display name may use any colour, and no line's error colour
    changes.
17. **Rings hit and maps finished are counted per racer across the cup.** `CupRound.record` gains the rings a racer passed on a
    map, and `CupScore` (`race/scoring`) gains `ringsPassed`. The total is the sum of the ring counts of every map of the cup, read
    from the cup's loaded maps when the standings are announced, whether or not the racer played them. "Maps finished" stays on each
    row.
18. **The standings colours and the viewer's line.** Places one to three use `Palette.GOLD`, `SILVER` and `BRONZE`, the medal
    colours the game already has. The viewer's line is bold with a "»" marker. No other colour is added.
19. **Nothing else is added to the end screen.** No play-again item, no longer pause, no hover breakdown.
20. **The sidebar holds ten map lines.** `race/lobby-cup-display` is corrected to ten, which matches `LobbyBoard.MAX_MAP_LINES`. The
    scenario name "A cup with twelve maps or fewer" is kept, so the archive matches the base requirement; its content now uses ten
    maps.

## Golden masters

These transcripts change, and the pull request lists each one with its reason:

- Every transcript that prints a map or cup name with no colour tag now shows a palette hue. The fixtures use synthetic ids
  (`grand_tour`, `ridge-run`, `checkpoint_cup`, `com_tour`) and no display name, so each name also changes to its title-case
  form ("Grand Tour").
- The standings transcript changes its rows (points, best time, rings hit), gains the heading colour and the map line, and
  shows the medal colours.
- The lobby board transcript changes its sidebar title and map lines.
- Operator output (`config-golden`) keeps the key from `add-catalogue-keys-and-authors`. It does not change in this change.

## Architecture

| Type or change | Module | Package |
|---|---|---|
| `DisplayName` (parse, tag whitelist, visible length) | `voyager-platform` | `net.elytrarace.voyager.platform.text` |
| `NamePalette` (six hues, hash choice) | `voyager-platform` | `net.elytrarace.voyager.platform.text` |
| `CupFinale` (placement titles, sounds, burst schedule) | `voyager-platform` | `net.elytrarace.voyager.platform.cup` |
| `TutorialPages` sixth page | `voyager-platform` | `net.elytrarace.voyager.platform.tutorial` |
| `CupScore.ringsPassed`, `CupRound.record` | `voyager-race` | `net.elytrarace.voyager.race.scoring`, `…race.cup` |
| Title-case fallback in `MapDefinition`, `CupDefinition` | `voyager-api` | `net.elytrarace.voyager.api.race` |

Dependency direction holds: `api` ← `race` ← `platform`. `race` does not import `platform.text`, and the parsed component never
enters `race`.

### Fitness rules

- `message-fitness`: no `Component.text` literal outside `platform.text`, and `MiniMessage.deserialize` only in `platform.text`.
  Names reach the rest of the platform as parsed components.
- `message-style` (modified): the one-accent test ignores a display name's colour.
- New, if the code PR finds the rule is not yet in `voyager-fitness`: `NamePalette` takes its hues from `Palette` or from one
  constant list, with a seeded violation (`allowEmptyShould(false)`).

## Migration and archive order

- `add-display-names` (#346) is not yet archived. Its requirement "A display name is plain text and is refused when it is
  unusable" is replaced by `catalog/display-names`. **Archive order:** archive `add-display-names` first, then this change; the
  plain-text requirement is replaced then. Archiving is not part of this change, and nothing is archived now.
- `add-catalogue-keys-and-authors` and `add-display-names` both modify the `file-schema` requirement. Whichever is archived second
  rebases its delta.
- No shipped display name changes (decision in `proposal.md`).

## Open risks and open questions

1. **Gold collision.** The owner chose gold for names. It is close to the medal gold `#FFCC33`, so a gold name and a gold medal
   can share a line. Accepted for now; the owner may pick a different gold.
2. **Intro title timing.** Fade in 10, stay 60, fade out 20 ticks, as decided. The countdown must be at least 90 ticks; the dev
   timings are checked in the code PR.
3. **Fireworks API.** Whether the pinned Minestom version can set a firework explosion on a rocket entity is verified in the code
   PR. Particles and sound are the fallback.
4. **Sixth tutorial page.** Decided by the owner: the authors go on a new tutorial page.
5. **Archive order.** `add-display-names` is archived before this change (see "Migration and archive order").
6. **Pulse review.** The colour, the fanfare and the timing have not yet been reviewed by the game psychologist role, as
   `CLAUDE.md` asks for every gameplay and UI decision. The owner decides whether that review happens before the code PR.
