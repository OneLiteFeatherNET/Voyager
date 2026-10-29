# Map and cup files

This reference lists every field of the two data files the server reads at boot, what each field defaults to, what the loader refuses, and which test holds each rule.

The files live in the data directory the server is started with: one definition per `.json` file under `maps/` and `cups/`. Subdirectories are not read. The file name is free; the definition's `key` is its identity, and a cup's `mapNames` refer to map keys, not file names.

A map or cup is identified by an Adventure key in the `elytrarace` namespace, written in full as `elytrarace:<value>`, for example `elytrarace:blue_and_red`. The value is lower case letters, digits, underscores and hyphens, at most 32 characters, and starts with a letter or digit. Version 2 files state the key in a `key` field. Version 1 files state a flat `name`, which is read as the key `elytrarace:<name>`. The decision is recorded in [ADR-0040](../decisions/0040-catalogue-keys.md).

A file the loader refuses stops the server at boot. The refusal names the file, followed by the cause:

```text
/data/maps/sky-drift.json is not a valid definition: map 'elytrarace:sky-drift' declares schemaVersion 4, but this server reads up to schemaVersion 3
```

## Map file

| Field | Type | Required | Default or derivation | Refused when |
|---|---|---|---|---|
| `$schema` | string | No | None. The loader ignores it. | Never. The editor schema checks it as a relative path. |
| `schemaVersion` | integer | No | `1` when absent. | Not a whole number of 1 or more, or above `3`. The refusal names the declared version and the highest supported version. |
| `key` | string | Yes in version 2 | None. | Missing in version 2, not in the `elytrarace` namespace, or a value outside the rule above. The refusal names the field and the file. A version 2 file that also states `name` is refused. |
| `name` | string | Yes in version 1 | None. | Missing in version 1, a value outside the rule above (for example capitals or spaces), or present in version 2. A version 1 file that states `key` is refused. |
| `displayName` | string | No | The name a player reads, in MiniMessage with colour, rainbow, decoration and `reset` tags, for example `<gold>Blue</gold> & <red>Red</red>`. | A tag outside that set, or more than 40 visible characters once the tags are removed. The refusal names the field, the file and the tag. |
| `authors` | array of objects | No | None. | An author without a canonical UUID, a name outside `[A-Za-z0-9_]{1,16}`, more than eight authors, or one UUID stated twice. The refusal names the file and the field. |
| `world` | string | No | The value of the map's key, exactly as written, including case. | Present and blank. At boot, a world with no region data under the worlds path stops the server and the message names the resolved world. |
| `spawn` | vector | Yes | None. | Missing, or a component is missing. |
| `referenceTimeSeconds` | number | Yes | None. A fraction is kept. | Missing, not finite, or zero or negative. |
| `boostConfig` | object | Yes | None. | Missing, or either field below is missing. |
| `boostConfig.burnDurationTicks` | integer | Yes | None. | Zero or negative. |
| `boostConfig.cooldownTicks` | integer | Yes | None. | Not longer than `burnDurationTicks`. |
| `boostConfig.rocketsPerRacer` | integer | No | `64`, the item stack limit. | Less than 1, or more than 64. |
| `boostConfig.maxSpeedBlocksPerTick` | number | No | No cap. | Not finite, or zero or negative. The cap limits only the velocity a `BOOST` or `SLOW` ring writes. |
| `ringEffects` | object | No | Both factors at their defaults. | Not an object. A factor is refused as described below. |
| `ringEffects.boostMultiplier` | number | No | `1.5`. | Negative or not finite. |
| `ringEffects.slowMultiplier` | number | No | `0.5`. | Negative or not finite. |

