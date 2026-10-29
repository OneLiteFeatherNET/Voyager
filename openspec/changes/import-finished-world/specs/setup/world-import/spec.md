# Spec Delta

## Purpose

A builder makes a map from a finished world with `/map import`. The command copies the overworld region data of a world from an
import folder into the map's world folder, reads the spawn the world states, reports what the copy holds, and leaves a draft that
the builder completes with rings. A failed import leaves neither a world folder nor a draft behind.

## ADDED Requirements

### Requirement: An import copies a world's region data into a new map
**Priority:** MoSCoW Must

WHEN a builder runs `/map import <world-folder> <id>` with a valid world folder name and a valid id, and neither `drafts/<id>.json`,
`maps/<id>.json` nor `<worldsPath>/<id>` exists, THE SYSTEM SHALL copy the overworld region files of that world into
`<worldsPath>/<id>/region/`, SHALL create `drafts/<id>.json` with world `<id>`, and SHALL reply with the report and the command
`/map open <id>`.

#### Scenario: Import of a world with region files at its root
- **WHEN** a builder runs `/map import elytrarace-cube skycube` and the folder `elytrarace-cube` under the import root holds `region/` with two region files
- **THEN** `<worldsPath>/skycube/region/` holds the two region files, `drafts/skycube.json` exists with world `skycube`, and the reply names `/map open skycube`

#### Scenario: Id already in use
- **WHEN** a builder runs `/map import elytrarace-cube skycube` and a draft named `skycube` exists
- **THEN** the command is refused with a message naming the id, and no file or folder changes

### Requirement: The import root is configured, and the server does not create it
**Priority:** MoSCoW Must

THE SYSTEM SHALL read the import root from `VOYAGER_IMPORT_PATH` through the configuration loader, with the default `imports` under
the working directory. THE SYSTEM SHALL NOT create the import root. IF the import root is missing when `/map import` runs, THEN THE
SYSTEM SHALL refuse the import and SHALL name the absolute path it expected. The server SHALL start while the import root is missing.

#### Scenario: Server starts without the import folder
- **WHEN** the setup server starts and the import root does not exist
- **THEN** the server starts, the import root is not created, and no error is logged for the missing folder

#### Scenario: Import with a missing root
- **WHEN** a builder runs `/map import elytrarace-cube skycube` and the import root does not exist
- **THEN** the command is refused, and the message names the absolute path of the expected import root

#### Scenario: Import root set by the environment
- **WHEN** `VOYAGER_IMPORT_PATH` is set to `/srv/worlds`
- **THEN** the import reads world folders under `/srv/worlds`

### Requirement: Only the accepted world layouts are imported
**Priority:** MoSCoW Must

THE SYSTEM SHALL accept a world folder whose overworld region files are in `region/` at the world root, or in
`dimensions/minecraft/overworld/region/`. IF neither folder holds a region file, IF both do, OR IF the folder uses any other layout,
THEN THE SYSTEM SHALL refuse the import. The message SHALL name the accepted layouts, and when both layouts hold region files it SHALL
name both folders.

#### Scenario: Dimension layout accepted
- **WHEN** a world folder holds region files in `dimensions/minecraft/overworld/region/` and no others
- **THEN** the import copies those region files

#### Scenario: Other layout refused
- **WHEN** a world folder holds only `.mcr` files, or a Sponge `.schem` file, or a zip archive
- **THEN** the import is refused, and the message names `region/` and `dimensions/minecraft/overworld/region/` as the accepted layouts

#### Scenario: Two layouts at once refused
- **WHEN** a world folder holds region files both in `region/` and in `dimensions/minecraft/overworld/region/`
- **THEN** the import is refused, and the message names both folders, and nothing is copied

### Requirement: Only the overworld is copied, and the reply says what was ignored
**Priority:** MoSCoW Must

THE SYSTEM SHALL copy only the overworld region files. WHEN the world folder holds nether or end data, THE SYSTEM SHALL NOT copy it and
SHALL say in the reply that other dimensions were ignored.

#### Scenario: Nether and end ignored
- **WHEN** a world folder holds overworld region files and also `DIM-1/region/` and `DIM1/region/`
- **THEN** only the overworld region files are copied, and the reply says that the nether and the end were ignored

