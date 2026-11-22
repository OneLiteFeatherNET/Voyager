# cup-validation Specification

## Purpose
Scopes boot-time cup validation to the cup that will be played. The server resolves the selected cup and validates
only that cup in full, so a broken cup nobody plays cannot stop boot. Problems in other cups become one aggregated
WARN event and those cups are never played. An unnamed selection with several cup files, or a named selection that
matches a malformed file, is still refused with a message that names the files involved.

## Requirements

### Requirement: The selected cup is validated in full at boot
**MoSCoW:** Must. When the server boots, the server SHALL resolve every cup named by its selection (the cup of `VOYAGER_CUP`, or
each cup of `VOYAGER_CUP_ROTATION`) and validate each of them completely: its definition parses and every map it names resolves.
The check of any cup that the selection does not name SHALL NOT be a precondition for boot. THE SYSTEM SHALL validate the cups of
the rotation one cup at a time, and SHALL release one cup's worlds before it checks the next cup's worlds.

#### Scenario: Selected cup is consistent
- **WHEN** the server boots with a selected cup whose file parses and whose maps all resolve, and another cup file is malformed
- **THEN** boot completes and the selected cup is the one played

#### Scenario: Selected cup names an unknown map
- **WHEN** the selected cup names a map that no map definition provides
- **THEN** boot is refused with an error listing every such entry of that cup, in the existing message form

#### Scenario: Every cup of a rotation is consistent
- **WHEN** the server boots with a rotation whose cups all parse and whose maps all resolve, and another cup file is malformed
- **THEN** boot completes, and every cup of the rotation is playable

#### Scenario: A rotation cup names an unknown map
- **WHEN** a cup of the rotation names a map that no map definition provides
- **THEN** boot is refused with an error listing every such entry of that cup, in the existing message form

### Requirement: Boot refusal for the selected cup keeps its message quality
MoSCoW: Must. If the selected cup fails validation, then the server SHALL refuse boot with the existing exception type and message shape, and the message SHALL list every failing entry of the selected cup and no entry of any other cup.

#### Scenario: Several failing entries in the selected cup
- **WHEN** the selected cup has two entries naming unknown maps and another cup has an unknown map
- **THEN** the refusal lists the two entries of the selected cup and does not mention the other cup

### Requirement: Problems in other cups produce one aggregated warning
MoSCoW: Must. While a cup other than the selected cup has a problem, the server SHALL boot and SHALL log exactly one WARN event that names each such cup and each of its problems.

#### Scenario: Two unplayable cups
- **WHEN** two non-selected cups have an unknown map and an unparseable file respectively
- **THEN** exactly one WARN event is logged, and it contains both problems and the cup or file name of each

#### Scenario: No warning when every cup is consistent
- **WHEN** every cup file parses and every map of every cup resolves
- **THEN** no cup WARN event is logged

### Requirement: Only the selected cup is playable
**MoSCoW:** Must. The server SHALL NOT play any cup other than the cups of its selection, and a cup with a reported definition
problem SHALL NOT be selected by any resolution rule. A cup skipped at boot for missing worlds SHALL NOT be played until the next boot.

#### Scenario: Unselected broken cup is never played
- **WHEN** the server boots with cup "alpha" selected and cup "beta" has an unresolved map
- **THEN** every map played belongs to "alpha", and "beta" is absent from play

#### Scenario: A skipped cup is not played
- **WHEN** the rotation is "alpha,beta", the worlds of "beta" are missing at boot, and "alpha" is playable
- **THEN** every round plays "alpha" until the next boot

### Requirement: An unnamed selection is ambiguous when more than one cup file exists
MoSCoW: Must. If no cup is named and the cup directory holds more than one cup file, whether or not each file parses, then the server SHALL refuse boot with the ambiguous-cup error. That error SHALL name every cup file in the directory and both ways to choose a cup: `-Pcup=<name>` (Gradle) and `-DVOYAGER_CUP=<name>` (java -jar).

#### Scenario: One valid cup and one malformed cup, none named
- **WHEN** the directory holds one valid cup file and one malformed cup file, and no cup is named
- **THEN** boot is refused with the ambiguous-cup error, and the message names both cup files, `-Pcup=` and `-DVOYAGER_CUP=`

### Requirement: A named selection that matches a malformed file names that file
MoSCoW: Must. If the selected cup name matches the file name of a cup file that does not parse, then the server SHALL refuse boot naming that file as malformed, and SHALL NOT report the name as absent.

#### Scenario: Named cup is malformed
- **WHEN** the selected name is "gamma" and "gamma.json" does not parse
- **THEN** boot is refused and the message names "gamma.json"

### Requirement: An unknown selected name is refused with the existing message
MoSCoW: Should. If the selected cup name matches no cup file, then the server SHALL refuse boot with the existing no-such-cup error listing the available cup names.

#### Scenario: Typo in the selected name
- **WHEN** the selected name is "alhpa" and only "alpha" exists
- **THEN** boot is refused and the message lists "alpha"

### Requirement: Cup mode stays required
MoSCoW: Must. A cup file without a `mode` field SHALL remain a malformed cup, and this change SHALL NOT assign a default mode.

#### Scenario: Missing mode
- **WHEN** the selected cup file has no `mode` field
- **THEN** the cup is reported as malformed and boot is refused, as it is today

### Requirement: A rotation cup with missing worlds is skipped with one warning
**MoSCoW:** Must. IF a cup of the rotation names a map whose world folder is missing or holds no region data, THEN THE SYSTEM SHALL
skip that cup for this boot, log exactly one WARN event that names the cup and each missing world, and continue with the other cups of
the rotation. IF no cup of the rotation is playable after this, THEN THE SYSTEM SHALL refuse boot with the existing refusal, listing
each cup of the rotation and its problem.

#### Scenario: One of two cups has no worlds
- **WHEN** the rotation is "alpha,legacy" and the worlds of "legacy" are missing, and "alpha" is complete
- **THEN** boot completes, exactly one WARN event names "legacy" and its missing worlds, and rounds play "alpha"

#### Scenario: No cup of the rotation is playable
- **WHEN** the rotation is "legacy" alone and its worlds are missing
- **THEN** boot is refused, and the refusal lists "legacy" and its missing worlds

#### Scenario: A damaged world refuses boot
- **WHEN** the rotation is "alpha,legacy" and a world of "alpha" has an unreadable region
- **THEN** boot is refused and the refusal names the damaged world of "alpha", as it does today for the played cup

#### Scenario: The worlds of every cup are checked
- **WHEN** the rotation is "alpha,legacy" and both cups have complete worlds
- **THEN** the boot check reads the worlds of both cups, one world at a time, before the server listens

### Requirement: Check mode reports a skipped cup as a warning and exits zero
**MoSCoW:** Must. WHEN the configuration check runs in validate-and-exit mode and a rotation cup is skipped for missing worlds while at
least one cup of the rotation is playable, THE SYSTEM SHALL report the skipped cup as a WARNING and SHALL exit with status 0. Refusals
SHALL still exit with status 1.

#### Scenario: Check mode with one playable cup
- **WHEN** the check runs with the rotation `alpha,legacy`, where the worlds of `legacy` are missing and `alpha` is complete
- **THEN** the report names `legacy` as a WARNING with its missing worlds, and the exit status is 0

#### Scenario: Check mode with no playable cup
- **WHEN** the check runs with the rotation `legacy` alone, where its worlds are missing
- **THEN** the report names the refusal, and the exit status is 1