The boost fields and the ring factors are described in [Map boost and ring-effect tuning](map-boost-tuning.md).
| `guideLine` | object | Yes | None. | Missing, or one of the three fields below is missing. |
| `guideLine.lookAheadRings` | integer | Yes | None. | Less than 1. |
| `guideLine.particleSpacing` | number | Yes | None. | Less than 0.25. |
| `guideLine.mode` | string | No | `FULL`. | Any value other than `FULL` or `WINDOW`. |
| `guideLine.particle` | string | No | `END_ROD`. | Any value other than `END_ROD` or `DUST`. |
| `guideLine.dustColor` | string | No | `#FFC800`. | Not `#` followed by six hexadecimal digits, or given when `particle` is not `DUST`. |
| `guideLine.points` | array | Yes | May be empty. | Missing. Each point is refused as described below. |

`mode` decides how much of the line a racer sees. `FULL` draws the line from the ring the racer is heading for to the last
ring, and the part already flown is not drawn. `WINDOW` draws `lookAheadRings` rings past that ring and nothing more, which
is the look a course had before. `lookAheadRings` is required in both modes but has an effect only in `WINDOW`.

`particle` is the particle the line is drawn with. `dustColor` is the colour of a `DUST` line and has no effect, and is
refused, with `END_ROD`. The refresh is set by the particle and not by the file: the line is redrawn every 6 ticks for
`END_ROD` and every 4 ticks for `DUST`. A racer is sent only the points within 160 blocks of them.

A file that names none of the optional fields loads with the same rings, boost tuning, guide points, look-ahead and spacing as
before. Only the drawing changes: the line is drawn as a full line in `END_ROD`, where it was drawn as a window in amber dust.
The budget is 30,000 packets a second in total and 2,000 for any one racer at sixteen racers, and `particleSpacing` is
the value that keeps the shipped course inside it: 2.0 for `elytraraceblueandred`.

`elytrarace:palm_beach` names `particle` `DUST` and `dustColor` `#7A1FB8` (deep violet), so its line stays visible against its
bright sand and sky. Every other shipped map names `END_ROD` in its file. The palm_beach line at `particleSpacing` 1.0 is the one
accepted exception to the 2,000 per-racer limit: at sixteen racers its worst racer is sent 3,690 packets a second, and the total
is 29,800. The owner accepted this on 2026-10-29 and kept the spacing. `PalmBeachBudgetExceptionTest` pins the ceilings at 4,000
per racer and 30,000 in total.
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
| `radius` | number | Yes | None. The radius of a circle, or the semi-major radius of an oval. | Missing, or outside `[0.5, 32]` blocks. |
| `radiusMinor` | number | No | None. Stated only for an oval, with `majorAxis`. | Stated without `majorAxis`, outside `[0.5, 32]`, or greater than `radius`. A file below schema version 3 that states it is refused. |
| `majorAxis` | vector | No | None. Stated only for an oval, with `radiusMinor`. The unit vector of the major axis, in the ring's plane. | Stated without `radiusMinor`, not of unit length, or not perpendicular to `normal`. A file below schema version 3 that states it is refused. |
| `points` | integer | Yes | None. | Missing, not a whole number, or outside `[0, 100]`. |
| `type` | string | Yes | None. | Missing, or not one of `STANDARD`, `BOOST`, `SLOW`, `CHECKPOINT`. A misspelt type is refused rather than read as `STANDARD`. |

A ring of type `CHECKPOINT` is the reset target. When a racer is reset, the racer returns to the last `CHECKPOINT` ring they passed, and the rings passed after it are removed from their run (see [course reset](course-reset.md)). A map with no `CHECKPOINT` ring resets every racer to the map's spawn. A `CHECKPOINT` ring applies no velocity effect; only `BOOST` and `SLOW` do.

### Vectors

A vector is an object with `x`, `y` and `z`, each a number. All three are required.

### Derivation

Two fields may be left out because the loader can compute them. Leaving one out gives the same map as writing the value the loader would compute.

