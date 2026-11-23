# Proposal

**Type and scope:** `fix(race)`. Ships as one code change under this title. After `/race reload` moves a ring, the racing line
drawn to racers follows the new geometry, not the old. It does not change the line's look, its reach, its refresh rate, or the
catalogue format.

**Vertical slice:** `race` (the racing line's shape) and its presentation in `platform/render` (`GuideLineRenderer`), with the
reload path in `platform/catalog`.

## Why

The guide line is cached per map and never evicted. `GuideLineRenderer` keeps `linesByMap`, a `HashMap` keyed by
`map.key().asString()` (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/render/GuideLineRenderer.java:122`),
and fills it with `computeIfAbsent` on first use (`GuideLineRenderer.java:138`). Its own comment says a course's line "never
changes" and is "kept for the life of the server" (`GuideLineRenderer.java:118-120`).

That assumption breaks on reload. The renderer is one instance held by the singleton `CupSession`
(`voyager/platform/src/main/java/net/elytrarace/voyager/platform/cup/CupSession.java:229`), wired in
`voyager/server/src/main/java/net/elytrarace/voyager/server/inject/CupBeans.java:111`. A reload swaps the catalogue
(`voyager/platform/src/main/java/net/elytrarace/voyager/platform/catalog/CatalogReloader.java:89`), but the renderer keeps the
line built from the old rings. A racer on a reloaded course sees the line to where the rings used to be.

The layout identity already exists: `layout_version` is the first sixteen hex characters of the SHA-256 digest of the map's
canonical geometry, spawn and rings only (`docs/decisions/0037-player-stats-on-postgresql.md`; `voyager/platform/src/main/java/net/elytrarace/voyager/platform/catalog/writer/LayoutVersion.java:14`). A changed ring has a changed version.

## What Changes

- **Key by layout.** The cache key becomes the map key and its `layout_version`, so a changed layout is a different entry.
- **Evict on reload.** When a reload applies, entries whose layout version no longer matches the reloaded catalogue are removed.
  The eviction is run once per reload, not per tick.
- **Compute the identity on reload.** The layout version is read when the catalogue is applied, and the renderer receives it
  with the map; it does not hash geometry on each refresh.
- **Test.** A reload that moves one ring redraws the line through the moved ring, and a reload that changes nothing keeps the
  cached entries.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `race/racing-line-display`: a new requirement that the line follows the layout after a reload. Its existing requirements are
  unchanged.

## Trade-offs

### Pros

- Correct after a reload without a restart, which is what the owner runs `/race reload` for.
- Keyed by the existing identity (`layout_version`), so no second notion of "the same map" is introduced.

### Cons

- Eviction is one more rule to keep right: an entry missed by the eviction shows the old line until the next reload.
- The identity must be computed once at reload; if that computation is slow on a large catalogue, the reload takes longer.

## Out of scope

- The look, reach and refresh rate of the line (`scale-guide-line-packets`).
- Reloading the rotation or the cup (`catalog-reload` covers the outcome of a reload).
- A restart-free change of a map's guide line style, which is a separate question.

## Open questions

- None blocking. Confirm that a reload only changes `layout_version` when the geometry changes, not when a display name or author
  changes; the test pins that.

## Impact

- `voyager/platform`: `render/GuideLineRenderer` (key, eviction), `catalog/CatalogReloader` (eviction hook on apply),
  `cup/CupSession` (passes the reload event through, if needed).
- `voyager/race`: no change; the line's shape is a pure function of the map.
- No new configuration, no new message keys.