### Requirement: Folder names and links are checked before any copy
**Priority:** MoSCoW Must

THE SYSTEM SHALL accept a world folder name only when it matches `[A-Za-z0-9_.-]{1,64}`, is not `.` or `..`, and resolves to a folder under
the import root. IF a name fails the rule, or the resolved folder lies outside the import root, THEN THE SYSTEM SHALL refuse the import
before reading any file. IF a region file to be copied is a symbolic link, THEN THE SYSTEM SHALL refuse the import, and it SHALL copy
nothing.

#### Scenario: Traversal in the folder name
- **WHEN** a builder runs `/map import ../secret skycube`
- **THEN** the command is refused with a message naming the folder rule, and no file is read or copied

#### Scenario: Symbolic link region file
- **WHEN** a region file of the world folder is a symbolic link to a file outside the import root
- **THEN** the import is refused, and `<worldsPath>/skycube` is not created

### Requirement: Only region files are copied
**Priority:** MoSCoW Must

THE SYSTEM SHALL copy only the files named `r.X.Z.mca` from the chosen region folder. THE SYSTEM SHALL NOT copy `level.dat`, `uid.dat`,
`entities/`, `poi/` or `data/`.

#### Scenario: Entities and data are not copied
- **WHEN** a world folder holds `level.dat`, `uid.dat`, `entities/`, `poi/`, `data/` and region files
- **THEN** the copy holds the region files only

### Requirement: The import is limited in region bytes and region count
**Priority:** MoSCoW Must

THE SYSTEM SHALL refuse an import whose region files total more than `VOYAGER_IMPORT_MAX_BYTES` bytes, with a default of 268435456, or
whose region files number more than `VOYAGER_IMPORT_MAX_REGIONS`, with a default of 1024. THE SYSTEM SHALL check both limits before it
copies any file.

#### Scenario: Region total above the limit
- **WHEN** the region files of a world total more than the configured byte limit
- **THEN** the import is refused, the message names the limit, and `<worldsPath>/.<id>.importing` does not exist

### Requirement: The spawn is read from the world's overworld data
**Priority:** MoSCoW Must

WHEN the world's `level.dat` states a spawn, THE SYSTEM SHALL set the draft's spawn to the centre of the block that the world states,
that is `(x + 0.5, y, z + 0.5)`. THE SYSTEM SHALL take the spawn from the overworld `Data.spawn` when the world has one, and otherwise
from the legacy `Data.SpawnX`, `Data.SpawnY` and `Data.SpawnZ`.

#### Scenario: Legacy spawn keys
- **WHEN** the world's `level.dat` states `SpawnX` 0, `SpawnY` 65 and `SpawnZ` 0 and no `Data.spawn`
- **THEN** the draft's spawn is (0.5, 65, 0.5)

#### Scenario: Modern spawn
- **WHEN** the world's `level.dat` states `Data.spawn` in `minecraft:overworld` at (120, 70, -40)
- **THEN** the draft's spawn is (120.5, 70, -39.5)

### Requirement: The reply shows the spawn and its source, and hints at a default spawn
**Priority:** MoSCoW Must

THE SYSTEM SHALL show in the reply the block coordinates of the spawn that it set, and the source of that spawn, modern or legacy keys.
WHEN the block that the world states has `x` and `z` both 0, THE SYSTEM SHALL add to the reply a hint to check the spawn with `/map spawn`.
WHEN the world states no spawn, THE SYSTEM SHALL leave the spawn unset and SHALL say in the reply that no spawn was found.

#### Scenario: Modern spawn is shown without a hint
- **WHEN** the world's `level.dat` states `Data.spawn` in `minecraft:overworld` at (120, 70, -40)
- **THEN** the reply shows the block (120, 70, -40) and the modern source, and holds no `/map spawn` hint

#### Scenario: Default-looking legacy spawn gets the hint
- **WHEN** the world's `level.dat` states `SpawnX` 0, `SpawnY` 65 and `SpawnZ` 0 and no `Data.spawn`
- **THEN** the reply shows the block (0, 65, 0) and the legacy source, and adds the hint to check the spawn with `/map spawn`

#### Scenario: No spawn stated
- **WHEN** the world's `level.dat` states neither spawn form
- **THEN** the draft has no spawn, and the reply says that no spawn was found

