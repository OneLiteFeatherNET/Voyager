# Tasks

Commit types and scope: `feat(setup)` for every production change in this change, `test` for a test-only commit that changes no
behaviour, and `docs` for `docs/` pages and the ADR move. Each behaviour gets a failing test (Red) before its production code
(Green). Every test follows F.I.R.S.T.: synthetic region and `level.dat` files in `@TempDir`, injected size limits, a test executor
that runs the background task on the calling thread, no wall-clock reads, no network and no shared static state. The
integration test is tagged `integration` and is not part of CI. The open questions in `design.md` are answered by the owner before
section 3 starts, and the spike of task 4.1 runs before the pull request is opened. Task checkboxes are ticked in the follow-up
pull request, never in the code pull request.

## 1. Red: the import rules

- [ ] 1.1 Red: `WorldFolderNameTest` (api): accepts names of `[A-Za-z0-9_.-]{1,64}`; refuses `..`, `.`, a name with a separator, an empty name and a name of 65 characters (`feat(setup)`)
- [ ] 1.2 Red: `ImportGuardTest` (setup, `mapsetup`): a second claim of one id is refused while the first is held; release frees the id; two ids do not affect each other (`feat(setup)`)
- [ ] 1.3 Red: `ImportSettingsTest` (setup config, through a `ConfigLoader` over a fake environment): the defaults are `imports`, 268435456 bytes and 1024 regions; the keys `VOYAGER_IMPORT_PATH`, `VOYAGER_IMPORT_MAX_BYTES` and `VOYAGER_IMPORT_MAX_REGIONS` override them (`feat(setup)`)
- [ ] 1.4 Red: `WorldLayoutTest` (platform, `@TempDir`): a root `region/` is accepted; `dimensions/minecraft/overworld/region/` is accepted; both present is refused and the message names both; a folder with only `.mcr` files is refused and the message names the accepted layouts (`feat(setup)`)
- [ ] 1.5 Red: `FolderWorldImporterTest` (platform, `@TempDir`): a folder name with `..` or a separator is refused before any file is read, and a folder whose real path leaves the import root is refused (`feat(setup)`)
- [ ] 1.6 Red: `FolderWorldImporterTest`: a region file that is a symbolic link is refused, and nothing is copied (`feat(setup)`)
- [ ] 1.7 Red: `FolderWorldImporterTest`: only `r.X.Z.mca` files are copied; `level.dat`, `uid.dat`, `entities/`, `poi/` and `data/` are not, and the reply names the nether and end folders as ignored when they are present (`feat(setup)`)
- [ ] 1.8 Red: `FolderWorldImporterTest`: a region total above the injected byte limit is refused before any copy, and `worlds/` holds no folder afterwards (`feat(setup)`)
- [ ] 1.9 Red: `FolderWorldImporterTest`: a region count above the injected limit is refused before any copy (`feat(setup)`)
- [ ] 1.10 Red: `SpawnReaderTest` (platform, `@TempDir`): a gzip `level.dat` with `Data.spawn` in the overworld gives the block centre; one with only `Data.SpawnX/Y/Z` gives the block centre of the legacy keys; one with neither leaves the spawn unset (`feat(setup)`)
- [ ] 1.11 Red: `RegionCensusTest` (platform, `@TempDir`): a region with a namespaced full chunk, a bare `full` chunk, a partial `features` chunk, a chunk without status and an unreadable record gives full 2, partial 1, no status 1 and unreadable 1 (`feat(setup)`)
- [ ] 1.12 Red: `FolderWorldImporterTest`: a chunk whose `DataVersion` is 4904 is refused, the copy is removed, and the message names 4904 and 4903; a chunk at 4438 is accepted (`feat(setup)`)
- [ ] 1.13 Red: `FolderWorldImporterTest` with a test `DraftStore` whose save throws: the renamed world folder is removed afterwards, so neither the folder nor the draft exists (`feat(setup)`)
- [ ] 1.14 Red: `SetupCommandsTest`: `/map import` with an id that already has a draft, a world or a running import changes nothing and names the id (`feat(setup)`)
- [ ] 1.15 Red: `SetupPermissionTest`: `/map import` is refused for a builder without `voyager.setup.use`, and the refused builder changes no file (`feat(setup)`)
- [ ] 1.16 Red: `SetupCommandsTest`: a missing import root is refused with the absolute path it expected, and the server starts with it missing (`feat(setup)`)
- [ ] 1.17 Red: `MessageBundleParityTest` fails until every `voyager.setup.import.*` key exists in both `voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties` (`test`)
- [ ] 1.18 Red: confirm 1.1 to 1.16 fail for the stated reason and record the failure messages in the pull request (`test`)

