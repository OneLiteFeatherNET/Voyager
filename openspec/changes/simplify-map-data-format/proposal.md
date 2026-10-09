# Proposal

**Conventional Commits:** `feat(platform)`. Squash-merge PR title: `feat(platform): let map files leave out what the loader can derive`.

Slice: `catalog` (`voyager-platform`, the adapters that read map and cup files), with the converter in `tools/map-converter` writing the same files and the shipped data in `voyager-server/src/main/resources`. Research 005 roadmap items Q2 (derive ring index and default world) and Q3 (schema version and JSON Schema).

Out of scope: guide anchor re-encoding (research C4, the stride of 100 stays), Polar (C6), setup tooling and the `voyager-setup` slice, the cup `mode` default (research D3), hot reload (Q7), the validate task (Q6), scoped cup validation (Q4), and any change to the tree being replaced. No decision in the greenfield design spec changes: the ring stays a disc (research 7.4), and D10 is not touched.

## Why

A hand-written map file repeats what the loader can compute. Every ring states its own `index`, which must equal its position in the list, and every map states a `world` folder name that in practice is the map name. Each repetition is a place to be wrong, and the loader already refuses the wrong ones, so the repetition buys nothing. Map and cup files also have no version field and no schema, so a builder who types a wrong key gets a loader message after a restart rather than an editor warning while typing. Research 005 (C2, C3) asks for both fixes before `voyager-setup` starts writing these files.

## What Changes

- **Ring `index` becomes optional.** When a ring omits `index`, the loader assigns its zero-based position in the `rings` array. When a ring declares `index`, it must equal that position; a mismatch refuses the file and names it. Every file that loads today loads to an identical `MapDefinition`.
- **Map `world` becomes optional.** When a map omits `world`, the world is the map `name`, byte for byte (see design, decision 2). An explicit `world`, including the shipped `"ElytraraceBlueAndRed"`, is kept as written. A present but blank `world` is still refused.
- **`schemaVersion` on map and cup files.** Absent means version 1. A whole number of 1 or more is accepted up to the highest version this server reads (1). A zero, negative, fractional or non-numeric value, or a version above 1, refuses that file with a message that names the file, the declared version and the supported maximum. Nothing is loaded partially.
- **JSON Schema for both file kinds.** `voyager-platform/src/main/resources/schema/map.schema.json` and `cup.schema.json` (draft 2020-12). The shipped map and cup files reference them through `$schema` so editors offer completion and report errors while the file is edited. The loader does not validate against the schema at runtime (design, decision 5); the adapters remain the only runtime authority.
- **Converter output.** `tools/map-converter` writes `schemaVersion: 1` and keeps writing `index` and `world` explicitly, so converted files stay self-describing and regenerating the shipped data changes nothing but the new `schemaVersion` line.
- **Shipped data.** `maps/elytraraceblueandred.json` and `cups/test_cup.json` gain `$schema` and `schemaVersion: 1`. No value changes. Their explicit `world` and ring `index` values stay.
- **Documentation.** A new reference page `docs/reference/map-and-cup-files.md` (no map format reference exists today) lists every field, its default, its refusal message and the schema it is checked against.
- **BREAKING:** none. Every change is additive for valid files. The only new refusals are a `schemaVersion` above 1 and a ring `index` that contradicts its position, and no shipped or converted file triggers either.

## Capabilities

### New Capabilities

- `catalog/map-file-format`: which map fields are required, which are derived (ring index, world) and how derivation is checked.
- `catalog/file-versioning`: the `schemaVersion` rule for map and cup files, including the refusal of future versions.
- `catalog/file-schema`: the JSON Schema documents published for map and cup files, how shipped files reference them, and what the schema is and is not authoritative for.

### Modified Capabilities

- None. `openspec/specs/` holds no accepted capability yet; `catalog/catalog-loading` in change `unify-catalog-loading` is a new capability there, and this change does not alter its requirements.

## Impact

- `voyager-platform`: `adapter/RingAdapter` (position-based index, read by `MapDefinitionAdapter` instead of through the Gson registry), `adapter/MapDefinitionAdapter` (world default, `schemaVersion`), `adapter/CupDefinitionAdapter` (`schemaVersion`), a new package-private `adapter/SchemaVersion`, `CatalogDirectory` (drops the `Ring.class` registration, which nothing else uses). `voyager-platform/src/main/resources/schema/` is new. Test-scope only: one JSON Schema validator in `voyager-platform` `testImplementation`, pinned exactly (design, decision 5).
- `voyager-api`: no change. `MapDefinition`, `Ring` and their invariants stay as they are; derivation is a file-format concern and lives in the adapter.
- `tools/map-converter`: `CatalogWriter` adds `schemaVersion`; its tests assert it.
- `voyager-server`: shipped `maps/` and `cups/` files gain `$schema` and `schemaVersion`; `CommittedMapDataTest` asserts the new keys and that each `$schema` resolves.
- `voyager-fitness`: no new rule. `onlyPlatformDependsOnGson` is unaffected, and the validator is test-scoped inside `voyager-platform`, so it is not on any main classpath.
- Coordination: `unify-catalog-loading` also edits the catalogue load path. This change does not touch `CatalogDirectory`'s collection logic, so the two merge independently; whichever lands second rebases its refusal messages onto the other's wording.