### Requirement: The chunk census reports full, partial and unreadable chunks
**Priority:** MoSCoW Should

THE SYSTEM SHALL count, over the copied region files and one region at a time, the full chunks (status `full` or `minecraft:full`),
the partial chunks (any other status), the chunks without status and the unreadable chunks, and SHALL show the counts in the reply.
WHEN the census finds partial chunks, THE SYSTEM SHALL show their count and percentage, and SHALL add a warning. THE SYSTEM SHALL NOT
refuse an import for partial chunks.

#### Scenario: Census of mixed chunks
- **WHEN** the copied region holds a namespaced full chunk, a bare `full` chunk, a chunk with status `features`, a chunk with no status and an unreadable record
- **THEN** the reply counts 2 full, 1 partial, 1 without status and 1 unreadable, shows the partial share as 20 percent of the 5 chunk records counted (unreadable records included), holds the partial warning, and the import is not refused

#### Scenario: Census of a complete world
- **WHEN** every chunk of the copy is full
- **THEN** the reply has no partial warning and the import is not refused

### Requirement: A world newer than this server is refused
**Priority:** MoSCoW Must

IF the `level.dat` or any chunk of the copy states a `DataVersion` above 4903, THEN THE SYSTEM SHALL refuse the import, SHALL remove the copied folder,
and SHALL name the version found and the highest version supported. A world at `DataVersion` 4438 SHALL NOT be refused for its version.

#### Scenario: Newer data version refused
- **WHEN** a chunk of the copy states `DataVersion` 4904
- **THEN** the import is refused, `<worldsPath>/.<id>.importing` and `<worldsPath>/<id>` do not exist, and the message names 4904 and 4903

#### Scenario: Older data version accepted
- **WHEN** the copy's `level.dat` states `DataVersion` 4438 and no chunk is above 4903
- **THEN** the import is not refused for its version

### Requirement: The copy and the census run off the tick, and a failure leaves nothing behind
**Priority:** MoSCoW Must

THE SYSTEM SHALL run the copy, the census and the draft save off the server tick, and SHALL tell the builder when the import has finished or
failed. IF a step after the copy fails, THEN THE SYSTEM SHALL remove the copied world folder, so that afterwards either both the world folder
and the draft exist, or neither does.

#### Scenario: Save fails after the copy
- **WHEN** the draft save throws after the world folder has been renamed into place
- **THEN** `<worldsPath>/<id>` does not exist, `drafts/<id>.json` does not exist, and the builder is told that the import failed

### Requirement: One import per map id at a time
**Priority:** MoSCoW Must

WHILE an import of an id is running, THE SYSTEM SHALL refuse a second import or a `/map new` of that id, with a message naming the id. THE SYSTEM
SHALL release the id when the import ends, whether it succeeded or failed.

#### Scenario: Second import of a running id
- **WHEN** a builder starts an import of `skycube` and another builder starts an import of `skycube` before the first ends
- **THEN** the second is refused with a message naming `skycube`, and the first continues

#### Scenario: Id free after a failure
- **WHEN** an import of `skycube` fails and a builder starts another import of `skycube`
- **THEN** the second import is not refused for the id

### Requirement: The import needs its own permission node as well as the setup permission
**Priority:** MoSCoW Must

THE SYSTEM SHALL require both `voyager.setup.use` and `voyager.setup.import` before it runs `/map import`. The other `/map` subcommands
SHALL keep requiring `voyager.setup.use` alone. A builder who fails either check SHALL change nothing.

#### Scenario: Builder without the setup permission
- **WHEN** a builder without `voyager.setup.use` runs `/map import elytrarace-cube skycube`
- **THEN** the command is refused, and no file or folder changes

#### Scenario: Builder without the import permission
- **WHEN** a builder who holds `voyager.setup.use` but not `voyager.setup.import` runs `/map import elytrarace-cube skycube`
- **THEN** the command is refused, no file or folder changes, and a `/map new` by the same builder is still allowed

#### Scenario: Builder with both permissions
- **WHEN** a builder who holds both `voyager.setup.use` and `voyager.setup.import` runs `/map import elytrarace-cube skycube`
- **THEN** the import runs as the other requirements of this capability state
