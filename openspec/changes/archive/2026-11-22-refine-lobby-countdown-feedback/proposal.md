# Proposal

**Type and scope:** `fix(race)`. The lobby's code and its tests live in the `race` slice (`voyager/platform/.../lobby`), and every
lobby commit so far uses that scope (`feat(race): rework the waiting lobby ...`, #348). The commitlint config sets no scope
list, so `fix(race)` is valid; `fix(lobby)` would start a second scope for the same code. Ships as one code change under the
title `fix(race): play a chime when the lobby fills and keep lobby items until go`.

## Why

Two details of the lobby shipped in #348 do not match what the owner asked for.

1. When the lobby fills and the countdown is shortened, the racers hear a xylophone note. The owner wants the Cygnus chime
   (`block.note_block.chime`), which matches the countdown plings of the lobby.
2. The leave door and the tutorial book disappear about three seconds before GO. The racer sees the items vanish while the
   countdown is still running, and the start is already committed. The items must stay until GO and be removed exactly at GO.

The cause of the second point is the room's item rule: it removes the items on the first tick of the `COMMITTED` situation,
which begins three seconds before the GO tick (`StartGate.COMMIT_WINDOW`). The first map and the lobby hints therefore lose
their items too early.

## What Changes

- The full-lobby sound becomes `block.note_block.chime` at volume 0.8 and pitch 1.0, played once to each waiting racer when
  the countdown is shortened. The xylophone is no longer played.
- The lobby items (leave door in slot 8, tutorial book in slot 1) stay in the racer's inventory through the committed window.
  They are removed on the tick the situation becomes `RUNNING`, the GO tick, and no earlier.
- Nothing else changes: the pling table, the join sound, the cancelled drop and swap, the names, the leave command and the
  tutorial pages keep their current behaviour.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `race/lobby-feedback`: the full-lobby requirement names the chime instead of the xylophone. The distinctness requirement
  gains one named exception, because the chime is the race's reset sound (`RaceFeedback`). The owner chose the chime
  knowing this; the exception is limited to the full-lobby sound.
- `race/lobby-items`: the removal requirement moves from the committed window to the GO tick.

## Dependencies

- `cygnus-style-lobby` is not archived yet. The deltas here are written against its requirements, so they must be applied
  after that change, or together with it. If it is archived first, these MODIFIED blocks apply to the archived main specs
  unchanged.

## Out of scope

- The pling table, the join sound and the boss bar or HUD texts (`race/lobby-hud`, `race/lobby-feedback`, unchanged).
- The race's own sounds in `RaceFeedback`, including the reset chime. Changing the reset sound is not part of this change.
- The race start itself: the GO tick, the commit window (`StartGate.COMMIT_WINDOW`) and the length of the lobby are not changed.
- Saving any lobby state (`race/lobby-items` says nothing is saved, and that stays true).
- Archiving `cygnus-style-lobby` and ticking its tasks; both are follow-up work the owner asks for separately.
