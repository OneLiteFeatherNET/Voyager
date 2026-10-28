# Spec Delta

## Purpose

The lobby items stay in a waiting racer's hotbar through the whole countdown, including the committed window, and are removed
on the GO tick, not three seconds earlier. Everything else about the items (the slots, the names, the drop and swap rules, the
leave command and the lack of saved state) stays as `race/lobby-items` specifies.

## MODIFIED Requirements

### Requirement: The items are removed when the race starts
**MoSCoW:** Must. WHEN the GO tick of the cup is reached, THE SYSTEM SHALL remove the leave item and the tutorial book from each
racer's inventory on that tick, before the racer enters the first map. THE SYSTEM SHALL keep both items in the inventory on every
earlier tick, including the ticks of the committed window that come before GO.

#### Scenario: The items are still held in the committed window
- **WHEN** the start is committed and the GO tick is still three seconds ahead
- **THEN** the racer's slot 8 still holds the leave item and slot 1 still holds the tutorial book

#### Scenario: The race starts
- **WHEN** the GO tick of the first map is reached
- **THEN** neither the leave item nor the tutorial book is in the racer's inventory, and the removal happens on that tick
