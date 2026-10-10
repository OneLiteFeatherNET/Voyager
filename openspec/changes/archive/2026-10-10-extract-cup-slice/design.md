# Design

## Context

See proposal.md for the motivation. The starting state on `main`, checked on 2026-10-10:

- `voyager-server/.../server/game/CupSession.java` (808 lines) holds the cup use cases (`MapScorer.score` in `mapFinished`,
  `standings.record`, `closeMap`, `scoreOn`, `cupOrder`, `MedalCountdown.outlook` for the HUD), builds two `HudState`
  values (`flightState`, `startingState`), sends every chat and title line, and defines the per-tick order in `tick()`.
- `server.game` also holds `CupStandings`, `CupStanding` (scoring types), `Racers`, `Rockets`, `LivePlayerSampler`,
  `CurrentMapBlocks` (Minestom), and `CatalogReloadService`.
- `server.inject.RaceBeans` has one bean, `CupSession`, built by `CupSession.create(...)`, which constructs three
  package-private collaborators itself.
- `VoyagerServer` builds `RaceCommand` by hand with `graph.get(...)`, and `RaceCommand` depends on the concrete
  `CupSession`.
- `docs/explanation/architecture.md` says `CupSession` moves to `race.cup`. This design corrects that: only its pure parts
  go to `race.cup`. The Minestom glue goes to `platform.cup`, because the owner's rule is that Minestom-facing adapters stay
  in platform or server.
- ADR-0019 pins the catalogue snapshot per round (`CupSession.start` pins it). The move must keep that pin.
- The per-tick order is one of the constraints this change must keep. The current order is: flight sample, boost burn,
  phase advance (which, when the cup has finished, also announces the cup result).

Migration items closed here (numbering of the archived design, `define-clean-architecture-with-vertical-slices/design.md`):
1, 2, 3, 5, 6, 7, 8, 9, 10, and addendum item 28 (`CupSession` imports Minestom; numbered provisionally by
`freeze-slice-boundary-violations`, design D7). Item 22 moves as a package change only and keeps its "Acceptable"
classification. Item 23 stays open (see Non-Goals). Note: the proposal and the owner's brief call `CupResolution` item 25;
in the archived list it is item 23, and item 25 is `SetupMessages`.

**Prerequisite:** `freeze-slice-boundary-violations` is archived, so that the frozen store it creates exists on `main`.
Its store path and its entries are read at apply time (task 0.1). This change builds on that change's spec version: the
requirement texts it modifies are the texts that change gives, not the texts of the archived main spec.

## Goals / Non-Goals

**Goals:**
- Behaviour-identical: every scenario in the Golden Master transcript is byte-identical after each commit.
- The cup use cases compile in `voyager-race` with no Minestom, Adventure, Xerus or platform import.
- The composition root wires the cup and the race command, and computes nothing.
- The per-tick order is declared once, in one `@Bean` method, and is tested.
- The frozen store shrinks by the closed items and never grows. Every frozen rule stays green at every commit.

**Non-Goals:**
- Item 23: the cup-selection rule in `CupResolution` (`platform.catalog`) stays where it is. Its rule takes a platform type
  (`CatalogReading`), so moving it needs a port first, and that is its own change.
- No change to `race.run`, `race.flow`, `race.scoring` behaviour, to `voyager-api`, or to `voyager-setup`.
- No narrow port in front of `RaceCommand` (its dependency on the concrete `CupSession` is kept; see Open Questions).
- No change to message keys, message text, `describe()` text or the set of log lines.
- No change to `MapTransition` (item 12, follow-up 3).
- Addendum item 29 (`MapDraftAdapter`, mapsetup contract in `platform.catalog`): untouched, stays in the store.

## Decisions

### D1. Pure cup use cases go to `race.cup`

Module `voyager-race`, package `net.elytrarace.voyager.race.cup`, with `package-info.java` (`@NotNullByDefault`).

| Type | Source | Role |
|---|---|---|
| `CupStanding` (record) | `server.game.CupStanding` | Moved unchanged (item 3). |
| `CupStandings` | `server.game.CupStandings` | Moved unchanged (item 2). Records map scores, closes maps with the cup's mode, orders the cup. |
| `CupRound` (public final class) | new | Use-case facade over `CupStandings` and `MapScorer`: `reset()`, `recordMap(mapIndex, racer, progress, map, timeOnCourse, raceLength)` (calls `MapScorer.score`, then records), `closeMap(mapIndex, cup)`, `scoreOn(racer, mapIndex)`, `order()`. |
| `MapFigures` (public record) | new | The HUD figures of a racer during a map: game tick, elapsed time, rings passed, ring count, last ring tick, map number, map count, map name, `MedalOutlook`. Factories `inFlight(run, clock, map, mapNumber, mapCount)` and `starting(map, mapIndex, mapCount)`. The rule "a finished racer is shown the clock they finished on" moves here with `outlookAt`. |
| `exception/UnresolvedCupException` | `platform.catalog.exception` | Moved (item 5). Its `exception/package-info.java` is new in `race.cup`. |

