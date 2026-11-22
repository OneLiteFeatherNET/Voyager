# race/tutorial-book Specification

## Purpose
The tutorial book explains the race in a few pages, so a racer learns the rings, the rockets, the boost and slow rings, the
checkpoints and the medals before the start. It is a dialog the racer opens on purpose, in the racer's own language, and it
is never saved.

## Requirements

### Requirement: The tutorial opens only when the racer asks for it
**MoSCoW:** Must. THE SYSTEM SHALL open the tutorial dialog only when a racer right-clicks the tutorial book, and SHALL NEVER open
it by itself, at join, at the start or on any other event.

#### Scenario: A racer joins
- **WHEN** a racer joins the lobby
- **THEN** no tutorial dialog opens

### Requirement: The tutorial has five fixed pages and a sixth page for the armed cup
**MoSCoW:** Must. THE SYSTEM SHALL show the tutorial as five fixed pages in this order: how to play (rings and points), rocket boost and
its cooldown, the BOOST and SLOW rings, checkpoints and resets, and medals with their target times. THE SYSTEM SHALL show a sixth page,
"Next cup", after them, which names the armed cup and its maps in play order. THE SYSTEM SHALL build the sixth page each time the
tutorial is opened from the same board the lobby sidebar reads.

#### Scenario: Opening the tutorial
- **WHEN** the tutorial opens
- **THEN** the first page is "how to play"

#### Scenario: Going through the pages
- **WHEN** a racer presses Next on each page in turn
- **THEN** the pages appear in the order of this requirement, and the last page is "Next cup"

#### Scenario: The sixth page names the armed cup
- **WHEN** a racer opens the tutorial while the rotation's armed cup is "legacy_cup"
- **THEN** the "Next cup" page names "legacy_cup" and lists its maps in play order

#### Scenario: More maps than the page holds
- **WHEN** the armed cup has more maps than the sidebar holds lines for
- **THEN** the "Next cup" page lists the same first maps as the sidebar, and one line that counts the rest

#### Scenario: No rotation configured
- **WHEN** the server plays a single cup and no rotation is configured
- **THEN** the "Next cup" page names that cup

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
