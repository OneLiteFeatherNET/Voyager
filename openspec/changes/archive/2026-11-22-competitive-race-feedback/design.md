# Design

## Context

### What a racer sees now, between two maps

- The game phase ends and `CupSession.mapFinished` runs (`platform/cup/CupSession.java:842`). It removes each racer's boss bar
  (`hud.standDown(racer)`, line 858; `hud/RaceHud.java:153-155`). The bar is created only during the race (`RaceHud.java:203-211`).
  Between maps no bar and no timer are shown.
- The END phase lasts 8 s (`race/flow/RaceTimings.java:53-54`), then the next lobby lasts 20 s, with the three-second start
  countdown at its end (`race/flow/StartGate.java`, `COMMIT_WINDOW`; `platform/hud/StartCountdown.java:40`). The next GO is 28 s
  after the phase ends, in production.
- The start countdown already shows a title and a beat for 3, 2, 1 (`platform/hud/RaceFeedback.java:137-143`). It stays as it is.
- The result chat after a map is one breakdown line (`platform/cup/CupAnnouncer.java:88-100`, `announceMapScore`).
- The personal best is written in `CupSession.mapFinished` by `recordRace` (line 865), before the result is announced (line 870).
  A read at announce time would read the value just written, so the read starts at the map's GO.
- Statistics are on only when `voyager.persistence` (`VOYAGER_PERSISTENCE`) is set and the database is enabled
  (`server/config/PersistenceSettings.java:36`, `63-64`).
- The personal best is per map, layout and mode, for ranked play only (`openspec/specs/stats/player-stats`). A computer racer is
  never recorded (`openspec/specs/stats/player-stats`, "Computer racers are never recorded").

### What `smooth-map-transitions` changes for this change

Racers are placed on the next map at the start of the wait, and a finisher is placed when their own glide-out ends. So a racer
may stand in the next map while the previous map's phase still runs for others. The countdown and the sidebar must work there,
and a racer must see the standings before the phase ends.

## Decisions

### 1. The countdown counts to the next GO, wherever the racer stands

The boss bar is shown to every human racer from the end of a map until the next map's GO. Its title is "Next map in N s" (German
"Nächste Map in N s"), where N is the whole seconds left until GO, rounded up. Its progress is the share of the wait that has passed.
It keeps the existing colour, overlay and layout (`Palette.BOSS_BAR_COLOUR`, the `polish-race-hud` layout), so only the title changes.

N is computed from the race state, the END remainder plus the lobby remainder. It is not read from the wall clock, so a test drives it
with an explicit clock.

A racer who stands in the next map before the previous map's phase ends has no N yet, because the phase end is what starts the wait.
For them the bar reads "Waiting for <n> racers" (German "Warte auf <n> Racer"), where n is the number of racers still on the previous
map. The string is new. It is flagged for the owner's acceptance in the code PR. When the phase ends, the bar switches to "Next map in N s".

The bar is not shown before the first map or after the last map. After the last map the cup's end screen takes over.

### 2. The standings sidebar, live while the wait runs

The sidebar lists the racers who played the map, computer racers included:

- Finishers first, by finish time, each with the time, and the gap to the leader ("+0.0 s" for the leader).
- Racers still racing the map, while the phase runs, marked as racing.
- Racers who did not finish, marked as such.

The sidebar holds at most fifteen lines, with "+ n more" when there are more racers than lines. It is shown to a racer from the moment
they stand in the next map, and it is removed at the next map's GO.

The sidebar is recomputed each second during a wait, so it shows the live state: racers still racing become finishers as they finish
(while the phase runs), and the list is settled when the phase ends. It is built from the pure `MapStanding` of decision 5.

### 3. Computer racers count in places and gaps

A computer racer has a finish time like a player's. The place total ("of n") counts every racer who started the map, computer racers
included. Gaps are measured to the racer directly ahead or behind, whoever it is. A computer racer has no chat line, because it has no
client, and no personal best, because it is never recorded.

