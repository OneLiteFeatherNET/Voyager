# Spec Delta

## Purpose

A waiting racer reads the lobby without looking at the sidebar: the action bar says how many racers are missing or how long
the start is, and the experience bar drains with the countdown. Every lobby line is translated, carries one strong accent,
and is drawn for that racer alone.

## ADDED Requirements

### Requirement: Lobby action bar while waiting
**MoSCoW:** Must. WHILE a racer waits in the lobby and the minimum is not met, THE SYSTEM SHALL show that racer on the action bar
"Noch N Spieler bis zum Start" in German and "Need N more players to start" in English, where N is the minimum minus the racers
online, with the singular key for N = 1. WHILE the countdown runs, THE SYSTEM SHALL show "Start in N s" in both languages, with
N the whole seconds left.

#### Scenario: One racer short in English
- **WHEN** one racer of a minimum of two is online, in a racer whose language is English
- **THEN** the action bar reads "Need 1 more player to start" (the singular key of the two plural forms of `player-messages`)

#### Scenario: One racer short in German
- **WHEN** one racer of a minimum of two is online, in a racer whose language is German
- **THEN** the action bar reads "Noch 1 Spieler bis zum Start"

#### Scenario: The countdown replaces the count
- **WHEN** the minimum is met and twelve seconds of the lobby remain
- **THEN** the action bar reads "Start in 12 s" for that racer

### Requirement: The experience bar counts down the lobby
**MoSCoW:** Must. WHILE a racer waits in a countdown, THE SYSTEM SHALL set the racer's experience level to the whole seconds left
and the experience bar to the fraction of the lobby that remains. WHEN the countdown is cancelled, or the racer leaves the lobby,
THE SYSTEM SHALL set the experience level and bar to zero for that racer.

#### Scenario: Ten seconds left
- **WHEN** ten seconds of a twenty second lobby remain
- **THEN** the racer's level reads 10 and the bar is half full

#### Scenario: A cancelled countdown clears the bar
- **WHEN** the countdown is cancelled with eleven seconds left
- **THEN** the racer's level reads 0 and the bar is empty

### Requirement: Lobby lines carry one strong accent
**MoSCoW:** Should. THE SYSTEM SHALL write each lobby line with one accent palette colour besides the dim structure colour, and
SHALL NOT use the dim colour alone for information, so that a racer can read the countdown and the count at a glance.

#### Scenario: The countdown line is not dim
- **WHEN** the lobby countdown line is shown
- **THEN** its number is in an accent colour, and only the words around it are dim

### Requirement: Every lobby line is translated and rendered in the racer's language
**MoSCoW:** Must. THE SYSTEM SHALL build every lobby line from a translation key, and SHALL render it in the racer's client locale
with English as the fallback, as `player-messages` specifies. THE SYSTEM SHALL NOT build a lobby line from a literal string in
code.

#### Scenario: A German racer sees German
- **WHEN** a `de_DE` racer is in the lobby
- **THEN** the action bar, the sidebar title's frame lines, the status line and the footer are German

### Requirement: The lobby HUD is per racer
**MoSCoW:** Must. THE SYSTEM SHALL send the action bar, the experience bar and the sidebar to each waiting racer separately, so that
each one is rendered in that racer's language. THE SYSTEM SHALL keep the hologram as the one shared display of the lobby, under
`race/lobby-cup-display`.

#### Scenario: Each racer gets their own action bar
- **WHEN** a `de_DE` racer and an `en_US` racer wait in the same room with one racer short
- **THEN** each sees the action bar in its own language

### Requirement: Without a lobby world the lobby HUD shows in the first map
**MoSCoW:** Must. WHERE no lobby world is configured, THE SYSTEM SHALL show the lobby HUD of this capability to a waiting racer in
the first map until the start is committed, and SHALL hide it when the start is committed.

#### Scenario: No lobby configured
- **WHEN** no lobby world is configured and one racer waits in the first map with one of two racers online
- **THEN** that racer's action bar shows the missing count, and the action bar is cleared when the start is committed