Dependencies of `race.cup`: `race.run`, `race.progress`, `race.flow`, `race.scoring`, `api.race`. No cycle: none of those
packages imports `race.cup`. The fitness rules `raceDoesNotDependOnMinestomXerusOrItsWorldLoader`,
`raceDoesNotDependOnPaper`, `raceDoesNotDependOnPlatform` and the clock and static-state rules for `race` cover the new package.
No new rule is needed for the boundary itself.

Logging stays in the platform adapter, so `race.cup` writes nothing and reads no clock.

Alternatives considered:
- Keep `CupStandings` in `server.game`, which is what the composition root does today. Rejected: it is the violation.
- Put `MapFigures` in `platform.hud`. Rejected: the figures are decided by the race (which clock, which outlook), and only their
  rendering into `HudState` is a platform concern.

### D2. Minestom adapters go to platform

| Type | Target package | Change |
|---|---|---|
| `Racers`, `Rockets` | `platform.flight` | Moved (items 6, 7). Visibility widened to `public` only where a caller now lives in another package. |
| `LivePlayerSampler` | `platform.cup` | Moved (item 8). Not `platform.flight`: see the paragraph below the table. |
| `CurrentMapBlocks` | `platform.world` | Moved (item 9). |
| `CupSession` (the Minestom-facing orchestrator) | `platform.cup` | Moved in phase 4 (item 28). Keeps the phase driver, the players supplier, the instance manager, the map transition, the racer feedback calls and `describe()`. It delegates every score and every figure to `race.cup`. Its constructor is public and takes its collaborators (replaces `create`). |
| `CupAnnouncer` | `platform.cup` | New, extracted from `CupSession`: `report`, `announceMapScore`, `announceCupResult`, `announceCupPlace`, `broadcast`, and the fallback clock for a score with no completion time. It sends `Messages` and `RaceFeedback`. It is created in `platform.cup` directly, never in `server.game`. |
| `HudStates` (static `of(MapFigures)`) | `platform.hud` | New. The only place a `MapFigures` becomes a `HudState`. |

`CupSession` keeps logger calls. The logger category of those classes changes (it follows the class), and the Golden Master
pins level and message text only (see the characterization capability).

**Why `LivePlayerSampler` goes to `platform.cup`.** It imports `platform.tick.FlightSample` and `platform.tick.FlightSampler`.
`platform.tick` already depends on `platform.flight` (`FlightTick` uses `FlightTransition`; `FlightTickDriver` uses
`FlightTracker`). A `platform.flight` class that uses `platform.tick` would close a cycle, which is a new violation of the
frozen platform cycle rule (R4 of `freeze-slice-boundary-violations`, item 27). That store must not grow. `platform.cup` is
a sink: no other platform package imports it, so a class there closes no cycle. The live sampling is also the cup's
per-tick "flight sample" step, so the placement is cohesive. Item 14 (`FlightTick` in `platform.tick` depends on
`platform.flight`) stays open; see Open Questions for the alternative.

### D3. The per-tick order is declared once

`platform.cup.TickStep` is a record `(String name, Runnable action)`. `platform.cup.TickPipeline` is an immutable ordered list of
steps with `of(List<TickStep>)`, which refuses an empty list, and `run()`, which runs each step in order. Both live in
`platform.cup` because the order is the cup's; `platform.tick` (infrastructure) stays free of it. Neither type has a race
import, so either package would pass the infrastructure rule.

`CupBeans` builds the pipeline in one `@Bean` method, which is the only place the order is written:

1. `"flight sample"`: `session::sampleFlight` (replaces the first loop of `tick()`, which records `lastSimulated`);
2. `"boost burn"`: `boosts::advance`;
3. `"phase advance"`: `session::advancePhase` (the phase driver's `onUpdate()`, then the cup-result announcement when the cup has
   finished and a map was current).

`VoyagerServer.scheduleTick` runs `pipeline.run()` at `TICK_START`. `CupSession.tick()` is removed. The failure path is unchanged:
on an exception `VoyagerServer` still calls `session.stop()`.

The third step contains the cup-result announcement, so "phase advance last" stays true of the pipeline as the spec states it.

Alternatives considered:
- Keep the three calls in `tick()`. Rejected: the order would stay in a class body, which item 10 and the per-tick pipeline
  requirement forbid.
- Inject the steps as a collection. Rejected by the per-tick pipeline requirement; the pipeline is one bean.

### D4. Composition root: `CupBeans`, `ServerBeans`, the race command as a bean

