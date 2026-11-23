# Design

## Context

- `GuideLineRenderer.render` looks up `linesByMap` by `map.key().asString()` and computes on a miss (`GuideLineRenderer.java:122,138`).
  The class comment states the cache lives for the server's life (`GuideLineRenderer.java:118-120`).
- The renderer is a field of `CupSession` (`platform/cup/CupSession.java:229`), which is a singleton bean built in
  `server/inject/CupBeans.java:111`.
- `CatalogReloader.reload` reads the directory and returns `ReloadOutcome.Applied` with the new catalogue, or `Rejected`
  (`platform/catalog/CatalogReloader.java:89`). A refused reload changes nothing.
- `LayoutVersion` computes the 16-character SHA-256 identity over spawn and rings (`platform/catalog/writer/LayoutVersion.java:14`).

## Decisions

### 1. The cache key is the map key and the layout identity

`linesByMap` is keyed by `(mapKey, layoutVersion)`. A lookup with a layout version that is not cached computes the line. A stale
entry for the same map key is not returned, because its layout version no longer matches the one the map now carries.

### 2. Eviction runs once, on apply

When `ReloadOutcome.Applied` is produced, the renderer receives the reloaded maps and drops every entry whose
`(mapKey, layoutVersion)` is not in the new catalogue. A refused reload does nothing. The eviction is a single pass over at most a
few dozen entries, so it is not a performance concern; the per-tick path only reads.

The layout version is read from the map that the catalogue carries. It is computed once when the catalogue is built, not per tick
and not per refresh.

### 3. Dependency direction

`race` (the line's shape, a pure function of the map) <- `platform/render` (the renderer, keyed by the layout identity) <-
`platform/catalog` (the reload hands the new maps to the renderer through `CupSession`). `race` gains no import. No new type is
needed beyond a small key record in `platform/render`, `LineKey(String mapKey, String layoutVersion)`.

### 4. Fitness

- `voyager-fitness` already keeps `race` free of platform imports. No new rule.
- The renderer is a platform type and is exercised by unit tests that build a `MapDefinition` fixture per test, with no server.

## Risks

- A missed eviction path shows the old line until the next reload. The integration test drives a reload through the real
  `CatalogReloader` and asserts the line, so the path is covered.
- Two maps may share a key during a rename; the key includes the layout version, which is what the racer sees, so the risk is low.
