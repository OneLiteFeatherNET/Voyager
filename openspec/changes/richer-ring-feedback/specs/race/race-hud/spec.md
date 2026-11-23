## MODIFIED Requirements

### Requirement: The action bar labels its clock and its ring count
**MoSCoW:** Must. WHILE a racer flies a map, THE SYSTEM SHALL show the action bar with the clock and the ring count, each named by
a translated label, in the form "Time 0:02.1 · Rings 0/35" in English and "Zeit 0:02.1 · Ringe 0/35" in German. THE SYSTEM SHALL
show the values in a bright palette colour and the labels in the dim palette colour. WHILE the ring counter is flashing after a
ring pass, THE SYSTEM SHALL show the ring's points as "+N" in the same fixed-width pad, in the OK palette colour. WHEN a
missed-ring, boost, slow or checkpoint line is due, THE SYSTEM SHALL show that line in place of the flight line, in the order of
precedence: missed ring, then checkpoint or boost or slow, then the flight line.

#### Scenario: The action bar in English
- **WHEN** the racer is in the locale `en_US`, has flown 2.1 seconds and has passed none of the 35 rings
- **THEN** the action bar reads "Time 0:02.1 · Rings 0/35"

#### Scenario: The action bar in German
- **WHEN** the racer is in the locale `de_DE` under the same conditions
- **THEN** the action bar reads "Zeit 0:02.1 · Ringe 0/35"

#### Scenario: The ring counter turns green just after a ring
- **WHEN** the racer passed a ring in the last few ticks
- **THEN** the ring count is shown in the palette's OK colour, and the labels are unchanged

#### Scenario: The points marker shows during the flash window
- **WHEN** the racer passes a ring worth 10 points in the last eight ticks
- **THEN** the action bar shows "+10" in the pad beside the ring count, in the OK colour, and the marker is gone when the flash ends

#### Scenario: A zero-point ring is still marked
- **WHEN** the racer passes a ring worth 0 points
- **THEN** the action bar shows "+0" for the flash window

#### Scenario: A missed ring takes precedence over a checkpoint line
- **WHEN** a missed-ring line and a checkpoint line are due in the same tick
- **THEN** the action bar shows the missed-ring line, and the checkpoint line is not shown
