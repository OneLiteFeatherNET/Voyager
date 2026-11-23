# Design

## Dependency direction

Content only. The map and cup files are read by the catalogue loader (`catalog/catalog-loading`) and validated by the boot-time
checks (`cup-validation`). No Java changes are proposed, so the Clean Architecture direction is unchanged:

```
server (boots the shipped data directory)
  -> platform (catalogue reader, validation check)
       -> race and api (the ring, boost and medal types the files are read into)
```

Nothing new depends on anything. Every field the content uses exists in `api` and in the map format.

## New and changed types

| Type | Module | Kind | Role |
|---|---|---|---|
| Map files (`maps/*.json`) | `voyager-server` resources | data | Gain `rocketsPerRacer`, `maxSpeedBlocksPerTick` (existing fields), varied ring `type` and `points`, and calibrated `referenceTimeSeconds`. |
| Cup files (`cups/*.json`) | `voyager-server` resources | data | `dev_cup.json` added once its maps have worlds. `solo_practice.json` comes from `start-solo-practice`. |
| Calibration record | in each map's `notes` | data | Sample count, date, method (top-quartile median). Plain text in the existing `notes` array, so the map format gains no field. |
| `MedalBrackets`, `BoostConfig`, `RingEffectTuning` | `voyager-api` | existing | Read, not changed. |

## Decisions

- **No new map-format field for calibration.** A structured `calibration` object would be cleaner, but it would change the map schema
  (`catalog/map-file-format`) and every loader. The notes array already carries the provisional marks, so the record goes there
  until a second change proves the need for a field.
- **Speed cap from geometry.** `maxSpeedBlocksPerTick` is set from path length divided by reference time, so the number is traceable
  to the map and not to taste. `RingEffectTuning.DEFAULT` multiplies by 1.5 (`RingEffectTuning.java:19`); the cap bounds the result.
- **Bonus ring replaces a line ring.** A bonus ring off the main line replaces one line ring, so the alpha map keeps 35 rings and
  the ring-count scenarios of `catalog/shipped-catalogue` stay true. Adding a 36th ring would change those scenarios.
- **The development cup waits for worlds.** `dev_cup` names three maps that have no world or spawn yet. Its `SHALL` takes effect only
  when they do, so the requirement is written with that condition.

## Dependencies on other changes

- `start-solo-practice` changes the requirement "Exactly two cups ship, alpha_cup and legacy_cup" to three cups. This change's
  MODIFIED block lists four cups, so it archives after `start-solo-practice`. If the solo practice change is dropped, the block must
  list `dev_cup` alone as the third cup.
- `end-stuck-map-waits` uses the reference time as the basis of the grace window. Its window is only as fair as the calibration
  here, so the owner should accept the calibration before the grace formula is fixed.

## Fitness

- `ConfigurationBoundaryTest` and the map and cup loader tests: no code path changes; the catalogue loads the new files at boot.
- `ApiPurityTest`: no `api` type changes.
- `MessageBundleParityTest`: not affected, since no user-facing string is added by this change. If the dev cup gets a display name,
  its key is added to every bundle.
- The existing shipped-catalogue checks (`cup-validation`, "The shipped catalogue loads without any problem") are the enforcement for
  the data. No new ArchUnit rule is needed.

## Risks

- Changing a reference time moves medal tiers on existing standings. Stored records are not rewritten, so a racer's past medal and
  the new tier can disagree. The owner must accept this per map before the calibrated value is committed.
- A bonus ring or a SLOW ring changes the lines a racer uses. The playtest must confirm the chicane is not a trap; the notes must say
  which rings are untested.
- A speed cap that is too low makes BOOST feel like nothing; too high leaves the economy unbounded. The first value is a starting point
  for the playtest, not a conclusion.
