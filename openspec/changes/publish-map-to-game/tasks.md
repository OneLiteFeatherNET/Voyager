# Tasks

Commit types and scope: `feat(setup)` for the port's use, the platform publish code, the planner, the commands, the
messages and their tests; `feat(setup)` for the cup publish once `cup-authoring` is merged; `refactor(catalog)` only if a
shared helper is extracted, as its own commit with no behaviour change; `docs` for the `docs/` pages and the ADR move; `test`
only for test-only commits. One type per commit. Each behaviour gets a failing test (Red) before its production code (Green).
Every test follows F.I.R.S.T.: a `@TempDir` data directory and publish root per test, `Clock.fixed` for the revision time, no
sleeps, no shared static state, no read of the shipped `catalog/` or `worlds/`. The owner decided the publish route and the
pickup on 2026-11-02 (`design.md`, decisions 1 and 13). Implementation proceeds on the recommended defaults of the open
questions, and the owner may change a default before the code pull request (task 0.6). Task checkboxes are ticked in the follow-up pull request,
never in the code pull request.

## 0. Spikes and owner decisions (no production code)

- [ ] 0.1 Spike: read the game's world loading (`MapInstances`, the Falco loader, `SettledChunkLoader`) and record whether a region file is read when a chunk loads, or once at open. Record the answer in `design.md`. The design does not rely on it (`test`)
- [ ] 0.2 Spike: confirm that `CatalogLoader.read` on `current/data`, where `current` is a symbolic link moved by `ATOMIC_MOVE`, sees the new revision after the move, on Linux, and that `MapDefinitionAdapter` reads a stated `world` such as `skyfortress.r1` as written (`test`)
- [ ] 0.3 Spike: check whether a world made by `/map new` (the void template) holds region data. The answer decides open question 5 of `design.md` (`test`)
- [ ] 0.4 Hangar: confirm whether the setup task is static or dynamic with `autoDeleteOnStop`, and where its drafts live on the node. Record the answer in `design.md` (`test`)
- [ ] 0.5 Helix: confirm how Minestom `2026.09.12-26.2` attaches the syntax of a second command object to the `map` root (`/map publish`). If it cannot, the syntax is added to `SetupCommands`, which adds no freeze-store entry because the class is already a frozen violation (`test`)
- [ ] 0.6 Owner: confirm or change the recommended defaults of the eight open questions of `design.md`, and record any change there. Not blocking: section 1 starts on the defaults (`docs`)

## 1. Red: the candidate, the store, the commands and the layout

