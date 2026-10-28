# Design

## Context

### What a racer sees now, between two maps

- The game phase ends and `CupSession.mapFinished` runs (`platform/cup/CupSession.java:842`). It removes each racer's boss bar
  (`hud.standDown(racer)`, line 858; `hud/RaceHud.java:153-155` removes the bar). The boss bar is created only in the race
  (`RaceHud.java:203-211`). Between maps, no bar and no timer are shown.
- The END phase lasts 8 s (`race/flow/RaceTimings.java:53-54`, `endBetweenMaps`), then the next map's lobby lasts 20 s, with
  the three-second start countdown at its end (`flow/StartGate.java`, `COMMIT_WINDOW`; `platform/hud/StartCountdown.java:40`).
  The next GO is therefore 28 s after the last finish, in production.
- The start countdown already shows a title with the digits 3, 2, 1 and a countdown beat in those three seconds
  (`platform/hud/RaceFeedback.java:137-143`, `countdown`). See open question 1.
- The result chat after a map is one breakdown line (`platform/cup/CupAnnouncer.java:88-100`, `announceMapScore`). Its title
  shows the medal and its subtitle the next goal (`RaceFeedback.mapResult`, the `race-feedback` requirement "The map result
  names the next goal in seconds"). Nothing compares the racer with anyone else.
- The personal best of a map is written in `CupSession.mapFinished` by `recordRace` (line 865), which runs before the result
  is announced (line 870). Reading the best at announce time would therefore read the value just written.
- Statistics are on only when `voyager.persistence` (`VOYAGER_PERSISTENCE`) is set and the database is enabled
  (`server/config/PersistenceSettings.java:36`, `63-64`). When it is off, the server runs normally and keeps no statistics.
- The personal best is per map, layout and mode, and it counts ranked play only (`openspec/specs/stats/player-stats`,
  "The personal best of a map keeps the fastest time" and "The profile counts ranked play only").

## Decisions

### 1. The between-maps countdown counts to the next GO

The boss bar is shown from the finish of a map until the next map's GO, for each racer who played the map. Its title is
"Next map in N s" in English and "Nächste Map in N s" in German, where N is the whole seconds left until GO. Its progress is the
share of the 28-second wait that has passed. It uses the existing bar's colour and overlay (`Palette.BOSS_BAR_COLOUR`, the
`polish-race-hud` layout), so only the title changes between the race and the wait.

N is computed from the race state: the END phase's remaining time plus the lobby's remaining time, rounded up. It is not read
from the wall clock, so the count is repeatable in a test with an explicit clock.

The bar is not shown before the first map or after the last map. After the last map the cup's end screen takes over.

### 2. The standings sidebar between maps

Between the map's finish and the next GO, the sidebar lists the racers who played the map, in order:

- Finished racers first, by finish time, each with the time and, for the others, the gap to the leader ("+0.0 s" for the leader).
- Then racers who did not finish, marked as such.

The sidebar has at most fifteen lines (the scoreboard limit `show-colored-map-and-cup-names` uses). If there are more racers
than lines, the list ends with "+ n more". The sidebar is built from the `MapStanding` of this change, a pure function of the
finish times. It is shown in place of the lobby board for those racers, and it is cleared at GO.

The sidebar is a live view of a settled list. After the game phase ends no racer is still racing, so the list changes only
when a racer leaves the cup. The countdown in the boss bar is the part that moves.

### 3. The place and gap line

`CupAnnouncer.announceMapScore` gains one chat line for each racer who finished the map. It is sent after the breakdown line
and before the personal best clause. The line has three parts:

- **Place.** The place among the racers who started the map, by finish time, with the tie broken by the earlier finish tick.
  "Place 2 of 5": 2 is the place, and 5 counts every racer who started the map, DNFs included.
- **Gap.** For a place other than first: the seconds behind the racer directly ahead, "+1.4 s behind <name>". For first place:
  the seconds ahead of the racer directly behind, "0.8 s ahead of <name>". When the racer is alone on the map, the gap is omitted.
- **Names.** The other racer's name is plain text. The line takes the one-accent rule of `message-style`, and no colour of this
  change's own is added. The map's name colour comes from `show-colored-map-and-cup-names`, not from this line.

A racer who did not finish gets no place line. The DNF line that already exists stays the only line for them.

The line is a translated component (`voyager_en_US` and `voyager_de_DE`, with `<arg:N>` placeholders), so it passes the
parity test. German uses the informal "du" in its sentences; the German decimal form is the one the existing German time strings
use (checked in the code PR).

### 4. The personal best clause

