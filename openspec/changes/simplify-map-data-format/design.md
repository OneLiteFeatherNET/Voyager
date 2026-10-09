# Design

## Context

Map files are read by `MapDefinitionAdapter` and `RingAdapter` in `voyager-platform`, which refuse any absent required
field by name (`JsonFields`). `MapDefinition` and `Ring` in `voyager-api` enforce the course invariants: rings indexed
`0..n-1` in list order, a non-blank world, a unit normal. The converter in `tools/map-converter` writes the same files
from the legacy format, and depends on `voyager-api` and Gson only, not on `voyager-platform`. `MapInstances` in
`voyager-platform` resolves `MapDefinition.world()` to a directory under the worlds path, and on Linux that lookup is
case-sensitive.

Research 005 (C2, C3) found that the ring index repeats the list position and that the format has no version or schema.
Section 7.4 of that report decides that the ring stays a disc, so `Ring` keeps its center, normal and radius.

## Goals

- A hand-written map that omits what the loader can derive (ring index, world) loads to the same `MapDefinition` as the
  fully written one.
- Every file valid today loads unchanged.
- A file from a future format is refused with a plain message that names the supported version.
- An editor completes and checks a map or cup file while it is written.

## Non-Goals

- Guide anchor re-encoding (research C4): the order-index stride of 100 stays.
- Polar, setup tooling, the cup `mode` default, hot reload, the validate task, scoped cup validation.
- Runtime JSON Schema validation (decision 5).
- Changing `voyager-api`. Derivation is a file-format concern; the records keep their invariants.

## Before and after

Before: the shipped map states everything, including values the loader could derive.

```json
{
  "name": "elytraraceblueandred",
  "world": "ElytraraceBlueAndRed",
  "spawn": { "x": 109.0, "y": -62.0, "z": 54.0 },
  "referenceTimeSeconds": 60.0,
  "boostConfig": { "burnDurationTicks": 30, "cooldownTicks": 40 },
  "guideLine": { "lookAheadRings": 2, "particleSpacing": 1.0, "points": [ ... ] },
  "rings": [
    { "index": 0, "center": { "x": 85.0, "y": -54.0, "z": 54.0 },
      "normal": { "x": -1.0, "y": 0.0, "z": 0.0 },
      "radius": 3.605551275463989, "points": 10, "type": "STANDARD" },
    { "index": 1, "center": { "...": "..." }, "normal": { "...": "..." },
      "radius": 3.605551275463989, "points": 10, "type": "STANDARD" }
  ]
}
```

After: a new map written by hand, the way a builder would now write it. The map is named `sky-drift`, and its world
directory is `sky-drift`. The `$schema` path is relative to the file, as the shipped files use it.

```json
{
  "$schema": "../../../../../voyager-platform/src/main/resources/schema/map.schema.json",
  "schemaVersion": 1,
  "name": "sky-drift",
  "spawn": { "x": 0.0, "y": 80.0, "z": 0.0 },
  "referenceTimeSeconds": 60.0,
  "boostConfig": { "burnDurationTicks": 30, "cooldownTicks": 40 },
  "guideLine": { "lookAheadRings": 2, "particleSpacing": 1.0, "points": [] },
  "rings": [
    { "center": { "x": 10.0, "y": 80.0, "z": 0.0 },
      "normal": { "x": 1.0, "y": 0.0, "z": 0.0 },
      "radius": 4.0, "points": 10, "type": "STANDARD" },
    { "center": { "x": 20.0, "y": 81.0, "z": 0.0 },
      "normal": { "x": 1.0, "y": 0.0, "z": 0.0 },
      "radius": 4.0, "points": 10, "type": "STANDARD" }
  ]
}
```

The loader reads the second file as ring 0 at index 0, ring 1 at index 1, and world `sky-drift`. Its `MapDefinition`
equals the one the first form would produce if it were written with those values.

## Decisions

### 1. Ring index: derive from position, check when declared