### 4. The place line

`CupAnnouncer.announceMapScore` gains one chat line for each human racer who finished the map. It is sent after the breakdown line and
before the personal best clause:

- **Place.** Among all racers who started the map, by finish time, with ties broken by the earlier finish tick. "Place 2 of 5":
  2 is the place, and 5 counts every racer who started, DNFs and computer racers included.
- **Gap.** For a place other than first, the seconds behind the racer directly ahead. For first place, the seconds ahead of the racer
  directly behind. When the racer is alone on the map, the gap is omitted.
- **Names.** Plain text. The line takes the one-accent rule of `message-style`, and no colour of this change's own is added.

A racer who did not finish gets no place line. The did-not-finish line that already exists stays the only line for them.

The line is a translated component (`voyager_en_US` and `voyager_de_DE`, `<arg:N>` placeholders), so it passes the parity test. German
uses the informal "du" where a sentence addresses the reader. The German decimal form follows the existing German time strings (checked
in the code PR).

### 5. Pure logic in `race`, rendering in `platform`

- `race/standing`: `MapStanding` (the order of the racers who played a map, each place, each gap, the racing and DNF marks) and
  `PersonalBestComparison` (the three outcomes and the delta). Pure, no Minestom, no clock read.
- `race/flow`: the seconds to GO from the phase state and `RaceTimings`, as a pure function.
- `platform/cup`: `CupAnnouncer` builds the place line; `CupSession` reads the personal best at GO and passes it on.
- `platform/hud`: `RaceHud` shows the wait's title. The sidebar is in `platform/lobby`, next to `LobbyBoard`.

### 6. The personal best clause

The clause appears only when all three hold: statistics are on, the cup is ranked, and the racer is human and has a stored best for
the map, layout and mode. It reads "New best! −2.1 s" when the finish beats the stored best, "+0.6 s to your best" when it is slower,
"Equal to your best" when equal, and "New best!" with no delta on a first run.

The best is read once, at the map's GO, for each ranked human racer. The read is asynchronous and must answer within two seconds, as the
stats requirement "A read answers within two seconds or says it cannot" requires. If the read has not returned at the finish, the
clause is omitted and nothing is guessed.

**Statistics off.** No best is read and no clause is shown. The place line and the gap stay. No line says "best", "new best" or "equal to
your best". No session-only best is kept. The owner declined it.

### 7. No extra cue in the last three seconds

The start countdown keeps its digits, title and beat, and this change adds no title and no pling to those seconds. The boss bar carries
the count. This is the owner's decision.

## Architecture

| Type or change | Module | Package |
|---|---|---|
| `MapStanding`, `PersonalBestComparison` | `voyager-race` | `net.elytrarace.voyager.race.standing` (with `package-info`) |
| Seconds to GO | `voyager-race` | `net.elytrarace.voyager.race.flow` |
| Place line, personal best read at GO | `voyager-platform` | `net.elytrarace.voyager.platform.cup` |
| Wait title in the boss bar | `voyager-platform` | `net.elytrarace.voyager.platform.hud` |
| Wait sidebar | `voyager-platform` | `net.elytrarace.voyager.platform.lobby` |
| Messages (English and German) | `voyager-platform` | `net.elytrarace.voyager.platform.text` |

Dependency direction holds: `race` does not import `platform`, and the standing is a plain record that `platform` renders.
No new fitness rule is needed, because no new boundary is created.

## Open questions

1. **The new "Waiting for n racers" string.** It is needed for a racer who stands in the next map before the phase ends. It is new and
   needs the owner's acceptance in the code PR. Recommendation: accept.
2. **Pulse review.** `CLAUDE.md` requires the game-psychologist review of gameplay and UI decisions. The owner decides whether it happens
   before the code PR.
3. **German wording.** The German titles and the place line are drafts. A native reader checks them before the code PR.
