# Spec Delta

## Purpose

The tutorial book gains a sixth page that names the cup a waiting racer will play next, with its maps in play order. The five
explanatory pages stay as `cygnus-style-lobby` specifies them. The book still stores nothing.

Depends on `cygnus-style-lobby`: the base requirement below is written there and is not yet archived.

## RENAMED Requirements

- FROM: `### Requirement: The tutorial has five pages in a fixed order`
- TO: `### Requirement: The tutorial has five fixed pages and a sixth page for the armed cup`

## MODIFIED Requirements

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
