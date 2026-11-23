# Spec Delta

## Purpose

A racer who is racing has no way out but the race, which is the rule that keeps a run honest. Between maps, and after a racer has
finished their own run, the racer may leave. The leave item and the hub command follow that rule, and the items return for the wait.

## MODIFIED Requirements

### Requirement: The items are removed when the race starts
**MoSCoW:** Must. WHEN the GO tick of a map is reached, THE SYSTEM SHALL remove the leave item and the tutorial book from each
racer's inventory on that tick, before the racer enters the map. THE SYSTEM SHALL keep both items in the inventory on every
earlier tick, including the ticks of the committed window that come before GO. THE SYSTEM SHALL return the leave item between maps
as `race/lobby-items` "The leave item is available between maps and after a finish" specifies.

#### Scenario: The items are still held in the committed window
- **WHEN** the start is committed and the GO tick is still three seconds ahead
- **THEN** the racer's slot 8 still holds the leave item and slot 1 still holds the tutorial book

#### Scenario: The race starts
- **WHEN** the GO tick of a map is reached
- **THEN** neither the leave item nor the tutorial book is in the racer's inventory, and the removal happens on that tick

### Requirement: The leave command and the hub command do what the item does
**MoSCoW:** Should. THE SYSTEM SHALL register `/leave` and `/hub` for every racer. Each sends the racer to the hub as the leave item
does, and each is refused with a translated line when the racer is racing and has not finished the map.

#### Scenario: The command from the lobby
- **WHEN** a waiting racer types `/leave`
- **THEN** the racer is handed to the hub

#### Scenario: The command during a race
- **WHEN** a racer who is in a map and has not finished it types `/hub`
- **THEN** the command is refused with a translated line and the racer stays in the race

#### Scenario: The command between maps
- **WHEN** a racer waiting between two maps types `/hub`
- **THEN** the racer is handed to the hub

## ADDED Requirements

### Requirement: The leave item is available between maps and after a finish
**MoSCoW:** Should. WHILE a racer waits between two maps of a cup, or has finished its own run on the map that is being raced, THE
SYSTEM SHALL give the racer the leave item in slot 8, and SHALL let the racer use the leave item and `/hub`. WHILE a racer is racing and
has not finished, THE SYSTEM SHALL NOT give the leave item and SHALL refuse `/hub`.

#### Scenario: The item returns for the wait
- **WHEN** a map's race phase ends and the wait for the next map begins
- **THEN** the racer's slot 8 holds the leave item, and a right click hands the racer to the hub

#### Scenario: A finished racer may leave
- **WHEN** a racer finishes a map and right-clicks the leave item during the racer's glide-out
- **THEN** the racer is handed to the hub

#### Scenario: A racer still racing may not leave
- **WHEN** a racer who has not finished the map right-clicks slot 8 during the map
- **THEN** the racer is not handed to the hub, and the racer stays in the race