- [ ] 1.1 Red: `WorldFingerprintTest` asserts that a fingerprint is equal for equal worlds, and differs for a changed region file, an added file and a renamed file, in `@TempDir` worlds (`feat(setup)`)
- [ ] 1.2 Red: `PublishPlannerTest` asserts that a ring-only change reuses the world name, that a changed fingerprint names `<id>.r<k>` with the next `k`, and that the checks run in the order of the design (`feat(setup)`)
- [ ] 1.3 Red: `CandidateCheckTest` asserts that a cup naming a map the candidate lacks is refused and the name is listed, that a world with no region data is refused and the world is named, and that every world of the candidate is checked, not only the changed one (`feat(setup)`)
- [ ] 1.4 Red: `CandidateCheckTest` asserts that every problem of a candidate appears in one reply, and that a map file the game's reader refuses is listed with the game's own message (`feat(setup)`)
- [ ] 1.5 Red: `RevisionStoreTest` asserts that a publish writes staging, the manifest and the pointer in that order, and that a fake `FileMover` that fails the pointer move leaves `current` on the previous revision with no staging folder (`feat(setup)`)
- [ ] 1.6 Red: `RevisionStoreTest` asserts that two stores over one `@TempDir` play two setup servers, and that the second publish is refused when the number is taken or the pointer moved, with `current` naming the first publisher's revision (`feat(setup)`)
- [ ] 1.7 Red: `RevisionStoreTest` asserts that a world folder is never written over an existing one, and that an existing folder keeps its bytes (`feat(setup)`)
- [ ] 1.8 Red: `LayoutCompatibilityTest` asserts that the game's `CatalogLoader.read` on `current/data` reads a published revision, that moving the pointer to a newer revision changes what a later read sees, and that a read made before the move keeps the earlier revision, since the game reads at its start only (`feat(setup)`)
- [ ] 1.9 Red: `CataloguePublisherTest` (fake ports, no file) asserts that a map with no ring, a draft changed on disk, and a missing claim are each refused before any port writes (`feat(setup)`)
- [ ] 1.10 Red: `CataloguePublisherTest` asserts that a successful map publish returns revision `n+1` with the item and the world name, that the reply says when no published cup names the map, and that the reply says the change plays at the game's next start (`feat(setup)`)
- [ ] 1.11 Red: `CupPublishTest` asserts that `/cup publish` refuses a cup naming a map the previous revision lacks, and copies the cup file byte for byte when the check passes (`feat(setup)`)
- [ ] 1.12 Red: `RollbackTest` asserts that a rollback writes a new revision with revision `n`'s data, that the pointer never moves backwards, and that a rollback to a revision whose world is missing is refused with the world named (`feat(setup)`)
- [ ] 1.13 Red: `HistoryTest` asserts the ten newest revisions, newest first, with number, time from the fixed clock, builder and action (`feat(setup)`)
- [ ] 1.14 Red: `PublishConfigTest` asserts that an unset `VOYAGER_PUBLISH_PATH` replies that publishing is not configured and writes nothing, and that a missing folder is refused, named, and not created (`feat(setup)`)
- [ ] 1.15 Red: `SetupPermissionTest` asserts that a builder with `voyager.setup.use` but not `voyager.setup.publish` is refused by `/map publish`, `/cup publish` and `/catalogue rollback`, and that `/catalogue history` needs only `voyager.setup.use` (`feat(setup)`)
- [ ] 1.16 Red: `PublishFreeSpaceTest` asserts that a publish is refused before the copy when the free space is below the world size plus 256 MiB (`feat(setup)`)
- [ ] 1.17 Red: the bundle parity test (`MessageBundleParityTest`) fails for the new keys until both bundles hold them (`feat(setup)`)
- [ ] 1.18 Red: confirm that 1.1 to 1.17 fail for the stated reason, and record the failure messages in the pull request (`test`)

## 2. Green: the port, the platform, the planner and the commands

