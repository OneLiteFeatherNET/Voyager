# Proposal

**Type and scope:** `fix(race)`. Ships as one code change under this title. A computer racer (COM) takes the map's boost: the boost
changes its target speed after the reaction delay, as the spec already says it should.

**Depends on:** nothing. The boost factor is read from the map's existing `ringEffects` (`RingEffectTuning.boostMultiplier`).

## Why

A COM racer is meant to react to a boost ring after a delay. The spec says so (`openspec/specs/race/com-racers/spec.md:94-96`,
scenario "A boost reaction delay is applied": the boost takes effect after the MEDIUM reaction delay, not before).

The code records the delay and then applies nothing:

- `voyager/platform/src/main/java/net/elytrarace/voyager/platform/com/PilotBrain.java:13-14` calls the four-argument
  `ComPilot.step`, which passes `ComSpeedEffect.NONE` (`voyager/race/src/main/java/net/elytrarace/voyager/race/com/ComPilot.java:69-70`).
- The boost branch sets `boostDue` after the delay (`ComPilot.java:94-96`), and on the tick it is due applies the identity effect
  (`ComPilot.java:99-101`).

So a COM racer passes a boost ring and keeps its speed, while a human racer gets the boost. The spec's reaction-delay behaviour
is not reachable, and the COM racers do not respond to the course the way the spec describes.

## What Changes

- **Boost reaches the brain.** The map's `boostMultiplier` is turned into a `ComSpeedEffect` for each COM racer's map, and passed
  into the brain with each step (`ComBrain.step` gains the effect as a parameter).
- **Reaction delay honoured.** The boost takes effect on the tick the delay ends, as the existing scenario states. The existing
  acceleration bound keeps the speed from jumping.
- **Cap unchanged.** The reference-speed cap (com-racers "never exceed the reference speed") still applies after the boost. A boost
  lifts a MEDIUM or EASY racer toward the cap and does nothing to a HARD racer already at it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. The existing cap and reaction-delay requirements keep their wording. The boost rule is ADDED.

- `race/com-racers`: ADDED a requirement that a boost ring multiplies a computer racer's target speed by the map's boost factor,
  after the reaction delay, within the reference cap and the acceleration bound.

## Trade-offs

### Pros

- Meets the spec's reaction-delay scenario, which describes a behaviour the code does not yet have.
- Small: one parameter on the brain's step, one factory call per map, and tests that use a fake brain and a fake effect.
- Keeps the difficulty curve: the boost is a target-speed effect capped at the reference speed, so HARD gains nothing beyond the
  cap while EASY and MEDIUM gain most.

### Cons

- COM racers become faster on boost maps. The cup balance changes, and the owner must rebalance the boost factors or the COM
  tuning. The change alone is a difficulty change, not only a fix.
- It is a kinematic target-speed multiplier, not a rocket impulse. A COM racer does not get the velocity burst a human racer gets
  from a boost ring, so a human who crosses the same ring still gains more. The difference is stated here, not hidden.
- A HARD racer, already at the reference cap, is unaffected by design. That follows from the cap, but a reviewer may read it as the
  boost not working on HARD.

## Out of scope

- The human racer's boost (`race/ring-effects`), which is unchanged.
- Per-difficulty boost reactions (the delay values are the existing ones).
- Rebalancing the boost factors or the COM tuning. The owner does that after this lands.

## Open questions for the owner

- After the change, should the cup's boost maps be rebalanced now, or should the boost for COM racers be scaled by difficulty
  (for example, half on EASY) so the existing curve stays?
