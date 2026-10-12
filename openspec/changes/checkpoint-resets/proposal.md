# Proposal

Conventional Commits title: `feat(race): reset racers to the last checkpoint with a deployed elytra`

## Why

The owner decided on 2026-10-12 that the reset behaviour shipped by `remove-start-launch` and
`fix(race): let racers take off themselves at GO and after a reset` (#311) is not usable as it stands, for three reasons:

- A reset sends a racer to the last ring passed, of any type. The owner wants a reset to return a racer to the last
  `CHECKPOINT` ring they passed, and to the map spawn when they have passed none. `ProgressTracker` already records the
  last checkpoint (`RingProgress.lastCheckpointIndex`), and nothing reads it.
- Since #311 a racer reset in mid-air falls. `RunResetter.reset` calls `Racers.place`, which ends the glide, and the
  landing rule needs a previous glide, so the fall is never reset. The owner wants the racer to arrive with the elytra
  already deployed, so that they glide at once. No velocity impulse is added. ADR-0028 is amended for this.
- The landing rule has no grace. A racer placed close to the ground who touches it after a glide is reset again at once,
  which can chain. The tree being replaced counted a landing only after 20 airborne ticks and had a 40-tick cooldown; the
  rebuild has neither.

## What Changes

- **Reset target**: a reset places the racer at the centre of the last `CHECKPOINT` ring they passed, facing along that
  ring's flow normal. A racer who has passed no `CHECKPOINT` ring goes to the map's spawn, facing the first ring as a map
  start does.
- **Progress on reset**: the racer keeps the rings up to the reset target and loses the rings passed after it. Those
  rings score again when they are flown again. A spawn reset keeps none. See design D2. This amends the Must requirement
  "A reset keeps every ring already passed" and is the one point the owner must confirm before apply.
- **Glide on reset**: a reset places the racer with the gliding flag set, the elytra and the rockets in hand, and zeroes
  the velocity through `VelocityExit`. No impulse is sent. This amends the requirement "A reset returns the racer without
  an impulse", which said the racer is not gliding. Whether the client keeps a glide that the server starts on a racer is
  a risk, not a fact. Design D3 names the fallbacks.
- **Landing grace and cooldown**: a landing counts only after at least 20 consecutive airborne ticks, and no landing is
  counted during the 40 ticks after a reset. Out-of-bounds resets are gated by neither. The numbers are the values the
  tree being replaced used (`OutOfBoundsSystem`). They are seeds to tune in a playtest (design D4).
- **Announcement**: the out-of-bounds and landing titles name the checkpoint number, or the start, instead of the last
  ring number.
- **Docs**: `docs/reference/course-reset.md` (target table, landing rule, the "not implemented" list), the playtest guide,
  and a new ADR (design D8).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `race-course-reset`: the target, the progress kept, the glide after a reset, the landing rule (grace and cooldown), and
  the announcement text. The delta is in `specs/race-course-reset/spec.md`.

`race/waiting-lobby` is not changed. GO is not a reset and keeps its release with no impulse and no glide.

## Type and scope

`feat(race)`, not `fix(race)`. The change adds a new reset rule (the checkpoint target), new state (the airborne and
cooldown counters) and a changed contract for what a reset gives the racer. A fix would restore a behaviour the project
already had, and no shipped reset does what the owner now asks. The #311 fall is a symptom this change replaces, not its
subject.

The glide-on-reset and the grace are one change, because a glide without the grace can chain on a low ring, and the grace
without the glide protects a case that does not exist. Splitting them would ship a known loop. If the owner wants the
#311 regression fixed on its own first, that is a separate `fix(race)` change, and this one is rebased on it.

## Scope and slices

- Race slice (`voyager/race`): `RunReset` (target and landing rules), `RaceRun` (two counters, and the gliding state a
  reset records), `ProgressTracker` (logic unchanged; the rollback is tested through `RunReset`).
- Cup and flight in `voyager/platform`: `RunResetter` (rollback, glide, zero velocity), `Racers.place` (flag set),
  `CupSession.sendBackIfLostOrLanded` (passes the new counters and the ground state to the landing rule), `RaceRuns`.
- Announcement text: the reset title keys in `voyager/server` resources.
- Out of scope: map files and the catalogue. No `CHECKPOINT` is added to any shipped map, and the committed map has none
  (design D2). Also out of scope: a catalogue check that a checkpoint lies inside the bounds, the spawn lift (design D3,
  option S2, not chosen), the rocket's burn and cooldown durations, race-clock rules, scoring formulas, the GO release,
  and the legacy tree.

## Impact

- Code: `RunReset`, `RaceRun` and their tests (`RunResetTest`, `RaceRunTest`), `RunResetterTest`, `RacersTest`,
  `CupSessionTest`, `CupPlaythroughTest`; in `voyager/platform`, `Racers`, `RunResetter`, `CupSession`, `RaceRuns`.
- Golden master: no existing transcript records a reset (design D7). One reset transcript is added.
- Docs: `docs/reference/course-reset.md`, `docs/guides/how-to-run-a-playtest.md`, `docs/decisions/0031-*.md` (new), and a
  status note in `docs/decisions/0028-*.md`.
- Gameplay: a racer reset to a checkpoint keeps less progress than before, and a racer reset on a map with no checkpoint
  restarts its ring progress. Both need a playtest and a Pulse review (design R1 and R2).
