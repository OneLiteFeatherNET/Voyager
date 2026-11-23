# Proposal

**Type and scope:** `perf(server)`. Ships as one code change under this title. A server that starts with a cup rotation checks the
worlds of every cup in the rotation by reading every chunk, which is the slow part of the boot between two cups. This change makes
that boot check cheap and keeps the full read for the explicit config check. It does not change the catalogue checks, the refusal
format, the rotation order, or which cup is armed.

**Vertical slice:** `server` (boot and the config check) and `platform/rotation` (the rotation validation it calls).

## Why

With `VOYAGER_CUP_ROTATION` set, boot calls `ConfigCheck.rotationBoot` (`voyager/server/src/main/java/net/elytrarace/voyager/server/VoyagerServer.java:265-266`).
That call passes `worlds::checkWorld` (`voyager/server/src/main/java/net/elytrarace/voyager/server/config/ConfigCheck.java:302-309`),
and `RotationValidation` runs the deep check for every world it finds present (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/rotation/RotationValidation.java:118-130`).
`checkWorld` calls `readEveryChunk`, which loads every chunk of every region file with `loadChunk(...).join()`
(`voyager/platform/src/main/java/net/elytrarace/voyager/platform/instance/MapInstances.java`, around lines 165-182 and 206-209).

This deep read is not behind the explicit `voyager.config.check` switch. It runs on every boot of a rotation server, so the time
between two cups includes a full read of every rotation world. ADR-0047 says the next cup's wait "is not measured here"
(`docs/decisions/0047-restart-service-after-cup.md:76`), so the cost is known to exist but not to be large.

The reference documentation says something different: `docs/reference/config-check.md:6-9` says a normal boot checks the worlds
of the played cup only. The rotation boot checks every cup in the rotation. That discrepancy is part of this change's note.

## What Changes

- **Cheap boot check.** A normal boot with a rotation checks folder and region presence and the region headers only. It does
  not load chunks.
- **Full read stays for the config check.** `-Dvoyager.config.check` (the validate-and-exit run, `VoyagerServer.java:167`) keeps
  the full chunk read for every map.
- **Measure first.** Before any change, the cold boot of a three-cup rotation is measured and recorded, so the gain is known.
- **Optional cache.** If the measurement shows the cheap check misses real corruption that matters, a cache keyed by region file
  size and modification time is considered in design, not assumed.
- **Docs.** `docs/reference/config-check.md` states what a rotation boot checks, which fixes the discrepancy above.
- **BREAKING (boot behaviour):** a corrupt chunk in a non-played rotation cup no longer stops the boot; it surfaces when that cup
  is armed, mid-rotation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `server/config-check-mode`: "Normal boot refuses with the same report" gains the cheap rotation check, and the deep read is named
  as the config check's own job.

## Trade-offs

### Pros

- Faster cup restarts, since the boot no longer reads every chunk of every rotation world.
- Lower boot heap, because chunks are not loaded just to be checked.
- The full read is still one command away, so nothing is lost for the operator who wants it.

### Cons

- A corrupt chunk surfaces mid-rotation, when the cup is armed, not at boot. That is a worse place for the owner to learn of it.
- A cache, if chosen, must be invalidated correctly when a world changes; a stale cache says a broken world is fine.
- Boot-refusal tests change: the test that expects a corrupt chunk to refuse boot must move to the config check.

## Out of scope

- The catalogue-wide checks (JSON, schema, references), which stay as they are.
- The rotation order and the arming of the next cup (`cup-rotation`, `restart-after-cup`).
- The cold-start time of the Minestom server itself.

## Open questions

- Owner: accept a corrupt chunk surfacing mid-rotation, or keep the deep read at boot and only make it cheaper with a cache?
- Owner: should the config check also run at every restart, as a cheap pre-cup step?

## Impact

- `voyager/server`: `VoyagerServer` (the rotation boot path), `config/ConfigCheck` (the cheap versus deep choice), the existing
  boot-refusal tests.
- `voyager/platform`: `rotation/RotationValidation` (the check mode passed in), `instance/MapInstances` (a cheap header read beside
  `readEveryChunk`).
- `docs/reference/config-check.md` and `docs/decisions/0047-restart-service-after-cup.md` (the measured cold start, as a note).
