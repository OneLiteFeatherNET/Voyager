## MODIFIED Requirements

### Requirement: Normal boot refuses with the same report
Priority: Should. A server started without the check property SHALL refuse to start on an invalid configuration, and the refusal message SHALL list every error from the same report, not only the first. WHEN a server with a cup rotation starts without the check property, THE SYSTEM SHALL check each rotation world's folder and region headers, and SHALL NOT read every chunk of those worlds; the full chunk read is the job of the config check only.

#### Scenario: boot refusal lists every error
- **WHEN** the server starts normally and two errors exist
- **THEN** the refusal names both, not only the first

#### Scenario: rotation boot does not read every chunk
- **WHEN** the server starts normally with a cup rotation of three cups
- **THEN** no chunk of any rotation world is loaded during boot, and the folder and region headers of each world are checked

#### Scenario: config check reads every chunk
- **WHEN** the server is started with `voyager.config.check` set
- **THEN** every chunk of every map's world is read, and a corrupt chunk is reported as an error naming the region file

## ADDED Requirements

### Requirement: A corrupt chunk outside the armed cup is reported when that cup is armed
Priority: Should. WHEN a rotation cup that is not armed contains a world with an unreadable region, THE SYSTEM SHALL report it when that cup is armed for a round, and the report SHALL name the region file and the cup.

#### Scenario: a corrupt world in a later cup
- **WHEN** the second cup of a rotation contains an unreadable region and the server boots on the first cup
- **THEN** boot proceeds, and the problem is reported when the second cup is armed, naming the region file and the cup

### Requirement: The boot cost is measured before and after
Priority: Should. THE SYSTEM SHALL record the cold boot time of a three-cup rotation before and after this change, in the code pull request, so that the gain is measured rather than assumed.

#### Scenario: a measured boot
- **WHEN** the change is ready for review
- **THEN** the pull request names the cold boot time before and after, on the same machine and the same worlds
