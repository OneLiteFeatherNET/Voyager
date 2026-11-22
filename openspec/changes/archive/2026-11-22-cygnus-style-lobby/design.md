# Design

## Context

The waiting room is `WaitingRoom` (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/lobby/`). It owns the
online count, the countdown and the cup that waiting racers see. Its displays are built from verified code at `origin/main`:

| Display | Site | Today |
|---|---|---|
| Lobby boss bar | `LobbyHud.java:26` (colour WHITE), `:52-53` (`BossBar.bossBar`, `showBossBar`), `:76` (`hideBossBar`); `WaitingRoom.java:327-329` chooses the title | count and countdown in a bar at the top |
| Count and countdown texts | `Messages.java:229` `lobbyWaiting`, `:240` `lobbyCountdown`; keys `voyager.lobby.*` (`Messages.java:51-62`) | `Component.text` and translated keys mixed |
| Sidebar | `LobbySidebar.java:92-94`: score is `lines.size() - index` | map lines with fake rank numbers |
| Board | `LobbyBoard.java:43` (`new LobbyBoard(cup.name(), cup.mapNames())`), `:48`, `:60`, `:64` | ids |
| Board text | `Messages.java:305-306` (`Component.text(cup)`), `:317-318` (`Component.text("%d. %s")`), `:328` (translated "more") | two hard-coded lines |
| Hologram | `LobbyHologram.java:71-76` (title, newline, lines) | one shared text entity |
| Race start | `Racers.prepare` (`flight/Racers.java:68`) clears the inventory at `:71` | nothing is kept |
| Firework use | `VoyagerServer.java:446-457` (`PlayerUseItemEvent`, rockets) | the only item listener |
| Hub handoff | `platform/handoff/HubHandoff.java:77` `send(Collection<Player>)`; `WaitingRoom` keeps `handedOff` (`:75`, added `:264`, removed `:178`, filtered `:287`) | used for finished cups |
| Race countdown titles | `hud/RaceFeedback.java:127-132` (`countdown`) | digit titles |
| Commands | `VoyagerServer.java:278-281`, `:298` register commands | `/race`, `/stats`, `/leaderboard`, `/stop` |

Cygnus is the reference, read at `origin/main` of `/mnt/projects/oss/onelitefeather/Cygnus` (the local checkout is behind
it). Verified there: the countdown sounds in `game/src/main/java/net/onelitefeather/cygnus/phase/task/
LobbyCountdownSoundTask.java:28-34`; the lobby hints (leave and tutorial items, join audio hint) in `game/src/main/java/
net/onelitefeather/cygnus/lobby/LobbyHintsFeature.java`; the tutorial commit `ca6b8cc4` ("feat(tutorial): add a lobby
tutorial for both roles (#441)") with `tutorial/TutorialFeature.java` and `tutorial/TutorialPages.java`, reachable from
`origin/main`; the item slots `LOBBY_SLOT = 8` (`DARK_OAK_DOOR`) and `TUTORIAL_SLOT = 1` (`ENCHANTED_BOOK`) in
`utils/Items.java`. Cygnus's action-bar text is hard-coded English in `phase/task/LobbyWaitingTask.java` (read in the local checkout, not
re-verified on `origin/main`); Voyager does not copy that, because every line here is translated.

## Facts that shape the design

1. **The golden harness runs the waiting room.** `CupSessionGoldenMasterTest.java:483` builds `WaitingRoom` with
   `new LobbyHud()`, and the transcripts record `Ada actionbar ""` in the LOBBY phase (for example line 8 of
   `golden/cup-session/lobby-and-start.txt` and `lobby-world-start.txt`). The new lobby action bar changes those lines. This
   is an expected, reviewed golden change, listed in the "Golden masters" section below and in the modified
   `race/waiting-lobby` scenario.
2. **Boss bars are not in the transcripts.** No golden file records a boss bar, so removing the lobby boss bar does not touch
   a golden file.
3. **The sidebar has a fifteen-line limit.** The client shows at most fifteen sidebar lines (to verify in the spike, task 2.1).
   Today the sidebar can hold twelve map lines plus a title; a title, a racer line, a status line and a footer need room
   too, so the map lines drop to ten.
4. **The hologram is one entity.** A text display is one entity; every viewer sees the same text. A translated text cannot
   follow each racer's language, so the hologram can show only the display names, which are proper nouns (`add-display-names`).
5. **Racers' facing and inventory.** `Racers.prepare` clears the inventory at race start, so the items disappear without new
   code. The lobby items are given on join and need the drop and swap cancellation only.

## Decisions

1. **Action bar while waiting** (`race/lobby-hud`). Below the minimum, the action bar shows "Noch N Spieler bis zum Start" /
   "Need N more players to start", with the singular key for N = 1. The count moves off the boss bar. During the countdown the
   action bar shows "Start in N s" in both languages, the same words, so the German key and the English key share the
   structure.
2. **Experience-bar countdown** (`race/lobby-hud`). The experience level is the whole seconds left, and the bar is the
   fraction of the lobby left. On cancel or on leaving the lobby, level and bar go to zero. Nothing is saved, so a racer's
   earlier experience is not restored; Minestom gives no experience in the lobby, and the risk is recorded below.
3. **No boss bar in the lobby** (`race/waiting-lobby` REMOVED). `LobbyHud` stops creating and showing a boss bar. Its
   `LobbyBoard` is still built, for the sidebar and the hologram.
4. **Sidebar** (`race/lobby-cup-display`). The title is the cup's display name. Lines: the racers line ("Racers 3/16"), a
   blank, up to ten map lines (the first nine and "+N more" when the cup has more than ten), a blank, the status line
   ("waiting" or "starts in N s") and a footer ("Elytra Race" / "Elytra-Rennen"). Map lines are "1. Blue & Red" with the
   position in play order, as `Messages.lobbyBoardMap` already formats them, now translated.
5. **No score numbers.** The sidebar line is created with a blank number format so that no number appears in the score
   column. If Minestom 2026.08.28-26.2 has no blank number format, the fallback is a score of zero with the number hidden by
   the client's rendering; the spike (task 2.2) chooses, and the decision is recorded in the PR. The owner's rule is
   "no fake score numbers", so a visible number is not acceptable.
6. **Hologram** (`race/lobby-cup-display`). One shared entity. It shows the cup's display name and the display names of its
   maps. The "+N more" line is not on the hologram: it is the one translated line, and a shared entity cannot translate it per
   racer. The sidebar keeps it. This is the decision the owner asked for.
7. **Per-racer rendering.** The sidebar, the action bar and the experience bar are sent to each racer by that racer's
   `Player`, so each uses the racer's client locale through Minestom's automatic component translation. The lobby
   `LobbyBoard` text is built from keys only, so no literal reaches a racer.
8. **Lobby colours.** Each lobby line has one accent: numbers in `Palette.VALUE` (white) or `Palette.OK` (green) and words in
   `Palette.DIM` (grey, 6.13 to 1 on `#101010`). The countdown digits of the title are in `Palette.OK`. This follows
   `message-style` (one accent per line) and the contrast table of `polish-race-hud`.
9. **Countdown pling** (`race/lobby-feedback`). The table is from `LobbyCountdownSoundTask.java:28-34` in Cygnus, unchanged:
   10 s (0.5, 1.000), 5 s (0.6, 0.944), 3 s (0.7, 0.667), 2 s (0.75, 0.630), 1 s (0.85, 0.561), 0 s (0.9, 0.500). The pling
   is `BLOCK_NOTE_BLOCK_PLING` on the MASTER source.
10. **Join sound and full-lobby sound** (not given by the owner, chosen here). A join plays `ENTITY_ITEM_PICKUP` at 0.4 and
    pitch 1.0 to the racers already waiting. A shortened countdown plays `BLOCK_NOTE_BLOCK_CHIME` at 0.8 and pitch 1.0 once.
    Both differ from the ring, reset, DNF and missed-ring sounds of `race-feedback`; the spike (task 2.3) checks the race sample
    names, and the test in `race/lobby-feedback` keeps them apart.
11. **Titles.** The 3, 2 and 1 digit titles are the existing `RaceFeedback.countdown` (`RaceFeedback.java:127-132`), with the
    digit in the strong accent.
12. **Items** (`race/lobby-items`). Slot 8: `DARK_OAK_DOOR`, the leave item. Slot 1: `ENCHANTED_BOOK`, the tutorial book.
    Both are the Cygnus slots (`Items.java`, `LOBBY_SLOT` and `TUTORIAL_SLOT`). The owner allowed slot 0 or 1; slot 1 is chosen
    to match Cygnus. They are given on join and removed by `Racers.prepare`. Drop and swap are cancelled by listeners on
    `ItemDropEvent` and `PlayerSwapItemEvent`, as Cygnus does in `LobbyHintsFeature`.
13. **Leave goes straight to the hub** (`cloudnet/hub-handoff`). The item and the commands call `HubHandoff.send(List.of(player))`
    directly, as the owner decided. They do not use Cygnus's `LobbyReturnRequestEvent` and do not kick. The handed-off set of
    `WaitingRoom` is not touched. Without an installed handoff, the local fallback of `cloudnet/hub-handoff` disconnects with a
    translated message.
14. **`/leave` and `/hub`** are the same action, refused with a translated line when the racer is in a map (not in the lobby).
15. **Tutorial** (`race/tutorial-book`). Five pages, opened only by right-click on the book, with Back, Next and Close. Its
    text is keys in both bundles. Its numbers (rocket cooldown, points) come from the same source as the race; the page text
    does not restate them. The dialog library is decision 16.
16. **Dialogs use Pica 0.1.6** (ADR-0039, proposed, below).

## Golden masters

- The LOBBY-phase action bar records in `golden/cup-session/lobby-and-start.txt` and `lobby-world-start.txt` change from the
  empty string to the lobby line for the racer. This is the only expected golden change of this change. It is reviewed line by
  line in the code PR and recorded in the PR description.
- No boss bar and no sidebar text is in a golden file, so those change freely.
- The flight action bar (`voyager.hud.flight`) is not changed here; `polish-race-hud` owns it.

If a lobby line changes in any other transcript, the change is wrong and is reopened.

Two changes modify the same requirement, "Once a cup has started, its per-tick behaviour is unchanged":
`keep-spawn-facing-at-go` (GO facing) and this change (lobby action bar). The delta of this change already names both
exceptions in its scenario. Whichever change archives second takes this text, so the two exceptions are kept together and
neither is dropped.

## Architecture

| Type | Module | Package | Direction |
|---|---|---|---|
| `LobbyHud` (edited: action bar, experience bar; boss bar removed) | `voyager-platform` | `platform.lobby` | uses Minestom and `Messages` |
| `LobbyBoard`, `LobbySidebar`, `LobbyHologram` (edited) | `voyager-platform` | `platform.lobby` | the board reads the display names from the snapshot |
| `WaitingRoom` (edited) | `voyager-platform` | `platform.lobby` | no new bookkeeping |
| `LobbyCountdownSounds` (pure table, seconds to sound) | `voyager-platform` | `platform.lobby.sound` | no DI annotation |
| `LobbyItems` (give, remove, cancel listeners) | `voyager-platform` | `platform.lobby.items` | registers on an `EventNode` given by the server |
| `TutorialBook` (dialogs with Pica), `TutorialPages` (page model) | `voyager-platform` | `platform.tutorial` | the only package that imports `net.onelitefeather.pica..` |
| `LeaveCommand`, `HubCommand` | `voyager-server` | `server.command` | composition root; DI in `ServerBeans` |
| listener wiring | `voyager-server` | `server` | `VoyagerServer` wires the nodes |

Dependency direction: the platform slice depends on `voyager-api` and on Minestom and Pica; `voyager-api` depends on neither.
The composition root wires the commands and the event nodes. DI annotations stay in `voyager-server`, as
`dependency-injection-boundaries` requires.

### voyager-fitness

- A new rule: `net.onelitefeather.pica..` may be imported only from `net.elytrarace.voyager.platform.tutorial..`. It is a new
  `@ArchTest` with `allowEmptyShould(false)`, in the fitness module, which `FitnessCoverageTest` already requires for the
  platform module.
- The existing rules hold: no Minestom type enters `voyager-api`, no DI annotation enters the platform, and the literal-text
  rule of `ApiPurityTest` stays, because every lobby line comes from a key.

## ADR-0039 (proposed): Pica dialogs for the tutorial book

The repository's last ADR is 0038 (`docs/decisions/0038-player-message-i18n.md`), so the next free number is 0039. The
owner's rules for an OpenSpec proposal PR allow only files under `openspec/changes/<change>/`, so this ADR is not added to
`docs/decisions/` in this PR. It is written here, and the code PR that adds the dependency (task 5.1) adds it to
`docs/decisions/0039-tutorial-dialogs-with-pica.md` with the status below.

```markdown
# ADR-0039: Tutorial dialogs use the Pica library

## Status

Proposed, on 2026-10-27, with the change `cygnus-style-lobby`. Not accepted until the owner accepts it.

## Date

2026-10-27

## Decision makers

- TheMeinerLP (project owner), who asked for the tutorial book and for Pica.

## Context and problem statement

A waiting racer opens a tutorial from a hotbar book. The tutorial has five pages with Back, Next and Close buttons, in the
racer's language. Minestom 2026.08.28-26.2 can send dialogs, but the building of a multi-page dialog with buttons is
verbose, and the rest of the OneLiteFeather games (Cygnus) build the same tutorial with Pica.

## Decision drivers

- Buttons with actions for Back, Next and Close, per page, in the racer's language.
- The same pattern as Cygnus, so one team maintains one way to build dialogs.
- One new dependency with a clear version pin and a clear repository.
- No new state: a dialog is built per racer and not stored.

## Considered options

1. **Pica 0.1.6 (`net.onelitefeather:pica`), used only in `platform.tutorial` (chosen).** The same library Cygnus uses.
2. **Hand-built dialog packets through the Minestom API.** No dependency, but the page model, the buttons and the packet
   layout are written and tested here.
3. **A vanilla written book.** Native paging, no buttons, no per-page actions, and the book text is an item stored in the
   player's inventory.
4. **Chat pages.** Simple, but there are no buttons, and the pages scroll away.

## Decision outcome

Option 1. The library is pinned at `0.1.6` in the programmatic version catalogue of `settings.gradle.kts`, because the
`aonyx-bom` (0.8.9) that the platform already imports could not be read anonymously to confirm that it manages Pica. The
dependency resolves from `https://repo.onelitefeather.dev/releases`; the Pica 0.1.6 POM answers HTTP 200 there without
credentials. Pica is imported only by `net.elytrarace.voyager.platform.tutorial`, enforced by a `voyager-fitness` rule.

### Consequences

- Good: one dialog library across the OneLiteFeather games, with the buttons and the actions provided.
- Good: the page model stays pure and testable; the library is confined to one package.
- Bad: a new dependency to keep current. The version is pinned and changed only by a PR.
- Bad: the library's Minestom version must match `2026.08.28-26.2`. Task 2.4 checks that the dialog opens on this server.
- Neutral: the owner's Cygnus reference uses Pica 0.1.6 on `origin/main`.

## Links

- `openspec/changes/cygnus-style-lobby/` (proposal, design, specs, tasks).
- `voyager/platform/src/main/java/net/elytrarace/voyager/platform/tutorial/` (the code that uses it, once implemented).
```

## Open risks

- Minestom's blank sidebar number format may not exist; decision 5 has a fallback, and the spike decides before the code.
- The lobby experience bar overwrites a racer's own experience while they wait; nothing is restored (decision 2).
- Pica 0.1.6 must open a dialog on Minestom 2026.08.28-26.2. If the spike fails, the decision is reopened, not patched.
- The hologram's shared text loses the "+N more" line (decision 6); the owner may want a second hologram line with the count
  in the server's language, which would be a new decision.
- `/leave` during a race is refused, so a racer who wants out mid-race uses the normal disconnect, not this command.
