# Design

## Context

Ring credit is advanced in `race.progress` (`ProgressTracker`) from two consecutive positions per racer. The positions are the
client's reported ones (`CupSession.java:963-967`, `LivePlayerSampler.java:59-62`). The shadow simulation in the platform keeps
a server-side velocity for boost and velocity maths, and it already diverges from the client by a small offset. No code compares
the reported displacement with what flight allows.

## Goals and non-goals

- Goal: flag a sampled tick whose reported displacement exceeds what the shadow speed allows, in observe mode first, and
  withhold ring credit for flagged ticks in enforce mode.
- Goal: thresholds come from measured traces, not from guesses.
- Non-goal: kicks, bans, score penalties, anti-cheat beyond displacement, changes to the flight formulas.

## Decisions

### D1. The bound is a pure function in voyager-physics

`net.elytrarace.voyager.physics.plausibility.DisplacementBound` takes the shadow speed of the two ticks, the elapsed ticks and
the reported displacement, and returns a `DisplacementVerdict` (plausible flag, excess). Named constants hold the slack: a
multiplicative factor for speed and an additive allowance for ring effect snaps and firework bursts. The constants are
`static final` in that class and the class has a private constructor.

### D2. The race receives a boolean, not the physics

`voyager-race` depends only on `voyager-api`, and `voyager-physics` depends only on `voyager-api`. The platform adapter asks
`DisplacementBound` for the verdict and passes `plausible` into `RaceRun` advance. Race never imports physics. Dependency
direction: `platform -> physics`, `platform -> race`, and neither inner module knows the other.

### D3. Observe first, enforce after calibration

Observe mode records flags and the largest excess per run, into `plausibility_error`. Enforce mode is a separate pull request,
merged only after the owner accepts thresholds from the observe-mode traces.

### D4. Flags are per segment, not per run

A flagged tick withholds credit only for the segment it covers. A finish cannot be granted by a flagged tick. A run's flag is the
maximum excess, so one bad tick is visible in the result.

### D5. Ring effects and boost are part of the bound

Ring BOOST and SLOW effects and firework bursts move the player with server-sent velocity. The shadow speed already includes
them, so the bound uses the shadow speed after the effect. A test fixture per effect is required before enforce mode.

### D6. The check is off the tick path only in the sense of no I/O

The check is a few arithmetic operations per sampled racer per tick, with no allocation beyond the verdict. It runs in the
existing sampling loop, so no new thread is added.

## Module map

| Type | Module | Package |
|---|---|---|
| `DisplacementBound`, `DisplacementVerdict` | voyager-physics | `physics.plausibility` |
| `RingPlausibility` (mode enum `off`, `observe`, `enforce`) | voyager-api | `api.race` |
| `RaceRun` advance parameter `plausible` | voyager-race | `race.progress` |
| Verdict wiring in the sampling loop | voyager-platform | `platform.cup` |
| `RingPlausibilitySettings` record | voyager-server | `server.config` |
| `plausibility_error` write | voyager-persistence | `persistence.entity` (existing column) |

## Dependency direction (Clean Architecture)

`voyager-server -> voyager-platform -> {voyager-physics, voyager-race} -> voyager-api`. Physics and race do not depend on each
other. The composition root reads the mode and passes it down; no domain type reads configuration.

## Fitness

- Existing rule "Dependencies point inward only" covers the module edges.
- New `@ArchTest` in `voyager-fitness`: classes in `net.elytrarace.voyager.race..` do not depend on
  `net.elytrarace.voyager.physics..`. `allowEmptyShould(false)`.
- New `@ArchTest`: classes in `net.elytrarace.voyager.physics.plausibility..` do not depend on `net.minestom..`, `java.io..` or
  `java.nio..`.

## Test plan (F.I.R.S.T.)

- Unit: `DisplacementBoundTest` with fixed speeds and displacements; no clock, no I/O.
- Unit: `ProgressTrackerPlausibilityTest` with a plausible flag injected; the ring is credited in observe mode and withheld in
  enforce mode.
- Fixture: `LegitimateTraceTest` reads committed trace fixtures from a test resource and asserts no tick is flagged. Fixtures are
  read-only, so the test is repeatable.
- No `Thread.sleep`, no system clock, no shared static state; the constants are read, not mutated.

## Architecture decision

ADR-0049 "Ring credit is checked for plausibility before enforcement" (MADR 4.0, status Proposed) records D1 to D3 and the
thresholds' source. It is written in the phase 1 pull request, not in this change directory.

## Risks

- A false positive in enforce mode withholds a legitimate ring. Mitigation: observe mode first; the owner accepts the thresholds.
- Shadow divergence grows with latency. Mitigation: the additive allowance is set from a high-latency trace.
- The bound's slack is a cheat surface. Mitigation: the owner accepts the residual risk in the open questions.
