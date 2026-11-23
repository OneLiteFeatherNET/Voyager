# Design

## Context

The game loop is scheduled in the server composition root (`VoyagerServer.java:347`, `scheduleTick` at `:591`). The tick pipeline
runs in `voyager-platform` (`platform.tick`). Particle packets are sent from `platform.cup` (`Fireworks`) and from the render
package. The statistics writer is in `voyager-persistence` and exposes its counters through accessors that nothing calls
(`StatsWriter.java:118-133`). The `/race` command is `voyager/server/.../command/RaceCommand.java` and shows the cup status with
no subcommand.

## Goals and non-goals

- Goal: an operator sees mean and maximum tick duration, particle packets per window and the writer's counters, on the console
  and in `/race`.
- Goal: the numbers are testable with no clock and no server.
- Non-goal: an exporter, a dashboard, alerting, spill-to-file, changed queue or retry policy.

## Decisions

### D1. A window is 600 ticks, counted by the tick

`TickWindow` counts ticks, adds each tick's duration in nanoseconds to a sum and keeps a maximum. On the 600th tick it produces an
immutable `TickSummary` and resets. The duration comes from an injected `LongSupplier` (nanoseconds), so a test supplies fixed
values. The server passes `System::nanoTime`.

### D2. Particle packets are counted by a counter the sender increments

A `PacketCounter` is an `AtomicLong`-free plain counter incremented on the tick thread where particles are sent. Packets sent from
other threads are not counted: the rule is stated in the spec's scope, and a test asserts the tick-thread path. Keeping it on one
thread avoids a shared-state contract; a second thread would need its own counter.

### D3. Writer counters are read, not copied

At each window's end the composition root reads `written()`, `retried()`, `droppedFull()` and `droppedData()` from the writer's
accessors. No counter is added to the writer.

### D4. The log line is the only I/O, once per window

One class, `WindowLog`, is the only observer class that calls a logger. It is called on the 600th tick. The rest of the observer
has no logging import.

### D5. `/race` reads the last summary

`RaceCommand` gets the last completed `TickSummary` from an observer snapshot and renders it. Before the first window, the view says
that no window has completed. The command runs on the server's command thread, so it reads an immutable snapshot published by the
tick thread through a `volatile` reference.

## Module map

| Type | Module | Package |
|---|---|---|
| `TickWindow`, `TickSummary`, `PacketCounter` | voyager-platform | `platform.observe` |
| `WindowLog` | voyager-platform | `platform.observe` |
| Schedule and wiring of the window, `/race` output | voyager-server | `server.inject`, `server.command` |

## Dependency direction (Clean Architecture)

`voyager-server -> voyager-platform -> voyager-api`, and `voyager-server -> voyager-persistence`. The composition root holds the
writer and reads its accessors at the window's end, then passes plain `long` values to the observer. `voyager-platform` and
`voyager-persistence` do not depend on each other, and the observer does not import persistence.

## Fitness

- New `@ArchTest` in `voyager-fitness`: classes in `net.elytrarace.voyager.platform.observe..` do not depend on `java.io..`,
  `java.nio..` or `java.net..`. `allowEmptyShould(false)`.
- New `@ArchTest`: only `net.elytrarace.voyager.platform.observe.WindowLog` depends on a logging API. `allowEmptyShould(false)`.
- Existing module rules cover the edges.

## Test plan (F.I.R.S.T.)

- Unit: `TickWindowTest` with a stub duration supplier: mean and maximum after 600 ticks; no summary before 600; reset after
  the summary.
- Unit: `PacketCounterTest`: count per window; reset per window.
- Unit: `WindowLogTest` with `LogCapture`: one INFO line per window with the mean, the maximum, the packet count and, when the
  writer is supplied, its four counts; no line when statistics are off.
- Unit: `RaceStatusViewTest`: no value shown before the first window.
- No `Thread.sleep`; no system clock (`System::nanoTime` is supplied only by the server); no shared static state.

## Architecture decision

No ADR. The decision is local to the observer's shape (D1 to D5) and is recorded in this design. The owner may ask for an ADR when
an exporter is considered.

## Risks

- The `nanoTime` read on each tick costs a few nanoseconds; the rule is measured in the tests' budget, not assumed.
- A window that spans a cup restart reports the restart's ticks as part of the window; the INFO line names the window's first tick
  to make that visible.
- Particle packets sent off the tick thread are missed. The spec states the scope; no such path exists in main sources today.
