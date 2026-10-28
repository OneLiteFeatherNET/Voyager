# ADR-0041: Braking after the finish and after the time limit is a second kind of server-sent velocity

## Status

Proposed

The owner accepts or rejects this record. The change that implements it is `smooth-map-transitions` (OpenSpec, design
decision 4). Status moves to Accepted only on the owner's word.

## Date

2026-10-28

## Decision makers

- TheMeinerLP (project owner). The owner asked for the racer to stop being dropped out of the sky at the finish, and for
  the racer to be placed on the next map without a stall. The braking is the answer to the first.

## Context and problem statement

The velocity boundary is `VelocityExit`, and it names the forces that may leave the server as a velocity: a zero when a map
ends or a racer is reset, and a ring `BOOST` or `SLOW` effect. Normal elytra flight is client-authoritative, as in Vanilla,
and the server sends nothing else.

A racer who passes the last ring of a map is stopped in the air today. The map's game phase ends when the last racer finishes,
and its end sends a zero velocity and clears the elytra, so the racer drops out of the sky. The owner playtested the three-map
`legacy_cup` and called this out. The same happens to a racer still in the air when the time limit ends.

The question is how the flight is brought to a stop without a stop: whether the server may send a velocity to slow a racer
after a finish, and by what rule.

## Decision drivers

- A racer who finished must not fall out of the sky, nor stop dead in mid-air.
- The client's flight stays the client's. A velocity leaves the server only for a force the project has named.
- The braking must not change the scoring, the ring detection or the finish time. It acts after the finish is recorded.
- The braking has to be measurable in a playtest, since its numbers are a first estimate.

## Considered options

1. **Do nothing after the finish.** Rejected: it is the behaviour the owner reported.
2. **Slow Falling.** Considered. It lowers the fall but does not reduce the forward speed, so the racer still crosses the
   map's end at full speed. It is kept as the fallback if the damped velocity fights the client's flight in the smoke test.
3. **A damped velocity through `VelocityExit`, for 50 ticks (2.5 s), down to a quarter of the finish speed, never reversed (chosen).**
   The curve is a pure function in `voyager-race` (`race/glide`). The server sends one velocity per tick through the sanctioned
   door, and the glide ends with the elytra off and a zero velocity.
4. **Stop the racer at the finish with a zero.** Rejected: it is the cut-off the owner reported.

## Decision outcome

Chosen option: **3**.

Braking after the finish, and after the map's time limit for a racer still in the air, is a second kind of server-sent velocity.
The first is the zero that ends a flight and the ring effect already named in `VelocityExit`. The braking is named there as its
own kind, and it is the only kind that is sent every tick for a fixed span rather than once.

- `VelocityExit` is still the only place a velocity reaches Minestom, and `ApiPurityTest.onlyVelocityExitSendsAVelocityToMinestom`
  keeps its rule.
- The damped velocity is computed from the racer's last two positions, so it follows the flight the client actually flew.
- The glide captures the map it started on, so it is unaffected by the next map becoming the current one.
- The firework boost is still not a velocity sent by the server; it stays a rocket the client boosts itself.

## Consequences

- Positive: a racer who finishes keeps a visible, decelerating flight to the end of the glide, and no racer is stopped in mid-air.
- Positive: the server still overrides the client's flight at one named moment per racer, the finish or the time limit.
- Negative: the server now sends up to 50 velocities per finished racer. The cost is one packet per racer per tick for 2.5 s,
  which is small beside the particle budget of `race/racing-line-display`.
- Negative: the quarter-speed target and the 50-tick length are a first estimate. The owner decides them after a playtest.
- Reversibility: high. The glide is a single class and one call site in the cup; removing it restores the zero at the finish.

## Confirmation

- `GlideOutTest` pins the curve: the speed at the end of 50 ticks is at most a quarter of the finish speed, the speed falls on
  every tick, and the direction never reverses.
- `GlideOutSessionTest` pins the platform behaviour: a velocity is sent on each of the 50 ticks, the elytra is off with a zero
  velocity after the glide, and a racer airborne at the time limit glides out while a racer on the ground does not.
- `ApiPurityTest.onlyVelocityExitSendsAVelocityToMinestom` keeps the sole sender rule.

## More information

- Change: `openspec/changes/smooth-map-transitions/` (proposal, design decision 4 and decision 8, the spec `race/finish-glide-out`).
- Related: [ADR-0028](0028-racers-take-off-without-a-server-impulse.md), which removed the launch impulse and kept the zero.
- Numbering: ADR-0040 is held in flight by `add-catalogue-keys-and-authors`, so this record takes the next free number.
