# Movement of Computer Racers on the Racing Line: An Arc-Length Follower with Measured Speed Bounds

**Authors:** Voyager Development Team | **Date:** 2026-10-14 | **Status:** Draft, awaiting the owner's playtest

## Abstract

Voyager's computer racers ("COM n") fill the grid of a cup. The server moves each one along the racing line that a human
sees, because the server, not the client, decides where a computer racer is. This note records the movement model, the
speed and turn bounds measured from the elytra traces in the repository, and the open questions. The model is an
arc-length follower: a scalar distance along the line, a speed that approaches a target at a bounded acceleration, and a
seeded lateral wander. The measured bounds are a cap of 1.7 blocks per tick, a floor of 0.70 blocks per tick, a yaw bound
of 0.91 degrees per tick, a pitch bound of 7.23 degrees per tick and a downward sink bound of 0.134 blocks per tick. The
floor moved from the 0.8 proposed in the design, because a steady glide never reaches 0.8. The yaw, pitch and sink bounds
are measured but not yet enforced by the brain. Two questions stay open: whether a client shows a player-type body without
a connection (S2), and whether the difficulty values feel right to a human.

## 1. Introduction

Normal elytra flight is client-authoritative (ADR-0028), so a computer racer's position cannot come from a client. The
server has to place it. The design asks for racers that look and move like racers and that follow the line humans are
shown, with no elytra physics for the bot. The question this note answers is how to move a body along that line so that
it looks plausible, stays deterministic for a seed, and does not exceed what a vanilla glide does.

## 2. Method

### 2.1 Movement model

The model is an arc-length follower, chosen over a steering controller and over a replay of a recorded line (design D1).
The brain computes the cumulative arc length of the racing line once per map (`ComPath`). Each game tick it holds one
scalar distance `s` along the line and a speed `v`, both in blocks and blocks per tick.

1. The target speed is `v* = factor × personal × 1.7`, where `factor` is the difficulty's speed factor and `personal` is a
   seeded variation within plus or minus 2 per cent. The factor is capped at 1.00, so no racer exceeds the reference.
2. The speed moves toward `v*` by at most an acceleration bound of 0.05 blocks per tick per tick, and is kept between the
   floor and the cap.
3. The distance advances by the speed. The point and tangent at `s` are found by binary search over the cumulative arc
   lengths.
4. A lateral offset, perpendicular to the tangent in the horizontal plane, is added. The offset is
   `o(s) = A sin(2π s / λ + φ) + m`, where `A` is the amplitude, `λ` the wobble period, and `φ` and `m` are seeded.
5. A missed ring is a detour. The racer is moved off the ring's plane, outside its disc, and returns through the disc, so
   the existing ring test counts the crossing. Each detour costs time, not progress. A racer that passes no ring for 400
   ticks is removed as did-not-finish.

The brain reads only the public line, the ring list and its own state. It reads no clock. The only randomness is the
seed.

### 2.2 Measurement

The bounds come from spike S1 (task 0.3). The spike measured every recorded elytra trace in
`voyager/physics/src/test/resources/traces/` and read off the speed, yaw, pitch and sink on each tick. Tick indices are the
`index` field of each file, and the `ticks` array starts at line 9. The physics reference
(`docs/reference/elytra-physics-26.2.md`) gives the rocket terminal velocity as `1.7 · lookAngle` at line 492 and the
steady boosted speed as `1.6862` at line 495.

### 2.3 Where the bounds live

| Bound | Constant | File and line |
|---|---|---|
| Cap (reference speed) | `ComTuning.REFERENCE_SPEED` | `voyager/race/src/main/java/net/elytrarace/voyager/race/com/ComTuning.java:32` |
| Floor | `ComTuning.FLOOR_SPEED` | `voyager/race/src/main/java/net/elytrarace/voyager/race/com/ComTuning.java:35` |
| Floor clamp | `clampSpeed` | `voyager/race/src/main/java/net/elytrarace/voyager/race/com/ComPilot.java:224` |
| Acceleration bound | `ComPilot.ACCELERATION_BOUND` | `voyager/race/src/main/java/net/elytrarace/voyager/race/com/ComPilot.java:31` |
| Watchdog | `ComPilot.WATCHDOG_TICKS` | `voyager/race/src/main/java/net/elytrarace/voyager/race/com/ComPilot.java:34` |
| Yaw, pitch, sink | none | not implemented |

## 3. Measurements