- `RaceBeans` is renamed to `CupBeans` (the requirement "Wiring is grouped in one factory per slice" names it). It contains the
  beans `CurrentMapBlocks`, `FireworkBoostTracker`, `LivePlayerSampler`, `MinestomCollisionSpace`, `FlightTickDriver`,
  `CupSession`, and `TickPipeline`. Every `@Bean` method only calls a constructor, and the tick step `Duration` comes from
  `MinecraftServer.TICK_MS` as today.
- `CupRound` is not a bean. `CupSession`'s constructor creates it. So `CupBeans` and `ServerBeans` import no `race.scoring`
  and no `race.cup` type, which the rule in D6 requires.
- `ServerBeans` gains `@Bean RaceCommand raceCommand(CupSession, CatalogReloadService, @External ServerSettings)`. The command's
  constructor only adds syntax and does not register, so building it during graph construction, after `MinecraftServer.init()`,
  is the same effect as today.
- `VoyagerServer` replaces `new RaceCommand(...)` with `graph.get(RaceCommand.class)` and registers it at the same point in the
  boot sequence.
- `CatalogReloadService` moves from `server.game` to the `net.elytrarace.voyager.server` root, next to the composition root that
  wires it. `server.game` and its `package-info.java` are deleted.

Alternative considered: leave the hand-built `RaceCommand` in `VoyagerServer`. Rejected: that is the wiring the requirement
"Graph wiring is readable in one place" asks to avoid, and it keeps a second place that builds a cup object.

### D5. Characterization first: the Golden Master

The harness is `voyager-server/src/test/java/net/elytrarace/voyager/server/CupSessionGoldenMasterTest` with a helper that
records the transcript. It is written and run against unchanged `main` before any code moves (phase 1).

- Four scenarios, one test method each, with one golden file each under `src/test/resources/golden/cup-session/`:
  `lobby-and-start`, `racing-boost-disconnect`, `skip-finish`, `restart-pending-reload`.
- The transcript, per tick: the tick number; the full `describe()` text; every chat and title message each racer received, in
  order; every log line as level and formatted message.
- Fixed inputs: a `Clock.fixed(...)` for the catalogue, fixed usernames, fixed UUIDs (`UUID.nameUUIDFromBytes`), explicit
  ticks through the pipeline, no sleeps, one fresh Minestom Env per test, following the setup of `CupSessionTest`.
- The test never rewrites a golden file. On mismatch it writes the received transcript to a `@TempDir` and fails naming the
  first differing tick and line.
- Message capture: the one open mechanism question. A spike (task 1.1) compares Minestom Testing's recorded connection packets
  with a recording wrapper and keeps the cheaper one that yields the chat and title lines of each racer in order.
- Known gap: the HUD (`RaceHud`, the boss bar) is not in the transcript. Its values are pinned by the unit test of `HudStates`
  (task 3.2), with values taken from the scenario's figures.
- Red/Green check: before the first move, two uncommitted probes must turn the harness red: swapping the boost and flight
  steps, and changing one message character. Both probes are reverted and the result is recorded in the commit body of task 1.2.

### D6. Fitness: the frozen store shrinks per commit, and the rules the moves make true

**Store rule.** A task that closes a migration item removes that item's entries from the frozen store in the same commit,
as `freeze-slice-boundary-violations` design D4 requires. No task adds an entry. The guard test `ClosedViolationsAreGoneTest`
(new, task 2.0) holds the list of source files of the closed items. It fails when a stored line names one of them. Each
closing task first appends its files to that list (Red: the test fails, because the store still holds the lines), then
removes the lines (Green). The closed items are 2, 3 (task 2.2), 6, 7, 8, 9 (task 3.1), and 1, 28 (task 4.6).

**Effects on the frozen rules of `freeze-slice-boundary-violations`:**

| Frozen rule | Outcome under this change | Tasks |
|---|---|---|
| R1 server does not depend on `race.scoring` (items 1, 2, 3) | Items 2 and 3 close in 2.2, item 1 in 4.6. When the store empties in 4.6, the rule becomes plain (freeze wrapper and store file removed, `allowEmptyShould(false)` kept) and is extended to `race.cup`: no class in `..server..` depends on `race.scoring..` or `race.cup..`. | 2.2, 4.6 |
| R2 server `game` package does not use Minestom (items 6, 7, 8, 9, 28) | Items 6 to 9 close in 3.1, item 28 in 4.6. The store empties in 4.6 and the rule becomes plain. The package is deleted in 5.1. The rule is then retired and replaced by R2' (below) in 4.7, before the package is deleted. | 3.1, 4.6, 4.7, 5.1 |
| R3 platform infrastructure does not depend on `race` (items 11, 13, 15, 20) | Unchanged. `CurrentMapBlocks` imports only Minestom. `TickPipeline` lives in `platform.cup`, not in `tick`. No entry is added. | 3.1, 4.1 |
| R4 platform slices are free of cycles (item 27) | Unchanged. `LivePlayerSampler` is in `platform.cup` (D2), so no flight-to-tick edge appears. Checked by the suite staying green in 3.1. | 3.1 |
| R5 mapsetup contracts live in platform mapsetup (items 24, 29) | Unchanged. Item 29 is outside this change. | none |
| R6 setup does not use Minestom (item 26) | Unchanged. | none |
| R7 race slices are free of cycles (plain) | Covers the new `race.cup` slice. No race slice imports `race.cup`, so no cycle. Must stay green. | 2.1 to 2.3 |
| R8 api slices are free of cycles (plain) | Unchanged. | none |

