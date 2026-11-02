# Spec Delta

## Purpose

A builder's finished map and cup must reach the game without a manual copy, and only after the game's own checks pass.
The game must never read a catalogue that is half written, and a running race must never see its terrain change. A
published revision is numbered, complete and immutable; the game reads the revision the pointer names. Requirements use
EARS patterns and MoSCoW priorities, as `openspec/config.yaml` requires.

Terms: a **revision** is one numbered, complete set of the game's maps and cups, with the worlds they name. A **candidate**
is a revision under construction. A **publish root** is the node directory named by `VOYAGER_PUBLISH_PATH`.

## ADDED Requirements

### Requirement: Publishing needs the claim and the publish node
**Priority:** MoSCoW Must

WHEN a builder runs `/map publish <id>`, `/cup publish <id>` or `/catalogue rollback <n>`, THE SYSTEM SHALL require the
permission `voyager.setup.use` and the permission `voyager.setup.publish`, and SHALL require that the builder holds the
claim of the map (`map:<id>`) for a map publish, or of the cup (`cup:<id>`) for a cup publish. IF a requirement is not met,
THEN THE SYSTEM SHALL refuse the command and SHALL change no file under the publish root.

#### Scenario: Builder without the publish node
- **WHEN** a builder who holds `voyager.setup.use` but not `voyager.setup.publish` runs `/map publish skyfortress`
- **THEN** the builder is refused with the denial message, and no file under the publish root changes

#### Scenario: Publish without the claim
- **WHEN** a builder with both nodes runs `/map publish skyfortress` and another builder holds `map:skyfortress`
- **THEN** the builder is refused with a message that names the holder, and no revision is written

### Requirement: A map is published only when it is ready
**Priority:** MoSCoW Must

WHEN a builder publishes a map, THE SYSTEM SHALL refuse the publish, naming each problem, IF the draft has no spawn, IF the
draft has no ring, IF the stored copy of the draft differs from the copy the session holds, or IF the world folder of the map
holds no region data. The checks SHALL use the same two blocking problems as `/map status`.

#### Scenario: Blocking problems of the status command apply
- **WHEN** a builder publishes a draft with no ring
- **THEN** the reply names the missing ring, and the publish root changes in no file

#### Scenario: Stored copy changed on disk
- **WHEN** the stored draft of `skyfortress` was changed by a second setup server after the builder loaded it
- **THEN** the publish is refused with the changed-on-disk message, and the publish root changes in no file

### Requirement: The game's own check runs on the whole candidate
**Priority:** MoSCoW Must

WHEN a publish builds a candidate, THE SYSTEM SHALL read the candidate with the game's catalogue reader, SHALL check that every
cup of the candidate names only maps the candidate holds, and SHALL check that every world named by any map of the candidate
holds region data. IF any check fails, THEN THE SYSTEM SHALL refuse the publish, SHALL list every problem in one reply, and
SHALL make no revision visible.

#### Scenario: Several problems are listed together
- **WHEN** a candidate has one cup that names a missing map and one map whose world holds no region data
- **THEN** the reply lists both problems, and `current` still names the previous revision

#### Scenario: The game's refusal is shown
- **WHEN** a candidate holds a map file that the game's reader refuses
- **THEN** the reply names that file with the game's own message

### Requirement: A revision is visible only when it is complete
**Priority:** MoSCoW Must

THE SYSTEM SHALL write a candidate into a staging folder, SHALL write its manifest, and SHALL move the pointer `current` to
the new revision as a single atomic step, as the last step of a publish. IF any step before the pointer move fails, THEN
THE SYSTEM SHALL keep `current` on the revision it named before, SHALL remove the staging folder, and SHALL reply that the
publish did not change the published catalogue.

#### Scenario: Failure before the pointer moves
- **WHEN** the move of the pointer fails during a publish
- **THEN** `current` names the previous revision, the game reads the previous revision, and no staging folder remains

