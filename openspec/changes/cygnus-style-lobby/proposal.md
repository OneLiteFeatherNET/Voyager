# Proposal

**Type and scope:** `feat(race)`. Ships as one code change under this title. It reworks the waiting lobby (the room before a
cup), its items and its tutorial. It does not change the cup, the race, the scoring or the course.

## Why

A racer who joins the lobby waits up to twenty seconds, and the waiting room tells them very little. The only line that
says how many racers are missing is on a boss bar that covers the top of the screen. The start countdown is a grey number
in that bar. The sidebar lists maps with numbers that look like a ranking, and it prints map ids. Every lobby line is grey, so
nothing draws the eye. The hub exit is missing: a racer who wants out has to use the proxy's own menu or disconnect. Nothing
in the lobby explains how to fly. The sibling game Cygnus solved the same waiting room with an action bar, an XP-bar
countdown, a tidy sidebar, hotbar items and a tutorial book; this change brings those patterns here and translates them.

## What Changes

- **Action bar while waiting.** Below the minimum, the action bar reads "Noch N Spieler bis zum Start" in German and "Need
  N more players to start" in English. This replaces the count line on the boss bar.
- **XP-bar countdown.** The seconds left of the start countdown show as the experience level, and the bar drains from full
  to empty over the lobby.
- **Boss bar removed.** The lobby boss bar is gone, for a racer in the lobby world and for a racer in the first map when no
  lobby world is configured.
- **Tidy sidebar.** The sidebar title is the cup's display name. Its lines are the maps by display name in play order, with
  no score numbers. It also shows the racers online out of the maximum, a status line (waiting, or the seconds to the
  start) and a footer line.
- **Strong colours.** Each lobby line carries one accent colour; grey is used for structure only.
- **Translated and per racer.** Every lobby line is a translation key, rendered in the racer's locale. The sidebar, the action
  bar and the XP bar are per racer. The hologram is one shared entity, so it shows only the display names, which are not
  translated (decision in the design).
- **Sounds.** The lobby countdown plays the note-block pling at 10, 5, 3, 2, 1 and 0 seconds, with the volumes and pitches of
  Cygnus. A soft sound plays when another racer joins. A distinct sound plays when a full lobby shortens the countdown.
- **Titles.** The 3, 2 and 1 digits keep their large title; the title colour is raised to the strong accent.
- **Hotbar items.** On lobby join a racer gets a leave item in slot 8 (a dark oak door, translated name and lore) and a
  tutorial book in slot 1. The items are removed at race start. Dropping and swapping them is cancelled.
- **Leave to hub.** Right-clicking the leave item, or running `/leave` or `/hub`, sends the racer to the network hub with
  the existing hub handoff. It does not mark the racer as handed off, so the waiting room's bookkeeping for a finished cup is
  untouched.
- **Tutorial book.** Right-clicking the book opens a multi-page dialog in the racer's language: how to play, rockets and their
  cooldown, BOOST and SLOW rings, checkpoints and resets, and medals with their target times. It has Back, Next and Close. The
  book never opens by itself, and nothing about it is saved.
- **Dependency.** The dialogs use the Pica library `net.onelitefeather:pica` at version 0.1.6, which Cygnus also uses. The
  architecture decision is recorded as a proposed ADR (design, section "ADR-0039 (proposed)").

## Capabilities

### New Capabilities

- `race/lobby-hud`: the waiting action bar, the XP-bar countdown, the lobby colours, the translation and per-racer rendering
  of the lobby lines.
- `race/lobby-feedback`: the countdown sounds and titles of the lobby, the join sound and the full-lobby sound.
- `race/lobby-items`: the leave item, the tutorial book, when the items are given and removed, and the cancelled drop and
  swap.
- `race/tutorial-book`: the dialog pages, their languages, the buttons, and the rule that nothing is saved and nothing opens by
  itself.

### Modified Capabilities

- `race/waiting-lobby`: the count line moves from the boss bar to the action bar; the boss bar requirements are removed; the
  countdown is read on the XP bar; the full-lobby scenario names the XP bar and the sound; the golden exception of
  `keep-spawn-facing-at-go` is extended to the lobby action bar lines.
- `race/lobby-cup-display`: the board no longer feeds a boss bar; the sidebar has a title, a racer count, a status line and a
  footer; the sidebar holds at most ten map lines; the hologram shows display names only.
- `cloudnet/hub-handoff`: a racer may leave to the hub from the lobby, through the item or the commands, without the
  handed-off bookkeeping.

## Out of scope

- The race boss bar and race action bar (`polish-race-hud`).
- The cup, the course, the scoring, and the rocket count.
- Cygnus's lobby-return event flow. Voyager sends the racer directly through `HubHandoff.send`, as the owner decided.
- Persisting anything: the tutorial, the items and the XP state are not saved.

## Impact

- `voyager/platform` lobby, hud and text (`WaitingRoom`, `LobbyBoard`, `LobbySidebar`, `LobbyHologram`, `LobbyHud`,
  `Messages`, `Racers`), a new lobby items and tutorial slice.
- `voyager/server` composition root: the items' event node, the `/leave` and `/hub` commands and the Pica dependency.
- `voyager/platform/build.gradle.kts` for Pica, and `settings.gradle.kts` for its version in the programmatic catalogue.
- Bundles `voyager_en_US.properties` and `voyager_de_DE.properties`.
- Golden masters: the lobby action bar lines of `lobby-and-start.txt` and `lobby-world-start.txt` change, as the design
  states, and are reviewed in the code PR.
