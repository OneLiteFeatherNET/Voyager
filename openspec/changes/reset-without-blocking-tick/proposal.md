# Proposal

**Type and scope:** `perf(race)`. Ships as one code change under this title. It moves the placement of a reset racer off the
server tick: the racer's target chunks load in the background, the racer is held still meanwhile, and the reset is applied on
the first tick after the chunks are present.

**Depends on:** `smooth-map-transitions` (archived). The non-blocking arrival path it added is reused here.

## Why

A racer who leaves the bounds or lands is reset to the last checkpoint or to the start. The reset is decided on the tick the
racer is detected (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/cup/CupSession.java:1024-1042`), and it moves
the racer through `RunResetter.reset` (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/cup/RunResetter.java:80`,
`:83`). The move calls `MapTransition.reposition`, which calls `Arrivals.reposition` (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/world/Arrivals.java:36-38`).
That method joins every chunk future around the target on the tick (`Arrivals.java:44-53`, join at 52), and then joins the
teleport (`Arrivals.java:38`).

So a reset freezes the whole server for as long as the chunks around the checkpoint take to load. The owner's analysis found
this is the last gameplay path that waits on the tick. The map transition was moved off the tick by `smooth-map-transitions`,
which deferred this reset path explicitly; its proposal records the reset as "noted for a later change". The architecture test
`voyager/fitness/src/test/java/net/elytrarace/fitness/TickBoundarySeededTest.java:35-44` freezes `Arrivals` as accepted debt in
the committed baseline, so the stall is invisible to the build. The stall grows with the deployment's larger view distance.

## What Changes

- **Background preload for a reset.** A reset starts loading the chunks around its target through the same `PendingArrival`
  path that map moves use, and returns at once.
- **Hold until present.** The racer is held in place (no gliding, no movement, the same hold the spawn freeze uses) until every
  target chunk is present. The reset is then applied on the next tick.
- **Second reset during the hold.** A second reset detected while the first is pending replaces the pending target with the new
  one and keeps the hold. The title and sound of the reset are shown on the tick the reset is detected, as today.
- **Measure first.** The stall of the current reset path is measured before the change (a task), so the change records a number.
- The baseline in `TickBoundarySeededTest` shrinks by the `reposition` entry once the path no longer joins on the tick.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. The existing requirements of `race-course-reset` (the title and sound on detection, the placement, the zero velocity) keep
their wording. The new rules are ADDED requirements.

- `race-course-reset`: ADDED a requirement that a reset is applied after its target chunks are present, and that the racer is
  held meanwhile.
- `race/async-arrival-preload`: ADDED a requirement that a reset preloads its target through the same non-blocking path as a map
  move.

## Trade-offs

### Pros

- The last gameplay path that waits on the tick stops waiting, so a reset in a large view distance no longer freezes every racer.
- It reuses the non-blocking arrival path that shipped with `smooth-map-transitions`; no second chunk-loading mechanism.
- The architecture baseline shrinks, so a new blocking wait is caught sooner.

### Cons

- The racer is held for a few ticks after a reset while the chunks load. The hold is a feel cost: a reset racer who is held
  still for a moment is slower to recover than one teleported at once. The measurement task sets the number.
- A second reset during the hold must be handled (above), which adds a state to the racer's reset record.
- While held, the racer is out of the race for a few ticks; the progress tracker must not count rings or effects on those ticks.

## Out of scope

- The reset rules: which causes reset a racer, the target, the cooldown and the landing grace (`race-course-reset`).
- The map-move path, which already does not block (`smooth-map-transitions`).
- The view-distance setting (a deployment setting).

## Open questions for the owner

- Accept a hold of a few ticks on a reset, or require that a reset never holds the racer (which would need the chunks to be
  present before the reset is decided)?