| Quantity | Unit | Measured | Source (file and tick or line) |
|---|---|---|---|
| Boosted plateau (peak) | blocks per tick | 1.68615 (chained boosts), 1.67161 (single boost) | `chained-boosts.json` tick 45; `single-boost.json` tick 51 |
| Terminal rocket speed | blocks per tick | 1.7 × look angle | `docs/reference/elytra-physics-26.2.md:492` |
| Glide plateau at pitch -5 | blocks per tick | 0.7148; ticks 150 to 199 median 0.7086; 0.8 never reached | `steady-glide.json` ticks 150 to 199 |
| Yaw on turning ticks | degrees per tick | 0.9091 on every turning tick (99 of 200 ticks) | `sustained-turn.json` ticks 104, 123, 125 |
| Pitch at climb onset | degrees per tick | 7.2222 for 9 ticks, peak at tick 45 | `climb-into-stall.json` ticks 42 to 48 |
| Pitch on dive and pull-out ramps | degrees per tick | 5.0 | `dive-and-pull-out.json` ticks 17 to 19 |
| Sink on steady glide | blocks per tick, downward | 0.1337 maximum | `steady-glide.json` tick 22; `sustained-turn.json` tick 22 |
| Dive speed without a rocket | blocks per tick | 2.35 (peak 2.33) | `dive-and-pull-out.json` tick 122; `pitch-extremes.json` |
| Pull-out sink | blocks per tick, downward | 2.11 | `dive-and-pull-out.json` tick 100 |

The bounds in the design (D14) are the measured values rounded to the precision the brain uses:

- **Cap 1.7 blocks per tick**, unchanged. The measured plateau of 1.686 is the rocket's steady speed, which the reference
  states as the terminal speed.
- **Floor 0.70 blocks per tick**, changed from 0.8. A steady glide at pitch -5 plateaus at 0.715, so a floor of 0.8 would
  hold a gliding computer racer faster than a vanilla glide. The change moves the golden transcript, which is regenerated
  in the same change.
- **Yaw bound 0.91 degrees per tick**, the value of every turning tick in the sustained turn.
- **Pitch bound 7.23 degrees per tick**, the climb onset. Dive and pull-out ramps reach 5.0.
- **Sink bound 0.134 blocks per tick**, the steady-glide maximum.

## 4. Findings

1. **The cap is the reference, and no difficulty exceeds it.** The easiest level aims at 0.85 of the cap, about 1.45
   blocks per tick. The hardest aims at the cap itself. A computer racer is never a faster racer than the physics
   reference allows.
2. **A glide never reaches 0.8 blocks per tick.** The floor of 0.8 proposed in the design would have forced a glide
   faster than the vanilla glide. 0.70 keeps the floor below the measured plateau.
3. **Humans dive faster than the cap.** A human can reach 2.35 blocks per tick in a dive without a rocket, and the pull-out
   sinks 2.11 blocks per tick. A computer racer on the racing line does not dive, so these figures sit outside the bounds
   and are not modelled. The owner must confirm that a computer racer may not match a human dive.
4. **The turn bounds are measurable, but not yet enforced.** The yaw and pitch bounds are visible in the traces. The brain
   does not limit the yaw, pitch or downward sink per tick, and no test asserts them.
5. **The boost delay is inert.** The brain carries a reaction delay for boost rings, which is 10, 4 or 0 ticks by
   difficulty, and it is tested against a fake ring effect. The server passes the brain no boost effect yet, so the delay
   does not act in a running server.

## 5. Tuning plan

1. Keep the cap at 1.7 and the floor at 0.70. Both are measured.
2. Tune the speed factor (0.85, 0.93, 1.00) and the wander amplitude (1.2, 0.6, 0.2 blocks) from the owner's playtest,
   one level at a time, with the other knobs fixed.
3. Enforce the yaw, pitch and sink bounds as assertions over a recorded lap of the brain, before any change to the
   steering. A violated bound is a defect in the brain, not a number to loosen.
4. Keep the seeded personal variation at plus or minus 2 per cent unless the playtest shows that racers at one level look
   identical.
5. Connect the boost reaction delay to the boost ring effects once the per-map boost rings reach the brain, then tune it
   with the other knobs.

## 6. Open questions

**S2, body visibility.** A player-type entity without a live connection is shown by the client only if the client accepts
it. No test can answer this, because the answer depends on a real client. The design builds the primary body as a plain
entity of the player type with a tab-list entry, and keeps an armour stand as the fallback. The owner checks the body in
playtest. If the body is hidden, the change switches to the armour stand before it ships.

**Difficulty feel.** The table is a starting point. Whether EASY feels slow and wandering, MEDIUM a fair race and HARD a
reference racer is a question for a human player, and the playtest answers it.

## 7. Limits

- The bounds come from the elytra traces the repository holds. They do not cover every flight a human can make.
- The brain reads only the public line, the ring list and its own state. It does not react to any human racer.
- The measurements are from the recordings, not from a live client. The S2 question is open for that reason.
- No timing of the brain is asserted. The per-tick cost is measured at smoke boot, not in a unit test.

## References

- `openspec/changes/com-racers/design.md`, decisions D1, D3, D13 and D14, and the owner decisions OQ1 to OQ10.
- `docs/decisions/0035-com-racers.md`.
- `docs/reference/elytra-physics-26.2.md`, lines 492 and 495.
- `docs/reference/com-racers.md`, the settings and the difficulty table.
- ADR-0028, racers take off without a server impulse.
