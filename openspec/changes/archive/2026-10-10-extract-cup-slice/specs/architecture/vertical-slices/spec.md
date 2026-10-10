## ADDED Requirements

### Requirement: Cup use cases are pure and live in the cup slice of race
**Priority:** MoSCoW Must

THE SYSTEM SHALL hold map scoring for a cup, cup standings, cup order, and the figures a racer's HUD shows during a map
in `voyager-race`, package `race.cup`. These types SHALL depend on no Minestom, Adventure, Xerus or platform type.

#### Scenario: Standings computed outside the cup slice
- **WHEN** a class outside `race.cup` computes a cup standing or a cup score
- **THEN** the change is rejected until that logic moves into `race.cup`

#### Scenario: Cup slice imports a platform type
- **WHEN** a class in `net.elytrarace.voyager.race.cup..` imports `net.minestom..` or `net.elytrarace.voyager.platform..`
- **THEN** the rule `raceDoesNotDependOnMinestomXerusOrItsWorldLoader` or `raceDoesNotDependOnPlatform` fails and names the class

### Requirement: The cup's Minestom adapter lives in platform
**Priority:** MoSCoW Must

THE SYSTEM SHALL hold the Minestom-facing adapter of a running cup, the announcer that sends its messages, and the mapping
from cup figures to HUD state in `voyager-platform`, in the packages `platform.cup` and `platform.hud`. The composition root
SHALL reach them only through their public types.

#### Scenario: Cup adapter in the server module
- **WHEN** a class in `net.elytrarace.voyager.server..` implements `RacePhaseListener` or sends cup messages to racers
- **THEN** the change is rejected until the adapter moves into `platform.cup`

#### Scenario: Cup use-case exception declared in platform
- **WHEN** a use-case exception of the cup (for example `UnresolvedCupException`) is declared in `platform`
- **THEN** the change is rejected in review, because `race.cup.exception` owns it, and `DesignRuleTest` still requires it to sit in an `exception` subpackage
