# Proposal

**Conventional Commits:** `feat(race)`. Squash-merge PR title: `feat(race): wait in a lobby until enough racers have joined`.

**Vertical slices touched:** `race.flow` (pure start rules), `platform.cup` (cup session), a new `platform.lobby`
slice (Minestom glue for the waiting room), `server.config` / `server.inject` / `server.command` (settings, wiring,
`/race start`). **Out of scope:** the flight tick and run reset (owned by `add-out-of-bounds-reset`), the alpha cup
content (owned by `add-alpha-cup-content`), a separate lobby world, spectator mode, a per-map time limit.

**Design spec decisions:** none altered. This change refines the `RaceTimings.lobby` wording of the greenfield
design's race flow (the lobby becomes the countdown that starts after the minimum is met). It records one new MADR
decision, ADR-0022 (`docs/decisions/0022-waiting-lobby-in-first-map.md`), which owner approval must accept before
merge.

## Why

Today the first player to join starts the cup (`VoyagerServer`, `PlayerSpawnEvent` handler, `session.start(false)`).
A single tester therefore runs a whole rotation alone, a second player arriving later lands mid-cup and waits for the
next map, and nothing tells a waiting racer why. Issue #170 asks for a minimum player count, a waiting state before
the countdown, and a defined outcome for late joiners and for racers who do not finish. Issue #101 reported that the
lobby countdown was shown in ticks as if it were seconds. The rebuild stores every phase length as a `Duration`, so
the unit error cannot recur structurally, but the countdown display still needs a regression test to close #101 for
the rebuild. The fixed 20 s lobby in `RaceTimings` is documented as a stopgap whose correct shape is "wait for
players"; this change builds that shape.

## What Changes

- **A cup starts when enough racers are online, not when the first one joins.** The minimum is the new setting
  `VOYAGER_MIN_PLAYERS` (system property, default 2; default 1 under `voyager.dev`). Explicit values win in both modes.
- **Waiting happens in the first map's world, at its spawn.** No lobby world, no new asset: the join handler already
  spawns racers into the rotation's first world. Waiting racers hold no elytra and no rockets; both are given at map
  entry, as today.
- **The start countdown is the cup's first lobby.** When the minimum is met, the cup starts and its lobby (20 s in
  production, 10 s under dev mode) runs with the existing three-digit countdown. Below the minimum during that lobby,
  the countdown is cancelled and the room returns to waiting. Once the last three seconds begin, the start is
  committed and a drop below the minimum no longer cancels it.
- **Between maps the minimum no longer gates anything.** The cup keeps running while at least one racer is online.
  When the last racer leaves a running cup, the cup is aborted and the room returns to waiting.
- **Racers who join a running cup wait** at the rotation's first spawn and enter the next map (today's
  `voyager.race.joined.midcup` behaviour, kept). They are not rejected and there is no spectator mode.
- **A cup that finishes returns the room to waiting.** If enough racers are still online, the next cup's countdown
  begins without an operator. The first map's racers are returned to the waiting spawn.
- **DNF.** A racer still running when a map's `GAME` phase ends is scored `DNF` (already true in `MapScorer`). This
  change adds the regression test and the wording the racer sees. The per-map limit of 150% of reference time from
  #170 is not built here; it depends on #168 time-bracket scoring and gets its own change.
- **`/race start` stays the operator override.** It starts the cup now with any number of racers. Whether it is
  registered outside dev mode is an open question (see below).
- **Messages.** Waiting, countdown and cancellation lines use new keys in `voyager_en_US.properties`
  (`voyager-server/src/main/resources`), through `platform.text.Messages`.
- **Config check.** `VOYAGER_MIN_PLAYERS` is validated by the config check and by boot: a non-number or a value below 1
  refuses start and names the key.
- **Documentation.** A how-to guide in `docs/guides/` explains how to run a playtest with N players, plus ADR-0022.

**BREAKING**: a single-player production server no longer starts a cup on its own. Operators who relied on that set
`VOYAGER_MIN_PLAYERS=1`, or use `/race start`.

## Capabilities

### New Capabilities

- `race/waiting-lobby`: when a cup may start, what a waiting racer sees, the countdown's cancel and commit rules,
  the between-cup and empty-server behaviour, and the DNF outcome of a map that ends unfinished.

### Modified Capabilities

- `server/config-check-mode`: one added requirement, that the minimum racer setting is validated by the check and by
  boot. No existing requirement changes.

No requirement of `cup-session-characterization` changes. The Golden Master scenarios keep starting the cup through
`CupSession.start(false)`, and the gate runs outside the tick pipeline, so every committed golden file stays
byte-identical (see the design).

## Impact

- `voyager-race`: new pure `race.flow.StartGate` (decisions only, no Minestom) and its table-driven test.
- `voyager-platform`: `cup/CupSession` gains a disarm and an abort path and keeps `start`, `describe` and the armed
  text unchanged where golden files read them; new `lobby` slice (`WaitingRoom`, a per-tick gate adapter, `Racers`
  equipment hooks); `text/Messages`; `hud/StartCountdown` gains a whole-second formatter for #101.
- `voyager-server`: `ServerSettings` (new minimum field, dev default), `ConfigCheck`, `inject/ServerBeans` and
  `inject/CupBeans` (wiring), `VoyagerServer` (join handler stops calling `start`, gate scheduled beside the tick),
  `command/RaceCommand` (status shows waiting, `start` registration), `voyager_en_US.properties`.
- Tests: `CupSessionGoldenMasterTest` unchanged; new lobby scenarios in a separate test class; `ServerSettingsTest`,
  `ConfigCheckSettingsTest` and the `CupWiring` helper updated for the new setting.
- Fitness: `SliceBoundaryRulesTest` baseline must not grow. R2' allows Minestom only in `VoyagerServer`, `server.inject`,
  `server.config`, `server.command`, so all Minestom glue goes to `platform.lobby`. R4 (platform slices free of cycles)
  must stay green with the new `lobby` slice.
- Docs: `docs/guides/how-to-run-a-playtest.md`, `docs/decisions/0022-waiting-lobby-in-first-map.md`, and the
  `RaceTimings` javadoc that calls the 20 s lobby a stopgap.
