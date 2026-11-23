# Tasks

Commit type and scope: `fix(race)` for the code and its tests, `docs` for `docs/reference/`. Each behaviour gets a failing test (Red)
before its production code (Green). Every test follows F.I.R.S.T.: a `MapDefinition` fixture per test, reload directories under
`@TempDir`, no clock, no shared static state.

## 1. Red: the line after a reload

- [ ] 1.1 Red: `GuideLineRendererCacheTest` "a map with a changed layout version gets a new line, not the cached one" (`fix(race)`)
- [ ] 1.2 Red: `GuideLineRendererCacheTest` "a map whose layout version is unchanged keeps its cached line" (`fix(race)`)
- [ ] 1.3 Red: `GuideLineRendererCacheTest` "applying a reload evicts the entries whose layout version is no longer in the catalogue"
  (`fix(race)`)
- [ ] 1.4 Red: `GuideLineRendererCacheTest` "a refused reload evicts nothing" (`fix(race)`)
- [ ] 1.5 Red: `ReloadRedrawsLineIntegrationTest` "a reload that moves one ring makes the drawn line pass through the new centre"
  (`fix(race)`)
- [ ] 1.6 Red: `ReloadRedrawsLineIntegrationTest` "a reload that changes only the display name keeps the line" (`fix(race)`)
- [ ] 1.7 Red: run 1.1 to 1.6 and record each failure message in the code pull request (`fix(race)`)

## 2. Green: the line after a reload

- [ ] 2.1 Green: `LineKey(mapKey, layoutVersion)` record in `platform/render`, with `package-info` @NotNullByDefault (`fix(race)`)
- [ ] 2.2 Green: `GuideLineRenderer` keys `linesByMap` by `LineKey` and gains an `evictExcept(Collection<LineKey>)` method (`fix(race)`)
- [ ] 2.3 Green: `CatalogReloader` hands the reloaded maps to the renderer through `CupSession` on `Applied` only (`fix(race)`)
- [ ] 2.4 Green: the layout version is read once when the catalogue is built, and carried with the map (`fix(race)`)
- [ ] 2.5 Refactor: confirm 1.1 to 1.6 pass, and that the existing `GuideLineRendererTest` passes without edits (`fix(race)`)

## 3. Verify

- [ ] 3.1 Run `./gradlew :voyager:platform:test :voyager:fitness:test` and confirm green (`fix(race)`)

## 4. Docs

- [ ] 4.1 Update `docs/reference/` to say that a reload redraws the racing line from the reloaded layout (`docs`)

## 5. Pull request

- [ ] 5.1 Open the code pull request titled `fix(race): redraw the racing line from the layout after a reload` from the
  implementation branch, with no file under `openspec/` (`fix(race)`)
