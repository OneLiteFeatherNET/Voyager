# Design

## Context

The draft holds its guide line as loaded (`MapDraft.guideLine`, `voyager/api/.../mapsetup/MapDraft.java:27, 33`). The game
reads a guide point by `orderIndex` on the shared order axis: ring `i` sits at slot `i * 100`, and a point on another slot
bends the line between ring `floorDiv(orderIndex, 100)` and the next ring (`GuidePoint.java:15-22, 56-57`). `MapDefinition`
refuses a point whose `afterRing` is below 0 or above `rings.size() - 2`, and a point on a ring's own slot
(`GuidePoint.java:42-43`). `GuideLine` refuses a look-ahead below 1 and a particle spacing below `MINIMUM_PARTICLE_SPACING`
(0.25, `GuideLine.java:107, 111, 114`).

## Goals / Non-Goals

Goals:

- A builder adds, lists and removes guide points without typing an index.
- Every guide change is saved, undoable and checked by the game's own rules before it is shown.
- The position derivation is a pure function, tested without Minestom.

Non-goals:

- Curved lines, spline control, or per-point particles.
- Any change to the game's reading.

## Decisions

### Deriving the order index from the eye

The builder's eye position `p` is projected onto the segment from the nearest ring centre to its successor. Let ring `i` be the
ring whose centre is nearest to `p` among rings `0..n-2`, and ring `i+1` its successor. The projection parameter `t` is clamped
to `[0, 1]`, and the order index is `i * 100 + clamp(round(t * 98) + 1, 1, 99)`. The slot offset is therefore in `1..99`,
never a ring's own slot, and a point at either ring lands one step inside the gap.

Alternative considered: take the nearest ring and always bend after it. Rejected: a point beside ring 5 but near the middle of
the gap to ring 6 would then be placed at the wrong end of the gap, and the builder could not tell why the line bends where it
does.

If the map has fewer than two rings, `add` is refused: a guide point needs a gap to sit in.

### Refusal before save

A guide change builds the candidate draft, runs `MapDefinition`'s checks on it (or the same bracket function, extracted), and
refuses with the bracket message when the check fails. The save is not attempted. This keeps the draft invariant that
`MapDefinition` enforces at load, so a saved file always loads.

### Markers

A marker is a particle line or a sign-free marker drawn at each guide point, spawned like ring previews
(`ring-preview`): on open, removed on close, not saved. The marker class lives in `adapter` next to the ring preview.

### Undo and the ring order

Each guide command goes through `MapSession.commit`. Ring order is not changed by guide commands, so the remap of
`edit-ring-order-and-position` is not needed here. When that change lands, it remaps the guide points, and this change's
derived index stays valid because it is derived from the current order.

## Dependency direction and modules

- `GuidePlacement` (the derivation and the refusal) lives in `voyager/setup/.../mapsetup`, depending on `voyager/api` only.
- The markers and commands live in `voyager/setup/.../adapter`.
- No change to `api`: `GuidePoint`, `GuideLine` and `MapDefinition` are reused as they are.

## Architecture rules

- Existing `setupDomainOnlyDependsOnApiAndJdk` covers `GuidePlacement`.
- New boundary: only `DraftEditor` builds a `GuideLine` for a setup draft. Covered by a new ArchUnit rule in `voyager/fitness`,
  `allowEmptyShould(false)`.

## Open questions

- Bends. A point bends the line at one place; a builder who wants a smooth arc needs several points. Recommendation: keep one
  point per bend for this change and decide spline authoring in its own proposal.
- Marker form. A particle line or a block marker. Recommendation: particle, to match the game's line; it needs a visual
  check in the setup world before the task is ticked.
- Is the marker rendered for every racer's view or only the builder's? Recommendation: builder only, since the setup server
  has one builder per map.