| Field | Rule | Test that holds the rule |
|---|---|---|
| Ring `index` | Left out, the index is the position. Declared, it must equal the position. | `MapDefinitionAdapterTest.readsARingWithoutIndexAsItsPositionInTheRingsArray`, `MapDefinitionAdapterTest.refusesARingWhoseDeclaredIndexIsNotItsPosition`, `MapDefinitionAdapterTest.readsTheSameMapWithOrWithoutItsRingIndexes`, `RingAdapterTest.takesTheIndexFromThePositionWhenTheFileLeavesItOut` |
| `world` | Left out, the world is the value of the map's key, byte for byte (`blue_and_red` for `elytrarace:blue_and_red`; the flat `name` in version 1). Declared, it is used as written. | `MapDefinitionAdapterTest.readsAMapWithoutWorldAsItsNameExactlyAsWritten`, `MapDefinitionAdapterTest.aVersionTwoMapWithoutWorldTakesTheValueOfItsKeyAsItsWorld`, `MapDefinitionAdapterTest.keepsAnExplicitWorldUnchangedWhenItDiffersFromTheName`, `MapDefinitionAdapterTest.refusesAPresentButBlankWorldNamingTheField` |
| `schemaVersion` | Left out, the version is `1`. Declared, it must be a whole number from 1 to the supported maximum, which is `2`. | `MapDefinitionAdapterTest.readsAMapWithoutSchemaVersionAsVersionOne`, `MapDefinitionAdapterTest.refusesSchemaVersionAboveTheSupportedMaximumNamingBothNumbers`, `CatalogValidationTest.reportsASchemaVersionAboveTheSupportedMaximumAsAProblemNamingTheFile` |
| `key` and `name` | A version 1 file states `name`, which becomes the key `elytrarace:<name>`. A version 2 file states `key`. | `MapDefinitionAdapterTest.aVersionOneFlatNameWithCapitalsIsRefusedNamingTheName`, `MapDefinitionAdapterTest.aVersionTwoMapWithAKeyInAnotherNamespaceIsRefusedNamingTheKey`, `MapDefinitionAdapterTest.aVersionTwoMapThatStatesANameIsRefusedNamingTheName`, `CatalogKeysTest` |
| `displayName` | Left out, the display name is the key's value in title case, `Blue And Red` for `elytrarace:blue_and_red`. | `MapDefinitionTest.aMapWithoutADisplayNameShowsItsKeyValueInTitleCase` |

The world default is case-sensitive on Linux, so a directory named `ElytraraceBlueAndRed` needs an explicit `"world": "ElytraraceBlueAndRed"` in its map file. The shipped maps do this.

## Cup file

| Field | Type | Required | Default or derivation | Refused when |
|---|---|---|---|---|
| `$schema` | string | No | None. The loader ignores it. | Never. |
| `schemaVersion` | integer | No | `1` when absent. | The same rule as for maps. |
| `key` | string | Yes in version 2 | None. | The same rule as for maps. |
| `name` | string | Yes in version 1 | None. | The same rule as for maps. |
| `displayName` | string | No | The cup's name a player reads, in the same MiniMessage form as a map's. | A tag outside the set, or more than 40 visible characters. The refusal names the field and the file. |
| `authors` | array of objects | No | None. | The same rule as for maps. |
| `mode` | string | Yes | None. | Missing, or not `RACE` or `PRACTICE`. |
| `mapNames` | array of strings | Yes | None. The order is the rotation. A flat id is read as its key. | Missing, not an array, empty, or an entry that is not a string. |
| `notes` | array of strings | No | None. The loader ignores it. | Never. |

At boot, every entry of `mapNames` must match the `key` of a loaded map. An unknown entry stops the server.

## Operator inputs

The cup named by the `VOYAGER_CUP` setting, and the map named by `/leaderboard`, accept either form: the key `elytrarace:alpha_cup`, or the flat id `alpha_cup`, which is read as `elytrarace:alpha_cup`. The flat form is kept until the owner removes it. Operator output prints the key, so an operator can copy it into a command.

## Display names

`key` is the identifier: the key of the catalogue, the name an operator types and the value in the file. `displayName` is the name a player reads, on the boss bar, the map banner, the lobby sidebar and hologram, and the results.

