# setup/map-status Specification

## Purpose
The builder can ask what is missing from a draft. The status command lists the items that block publication, so a
builder does not have to read the JSON file to know what is left.

## Requirements

### Requirement: Status lists spawn, ring count and blocking problems
**Priority:** MoSCoW Must

WHEN a builder runs `/map status` in an open map, THE SYSTEM SHALL reply with the spawn state (set or not set), the ring
count, and one line per blocking problem. A blocking problem is a missing spawn or a draft with no rings.

#### Scenario: Empty draft
- **WHEN** a builder runs `/map status` on a new draft
- **THEN** the reply says the spawn is not set, the ring count is 0, and lists both problems

#### Scenario: Complete draft
- **WHEN** a builder runs `/map status` on a draft with a spawn and 35 rings
- **THEN** the reply says the spawn is set, the ring count is 35, and lists no problems

### Requirement: Status reads the saved draft
**Priority:** MoSCoW Must

THE SYSTEM SHALL compute the status from the draft that was last saved successfully, so that the reply matches the file.

#### Scenario: Status after a failed save
- **WHEN** a placement failed to save and the builder runs `/map status`
- **THEN** the ring count does not include the failed ring
