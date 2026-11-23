# Design

## Context

- `GuideLineRenderer.render` (`platform/render/GuideLineRenderer.java`, around lines 138-152) takes the stretch for the racer's
  passed count, keeps the points within `REACH_BLOCKS = 160` (`:69`), builds one `ParticlePacket` per point with `packetAt`
  (`:204-206`) and sends the list with `racer.sendPackets`.
- The packet's contents depend on the point, the particle and the flags (`OVERRIDE_LIMITER`, `LONG_DISTANCE`), not on the racer.
  Only the selection depends on the racer.
- The line is cached per map (`linesByMap`, `:122`), so the stretch is a pure function of the line.

## Decisions

### 1. Build once per map, select per racer

A `PrebuiltLine` value holds, for each point of a map's line, its `ParticlePacket` (built once with the map's particle and flags)
and its `Vec3`. Each refresh filters by the racer's distance and sends the selected packets. The packets are immutable Minestom
records, so sharing them across racers is safe; the test asserts that no packet is rebuilt per refresh.

The packet type is `platform`'s, so `PrebuiltLine` lives in `platform/render`. `race` still owns the stretch and gains no import.

### 2. Level of detail is a selection, not a second packet type

Points more than 60 blocks from the racer are selected on alternate refreshes (`gameTick` parity). This is one more filter on the
same selection, so `refreshesOn` stays the only time check. The 60-block threshold is a constant in `GuideLineRenderer`, tested
at its boundary.

### 3. Hide is a per-racer set, not a map setting

A session-scoped set of racers with the line hidden. Hiding removes the racer from the render path before the selection, so a
hidden racer costs nothing. The set is cleared when the racer leaves the cup session.

### 4. Dependency direction and fitness

`race` (line shape) <- `platform/render` (prebuilt packets, selection, hide set) <- `platform/cup` (`CupSession` owns the hide set
and passes it to the renderer). No new cross-slice import; `voyager-fitness` covers `race` for Minestom imports already. No new rule.

## Risks

- The level of detail changes what a racer sees far away. The owner's decision on the "no gap" exception is required before Green.
- Packets built once are reused across ticks; a particle change in the catalogue must rebuild them, which ties this change to the
  layout identity used by `refresh-guide-line-on-reload`. The two changes should land in that order.
