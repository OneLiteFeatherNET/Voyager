# Spec Delta

## Purpose

The tutorial book explains the race in a few pages, so a racer learns the rings, the rockets, the boost and slow rings, the
checkpoints and the medals before the start. It is a dialog the racer opens on purpose, in the racer's own language, and it
is never saved.

## ADDED Requirements

### Requirement: The tutorial opens only when the racer asks for it
**MoSCoW:** Must. THE SYSTEM SHALL open the tutorial dialog only when a racer right-clicks the tutorial book, and SHALL NEVER open
it by itself, at join, at the start or on any other event.

#### Scenario: A racer joins
- **WHEN** a racer joins the lobby
- **THEN** no tutorial dialog opens

### Requirement: The tutorial has five pages in a fixed order
**MoSCoW:** Must. THE SYSTEM SHALL show the tutorial as five pages in this order: how to play (rings and points), rocket boost and its
cooldown, the BOOST and SLOW rings, checkpoints and resets, and medals with their target times.

#### Scenario: Opening the tutorial
- **WHEN** the tutorial opens
- **THEN** the first page is "how to play"

#### Scenario: Going through the pages
- **WHEN** a racer presses Next on each page in turn
- **THEN** the pages appear in the order of this requirement

### Requirement: Each page has Back, Next and Close
**MoSCoW:** Must. THE SYSTEM SHALL give each page a Close button, a Back button that returns to the previous page, and a Next button
that goes to the next page. THE SYSTEM SHALL NOT show Back on the first page or Next on the last page.

#### Scenario: The first page
- **WHEN** the first page is shown
- **THEN** it has Close and Next, and no Back

#### Scenario: The last page
- **WHEN** the last page is shown
- **THEN** it has Back and Close, and no Next

### Requirement: The tutorial is shown in the racer's language
**MoSCoW:** Must. THE SYSTEM SHALL render every page, label and button in the racer's client locale, with German and English text for
every key, and with English as the fallback, as `player-messages` specifies. THE SYSTEM SHALL render the dialog for each racer
separately.

#### Scenario: A German racer
- **WHEN** a `de_DE` racer opens the tutorial
- **THEN** every page and button is in German, and an `en_US` racer on the same server sees English

### Requirement: The tutorial stores nothing
**MoSCoW:** Must. THE SYSTEM SHALL NOT store which pages a racer has read, whether a racer has opened the tutorial, or any choice in it.
Each opening starts at the first page.

#### Scenario: Reopening the tutorial
- **WHEN** a racer closes the tutorial on page three and opens it again
- **THEN** it opens on page one

### Requirement: The tutorial numbers agree with the race
**MoSCoW:** Should. WHERE a page states a rocket cooldown, a ring points value or a target time, THE SYSTEM SHALL state the value that
the race uses, taken from the same source as the race and not typed again in the page text.

#### Scenario: A cooldown in the text
- **WHEN** the rocket page states the rocket cooldown
- **THEN** the stated value is the cooldown the race uses for that map