**R2', the retargeted server rule** (added in 4.7; `allowEmptyShould(false)`):
`noClasses().that().resideInAPackage("..server..").and().resideOutsideOfPackages("..server.inject..", "..server.command..", "..server.config..").and().doNotHaveFullyQualifiedName("net.elytrarace.voyager.server.VoyagerServer").should().dependOnClassesThat().resideInAPackage("net.minestom..")`.
The composition-root packages (`inject`, `config`) and the command adapter (`command`) may use Minestom, and so may the bootstrap class.
The rule still matches a class after the move: `CatalogReloadService`, which is in `server.game` in 4.7 and in the `server` root after 5.1.
Its Red is a probe: a scratch class in the `server` root that imports `net.minestom` must fail R2'.

**R1' and the race command.** The R1 extension in 4.6 is the only server rule on `race.cup`. The race-command rule is added in 5.2,
after `VoyagerServer` takes the command from the graph: no class outside `..server.inject..` calls a `RaceCommand` constructor
(`allowEmptyShould(false)`). Its Red is a probe: a scratch class that calls `new RaceCommand(...)` must fail it.

`FitnessCoverageTest` is unchanged: no module is added, and every rule names a package of a module that already has a rule.

### D7. Documentation and one ADR

- `docs/explanation/architecture.md`: migration status updated (items 1, 2, 3, 5, 6 to 10, and addendum 28 resolved, counts
  recalculated), and the cup and flight rows corrected (see Context).
- `docs/reference/semantic-anchors.md`: a row for "Golden Master", which the document does not yet list.
- `docs/decisions/0021-cup-use-cases-in-race-cup.md`: MADR 4.0, status **proposed**, recording D1 to D4. It needs the owner's
  approval before it is accepted, as the project rules require for architecture decisions. The number is 0021 because
  `freeze-slice-boundary-violations` keeps ADR-0020.

## Risks / Trade-offs

- **[The golden master is coarser than the code] -> Two probes in phase 1 must fail the harness; the transcript includes every
  tick's `describe()` and every message. The HUD is covered by a unit test only.**
- **[Log category changes in the console] -> Accepted. The transcript pins text and level, and the proposal names the change.**
- **[Widening `Racers`, `Rockets`, `CurrentMapBlocks` to `public`] -> Only where a caller moved. Their tests move with them.**
- **[Constructor replaces `create()`; tests that call `create` change] -> Mechanical, done in task 4.2, the golden harness is
  green before and after.**
- **[The pipeline adds indirection to the tick] -> One bean and one class; the order test in `platform` and the golden master
  both pin it.**
- **[`freeze-slice-boundary-violations` is not archived yet] -> Task 0.1 stops the change if the store is missing.**
- **[A closed entry is re-added by a rebase] -> `ClosedViolationsAreGoneTest` fails at once.**

## Migration Plan

The change is built as a sequence of commits on one branch, each leaving `./gradlew build` green, and it lands as one squash
commit (the PR title). Order: characterization (phase 1), pure `race.cup` (phase 2), platform adapters (phase 3), tick order and
`CupSession` move (phase 4), composition root and rule retargeting (phase 5), verification (phase 6), documentation (phase 7),
integration and PR (phase 8). Every commit that closes an item also shrinks the frozen store (D6).

Rollback before the squash is reverting the last commits; after it, a revert of the one commit restores `main`, because the change
has no data, schema or API effect.

## Open Questions

- The message-capture mechanism for the Golden Master (task 1.1). Either mechanism yields the same transcript content, so the
  choice does not change the specs or the task breakdown; the spike decides it.
- Item 14 and `LivePlayerSampler` (owner decision, not blocking). This change places the sampler in `platform.cup` to keep the
  frozen cycle rule green. The alternative is to move `FlightSample` and `FlightSampler` from `platform.tick` to
  `platform.flight`. That would close item 14 and let the sampler sit in `platform.flight`, but it is platform regrouping
  (follow-up 3), which this change excludes. The owner chooses; this change follows the placement in D2 unless told otherwise.
