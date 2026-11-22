# Spec Delta

## Purpose

A waiting racer has two items in the hotbar: a door that takes the racer back to the network hub, and a book that explains
the race. The items appear when the racer joins the lobby and disappear when the race starts. They cannot be dropped or moved
out of place, and nothing about them is saved.

## ADDED Requirements

### Requirement: The waiting racer receives the lobby items on join
**MoSCoW:** Must. WHEN a racer joins the lobby world, THE SYSTEM SHALL give that racer the leave item in slot 8 and the tutorial book in
slot 1, and SHALL keep the racer's other slots as they are.

#### Scenario: A racer joins the lobby
- **WHEN** a racer joins while no cup is running and a lobby world is configured
- **THEN** the racer's slot 8 holds the leave item and slot 1 holds the tutorial book

### Requirement: The items are removed when the race starts
**MoSCoW:** Must. WHEN the cup starts for a racer, THE SYSTEM SHALL remove the leave item and the tutorial book from that racer's
inventory, before the racer enters the first map.

#### Scenario: The race starts
- **WHEN** the GO tick of the first map is reached
- **THEN** neither the leave item nor the tutorial book is in the racer's inventory

### Requirement: The leave item sends the racer to the hub
**MoSCoW:** Must. WHEN a waiting racer right-clicks the leave item, THE SYSTEM SHALL send that racer to the network hub through the
installed hub handoff, as `cloudnet/hub-handoff` specifies. THE SYSTEM SHALL name the item "Leave" in English and "Verlassen" in
German, and give it a lore line that says where it sends the racer, in both languages.

#### Scenario: A right click on the leave item
- **WHEN** a waiting racer right-clicks slot 8
- **THEN** the racer is handed to the hub

### Requirement: The tutorial book opens the tutorial
**MoSCoW:** Must. WHEN a waiting racer right-clicks the tutorial book, THE SYSTEM SHALL open the tutorial dialog of `race/tutorial-book`
for that racer. THE SYSTEM SHALL name the book "Tutorial" in English and "Anleitung" in German.

#### Scenario: A right click on the book
- **WHEN** a waiting racer right-clicks slot 1
- **THEN** the tutorial dialog opens for that racer

### Requirement: The lobby items cannot be dropped or swapped
**MoSCoW:** Must. THE SYSTEM SHALL cancel any drop of the leave item or the tutorial book, and any swap of either into the off hand.

#### Scenario: A drop is cancelled
- **WHEN** a waiting racer drops the leave item
- **THEN** the item stays in the racer's inventory and nothing is dropped

#### Scenario: A swap is cancelled
- **WHEN** a waiting racer swaps the tutorial book into the off hand
- **THEN** the swap is cancelled and the book stays in its slot

### Requirement: The leave command and the hub command do what the item does
**MoSCoW:** Should. THE SYSTEM SHALL register `/leave` and `/hub` for every racer. Each sends the racer to the hub as the leave item
does, and each is refused with a translated line when the racer is not waiting in the lobby.

#### Scenario: The command from the lobby
- **WHEN** a waiting racer types `/leave`
- **THEN** the racer is handed to the hub

#### Scenario: The command during a race
- **WHEN** a racer who is in a map types `/hub`
- **THEN** the command is refused with a translated line and the racer stays in the race

### Requirement: The items are not saved
**MoSCoW:** Must. THE SYSTEM SHALL NOT save the lobby items, the tutorial state or any choice a racer makes in the tutorial.

#### Scenario: A racer joins again
- **WHEN** a racer who left the lobby joins again
- **THEN** the racer receives the items again, and no record of the earlier join is read
