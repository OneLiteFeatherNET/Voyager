# Design

## Context

- `ConfigCheck.rotationBoot` (`voyager/server/.../config/ConfigCheck.java:302-309`) builds a `MapInstances` and passes
  `worlds::checkWorld` to `RotationValidation.check`.
- `RotationValidation` checks `WorldFolders.check(worldsRoot, map.world())` first, and only for `PRESENT` worlds runs the deep
  check (`voyager/platform/.../rotation/RotationValidation.java:118-130`).
- `MapInstances.checkWorld` calls `readEveryChunk`, which loads each chunk of each region file (`MapInstances.java:165-182`) and
  then reads the world's health (`:206-209`).
- The config check property is `voyager.config.check` (`ConfigCheck.java:55`), read by `VoyagerServer` (`:167`).

## Decisions

### 1. Two modes of the same check, chosen by the caller

`RotationValidation.check` takes a `WorldCheckMode` value: `HEADERS` (folder and region header presence, no chunk load) or `FULL`
(the current deep read). `ConfigCheck.rotationBoot` passes `HEADERS` on a normal boot; the config check passes `FULL`. The mode is
a parameter, so no code path is duplicated.

`WorldCheckMode` is a sealed interface in `voyager/platform` with two `final` records, as the ManisGame rule for a controlled
extension point (design rule 1). It is not in `voyager/api`, because it describes the platform check, not a race rule.

### 2. The cache is a question for design, not a default

The `HEADERS` check does not catch a corrupt chunk. A cache keyed by region file size and modification time would catch a changed
file, not a corrupt file that was never changed. Decision: no cache in the first code change. The measurement in the spec decides
whether a cache is needed; if it is, a new change proposes it.

### 3. Dependency direction and fitness

`server` (composition root: chooses the mode) -> `platform/rotation` (validation, takes the mode) -> `platform/instance`
(`MapInstances`, the two reads). `WorldCheckMode` lives in `platform/rotation`, so `server` imports a platform type it already
imports. No new boundary; the existing `voyager-fitness` rules hold. No new rule is needed.

### 4. Tests

- `RotationValidationModeTest` (unit): with `HEADERS`, the deep check is never called (the test passes a checker that fails the
  test if called). With `FULL`, it is called once per world.
- `ConfigCheckBootTest` (unit, server): a normal boot with a rotation uses `HEADERS`; the config check uses `FULL`.
- The existing boot-refusal test for a corrupt chunk moves to the config-check test.

## Risks

- A corrupt chunk now surfaces mid-rotation. The owner must accept it before Green (see proposal open questions).
- The requirement that a corrupt chunk is reported when its cup is armed needs a full read at arming. That read is the cost this
  change moves off boot, so it must run off the racers' path, before the cup's lobby opens. Where it runs is an open design point,
  and it is measured with the boot numbers before it is accepted.
- The docs discrepancy (`docs/reference/config-check.md:6-9`) is fixed in the docs task, so the documented boot behaviour matches the
  code.