`RingAdapter` gets a method `Ring read(JsonElement element, int position)`. It reads `index` if present and refuses a
value other than `position` with a `JsonParseException` that names the position and the declared index. Without
`index`, the ring takes `position`. `MapDefinitionAdapter` calls it in a loop over the `rings` array, so the position is
always known. The Gson `Ring.class` registration in `CatalogDirectory` is removed, because a ring is never a top-level
file and nothing else asks Gson for one.

The derivation sits in the adapter, not in `MapDefinition`. The record cannot tell whether the file declared an index,
and its invariant (`index == position`) stays as it is, as the second line of defence against programmatic callers.

Alternatives: keep `index` required (no change, but the redundancy research C2 names stays); derive it in
`MapDefinition`'s compact constructor (rejected: the record would have to accept `Ring` values without indexes, which
weakens a type that is otherwise always valid).

### 2. World default: the map name, exactly

When `world` is absent, the world is `name` unchanged. Three options were weighed.

| Option | For | Against |
|---|---|---|
| A. Exact name (chosen) | Predictable: the directory name is what the file says. A mismatch fails at boot with the resolved name in the message. | Cannot derive the shipped world, because `ElytraraceBlueAndRed` is not recoverable from `elytraraceblueandred`. |
| B. Case-insensitive lookup of the name under the worlds path | Would let the shipped file drop `world`. | Makes the directory lookup depend on the filesystem and on lookup code. Two folders that differ only by case become ambiguous, and the file no longer says which world it means. |
| C. Rename the shipped world folder to lowercase | Makes the shipped file derivable. | The world directory lives outside the repository, under `VOYAGER_WORLDS_PATH` (9429 chunks, copied by operators, used by the boot smoke test). Renaming it breaks every existing data directory, which is a data migration, not a format change, and the brief forbids silent data changes. |

Decision: A for the derivation, and the shipped file keeps `"world": "ElytraraceBlueAndRed"` explicitly. The file, the
world directory and the boot log stay exactly as they are. The adapter sets the resolved world on the `MapDefinition`,
so `Opened world '...'` and the `UnknownWorldException` already name the value the server uses. New maps should name
their world directory exactly as the map, which is the convention the default encodes.

### 3. Schema version: one constant, read by both adapters

A package-private `adapter/SchemaVersion` holds `CURRENT = 1` and reads the field:

- absent: 1;
- not a whole number, or below 1, or not a number: refused by `JsonFields.integer`-style messages naming the field;
- above `CURRENT`: refused with "declares schemaVersion N; this server reads up to CURRENT".

`MapDefinitionAdapter` and `CupDefinitionAdapter` both call it, so the rule and the message cannot diverge. The
converter cannot import the constant (it does not depend on `voyager-platform`), so it writes the literal `1` and a
converter test asserts it; `CommittedMapDataTest` asserts the shipped files declare 1.

A future bump follows one procedure: add the migration to the adapter for the old version, add
`map.v2.schema.json`, then raise `CURRENT`. The old schema file stays for files that still declare 1.

### 4. Schema location and references

The schemas live in `voyager-platform/src/main/resources/schema/`. That module owns the format, so the file ships with
the code that enforces it, and tests load it from the classpath. Shipped files reference it with a relative `$schema`.
From `voyager-server/src/main/resources/maps/` the path is
`../../../../../voyager-platform/src/main/resources/schema/map.schema.json`, and the same depth applies to `cups/`.
Relative paths resolve in VS Code and IntelliJ for files inside the repository, with no network access.

The `$id` is `https://raw.githubusercontent.com/OneLiteFeatherNET/Voyager/main/voyager-platform/src/main/resources/schema/map.schema.json`
(and `cup.schema.json`). It is an identifier, not a fetch target: every reference inside the schema is local (`$defs`).

Alternatives: `docs/reference/schema/` (readable, but tests would read outside their module, and it duplicates the
resource path the docs already link). A hosted URL (needs a published site that does not exist).

