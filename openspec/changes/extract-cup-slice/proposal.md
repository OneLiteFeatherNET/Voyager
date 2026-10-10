# Proposal

Conventional Commits title: `refactor(race): extract the cup slice from the server module`

## Why

`voyager-server/.../server/game/CupSession.java` (808 lines) mixes three kinds of code: cup use cases (map scoring,
standings, cup order, medal outlook), HUD state building, and Minestom adapters. The per-tick order is written into
the body of `tick()`. The composition root therefore computes scores and standings, which the architecture specs
forbid (`architecture/vertical-slices`, "Composition root holds no use-case logic"). This is follow-up 2 of the
migration list recorded when the vertical-slice architecture was archived on 2026-10-10.

This change supersedes the name `move-cup-flow-out-of-server` that the archived migration list gives that follow-up.
It is named `extract-cup-slice` in this change and in `freeze-slice-boundary-violations`.

## Dependency

This change builds on `freeze-slice-boundary-violations`, which is archived before it. That change creates the frozen
violations store under `voyager-fitness/src/test/resources/archunit_store/`, and it is the base of every requirement
this change modifies:

- The MODIFIED block "Server holds no scoring or cup logic once the move is complete" starts from the text that
  `freeze-slice-boundary-violations` gives that requirement, and applies this change's change to it.
- The RENAMED and MODIFIED blocks of the server-game requirement start from that change's
  "The server's game package holds no Minestom adapter" text.
- The MODIFIED block "A fixed violation leaves the baseline in the same change" starts from that change's text.

`design.md`, "Effects on the frozen rules", records what this change does to each frozen rule of that change.

## What Changes

This is a behaviour-identical refactor. Every observable output of a running cup (scores, chat, titles, log text,
`/race` status, tick order) stays the same. No requirement of an existing capability changes.

- Pin the current per-tick behaviour of a running cup with a **Golden Master** before any code moves.
- Move the pure cup use cases from `voyager-server` into the cup slice of `voyager-race`, package `race.cup`:
  `CupStandings`, `CupStanding`, a `CupRound` use-case facade, a `MapFigures` value for HUD figures, and
  `UnresolvedCupException` in `race.cup.exception`.
- Move the Minestom adapters out of `server.game`: `Racers` and `Rockets` into `platform.flight`; `CurrentMapBlocks`
  into `platform.world`; `LivePlayerSampler` into `platform.cup` (not `platform.flight`, see design D2); the Minestom-facing
  `CupSession` and its `CupAnnouncer` into `platform.cup`; the mapping from `MapFigures` to `HudState` into `platform.hud`.
- Declare the per-tick order once: a `TickPipeline` built in one `@Bean` method of the cup factory, tested in isolation.
- Replace `RaceBeans` with `CupBeans`. Construct `RaceCommand` as a bean in `ServerBeans` instead of by hand in
  `VoyagerServer`. Move `CatalogReloadService` out of `server.game`, and delete that package.
- Update the frozen violations store of `freeze-slice-boundary-violations` in the commit that closes each item. The
  store gains no entry. The server-scoring rule becomes plain and covers `race.cup` too. The server-game rule is
  retargeted to the composition root and the server's adapter packages.
- Correct the cup row of `docs/explanation/architecture.md`, which says `CupSession` moves to `race.cup`. Only its
  pure parts move; the Minestom glue stays in `platform.cup`.

**Not a breaking change.** No API type in `voyager-api` changes. No public surface outside the modules changes.

## Capabilities

### New Capabilities

- `cup-session-characterization`: the Golden Master that pins a running cup's observable per-tick behaviour, so that
  moving cup code between modules cannot change it unnoticed.

### Modified Capabilities

- `architecture/architecture-enforcement`:
  - MODIFIED "Server holds no scoring or cup logic once the move is complete": rule Must, plain (no baseline), covers
    `race.cup`, and no longer names a frozen baseline.
  - RENAMED "The server's game package holds no Minestom adapter" to "Server holds no Minestom adapter outside the
    composition root", with a MODIFIED body, because `server.game` no longer exists.
  - MODIFIED "A fixed violation leaves the baseline in the same change": adds the closed-item guard and its scenarios.
    This replaces the separate ADDED requirement of the first draft, which said the same thing in other words.
- `architecture/vertical-slices`: adds the requirement that cup use cases are pure and live in the cup slice, and that
  the cup's Minestom adapter lives in platform.
- `architecture/dependency-injection-boundaries`: adds the requirement that the race command is a bean of the
  composition root.

No existing behaviour requirement (`cup-validation`, `server/catalog-reload`, `server/dependency-injection`, and the
rest) changes. Spec-level behaviour is unchanged by design.

## Scope

**In scope:** migration items 1, 2, 3, 5, 6, 7, 8, 9 and 10 of the archived design (`define-clean-architecture-with-vertical-slices`),
addendum item 28 (`CupSession` imports Minestom, provisional in `freeze-slice-boundary-violations`), item 22 as a package
move only, and the composition-root wiring of the cup and of the race command.

**Out of scope:**
- Item 23 (`CupResolution`: the cup-selection rule over parsed definitions). It stays open for a follow-up because its
  rule takes a platform type (`CatalogReading`) and needs a port first.
- Items 11 to 18, 24 to 27 (platform regrouping, API flattening, setup adapters, catalogue and world cycle), and
  addendum item 29 (`MapDraftAdapter`, the mapsetup contract in `platform.catalog`).
- `MapTransition` moving to `platform.cup` (item 12, follow-up 3).
- Any change to `race.run`, `race.flow`, `race.scoring` behaviour, to `voyager-api`, to `voyager-setup`, to message text,
  or to the set of log lines.
- Replacing the concrete `CupSession` dependency of `RaceCommand` with a narrow port.

**Design decision altered:** none of the decisions D1 to D10 of `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`
changes. D10 (avaje-inject, DI annotations only in composition roots) is followed, not altered.

**Items with no store entry:** items 5 and 10 are list-only in `freeze-slice-boundary-violations` (no ArchUnit dependency
rule expresses them). Closing them changes the migration list and the code, but no store entry.

## Impact

- Code: `voyager-server` (`server/game`, `server/inject`, `server/command`, `VoyagerServer`), `voyager-platform`
  (`flight`, `world`, `hud`, new `cup`), `voyager-race` (new `race.cup`, `race.cup.exception`), `voyager-fitness`.
- Tests: the existing cup tests move with their classes; a new Golden Master harness in `voyager-server`; a new `TickPipeline`
  test in `voyager-platform`; a `CupRound` test in `voyager-race`; the closed-item guard and the rule changes in `voyager-fitness`.
- Frozen store: entries removed for items 1, 2, 3, 6, 7, 8, 9 and 28; no entry added (`voyager-fitness/src/test/resources/archunit_store/`).
- Docs: `docs/explanation/architecture.md`, `docs/reference/semantic-anchors.md`, a new proposed ADR-0021
  (`docs/decisions/0021-cup-use-cases-in-race-cup.md`).
- Dependencies: none added or removed.
- Logs: the logger category of moved classes changes (the class name is part of it). Message text and level do not change.