#### Scenario: A reader sees whole revisions only
- **WHEN** the game boots while a publish runs
- **THEN** the game reads either the previous revision or the new one, never a mix of the two

### Requirement: Worlds are add-only and named by revision
**Priority:** MoSCoW Must

WHEN a publish names a world that the map's published world does not match, THE SYSTEM SHALL copy the world to a new folder
`worlds/<id>.r<k>` and SHALL NOT change any folder that exists under `worlds/`. WHEN the fingerprint of the world matches the
world the map already publishes, THE SYSTEM SHALL reuse that world and SHALL copy nothing. The published map file SHALL state
its `world` value explicitly.

#### Scenario: Ring-only change copies no world
- **WHEN** a builder changes a ring of a published map and publishes it again
- **THEN** the new revision names the same world folder, and no file under `worlds/` is written

#### Scenario: Terrain change copies a new world
- **WHEN** the terrain of a published map changed and the builder publishes it
- **THEN** a folder `worlds/<id>.r<k>` is written, the previous folder keeps its bytes, and the revision names the new folder

#### Scenario: A world name that exists is refused
- **WHEN** a publish would write a world folder that already exists
- **THEN** the publish is refused, and the existing folder keeps its bytes

### Requirement: A published change reaches the game at its next reload or start
**Priority:** MoSCoW Must

WHEN a revision is published, THE SYSTEM SHALL make it the revision the game reads at its next reload or start, with no copy
by an operator. THE SYSTEM SHALL NOT change a round that the game is running, and the reply SHALL say when the change plays.

#### Scenario: Published ring change during a round
- **WHEN** a builder publishes a changed ring of a map that is being raced
- **THEN** the running round keeps its rings, the reply says the change plays from the next round after a reload or a start

#### Scenario: Published terrain with a restart note
- **WHEN** a builder publishes a changed terrain of a map that is open in the game
- **THEN** the reply says that the game opens the new world at its next reload, and that a restart releases the old world

### Requirement: A cup is published only when every map it names is published
**Priority:** MoSCoW Must

WHEN a builder runs `/cup publish <id>`, THE SYSTEM SHALL refuse the publish, naming each map the cup names that the candidate
does not hold, and SHALL copy the cup file only after the candidate check passes. The copied file SHALL be the cup file that
`cup-authoring` wrote, so its notes stay and it carries no authors.

#### Scenario: Cup names an unpublished map
- **WHEN** a builder publishes a cup that names `canyon`, and `canyon` is not in the previous revision
- **THEN** the publish is refused and the reply names `canyon`

#### Scenario: Cup published after its maps
- **WHEN** every map the cup names is in the previous revision
- **THEN** the cup file is copied into the new revision and the reply names the revision

### Requirement: Publishing a map does not assign it to a cup
**Priority:** MoSCoW Must

WHEN a map is published, THE SYSTEM SHALL NOT add it to any cup. IF no published cup names the map, THEN THE SYSTEM SHALL say
so in the reply, and the reply SHALL name the next command, `/cup map add`.

#### Scenario: Map in no published cup
- **WHEN** a builder publishes `skyfortress` and no published cup names it
- **THEN** the publish succeeds, the reply says that no published cup plays the map, and no cup file changes

### Requirement: Every revision is numbered, timed and attributed
**Priority:** MoSCoW Must

WHEN a publish or a rollback succeeds, THE SYSTEM SHALL write revision `n+1` with a manifest that holds the revision number,
the time from the injected clock in ISO-8601 UTC, the builder's name as recorded at the claim, the action, the items it
changed, and the fingerprint of every world it names. Revision numbers SHALL NOT be reused.

#### Scenario: Manifest of a publish
- **WHEN** builder `Alpha` publishes `skyfortress` at the fixed clock time
- **THEN** `revisions/<n+1>/manifest.json` names revision `n+1`, the time, `Alpha`, the action `publish-map` and `skyfortress`