### 5. Validation: editor-side at runtime, schema test in test scope only

No runtime validation against the JSON Schema. Reasons:

- `ApiPurityTest.onlyPlatformDependsOnGson` and the comment in `voyager-platform/build.gradle.kts` make the catalogue the
  only JSON parser in the rebuild. A schema validator on the runtime classpath would add a second parser to
  `voyager-server`.
- The adapters already refuse each violation the schema describes, with the file named. A second runtime validator would
  have to agree with the adapters forever, and disagreement is a bug nobody sees until a file is refused by one and
  loaded by the other.
- The schema's purpose is feedback while editing, which costs nothing at runtime.

A test-scope check is kept: committed files validate against their schema, and a few deliberately invalid fixtures are
rejected. It needs one JSON Schema 2020-12 validator in `voyager-platform` `testImplementation`, pinned to an exact
version at least two weeks old when the change is applied. This is a new dependency, so the owner approves the choice
before it is added (task 4.2). If the owner declines it, task 4.1 is dropped; `$schema` resolution is still checked in
`CommittedMapDataTest`, and the schema documents are then covered by review only.

Schema strictness: the schema sets `additionalProperties: false` on the map, ring, guide-point and cup objects, and
allows `$schema` and `notes` at the top level. An unknown key is almost always a misspelling, and the loader cannot tell
a misspelling from a comment, so the schema is the stricter reader on purpose. The loader keeps ignoring unknown keys, so
an old file with an extra key still loads.

### 6. Converter output

`CatalogWriter` writes `schemaVersion: 1` first in each map and cup object, keeps writing `index` and `world`, and does
not write `$schema`, because the output directory is a command-line argument and a relative path to the schema cannot be
known. `$schema` is added to committed files by hand, and `CommittedMapDataTest` fails if it is missing or does not resolve.
Regenerating the shipped data therefore changes nothing except removing `$schema`, which a reviewer restores.

### 7. Shipped data

`elytraraceblueandred.json` and `test_cup.json` gain `$schema` and `"schemaVersion": 1`. No value changes. The ring
indexes and the explicit world stay, because they are what the converter writes and because the world value must not
change (decision 2).

## Architecture

- Dependency direction: `voyager-api` (unchanged) ← `voyager-platform` catalog adapters (derivation, version rule) ←
  `voyager-server` (shipped data, `CommittedMapDataTest`). `tools/map-converter` depends on `voyager-api` and Gson, and its
  writer's output is read by the adapters.
- New types: `adapter/SchemaVersion` (package-private, `voyager-platform`). No new module, no new package, so
  `package-info.java` and `@NotNullByDefault` coverage are unaffected.
- Fitness: no new rule. `onlyPlatformDependsOnGson` and the `ApiPurityTest` rules are unchanged; the validator is
  test-scoped inside `voyager-platform`, so it is on no main classpath. `FitnessCoverageTest` is unaffected.

## Risks

- **A typo in a derived name.** `"name": "sky-drfit"` with no `world` now loads the world `sky-drfit`. The boot check
  refuses it with that name in the message, so the failure is loud, but it is one step later than an explicit `world`
  would have been. Mitigation: the message names the resolved value; the docs say the world is the name.
- **Schema drift from the adapters.** The schema is a second description of the format. Mitigation: the committed-file
  and fixture test (4.1) catches most drift; the required-field lists are reviewed against the adapters in 4.2.
- **Strictness mismatch.** The editor rejects keys the loader ignores. Mitigation: documented in decision 5.
- **Future version bumps** need the procedure in decision 3, or old files are misread. Mitigation: the refusal rule makes
  a missed bump fail loudly on the first newer file.

## Open questions for the owner

1. Approve one test-scope JSON Schema 2020-12 validator in `voyager-platform` (task 4.2 names the candidate and the pin
   rule). Without it, the schema test is dropped.
2. Confirm the convention for new maps: the world directory is named exactly as the map `name`.
