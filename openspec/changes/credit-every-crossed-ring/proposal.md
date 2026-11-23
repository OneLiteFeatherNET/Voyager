# Proposal

**Type and scope:** `fix(race)`. Ships as one code change under this title. It changes two things in the ring progress of a
racer: rings crossed on the same tick are all credited, and a reset forgets the position the racer had before the reset.

**Depends on:** nothing. `fix-message-glossary-and-sounds` and `com-racers-take-boost` are independent.

## Why

A racer's progress is a count of rings passed, in order. The owner's game analysis found two ways the count goes wrong at speed
and after a reset.

**Rings crossed on one tick.** `ProgressTracker.advance` tests only the next ring in order (`voyager/race/src/main/java/net/elytrarace/voyager/race/progress/ProgressTracker.java:44-45`).
A racer whose step crosses two ring planes in one tick is credited for the first. The second ring is still open on the next tick,
but the racer's next step starts past its plane, so the second ring is reported missed and the run can no longer be completed
in order. The test that pins the current behaviour, `ProgressTrackerTest.countsAtMostOneRingPerTick`
(`voyager/race/src/test/java/net/elytrarace/voyager/race/progress/ProgressTrackerTest.java:84-91`), asserts that the second ring
stays open. The spec scenario "Two BOOST rings in one tick" (`openspec/specs/race/ring-effects/spec.md:61-63`) therefore cannot
happen: the second ring's effect is never applied on that tick.

**The first tick after a reset.** `CupSession` stores the racer's position into `lastPositions` before the reset is decided
(`voyager/platform/src/main/java/net/elytrarace/voyager/platform/cup/CupSession.java:964`), then resets the racer and teleports
them (`CupSession.java:982`, `voyager/platform/src/main/java/net/elytrarace/voyager/platform/cup/RunResetter.java:80-81`). The
stored position is only cleared when the map changes or the racer leaves (`CupSession.java:1687`, `1702`). On the first tick
after a reset, the segment from the stored pre-reset position to the new position is a teleport, not a flight. `ProgressTracker`
documents that a `null` previous position counts nothing (`ProgressTracker.java:35-36`), but the stored position is not `null`.

## What Changes

- **Every ring crossed on a tick is credited, in the order the rings were crossed.** The tracker tests the following rings against
  the same segment, in the order of the crossing parameter along the segment, and stops at the first ring the segment does not
  cross. `RingPass` exposes the crossing parameter so the order is decided by geometry, not by list position.
- **A reset clears the racer's last position.** The first tick after a reset counts nothing, as `ProgressTracker` already
  documents for a `null` previous position.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. The existing scenario "Two BOOST rings in one tick" is not changed; this change makes it reachable. The new rules are ADDED
requirements, because the current specs state no ring-credit rule.

- `race/ring-effects`: ADDED a requirement that every ring crossed on one tick is credited in the order crossed.
- `race-course-reset`: ADDED a requirement that the first tick after a reset counts no ring and no effect.

## Trade-offs

### Pros

- A racer is credited correctly at any speed; a run is no longer stuck on a ring that was crossed on the same tick.
- The "Two BOOST rings in one tick" scenario, already in the spec, becomes true.
- A reset no longer makes the racer cross a rings-plane along a teleport segment.

### Cons

- Two ring effects can compose on one tick, so one velocity write can be larger than one ring's factor. The map's velocity cap for ring
  effects still applies, but a map author who relied on one-ring-per-tick will see
  faster flight at high speed.
- It changes a rule a test states today (`countsAtMostOneRingPerTick`). The owner confirms the rule before the test is replaced.

## Out of scope

- The guide line and the ring display (`race/racing-line-display`).
- Ring detection geometry (`RingPass` plane and area tests). Only the crossing parameter is exposed.
- Reset targets and the reset causes (`race-course-reset` keeps its requirements; only the position is cleared).

## Open questions for the owner

- Confirm that two rings crossed on one tick should both count and both apply their effect. The alternative is to keep one
  ring per tick and clamp the step at the ring plane, which is a larger change to the physics tick.
