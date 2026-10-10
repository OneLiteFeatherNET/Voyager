# ADR-0021: Cup use cases live in race.cup, and the Minestom glue of the cup in platform.cup

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. The change that
implements it is `extract-cup-slice`, which does not mark this record Accepted.

## Date

2026-10-10

## Decision makers

- TheMeinerLP (project owner). Decided on 2026-10-10 that the pure cup use cases go to `race.cup`, that the Minestom glue
  of the cup goes to `platform.cup`, that `LivePlayerSampler` goes to `platform.cup`, and that `CupResolution` (item 23)
  stays out of scope.
- Drafted with the change `extract-cup-slice`.

## Context and problem statement

`voyager-server`'s `server.game.CupSession` (808 lines) mixed three kinds of code: cup use cases (map scoring, standings,
cup order, the medal outlook shown on the HUD), HUD state building, and Minestom adapters. Its per-tick order was written in
the body of `tick()`. The composition root therefore computed scores and standings, which the architecture forbids
(`architecture/vertical-slices`). The server's `game` package also held Minestom adapters, and the frozen baseline
(ADR-0020) recorded the violations of both rules.

The cup has two parts that need different homes. The use cases have no Minestom, Adventure, Xerus or platform type and
can be tested in plain JUnit. The session drives players, worlds and messages, so it is a platform adapter.

## Decision drivers

- Pure cup logic belongs to the cup slice of `voyager-race` and cannot depend on the server or the platform.
- The Minestom-facing cup belongs to `voyager-platform`, the only module that may use Minestom outside the composition root.
- The composition root wires the cup and computes nothing.
- The per-tick order is declared once and tested.
- The change is behaviour-identical: a Golden Master pins every scenario tick by tick.
- No frozen violation changes its text, so the baseline never grows (ADR-0020).

## Considered options

1. **Keep the cup in `server.game`, moving only the adapters.** Rejected: the composition root keeps computing scores,
   which the architecture forbids.
2. **Move the whole cup, including the Minestom session, to `race.cup`.** Rejected: `race` must not import Minestom or
   the platform (`raceDoesNotDependOnMinestomXerusOrItsWorldLoader`, `raceDoesNotDependOnPlatform`).
3. **Pure use cases to `race.cup`, Minestom glue to `platform.cup` (chosen).** Scoring and figures are decided in `race`,
   and the session that sends them to players is a platform adapter. Each part follows its existing rule.
4. **Put `LivePlayerSampler` in `platform.flight`.** Rejected: `platform.flight` would then import `platform.tick`, which
   already imports `platform.flight`, and that closes a cycle the frozen platform rule (R4) forbids. `platform.cup` is a
   sink, so the placement closes no cycle.

## Decision outcome

Chosen option: **3**, with the placement below. Each type lives in the module and package named.

| Type | Module and package |
|---|---|
| `CupRound` (use-case facade), `CupStandings`, `CupStanding`, `MapFigures` | `voyager-race`, `race.cup` |
| `exception.UnresolvedCupException` | `voyager-race`, `race.cup.exception` |
| `CupSession` (Minestom-facing cup), `CupAnnouncer`, `TickStep`, `TickPipeline`, `LivePlayerSampler` | `voyager-platform`, `platform.cup` |
| `HudStates` (the only `MapFigures` to `HudState` mapping) | `voyager-platform`, `platform.hud` |
| `Racers`, `Rockets` | `voyager-platform`, `platform.flight` |
| `CurrentMapBlocks` | `voyager-platform`, `platform.world` |
| `CupBeans` (wires the cup and declares the per-tick order once, in `tickPipeline`) | `voyager-server`, `server.inject` |
| `CatalogReloadService` | `voyager-server`, `server` root |

**Rules that hold the decision.** `r1_serverDoesNotDependOnRaceScoringOrCup` forbids the server from importing
`race.scoring` or `race.cup`. `r2_serverOutsideTheCompositionRootDoesNotUseMinestom` forbids Minestom outside the
composition root, the command adapter and `VoyagerServer`. `CompositionRootRulesTest` forbids building the race command
outside `server.inject`. `CupSessionGoldenMasterTest` pins the observable behaviour of a running cup.

**Deviations from the plan, recorded for the owner.**

- `CupSession` moved to `platform.cup` before the tick order was extracted. Its constructor names `CurrentMapBlocks`, so
  moving that class alone would have changed the text of a frozen violation and made it a new one. Moving the session
  with it removed its violations instead, with no store entry added.
- `UnresolvedCupException` is no longer caught by the server. `CupResolution.refusalOf` returns its message, so the
  server does not import `race.cup`.
- The cup's tests run in `voyager-server`, where they drive the same tick order through `CupBeans.tickPipeline`. Three
  test accessors of `CupSession` are public for them.

## Consequences

- Positive: the composition root only constructs; the cup's scoring and figures are tested in `race` without Minestom; the
  tick order is one bean with a test; the baseline shrank by the closed items and grew by nothing.
- Negative: the logger category of the moved classes changes (the log text and level do not); a reader of the cup now
  crosses three modules, and `CupSession` is the one place that joins them.
- Neutral: no API type in `voyager-api` changes, no dependency is added, and no migration item other than those named in
  the change is closed. Item 23 (`CupResolution`) stays open.

## Links

- [ADR-0017](0017-clean-architecture-with-vertical-slices.md): the slice and ring model this applies.
- [ADR-0020](0020-freeze-architecture-violations-as-baseline.md): the frozen baseline, which the closed items leave.
- [ADR-0019](0019-pin-catalogue-snapshot-per-round.md): the catalogue pin that `CupSession.start` keeps.
- `openspec/changes/extract-cup-slice/` (design D1 to D4).