## 2. Green: the port, the rules and the importer

- [ ] 2.1 Green: `WorldFolderName` record with the rule of 1.1, the port `WorldImporter` with `importWorld` and `discard`, and the records `ImportReport`, `Census` and the enum `WorldLayout` in `net.elytrarace.voyager.api.mapsetup`, with the exceptions of the import in `api/mapsetup/exception` and a `package-info.java` (`feat(setup)`)
- [ ] 2.2 Green: `ImportGuard` in `setup/mapsetup`, with the behaviour of 1.2 (`feat(setup)`)
- [ ] 2.3 Green: `ImportSettings` in `setup/config`, read through `ConfigLoader.get` only, with the defaults of 1.3 (`feat(setup)`)
- [ ] 2.4 Green: `RegionReader` and `RegionCensus` in `platform/world`: the header, the chunk records, the decompression, and the top-level `Status` and `DataVersion`, read one region at a time (`feat(setup)`)
- [ ] 2.5 Green: `SpawnReader` in `platform/world`: gzip `level.dat`, `Data.spawn` in the overworld first, then `Data.SpawnX/Y/Z`, at the block centre (`feat(setup)`)
- [ ] 2.6 Green: `FolderWorldImporter` in `platform/catalog`: the layout rule, the folder and link rules, the size limits checked before copying, the copy into `worlds/.<id>.importing`, the census, the data version refusal, the rename into `worlds/<id>`, and `discard` (`feat(setup)`)
- [ ] 2.7 Green: the rollback of 1.13: a failed save after the rename removes the renamed world (`feat(setup)`)

## 3. Green: the command and the messages

- [ ] 3.1 Green: the `import` subcommand in `SetupCommands`, gated by `voyager.setup.use` like the other subcommands, which takes the guard, runs the import on a background thread, saves the draft through `DraftStore.save` with `DraftEditor.skeleton` and `DraftEditor.withSpawn`, and replies on the main thread (`feat(setup)`)
- [ ] 3.2 Green: the import root is checked when the command runs, not at boot, so a missing root is refused with its absolute path and the server still starts (`feat(setup)`)
- [ ] 3.3 Green: the messages `voyager.setup.import.started`, `.done`, `.failed`, `.busy`, `.root.missing`, `.layout.unsupported`, `.layout.ambiguous`, `.folder.invalid`, `.folder.missing`, `.too.large`, `.dataversion.newer`, `.spawn.found`, `.spawn.none`, `.census`, `.partial.warning` and `.dimensions.ignored`, in both `voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties`, with `<arg:N>` placeholders and German in informal "du" (`feat(setup)`)
- [ ] 3.4 Refactor: confirm that no new code reads a system property, that no `Path` or platform type enters `voyager-api`, and that the tests of 1.1 to 1.16 still pass (`test`)

## 4. Spike, integration and documentation

- [ ] 4.1 Spike: run the integration test of 4.2 on the team-build worlds, record the census counts and whether `MapInstances.checkWorld` loads a copy at data version 4438, and record the answer to design open question 3 in the pull request (`test`)
- [ ] 4.2 Integration: one test, tagged `integration`, imports a team-build world when it is present, runs `MapInstances.checkWorld` on the copy, and skips with a message when it is absent (`test`)
- [ ] 4.3 Docs: `docs/guides/how-to-build-a-map-with-setup.md` gains the section "Import a finished world" (`docs`)
- [ ] 4.4 Docs: `docs/reference/configuration.md` lists `VOYAGER_IMPORT_PATH`, `VOYAGER_IMPORT_MAX_BYTES` and `VOYAGER_IMPORT_MAX_REGIONS` with their defaults (`docs`)
- [ ] 4.5 Docs: ADR-0042 moves from `design.md` to `docs/decisions/0042-import-finished-world.md` with the status Proposed, in the code pull request (`docs`)

## 5. Pull request

- [ ] 5.1 Open the code pull request titled `feat(setup): import a finished world into the setup server`, with the body filled from `.github/pull_request_template.md`; the body records the spike result of 4.1 and states that the import guard is replaced by `single-builder-map-claims`
