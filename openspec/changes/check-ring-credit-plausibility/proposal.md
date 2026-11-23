# Proposal

**Type and scope:** `feat(race)`. Ring credit is the race slice's rule (`race.progress`). The check's bound is a pure function in
the physics slice, and the platform adapter feeds its verdict into the race. Ships as code under `feat(race)`, in two pull
requests: phase 1 observes only, phase 2 enforces. Breaking: no. Phase 1 changes no score and no ring credit.

## Why

Ring credit is decided from the position the client reports. `CupSession.java:963-967` passes `racer.getPosition()` to
`runs.advance`, and `LivePlayerSampler.java:59-62` says ring collision is advanced with the client's reported position, not
the simulated one. `ProgressTracker.java:45` credits any segment that crosses a ring disc, and nothing bounds how far that
segment may be. A modified client that reports a position far along the course can pass rings it never flew through, and
the score follows. No check compares the displacement with what elytra flight allows.

The database already has a column for the result of such a check, `plausibility_error` (`RaceResultEntity.java:71-72`), and
nothing writes it. Two docs describe the guard as present: `docs/elytra-physics-reference.md:473` says the server owns
ring-collision decisions, so a desync cannot skip checkpoints, and `docs/reference/elytra-physics-26.2.md:154` specifies a
plausibility check. Neither is true in code. Before a public launch, ring credit is the score, so this gap decides who wins.

## What Changes

- A pure function in `voyager-physics` bounds the displacement a racer can make between two sampled ticks. It takes the shadow
  simulation's speed for those ticks, plus named slack constants. It has no Minestom or I/O import.
- The platform adapter computes the verdict for each sampled tick and passes a boolean `plausible` into the race's advance.
  `voyager-race` does not import `voyager-physics`; the rule stays inward.
- **Phase 1, observe:** a flagged tick is counted, the largest excess of the run is written to `plausibility_error`, and a
  cup-end summary is logged at INFO. Ring credit, scores and messages do not change.
- **Phase 2, enforce:** a flagged tick withholds ring credit, and the racer sees one action-bar line. It is not a kick and not a
  ban. Phase 2 starts only after the owner accepts the thresholds from phase 1's traces.
- A setting `VOYAGER_RING_PLAUSIBILITY` (`off`, `observe`, `enforce`; default `observe`) is read through the configuration
  loader and documented. `off` skips the check; phase 2 is enabled per server by the operator.

## Capabilities

### New Capabilities

- `race/ring-plausibility`: the displacement bound, the observe and enforce modes, the flag, and what a flagged tick does to
  ring credit.

### Modified Capabilities

- `persistence/postgres-storage`: ADDED requirement that a race result records its plausibility flag, using the existing
  `plausibility_error` column.
- `server/configuration`: ADDED requirement that `VOYAGER_RING_PLAUSIBILITY` is read through the loader, checked, and documented.

## Trade-offs

### Pros

- Closes the trivial ring-skip: a client cannot pass rings by reporting far-away positions without the bound flagging it.
- Reuses the shadow simulation that already exists for velocity and boost maths, and the schema hook that already exists.
- Observe-first gives real traces and a measured false-positive rate before any player is affected.

### Cons

- False positives from legitimate drift: the shadow simulation diverges from the client by about one tick at each end on a 60 ms
  connection (`LivePlayerSampler.java:51-62`), and ring BOOST and SLOW effects and firework bursts move the player in ways the
  bound must include.
- A cheat inside the bound passes. The bound stops teleports and large skips, not a slightly faster glide.
- Needs real-client traces to calibrate, which the owner must record (`docs/guides/how-to-record-a-trace.md`).
- Effort L across two phases.

## Out of scope

- Server-side authority over flight. Voyager keeps the client-authoritative flight model (CLAUDE.md, "Elytra velocity authority").
- Anti-cheat beyond displacement: speed hacks inside the bound, packet timing, and input replay.
- Kicks, bans or scoring penalties. Enforcement withholds ring credit only.
- Changes to the physics formulas of `ElytraSimulator`.
- Changes to the boost or ring effects.

## Open questions for the owner

- The enforcement threshold: how many standard deviations of the observed excess flag a tick, and whether a single flagged tick
  or a run of flagged ticks withholds credit.
- Whether phase 2 runs in the closed alpha at all, or only after a public launch.
