## ADDED Requirements

### Requirement: The selected cup is validated in full at boot
MoSCoW: Must. When the server boots, the server SHALL resolve the selected cup and validate that cup completely: its definition parses and every map it names resolves. The check of other cups SHALL NOT be a precondition for boot.

#### Scenario: Selected cup is consistent
- **WHEN** the server boots with a selected cup whose file parses and whose maps all resolve, and another cup file is malformed
- **THEN** boot completes and the selected cup is the one played

#### Scenario: Selected cup names an unknown map
- **WHEN** the selected cup names a map that no map definition provides
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
MoSCoW: Must. The server SHALL NOT play any cup other than the selected cup, and a cup with a reported problem SHALL NOT be selected by any resolution rule.

#### Scenario: Unselected broken cup is never played
- **WHEN** the server boots with cup "alpha" selected and cup "beta" has an unresolved map
- **THEN** every map played belongs to "alpha", and "beta" is absent from play

### Requirement: An unnamed selection is ambiguous when more than one cup file exists
MoSCoW: Must. If no cup is named and the cup directory holds more than one cup file, whether or not each file parses, then the server SHALL refuse boot with the existing ambiguous-cup error.

#### Scenario: One valid cup and one malformed cup, none named
- **WHEN** the directory holds one valid cup file and one malformed cup file, and no cup is named
- **THEN** boot is refused with the ambiguous-cup error

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