- A file that leaves `displayName` out, or gives it blank, shows the key's value in title case: `elytrarace:dragon_lobby` reads "Dragon Lobby".
- A name that sets no colour takes one of six bright hues, chosen by its key, so the same map is the same colour on every server. Any colour is allowed, red included.
- The text is shown as written and is never translated, so a German and an English player read the same name. MiniMessage tags in it are shown as plain characters.
- A name longer than 40 characters is refused at boot, rather than cut short.
- Operator output (command replies, the validation report and log lines) keeps the key, so an operator can find the file.

The shipped catalogue names `alpha_cup` "Alpha Cup", `legacy_cup` "Testing Cup", and the maps `blue_and_red` "Blue & Red", `palm_beach` "PalmBeach", `dragon_lobby` "DragonLobby" and `memoria` "Memoria". The tests are `ShippedCatalogueTest.theShippedCatalogueNamesItsCupsAndMapsForPlayers` and `CatalogLoaderTest` for the loader rules.

## Authors

A map or cup may state who built it in `authors`: an array of objects, each with a `uuid` and a `name`.

- `uuid` is a UUID in the canonical 8-4-4-4-12 form, in either letter case. It is stored in lower case and compared by value.
- `name` is a Minecraft username, `[A-Za-z0-9_]{1,16}`, kept as written. The loader does not look it up, so a rename in Mojang's service does not change the file.
- At most eight authors, and no UUID twice.

The shipped maps credit TheMeinerLP on `blue_and_red` and Seelenretterin on `palm_beach`, `dragon_lobby` and `memoria`. The shipped cups have no authors. Authors are data: they are not translated.

## Schema version

`schemaVersion` tells the loader which rules a file was written under. The server reads versions 1, 2 and 3.

- A file without the field is version 1.
- Version 2 states `key` (see above). Version 1 states `name`.
- Version 3 states an oval ring with `radiusMinor` and `majorAxis` (see the ring fields above). A ring with no oval field is a circle, and a version 3 file of circles loads like a version 2 file. An oval in a file of version 2 or lower is refused, and the message names the file, the ring's position and the field, so an older server never plays an oval as a circle. The decision is recorded in [ADR-0043](../decisions/0043-oval-rings.md).
- A file that declares a version above the highest version the server reads is refused. The message names the file, the declared version and the highest supported version, so an older server reports a newer file plainly rather than misreading it.
- A version that is not a whole number of 1 or more is refused, whether it is zero, fractional or written as text.

The files the converter in `tools/map-converter` writes still declare `"schemaVersion": 1`, and version 1 files still load.

## JSON Schema

The JSON Schema documents for both file kinds are in the platform module:

- `voyager/platform/src/main/resources/schema/map.schema.json` and `cup.schema.json`, for version 3;
- `voyager/platform/src/main/resources/schema/map-v2.schema.json` and `cup-v2.schema.json`, for version 2;
- `voyager/platform/src/main/resources/schema/map-v1.schema.json` and `cup-v1.schema.json`, for version 1.

Both use draft 2020-12. Each file in the committed data declares its schema with a relative `$schema` path, so an editor that supports `$schema` offers completion and reports errors while the file is edited. For a file in the repository the path resolves from the file's own location. A file copied into a data directory outside the repository does not resolve it, so the editor must be given the schema another way.

The schema is stricter than the loader in one respect. It sets `additionalProperties: false` on the map, ring, guide point and cup objects, so a misspelt key is reported in the editor. The loader ignores unknown keys so that an old file with an extra key still loads.

The schema does not replace the loader. The server does not validate files against it at runtime, and a file that the schema rejects but the loader accepts still loads.

`FileSchemaTest` checks the schema in two ways: the committed files validate against it, and a set of inline fixtures with one deliberate fault each is rejected with the expected field named. It also checks that every committed `$schema` resolves to the schema of its kind.
