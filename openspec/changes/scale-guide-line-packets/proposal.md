# Proposal

**Type and scope:** `perf(race)`. Ships as one code change under this title. It cuts the particle packets the racing line sends
each second, so that the shipped budget holds on the busiest course, and it gives a racer a way to hide the line. It does not
change which rings count, the scores, the physics, or the line's colour and particle.

**Vertical slice:** `race` (the racing line's shape and stretch) and its presentation in `platform/render`.

## Why

Each refresh sends one `ParticlePacket` per point in reach, per racer. `GuideLineRenderer.render` builds the packets inside
the racer loop (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/render/GuideLineRenderer.java:145-152`) and
`packetAt` allocates a new packet for each point (`GuideLineRenderer.java:204-206`). The reach is 160 blocks
(`GuideLineRenderer.java:69`).

The owner's budget is stated in `openspec/specs/race/racing-line-display/spec.md` ("Budget is stated for the shipped course"):
30,000 packets a second in total and 2,000 for any one racer, at sixteen racers (`docs/reference/map-and-cup-files.md:56-57`).
The shipped course keeps within it. The palm_beach course does not. Its research measured 29,800 packets a second in total and
3,690 for the worst racer with DUST, against the 2,000 limit, and the owner accepted that overage on 2026-10-29
(`docs/research/2026-10-29-palm-beach-guide-line-colour.md:62-73`).

The particle is forced to draw even for a client that has reduced particles: `OVERRIDE_LIMITER = true`
(`GuideLineRenderer.java:104`), and the reasoning is documented at `GuideLineRenderer.java:96-103`. A racer cannot turn the line
off, and `VOYAGER_MAX_PLAYERS` is only a soft limit (`docs/reference/configuration.md:24`), so a fuller lobby is
not capped by the server.

## What Changes

- **Level of detail.** Points more than 60 blocks from the racer are sent at half the refresh rate, on alternate refreshes.
  Points within 60 blocks keep the full rate. **MODIFIED:** the "redrawn often enough to show no gap" requirement, which is Must,
  gains this exception for distant points, and the owner must accept it.
- **Build once per map.** The packets for each point of a map are built once when the line is built, and each racer's refresh
  selects the subset in reach. Building the same packet per racer per refresh stops.
- **Hide the line.** A racer can hide their own line with a toggle, default on. The toggle changes presentation only, and is not
  stored across sessions in this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `race/racing-line-display`: "The line is redrawn often enough to show no gap" gains the distant-point exception, and a new
  requirement gives the per-racer hide toggle.

## Trade-offs

### Pros

- Holds the owner's budget on the busiest course without moving a ring or changing the colour.
- Fewer allocations on the tick path, since the packets are built once per map.
- A racer who finds the line distracting can hide it, which the current design does not allow.

### Cons

- The level of detail changes what a racer sees far down the course; distant points are seen at half their rate.
- Hiding the line removes the course reading that the line gives; a racer who hides it has to read the rings alone.
- The toggle is session-only, so a racer who hides it must hide it again after a restart.

## Out of scope

- The reach, the colour, the particle and the spacing of the line.
- A hard cap at sixteen racers (see Open questions).
- Persisting the toggle in `player-stats` or a settings store.

## Open questions

- Owner: enforce a hard cap for palm_beach at sixteen racers, or keep the accepted overage with the level of detail?
- Owner: is a half-rate far line acceptable under the "no gap" requirement, or should the level of detail start further out (for
  example 100 blocks)?
- Owner: should the toggle be session-only, or stored with the racer's profile?

## Impact

- `voyager/platform`: `render/GuideLineRenderer` (built-once packets per map, level of detail, the hide check).
- `voyager/platform`: the racer's toggle, in the command or item layer, chosen in design.
- `voyager/race`: no change; the stretch is still a pure function of the line.
- `docs/reference/map-and-cup-files.md` and `docs/reference/configuration.md` for the level of detail and the budget.
