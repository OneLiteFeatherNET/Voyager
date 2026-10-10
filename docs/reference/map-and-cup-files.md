# Map and cup files

This reference lists every field of the two data files the server reads at boot, what each field defaults to, what the loader refuses, and which test holds each rule.

The files live in the data directory the server is started with: one definition per `.json` file under `maps/` and `cups/`. Subdirectories are not read. The file name is free; the definition's `name` is its identity, and a cup's `mapNames` refer to map names, not file names.

A file the loader refuses stops the server at boot. The refusal names the file, followed by the cause:

```text
/data/maps/sky-drift.json is not a valid definition: map 'sky-drift' declares schemaVersion 2, but this server reads up to schemaVersion 1
```

## Map file

| Field | Type | Required | Default or derivation | Refused when |
|---|---|---|---|---|
| `$schema` | string | No | None. The loader ignores it. | Never. The editor schema checks it as a relative path. |
| `schemaVersion` | integer | No | `1` when absent. | Not a whole number of 1 or more, or above `1`. The refusal names the declared version and the highest supported version. |
| `name` | string | Yes | None. | Missing, or blank. |
| `world` | string | No | The map's `name`, exactly as written, including case. | Present and blank. At boot, a world with no region data under the worlds path stops the server and the message names the resolved world. |
| `spawn` | vector | Yes | None. | Missing, or a component is missing. |
| `referenceTimeSeconds` | number | Yes | None. A fraction is kept. | Missing, not finite, or zero or negative. |
| `boostConfig` | object | Yes | None. | Missing, or either field below is missing. |
| `boostConfig.burnDurationTicks` | integer | Yes | None. | Zero or negative. |
| `boostConfig.cooldownTicks` | integer | Yes | None. | Not longer than `burnDurationTicks`. |
| `guideLine` | object | Yes | None. | Missing, or one of the three fields below is missing. |
| `guideLine.lookAheadRings` | integer | Yes | None. | Less than 1. |
| `guideLine.particleSpacing` | number | Yes | None. | Less than 0.25. |
| `guideLine.points` | array | Yes | May be empty. | Missing. Each point is refused as described below. |
| `rings` | array | Yes | None. | Missing, or empty. |
| `notes` | array of strings | No | None. The loader ignores it. | Never. |

### Guide points

Each entry of `guideLine.points` has:

| Field | Type | Required | Refused when |
|---|---|---|---|
| `orderIndex` | integer | Yes | Is a multiple of 100, which is a ring's own slot. The point must also fall between two rings of the course. |
| `position` | vector | Yes | A component is missing. |

Points are sorted by `orderIndex` when the course is read, so the file may list them in any order.

### Rings

Rings are read in array order. The position of a ring in `rings` is its index: the first ring is index 0.

| Field | Type | Required | Default or derivation | Refused when |
|---|---|---|---|---|
| `index` | integer | No | The ring's zero-based position in `rings`. | Present and not equal to the position. The refusal names the position and the declared index. |
| `center` | vector | Yes | None. | Missing. |
| `normal` | vector | Yes | None. Stored as written. | Missing, or not of unit length. |
| `radius` | number | Yes | None. | Missing, or not positive. |
| `points` | integer | Yes | None. | Missing, not a whole number, or negative. |
| `type` | string | Yes | None. | Missing, or not one of `STANDARD`, `BOOST`, `SLOW`, `CHECKPOINT`. A misspelt type is refused rather than read as `STANDARD`. |

### Vectors

A vector is an object with `x`, `y` and `z`, each a number. All three are required.

### Derivation

Two fields may be left out because the loader can compute them. Leaving one out gives the same map as writing the value the loader would compute.

| Field | Rule | Test that holds the rule |
|---|---|---|
| Ring `index` | Left out, the index is the position. Declared, it must equal the position. | `MapDefinitionAdapterTest.readsARingWithoutIndexAsItsPositionInTheRingsArray`, `MapDefinitionAdapterTest.refusesARingWhoseDeclaredIndexIsNotItsPosition`, `MapDefinitionAdapterTest.readsTheSameMapWithOrWithoutItsRingIndexes`, `RingAdapterTest.takesTheIndexFromThePositionWhenTheFileLeavesItOut` |
| `world` | Left out, the world is the map's `name`, byte for byte. Declared, it is used as written. | `MapDefinitionAdapterTest.readsAMapWithoutWorldAsItsNameExactlyAsWritten`, `MapDefinitionAdapterTest.keepsAnExplicitWorldUnchangedWhenItDiffersFromTheName`, `MapDefinitionAdapterTest.refusesAPresentButBlankWorldNamingTheField` |
| `schemaVersion` | Left out, the version is `1`. Declared, it must be a whole number from 1 to the supported maximum. | `MapDefinitionAdapterTest.readsAMapWithoutSchemaVersionAsVersionOne`, `MapDefinitionAdapterTest.refusesSchemaVersionAboveTheSupportedMaximumNamingBothNumbers` |

The world default is case-sensitive on Linux, so a directory named `ElytraraceBlueAndRed` needs an explicit `"world": "ElytraraceBlueAndRed"` in its map file. The committed course does this.

## Cup file

| Field | Type | Required | Default or derivation | Refused when |
|---|---|---|---|---|
| `$schema` | string | No | None. The loader ignores it. | Never. |
| `schemaVersion` | integer | No | `1` when absent. | The same rule as for maps. |
| `name` | string | Yes | None. | Missing, or blank. |
| `mode` | string | Yes | None. | Missing, or not `RACE` or `PRACTICE`. |
| `mapNames` | array of strings | Yes | None. The order is the rotation. | Missing, not an array, empty, or an entry that is not a string. |
| `notes` | array of strings | No | None. The loader ignores it. | Never. |

At boot, every name in `mapNames` must match the `name` of a loaded map. An unknown name stops the server.

## Schema version

`schemaVersion` tells the loader which rules a file was written under. The server reads version 1 only.

- A file without the field is version 1.
- A file that declares a version above the highest version the server reads is refused. The message names the file, the declared version and the highest supported version, so an older server reports a newer file plainly rather than misreading it.
- A version that is not a whole number of 1 or more is refused, whether it is zero, fractional or written as text.

The files the converter writes declare `"schemaVersion": 1`.

## JSON Schema

The JSON Schema documents for both file kinds are in the platform module:

- `voyager/platform/src/main/resources/schema/map.schema.json`
- `voyager/platform/src/main/resources/schema/cup.schema.json`

Both use draft 2020-12. Each file in the committed data declares its schema with a relative `$schema` path, so an editor that supports `$schema` offers completion and reports errors while the file is edited. For a file in the repository the path resolves from the file's own location. A file copied into a data directory outside the repository does not resolve it, so the editor must be given the schema another way.

The schema is stricter than the loader in one respect. It sets `additionalProperties: false` on the map, ring, guide point and cup objects, so a misspelt key is reported in the editor. The loader ignores unknown keys so that an old file with an extra key still loads.

The schema does not replace the loader. The server does not validate files against it at runtime, and a file that the schema rejects but the loader accepts still loads.

`FileSchemaTest` checks the schema in two ways: the committed files validate against it, and a set of inline fixtures with one deliberate fault each is rejected with the expected field named. It also checks that every committed `$schema` resolves to the schema of its kind.
