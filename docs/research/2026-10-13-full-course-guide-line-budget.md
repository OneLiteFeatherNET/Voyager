# The Whole Racing Line as a Packet Budget: Measuring the Cost of a Full-Course Guide

**Authors:** Voyager Development Team | **Date:** 2026-10-13 | **Status:** Draft, awaiting the owner's playtest

## Abstract

Voyager's racing line showed a racer a window of two rings, about 90 blocks of a 1,588-block course. The project owner
asked for the whole course, as the old game drew it. A particle is one packet to one client, so the whole line is a
packet cost, and the owner set a budget of 30,000 packets per second in total and 2,000 for any one racer at sixteen
racers on the 35-ring course. This note records what the shipped course costs under the chosen design, measured from the
renderer's own output. The whole line at spacing 2.0 and the racer's distance cut-off of 160 blocks keep the sixteen-racer
field at about 15,083 packets per second in total, and no racer above about 1,700. Without the cut-off the worst racer at
the first ring would exceed the per-racer limit. One input is unverified: the lifetime of the END_ROD particle, which sets
the redraw interval. The redraw is taken at 6 ticks, not the 10 proposed, for that reason.

## 1. Introduction

The renderer draws a racer's stretch of the racing line by sending one `ParticlePacket` per point, to that racer only,
each time the line is redrawn. The cost of the design is therefore the number of points in the stretch, divided by the
refresh interval, summed over the racers on the course. Two levers set the cost: the spacing between points, which is a
map value, and the refresh interval, which belongs to the particle. A third lever, the distance cut-off, is a server
constant that bounds the cost by the local density of the course rather than by its length.

## 2. Method

The measurements come from the change's own test code and a temporary probe, which was removed before the commit.

1. **Points.** `RacingLine.of` samples the shipped map at each spacing (1.0, 2.0 and 3.0) with the map's rings and guide
   points unchanged. The point counts are the whole-line counts, from the first ring to the last.
2. **Sixteen racers.** Racers are placed at rings 0, 2, 4, ..., 30, which spreads the field along the course. Each is sent
   its FULL stretch, from its next ring to the last.
3. **Reach.** The share of each stretch within 160 blocks of its racer is counted with the renderer's own predicate.
4. **Rate.** The renderer's output over three seconds of ticks, divided by three, is the packet rate. The window is a
   multiple of every refresh interval in use (4, 6 and 10), so the figure is the steady rate and not a rounding of the
   window. A first attempt at a twenty-tick window counted four redraws in a span that holds 3.33 and overstated the
   worst racer by 20 per cent; the test was corrected.

## 3. Findings

**Points.** The whole line holds 1,669 points at spacing 1.0, 836 at 2.0 and 556 at 3.0. The spacing is the density,
so the cost of the whole line is proportional to one over the spacing.

**Reach.** At spacing 2.0 the sixteen FULL stretches hold 7,084 points without a cut-off. Of those, 4,525 (63.9 per cent)
are within 160 blocks of their racer.

**Rates at spacing 2.0 with a 6-tick END_ROD refresh.**

| Measure | Without reach | With reach | Limit |
|---|---|---|---|
| Total, sixteen racers | 23,613 packets/s | 15,083 packets/s | 30,000 |
| Worst racer | 2,787 packets/s (racer at the first ring) | 1,700 packets/s (racer at ring 4) | 2,000 |

The total is inside its limit even without the reach, because the racers are spread. The per-racer limit is not. A racer
at the first ring is sent the whole line, and at 6 ticks that is more than 2,000 packets a second. The cut-off is
therefore the mechanism that keeps every racer inside the per-racer limit, and the budget test enforces it.

**Alternatives.** At the owner's proposed 10-tick END_ROD refresh, with the reach, the same field would be sent 9,050
packets a second in total and at most 1,020 to one racer. At 1.0 spacing, which the course had before, the worst
racer would be sent about 3,400 packets a second even with the reach (1,019 points a redraw at 6 ticks). Spacing 2.0 is therefore the
smallest spacing that fits the owner's limits at the 6-tick refresh.

## 4. The Unverified Input

END_ROD's shortest particle life sets the redraw interval. A redraw interval longer than half that life leaves a gap
between two redraws in the line. The owner asked for the life to be verified and the interval chosen to keep the line
continuous. The life could not be read from any source reachable from this change: the decompiled 26.2 client source was
not available, and a search for the vanilla `EndRodParticle` lifetime found no published source. The safe interval is six
ticks, which is at most half of any life of twelve ticks or more. If the life is later read as at least twenty ticks, the
owner's ten-tick interval is valid and lowers the END_ROD packet rate by 40 per cent.

The other client-side inputs are not measured: the live-particle cap of the vanilla client, and the wire size of one
`ParticlePacket`. The packet size is an estimate of about 50 bytes, which at the measured rate gives roughly 0.75 MB per
second for the field in total, before compression.

## 5. Visual Density

A 2-block dotted END_ROD line may read as dots rather than a line. This is a judgement about the screen and not a
measurement. It is left to the owner's playtest of the shipped course, which replaces the render spike and the Pulse
review that the change first named. The ADR stays Proposed until that playtest is done.

## 6. Limits

- The sixteen racers are placed at evenly spaced rings. A field bunched at the start would send more, because its racers
  would share the same points inside one reach. The budget describes a spread field, not every possible placement.
- The measurements are for the shipped course. A longer course at spacing 2.0 grows linearly with its length (a
  3,000-block course at spacing 2.0 would be about 80,000 packets a second for sixteen racers at a 6-tick refresh without a
  cut-off), so the budget is checked again for
  each new course.
- The test runs in a Minestom test environment without a client. It counts what the server sends, which is the quantity
  the budget limits, and not what the client draws.

## 7. Conclusion

The whole racing line at spacing 2.0 fits the owner's budget on the shipped course, with the reach in place, at a 6-tick
END_ROD redraw. The per-racer limit depends on the reach, and the total depends on the spread of the field. The one input
that could change the numbers is END_ROD's shortest life. A verified life of at least twenty ticks would allow the
owner's ten-tick interval. Whether the line reads as a line is for the owner to judge on the shipped course.
