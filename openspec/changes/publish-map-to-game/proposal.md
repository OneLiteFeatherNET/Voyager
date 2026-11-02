# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under the title `feat(setup): publish checked maps and cups to the game catalogue`. The `/cup publish` part of it needs `cup-authoring` merged first, because that change writes the cup files this one publishes.

## Why

A builder finishes a map in the setup server, and `/map status` reports it ready. Getting it into the game is still a hand job. The draft is one file and the world is another folder. The operator copies both into the game's catalogue and template. A mistake shows only when the game refuses to boot, or when a cup names a map nobody copied. The setup server already knows the rules the game applies to a catalogue, so it should apply them before anything reaches the game, and the game should pick up the result without a copy.

## What Changes

- `/map publish <id>`: a builder who holds the map's claim publishes it. The setup server runs the blocking checks of `/map status` and the game's catalogue check (the catalogue reader, the cup cross-check and world presence) on a candidate revision. Only a candidate that passes becomes visible to the game.
- A published revision is a numbered, complete and immutable set of the game's maps and cups, with the worlds they name, kept under the publish root that `VOYAGER_PUBLISH_PATH` names. The game server reads the revision that the pointer `current` names. The change does not touch the game's code.
- Worlds are add-only. A published world is copied once under a new name (`<id>.r<k>`) and never changed afterwards, so a race that has a world open never sees its terrain change.
- `/cup publish <id>` publishes a cup. It refuses a cup that names a map no published revision holds.
- `/catalogue history` lists the revisions. `/catalogue rollback <n>` publishes the content of revision `n` as a new revision, so history is never rewritten.
- A new permission node, `voyager.setup.publish`, gates publish and rollback, on top of `voyager.setup.use`.
- A new configuration key, `VOYAGER_PUBLISH_PATH`, is read through the configuration loader (ADR-0036).
- All new player-facing text goes into both setup bundles (`en_US` and `de_DE`).
- Docs: the map how-to, the CloudNet deployment guide (the one-time change of the game's data and worlds paths), the configuration reference, the permission reference, and ADR-0048 (Proposed).

## Capabilities

### New Capabilities

- `setup/catalogue-publish`: the publish and rollback commands, the candidate check, the revision layout and its atomic pointer, the world snapshot rule, the play-time rule for a changed map, the cup assignment rule, the history, the permission node and the configuration key.

### Modified Capabilities

None. The new `/map publish` subcommand and the new `/catalogue` root are specified in the new capability. No requirement of an existing capability changes.

## Out of scope

- Publishing through a CloudNet template or S3 (design decision 2, option B). It is recorded as the migration target, not built here.
- Any change to the game server's code. The game reads its catalogue through its existing data-path and worlds-path settings, which the operator points at the publish root once.
- Game-side polling for new revisions. The game picks up a revision at its next reload or next start, as today (owner question 5).
- Seeding the live catalogue. The one-time move of the live maps and worlds into revision 1 is a documented operator step; a seed command is an open question.
- Retention and pruning of revisions and worlds. Nothing is deleted by Voyager in this change.
- Test flights. See `test-fly-draft-map`, which is independent of this change.
- Automatic cup assignment. A published map is played only when a cup names it, through the cup commands of `cup-authoring`.

## Impact

- Vertical slices: `setup` (the commands in `setup.inject`, the pure planner in `setup.publish`, the messages), `api` (the `CataloguePublisher` port, its records and exceptions in `api.publish`, one `PermissionNode` value), and `platform` (the revision store, the world snapshot and the candidate check in `platform.catalog.publish`, the configuration key). The `race` and `server` slices are not changed. The bridge is not changed.
- Greenfield design: if ADR-0048 is accepted, it amends the template cut for maps and cups in the section "CloudNet v4" of `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`. No numbered decision D1 to D16 changes.
- ADR-0045 (cup files from the setup server) says the operator copies cups by hand and that a publish command is later work. When ADR-0048 is accepted, it replaces that copy step. ADR-0045 is not edited.
- Operators: once, the live game task's data and worlds paths point at the publish root. That is an operator action after the merge, not part of the pull request.
- Size: L. It crosses four slices and adds a node and a configuration key.
