# Design

## Dependency direction

```
server (composition root: reads the retire and grace settings, wires the actions)
  -> platform.lobby, platform.cup (retire item and command, hub handoff, boss bar, WaitingRoom)
       -> race (RaceStateMachine, GraceWindow, JoinEstimate, MapScorer: pure, no Minestom)
            -> api (MedalTier.DNF, GameMode, the record types of the race model: pure)
```

The scoring and the timing are pure; the item, the command and the boss bar are adapters in `platform`. No edge points outward.

## New and changed types

| Type | Module | Kind | Role |
|---|---|---|---|
| `GraceWindow` | `voyager-race` (`race.flow`) | new record | Starts at the first finish, counts in ticks, reports remaining ticks, ends at `max(30 s, 1.5 × reference)` or earlier at the race cap. Pure. |
| `Retirement` | `voyager-race` (`race.scoring`) | new value, used by `MapScorer` | Turns a retired run into the existing `MedalTier.DNF` result. No new medal. |
| `JoinEstimate` | `voyager-race` (`race.flow`) | new record | Upper bound in seconds until a joiner's map, from `RaceTimings` (`race`, `endBetweenMaps`, `lobby`). Pure. |
| `RetireAction` | `voyager-platform` (`platform.cup`) | new adapter | Handles the retire item and `/retire`; calls `Retirement`. Refused unless the racer is racing and unfinished. |
| `LeaveRule` | `voyager-platform` (`platform.lobby`) | new adapter helper | The one predicate the leave item, `/leave` and `/hub` all ask: waiting in the lobby, between maps, or finished. Replaces the current "in the lobby" check. |
| `RaceTimings` | `voyager-race` (`race.flow`) | existing | Read by `GraceWindow` and `JoinEstimate`. No new field. |
| `ServerSettings` key `VOYAGER_GRACE_SECONDS_FLOOR` | `voyager-server` (`server.config`) | optional new key | Default 30. The only tunable; the factor 1.5 stays a constant until the owner decides. |

## Decisions

- **Grace is a race rule, not a platform rule.** The window is a function of ticks and reference time, so it lives in `race.flow` and
  is tested without Minestom. The platform only displays it.
- **Retire reuses the DNF path.** `MapScorer` already gives `MedalTier.DNF` to a racer who has not finished when a map ends. A retire
  makes the same outcome sooner, so the standings, the medal logic and the statistics need no change.
- **One predicate for leaving.** Today the lobby's leave check is one rule and `/hub` has another. A single `LeaveRule` keeps the item,
  the command and the hub handoff in agreement; the spec's three scenarios per entry point pin it.
- **No ADR for the product rule.** The overturned Won't is a product decision recorded in the proposal. If the owner accepts it, a
  MADR file is written by the docs task, because the design rules in CLAUDE.md say decisions are recorded.

## Fitness

- `SliceBoundaryRulesTest.r7_raceSlicesAreFreeOfCycles` and `r3_platformInfrastructureDoesNotDependOnRace`: `GraceWindow`,
  `JoinEstimate` and `Retirement` stay in `race` and do not import `platform`.
- `ApiPurityTest`: `MedalTier` and `RaceTimings` in `api` keep their pure shape; `Retirement` does not move into `api`.
- `ConfigurationBoundaryTest.environmentAccessIsConfinedToTheEdge` and `CompositionRootRulesTest.onlyServerConfigReadsLobbySettings`:
  the grace floor key is read only in `server.config`.
- `TickBoundaryTest`: `GraceWindow` and `JoinEstimate` count ticks and do not read the wall clock.

No new ArchUnit rule is proposed; each new boundary is covered by an existing rule.

## Risks

- The grace window touches the end of a map, which is the path the cup-session golden transcripts pin. The window is off until the
  first finish, so a map where everyone finishes must replay byte-identical. The tasks replay them.
- The reference time behind the window is an estimate on the shipped maps (see `calibrate-shipped-catalogue`). Until the calibration
  change lands, the window is tuned against guesses.
- Leaving between maps changes the join and the cup membership: a racer who leaves between maps must not count as a cup racer for the
  next map. The cup-round rules that drop a departed racer (`race/waiting-lobby`, "One of two racers leaves between maps") apply
  unchanged, and a test pins it.