The clause is part of the same line when, and only when, all three hold:

- statistics are on (`PersistenceSettings`), and
- the cup is ranked, and
- the racer finished the map and has a stored personal best for that map, layout and mode.

The personal best is read once, at the map's GO, into the racer's run. It is not read at the finish, because the finish writes
the new time (`CupSession.java:865`) before the result is announced (line 870). The read is asynchronous and must answer within
two seconds, as the stats requirement "A read answers within two seconds or says it cannot" requires. If the read has not come
back at the finish, the clause is omitted. It is never guessed.

The clause reads "New best! −2.1 s" when the finish beats the personal best by 2.1 seconds, and "+0.6 s to your best" when it is
slower by 0.6 seconds. An equal time is "Equal to your best". A first run on a map is "New best!" with no delta.

**When statistics are off**, no personal best is read and no clause is shown. The place line and the gap stay. Nothing says
"best" or "new best", because nothing is stored to compare with. A session-only best (kept in memory, lost on restart) is not
proposed here. It would show a best that the next restart forgets, and it would look like the real one. Open question 3 asks
the owner whether to add it anyway.

### 5. The last three seconds

The owner decided "a title plus a pling in the last 3 s" of the wait. The start countdown already uses the last three seconds
before GO for the digits 3, 2, 1, each with a title and the countdown beat (`RaceFeedback.countdown`). The owner's cue and the
digits fall in the same three seconds. Open question 1 asks which of two designs the owner wants before the code PR starts.

Until the owner answers, the spec states the owner's decision as written, and the design keeps the rule that every sound means
one thing (`race-feedback`): the pling is a sample that is none of the countdown beat, the ring bell or the lobby chime.

### 6. Pure logic in `race`, rendering in `platform`

- `race/standing`: `MapStanding` (the order of the racers who played a map, each place, each gap, the DNFs) and
  `PersonalBestComparison` (the three outcomes and the delta). Pure, no Minestom, no clock read.
- `race/flow`: the seconds until GO from the phase state, as a pure function of `RaceState` and `RaceTimings`.
- `platform/cup`: `CupAnnouncer` builds the line, `CupSession` reads the personal best at GO and calls the announcer.
- `platform/hud`: `RaceHud` shows the wait's title. The sidebar is in `platform/lobby`, next to `LobbyBoard`.

## Architecture

| Type or change | Module | Package |
|---|---|---|
| `MapStanding`, `PersonalBestComparison` | `voyager-race` | `net.elytrarace.voyager.race.standing` (with `package-info`) |
| Seconds until GO | `voyager-race` | `net.elytrarace.voyager.race.flow` |
| Standing and PB line, PB read at GO | `voyager-platform` | `net.elytrarace.voyager.platform.cup` |
| Between-maps title and last-seconds cue | `voyager-platform` | `net.elytrarace.voyager.platform.hud` |
| Between-maps sidebar | `voyager-platform` | `net.elytrarace.voyager.platform.lobby` |
| Messages (English and German) | `voyager-platform` | `net.elytrarace.voyager.platform.text` |

Dependency direction holds: `race` does not import `platform`, and the standing is a plain record that `platform` renders.
`voyager-fitness`: the `race` module already has its no-platform rule. No new boundary is created, so no new rule is added.

## Open questions

1. **The last three seconds collide with the start countdown.** The owner asked for a title and a pling in the last three
   seconds of the wait. The start countdown already shows a title and the beat for 3, 2, 1 in those seconds. Two options:
   - **A.** Replace the countdown digits' title with the wait's title for those seconds, and keep the beat. Pro: one title, one
     beat. Con: the digits are lost as a title.
   - **B.** Keep the countdown as it is and add no cue. Pro: no duplicated sound; the owner's "you don't know how long" is
     already answered by the boss bar. Con: the owner's pling is not played.

   Recommendation: B, with the boss bar carrying the count. The code PR does not start on this point until the owner chooses.
2. **Computer racers.** Should a computer racer (`CupSession`'s competitors) count in the "of 5" and in the place? They are in
   the standings. Recommendation: they count in the standings but not in the chat place, which compares human racers. The owner
   decides.
3. **Session best when statistics are off.** Should a session-only best be shown when statistics are off? Recommendation: no
   (see decision 4).
4. **Pulse review.** `CLAUDE.md` requires the game-psychologist review of every gameplay and UI decision. The colour, the
   gap wording and the countdown have not been reviewed. The owner decides whether that review happens before the code PR.
5. **German wording.** The German titles and the place line are drafts. The owner or a native reader checks them before the code PR.
