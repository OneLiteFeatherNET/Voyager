# Design

## Dependency direction

Clean Architecture rings, inward only:

```
server (composition root: reads settings, wires the gate)
  -> platform.lobby (WaitingRoom: clock of the lobby, sends messages, starts a cup)
       -> race (StartGate, SoloWait, RaceStateMachine: pure, no Minestom)
            -> api (GameMode, CupDefinition: pure interfaces, records, enums)
```

`race` already depends on `api`. `platform.lobby` already depends on `race`. The change adds no edge that points outward.

## New and changed types

| Type | Module | Kind | Role |
|---|---|---|---|
| `GameMode.minimumPlayers()` | `voyager-api` | existing | Read for the first time by the start gate. No change to the enum. |
| `StartGate` | `voyager-race` | existing, changed | Takes the cup's mode minimum; `VOYAGER_MIN_PLAYERS` (when set) overrides it. |
| `SoloWait` | `voyager-race` (`race.flow`) | new record | Counts the solo wait in ticks (`ticks`, `elapsedTicks`). No clock read, so the test drives ticks. |
| `PracticeGrid` | `voyager-race` (`race.cup`) | new record | The practice grid size, validated in the compact constructor (at least 1). |
| `ServerSettings` keys `VOYAGER_SOLO_WAIT_SECONDS`, `VOYAGER_PRACTICE_COM_COUNT` | `voyager-server` (`server.config`) | existing class, two keys | Read through the configuration loader at the composition root only. |
| `WaitingRoom` | `voyager-platform` (`platform.lobby`) | existing, changed | Ticks `SoloWait`; starts the practice cup when it elapses with one racer waiting. |
| Practice cup `elytrarace:solo_practice` | `voyager-server` resources (`cups/solo_practice.json`) | data | Mode `PRACTICE`, first map of alpha. |

## Decisions

- **No ADR.** The change reads an existing enum value and adds two settings. It is not an architecture decision, so no MADR file is
  written. If the owner wants the practice start recorded as a product decision, it is an ADR candidate.
- **Ticks, not a clock.** The solo wait counts server ticks (`SoloWait` in ticks, 20 per second), which is the same tick the lobby
  already uses. Tests drive ticks explicitly; no `Clock` and no sleep.
- **Practice grid is its own setting.** `VOYAGER_COM_COUNT` stays the RACE grid. A practice start consults only
  `VOYAGER_PRACTICE_COM_COUNT`, so the RACE golden transcripts are unchanged.
- **Ranked is a property of the cup, not the room.** The ranked flag comes from `GameMode.ranked()` (`GameMode.java`). Statistics
  and standings read it; the lobby labels read it; no new flag is added.

## Fitness

- `voyager-fitness` `ConfigurationBoundaryTest.environmentAccessIsConfinedToTheEdge` and
  `CompositionRootRulesTest.onlyServerConfigReadsLobbySettings`: the two new keys are read only in `server.config`.
- `SliceBoundaryRulesTest.r7_raceSlicesAreFreeOfCycles` and `r3_platformInfrastructureDoesNotDependOnRace`: `SoloWait` and
  `PracticeGrid` stay in `race`, and `race` does not import `platform`.
- `ApiPurityTest`: `GameMode` keeps its pure shape; no change is expected there, and the rule catches one if it appears.
- `NullabilityConventionTest`: the new `race.flow` and `race.cup` types are covered by their existing `package-info.java` files.

No new ArchUnit rule is added: the existing rules cover each new boundary. If `SoloWait` ends up in `platform`, a rule that
forbids `race` from importing it would be added to `SliceBoundaryRulesTest`.

## Risks

- The lobby's start timing shares code with the GO path (`CupSession.start`, `WaitingRoom.tick`). The solo start must not change
  the tick order the cup-session golden transcripts pin (`race/waiting-lobby`, "Once a cup has started"). The golden transcripts
  are replayed in the tasks.
- The shipped cup count goes from two to three. The catalogue loads the new file at boot; `MessageBundleParityTest` covers the new
  label keys in every bundled language.

## Spec note

The MODIFIED block for `catalog/shipped-catalogue` keeps the archived header "Exactly two cups ship, alpha_cup and legacy_cup",
because archive matches MODIFIED headers by name. Its text ships three cups. Renaming the header is a RENAMED block in a later
change, not part of this one.
