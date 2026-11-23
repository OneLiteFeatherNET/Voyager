## ADDED Requirements

### Requirement: The line follows the layout after a catalogue reload
**MoSCoW:** Must. WHEN a catalogue reload applies, THE SYSTEM SHALL draw the racing line of every map whose layout identity changed
from the reloaded geometry, and SHALL NOT draw a line computed from the geometry the reload replaced.

#### Scenario: A ring moved by a reload
- **WHEN** a map's ring is moved in its file and `/race reload` applies the catalogue
- **THEN** the racing line drawn to a racer passes through the moved ring's new centre, and no segment of the line is drawn from the
  old centre

#### Scenario: A reload that changes no geometry
- **WHEN** a reload applies and a map's layout identity is unchanged
- **THEN** the line for that map is kept from before the reload, and it is not recomputed

#### Scenario: A reload that changes only a display name
- **WHEN** a reload changes a map's display name or its authors and not its rings, guide points or spawn
- **THEN** the layout identity is unchanged and the line is kept

#### Scenario: A refused reload keeps the old line
- **WHEN** a reload is refused and the catalogue stays as it was
- **THEN** the line for every map is kept as it was