#### Scenario: Two publishers at once
- **WHEN** two setup servers on one publish root publish at the same time, and the second finds the number taken or the pointer moved
- **THEN** the second publish is refused, and `current` names the first publisher's revision

### Requirement: Rollback publishes an earlier revision as a new one
**Priority:** MoSCoW Must

WHEN a builder runs `/catalogue rollback <n>`, THE SYSTEM SHALL write revision `N+1` whose data is the data of revision `n`,
and SHALL run the candidate check on it. THE SYSTEM SHALL NOT move the pointer to an earlier revision. IF a world that
revision `n` names is missing, THEN THE SYSTEM SHALL refuse the rollback and name the world.

#### Scenario: Rollback of a ring change
- **WHEN** a builder rolls back to revision 4 while revision 6 is current
- **THEN** revision 7 holds the data of revision 4, and revision 6 keeps its files

#### Scenario: Rollback to a missing world
- **WHEN** revision 4 names `worlds/canyon.r2` and that folder is missing
- **THEN** the rollback is refused, and the reply names `canyon.r2`

### Requirement: History lists the revisions
**Priority:** MoSCoW Should

WHEN a builder with `voyager.setup.use` runs `/catalogue history`, THE SYSTEM SHALL list the last ten revisions with their
number, time, builder, action and items, newest first.

#### Scenario: History of ten revisions
- **WHEN** twelve revisions exist
- **THEN** the reply lists revisions 12 to 3

### Requirement: The publish root is configured and never created
**Priority:** MoSCoW Must

THE SYSTEM SHALL read `VOYAGER_PUBLISH_PATH` through the configuration loader. IF the key is unset, THEN each publish command
SHALL reply that publishing is not configured and the server SHALL keep running. IF the key names a folder that does not
exist, THEN THE SYSTEM SHALL refuse the command, SHALL name the folder, and SHALL NOT create it.

#### Scenario: Key unset
- **WHEN** `VOYAGER_PUBLISH_PATH` is not set and a builder runs `/map publish skyfortress`
- **THEN** the reply says that publishing is not configured, and the server keeps running

#### Scenario: Folder missing
- **WHEN** `VOYAGER_PUBLISH_PATH` names a folder that does not exist
- **THEN** the publish is refused with the folder's path, and the folder is not created

### Requirement: The publish runs off the tick and replies on the main thread
**Priority:** MoSCoW Should

THE SYSTEM SHALL run the world copy, the fingerprint and the candidate check on a worker thread, SHALL NOT join a future on
the tick, and SHALL send the reply on the main thread. IF the free space of the publish root is below the size of the world
copy plus a fixed margin of 256 MiB, THEN THE SYSTEM SHALL refuse before the copy.

#### Scenario: Free space too small
- **WHEN** a terrain publish needs 40 MB and the publish root has 30 MB free
- **THEN** the publish is refused before any file is copied

### Requirement: The publish reply is bilingual
**Priority:** MoSCoW Must

THE SYSTEM SHALL send every publish, rollback and history message from a key in both `voyager_setup_en_US` and
`voyager_setup_de_DE`, with the same `<arg:N>` placeholders. German SHALL use the informal "du".

#### Scenario: Parity of the new keys
- **WHEN** the bundle parity test runs
- **THEN** every new key exists in both bundles with the same placeholders

### Requirement: Publishing sends nothing to CloudNet
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT reference any class of `eu.cloudnetservice..` in the setup server, the platform or the API for this
change, and the bridge SHALL NOT change.

#### Scenario: Architecture rule holds
- **WHEN** `CloudNetBoundaryTest` runs after the change
- **THEN** it passes without a change to the test or to the bridge

## Won't (this change)

- A CloudNet template or S3 upload of the revisions (design, decision 2, option B).
- A game-side poll of the pointer, and any change to the game's code.
- Automatic assignment of a map to a cup.
- Automatic deletion of revisions or worlds.