- [ ] 2.1 Green: `api.publish` with `CataloguePublisher`, `RevisionNumber`, `Revision` and `PublishedItem`, and the exceptions `PublishRefusedException`, `RevisionNotFoundException`, `CatalogueChangedException` and `CataloguePublishFailedException` in `api.publish.exception`, each with `package-info.java` (`feat(setup)`)
- [ ] 2.2 Green: `PermissionNode.VOYAGER_SETUP_PUBLISH` with the value `voyager.setup.publish`, and the `PermissionNodeTest` case for it (`feat(setup)`)
- [ ] 2.3 Green: `platform.catalog.publish` with `WorldFingerprint`, `WorldSnapshot` (add-only copy), `CandidateCheck` (the game's reader, the cross-check of every cup, `holdsRegionData` for every world), and `RevisionStore` (staging, the number by `createDirectory`, the manifest, the pointer by `ATOMIC_MOVE`, the `FileMover` seam). `package-info.java` with `@NotNullByDefault` (`feat(setup)`)
- [ ] 2.4 Green: `FileCataloguePublisher` in `platform.catalog.publish`, which implements `CataloguePublisher` over 2.3 and reads the clock it is given (`feat(setup)`)
- [ ] 2.5 Green: `setup.publish` with `PublishPlanner`, a pure class that imports `api` and the JDK only, and the guard order of the design (`feat(setup)`)
- [ ] 2.6 Green: `PublishCommand` (`/map publish <id>`) and `CatalogueCommand` (`/catalogue history` and `/catalogue rollback <n>`) in `setup.inject`, and the `/cup publish` syntax after `cup-authoring` is merged. They ask `PermissionPolicy` and render replies with `SetupMessages`. Register them in `SetupServer` (`feat(setup)`)
- [ ] 2.7 Green: `VOYAGER_PUBLISH_PATH` read through `ConfigLoader` in `platform.config`, listed in `docs/reference/configuration.md` (`feat(setup)`)
- [ ] 2.8 Green: the message keys in `SetupMessages` (`platform.text`) and in `voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties`, German in the informal "du" (`feat(setup)`)
- [ ] 2.9 Green: the bean wiring in `SetupBeans`, which takes the `Clock` and the publish root, and does not change the existing beans (`feat(setup)`)
- [ ] 2.10 Green: confirm that every test of section 1 passes, and that `./gradlew :voyager:api:test :voyager:platform:test :voyager:setup:test` is green, run through `flock ~/.cache/claude-heavy.lock` (`test`)
- [ ] 2.11 Fitness: extend `ApiPurityTest.setupDomainOnlyDependsOnApiAndJdk` to `setup.publish..`. Confirm that `SliceBoundaryRulesTest`, `CloudNetBoundaryTest`, `PermissionBoundaryTest`, `PersistenceBoundaryTest` and `NullabilityConventionTest` pass without a change to them, and that no file under `voyager/fitness/src/test/resources/archunit_store` gains an entry. If the extension creates a violation, stop and report it (`test`)

## 3. Smoke, refactor and docs

- [ ] 3.1 Smoke: boot the setup shadow jar in a scratch directory with `VOYAGER_PUBLISH_PATH` set to a directory in that scratch directory, publish one map as a builder over a test connection, and record the revision line in the pull request. Stop the server with `stop` on standard input and expect a clean exit (`test`)
- [ ] 3.2 Smoke: boot the game server shadow jar with `VOYAGER_DATA_PATH` set to `<publish root>/current/data` and `VOYAGER_WORLDS_PATH` set to `<publish root>/worlds`, with the published revision in place. Wait for `Successfully enabled`, `Listening on` and `Voyager started`, quote them in the pull request, and stop cleanly (`test`)
- [ ] 3.3 Refactor: if `RevisionStore` and `JsonDraftStore` share the atomic write, extract it as its own commit with no behaviour change, and keep `JsonDraftStoreTest` unchanged (`refactor(catalog)`)
- [ ] 3.4 Docs: replace the section "Run the game server with the map" in `docs/guides/how-to-build-a-map-with-setup.md` with the publish, rollback, history and cup publish steps, and the note that a publish plays at the game's next start, after the cup that runs (`docs`)
- [ ] 3.5 Docs: `docs/guides/how-to-deploy-on-cloudnet.md` gets the publish root, the one-time change of the game task's data and worlds paths, the setting `VOYAGER_RESTART_AFTER_CUP` on the live task (ADR-0047), and the seed of revision 1 (open question 4 of `design.md`) (`docs`)
- [ ] 3.6 Docs: `docs/reference/permission-nodes.md` gets the row for `voyager.setup.publish`, and `docs/guides/how-to-grant-permissions.md` gets the release group (`docs`)
- [ ] 3.7 Docs: move the text of ADR-0048 from `design.md` to `docs/decisions/0048-publish-catalogue-revisions.md` with the status the owner gives it, Proposed unless accepted. Link `docs/decisions/0045-cup-files-from-the-setup-server.md` from it without editing it (`docs`)
- [ ] 3.8 Run `./gradlew :voyager:fitness:test` and `./gradlew build` for both trees, through `flock ~/.cache/claude-heavy.lock`, before the pull request is opened (`test`)

## 4. Ship

- [ ] 4.1 Open the code pull request under the title `feat(setup): publish checked maps and cups to the game catalogue`. Fill the sections of `.github/pull_request_template.md`, with the type `feat:` ticked. Describe the change from the builder's and the operator's side. Quote the failure messages of task 1.18 and the smoke lines of 3.1 and 3.2. State that ADR-0048 is Proposed and which owner answers the design took. End the body with the referral line the repository's rule asks for. Do not add a session link (`feat(setup)`)
