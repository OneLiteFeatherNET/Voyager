# Spec Delta

## Purpose

While a racer flies a map, the boss bar and the action bar are the only lines the racer reads without looking away from the
course. This capability states what each line says, how its parts are labelled, how readable its colours are, and that every
label exists in English and German.

## ADDED Requirements

### Requirement: The boss bar names the best reachable medal with a translated tier and its time
**MoSCoW:** Must. WHILE a racer flies a map with a medal still reachable, THE SYSTEM SHALL show in the boss bar the translated
name of that medal tier and the seconds left to keep it, followed by the word "left" in English or "noch" in German, with one
decimal. WHEN no countdown exists, as for the finish tier, THE SYSTEM SHALL show the translated tier name alone.

#### Scenario: Diamond with time left in English
- **WHEN** the racer is in the locale `en_US` with Diamond reachable for 57.9 seconds more
- **THEN** the boss bar reads "Diamond · 57.9 s left"

#### Scenario: Diamond with time left in German
- **WHEN** the racer is in the locale `de_DE` with Diamond reachable for 57.9 seconds more
- **THEN** the boss bar reads "Diamant · noch 57.9 s", with the decimal point as recorded in ADR-0038

#### Scenario: The finish tier has no countdown
- **WHEN** the racer's best reachable tier is Finish
- **THEN** the boss bar reads "Finish" in English and "Ziel" in German, and no seconds are shown

### Requirement: The boss bar title names the map by its display name
**MoSCoW:** Must. THE SYSTEM SHALL show in the boss bar the map's position in the cup and the map's display name from
`catalog/display-names`, and SHALL NOT show the map id.

#### Scenario: The map name in the boss bar
- **WHEN** the racer flies the map "elytraraceblueandred" as map 1 of 2 in the cup "alpha_cup"
- **THEN** the boss bar names the map "Blue & Red" and shows "1/2" for its position

### Requirement: The boss bar fill is the fraction of rings passed
**MoSCoW:** Must. THE SYSTEM SHALL set the fill of the boss bar to the rings the racer has passed divided by the rings in the map,
so the fill rises by one ring at each ring passed.

#### Scenario: Twelve rings of thirty-five
- **WHEN** a racer has passed 12 of the 35 rings of a map
- **THEN** the boss bar fill is 12 divided by 35

### Requirement: The boss bar colour does not depend on the medal
**MoSCoW:** Must. THE SYSTEM SHALL draw the boss bar in one fixed colour for every medal tier, so the medal is named by the title
and not by the bar.

#### Scenario: The colour stays the same across tiers
- **WHEN** the reachable medal changes from Diamond to Gold during a map
- **THEN** the boss bar colour does not change

### Requirement: Every race HUD text colour reads at 4.5 to 1 or more on a dark backdrop
**MoSCoW:** Must. THE SYSTEM SHALL use for the boss bar title and the action bar only the palette colours whose contrast with the
backdrop colours `#101010` and `#1E1E1E` is 4.5 to 1 or more, and a test SHALL check each of them.

#### Scenario: Every colour the HUD uses passes
- **WHEN** the contrast test runs over the colours of the boss bar and the action bar
- **THEN** every colour reads at 4.5 to 1 or more against both backdrops, and the test names any colour that does not

### Requirement: The action bar labels its clock and its ring count
**MoSCoW:** Must. WHILE a racer flies a map, THE SYSTEM SHALL show the action bar with the clock and the ring count, each named by
a translated label, in the form "Time 0:02.1 · Rings 0/35" in English and "Zeit 0:02.1 · Ringe 0/35" in German. THE SYSTEM SHALL
show the values in a bright palette colour and the labels in the dim palette colour.

#### Scenario: The action bar in English
- **WHEN** the racer is in the locale `en_US`, has flown 2.1 seconds and has passed none of the 35 rings
- **THEN** the action bar reads "Time 0:02.1 · Rings 0/35"

#### Scenario: The action bar in German
- **WHEN** the racer is in the locale `de_DE` under the same conditions
- **THEN** the action bar reads "Zeit 0:02.1 · Ringe 0/35"

#### Scenario: The ring counter turns green just after a ring
- **WHEN** the racer passed a ring in the last few ticks
- **THEN** the ring count is shown in the palette's OK colour, and the labels are unchanged

### Requirement: Every race HUD label exists in English and German with the same placeholders
**MoSCoW:** Must. THE SYSTEM SHALL define every label of the boss bar and the action bar in `voyager_en_US.properties` and
`voyager_de_DE.properties`, each with the same placeholders and palette tags as its English line, and SHALL NOT show a dotted
key or an English word to a `de_DE` racer.

#### Scenario: A German racer sees no English label
- **WHEN** a `de_DE` racer flies a map with every tier reachable
- **THEN** the boss bar and the action bar show German labels for every tier and for both values
