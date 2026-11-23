# Proposal

**Type and scope:** `feat(race)`. The change lets one racer fly. It touches the race slice's start gate and practice flow,
the platform lobby (`platform.lobby`) that reads the gate, the computer-racer grid (`race/com-racers`), the lobby labels and
one shipped cup. Ships as one code change under the title `feat(race): start a solo practice run for a lone racer`. Breaking:
no. The two-racer ranked start is unchanged.

## Why

A lone player on a production server cannot fly. The minimum racer count is two in production
(`voyager/server/src/main/java/net/elytrarace/voyager/server/config/ServerSettings.java:129`), and a single racer is only told
how many more are needed (`openspec/specs/race/waiting-lobby/spec.md`, requirement "A cup starts only when enough racers are
online"). Nothing else in the shipped content gives a lone racer a first flight:

- Both shipped cups are `"mode": "RACE"` (`voyager/server/src/main/resources/cups/alpha_cup.json:6`,
  `voyager/server/src/main/resources/cups/legacy_cup.json:6`).
- `GameMode.PRACTICE` declares a minimum of one player (`voyager/api/src/main/java/net/elytrarace/voyager/api/race/GameMode.java:10`),
  but `minimumPlayers()` (`GameMode.java:22`) has no caller in any `src/main`, so the start gate never reads the mode.
- A `PRACTICE` cup is a retry loop that never finishes (`voyager/race/src/main/java/net/elytrarace/voyager/race/flow/RaceStateMachine.java:20`,
  and the rotation rule at `RaceStateMachine.java:92-101`). The engine already supports it; no shipped content uses it.
- Computer racers default to zero (`VOYAGER_COM_COUNT`), and the grid fill is gated on the human minimum
  (`openspec/specs/race/com-racers/spec.md`, requirement "Computer racers fill the grid at cup start"), so a lone racer would fly alone.
- No string tells a racer whether a run is ranked. The English bundle (`voyager/server/src/main/resources/voyager_en_US.properties`)
  has no "ranked" and no "practice" line, so a racer who sees a result cannot tell which kind of run it was.

## What Changes

- The start gate reads the cup's mode minimum (`GameMode.minimumPlayers()`: RACE 2, PRACTICE 1). `VOYAGER_MIN_PLAYERS` still
  overrides both, and RACE keeps its two-racer minimum.
- A lone racer who waits in the lobby for a configurable solo wait (proposed 30 s) starts the shipped practice cup of the first map
  on their own. If a second racer joins during the wait, the RACE gate applies and the normal countdown runs instead.
- A practice start fills its grid with computer racers (grid size is an open question). Computer racers never count as racers,
  as today.
- A practice run is never ranked: it is not stored in ranked statistics and not in the cup standings.
- The lobby sidebar title and the tutorial each name the run: `Practice · not ranked` or `Ranked`.
- One shipped `PRACTICE` cup of the first map is added. The shipped catalogue goes from two cups to three.
- RACE keeps its two-racer minimum, its countdown and its standings. No engine rule for rings, boost or scoring changes.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `race/waiting-lobby`: the start gate reads the mode minimum; a lone racer starts a practice run after the solo wait.
- `race/com-racers`: the grid fill for a practice start; the "no computer racers" rule excludes a practice start.
- `race/lobby-hud`: the sidebar title names the run as ranked or practice.
- `race/tutorial-book`: one line names the run as ranked or practice.
- `catalog/shipped-catalogue`: the exactly-two-cups requirement becomes exactly three, with the practice cup.

## Decisions taken in this proposal

- **The practice cup is not in the rotation.** It is started only by a solo wait. Adding it to `VOYAGER_CUP_ROTATION` is an
  operator choice that this change does not make.
- **The practice run ends when the racer leaves or the operator stops it.** It does not end by itself, because `PRACTICE` never
  finishes (`RaceStateMachine.java:20`). The leave item and `/hub` are the way out, as in the lobby.
- **The solo wait is 30 s.** Long enough that a second racer who is already walking in gets the ranked start; short enough that a
  lone joiner flies within half a minute.

## Trade-offs

### Pros

- A lone joiner flies within about 30 s, instead of seeing "one more racer needed" indefinitely.
- Reuses the existing `PRACTICE` mode, the state machine and the computer racers. No new physics, no new scoring.
- Ranked results stay honest: practice runs are labelled and not stored as ranked.
- The rule ("Practice · not ranked") tells a racer what a run means before they fly it.

### Cons

- The solo wait and the practice grid size need tuning on a live server.
- One extra cup in the shipped catalogue, which the alpha's "exactly two cups" rule currently fixes.
- A solo run against computer racers can feel grindy after a few laps. The practice end-of-loop experience (what a racer sees
  when they leave, or after many retries) needs its own design pass.
- The sidebar title grows by a few characters, which the lobby HUD has to fit.

## Out of scope

- Ranked rules, the medal brackets, the scoring and the standings of RACE cups.
- Calibration of the reference times and the content of the shipped maps (see `calibrate-shipped-catalogue`).
- The retire action and the end of a stuck map (see `end-stuck-map-waits`).
- Persistence of practice runs in PostgreSQL.
- Putting the practice cup into the cup rotation by default.

## Open questions for the owner

- **Solo wait length:** 30 s proposed. Alternatives: 20 s (matches the lobby length) or 45 s.
- **Practice grid size:** how many computer racers fill a solo practice start? Proposed 3 (four racers in total), set by a new
  setting `VOYAGER_PRACTICE_COM_COUNT`. The current `VOYAGER_COM_COUNT` default of 0 would otherwise give a solo run no opponents.
- **Does the practice cup loop or end?** The state machine loops it on the same map. Proposed: it loops until the racer leaves;
  an end-of-loop screen is out of scope here.
