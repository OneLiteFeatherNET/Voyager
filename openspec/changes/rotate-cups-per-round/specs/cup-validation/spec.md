# Spec Delta

## Purpose

The boot check covers every cup a server can play, not only the cup it plays first. A definition error in any cup of the rotation
refuses boot, as a named cup does today. A missing world is a deployment condition: the cup is skipped with a WARN, and boot
refuses only when no cup of the rotation is playable.

## MODIFIED Requirements

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

### Requirement: Only the selected cup is playable
**MoSCoW:** Must. The server SHALL NOT play any cup other than the cups of its selection, and a cup with a reported definition
problem SHALL NOT be selected by any resolution rule. A cup skipped at boot for missing worlds SHALL NOT be played until the next boot.

#### Scenario: Unselected broken cup is never played
- **WHEN** the server boots with cup "alpha" selected and cup "beta" has an unresolved map
- **THEN** every map played belongs to "alpha", and "beta" is absent from play

#### Scenario: A skipped cup is not played
- **WHEN** the rotation is "alpha,beta", the worlds of "beta" are missing at boot, and "alpha" is playable
- **THEN** every round plays "alpha" until the next boot

## ADDED Requirements

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

