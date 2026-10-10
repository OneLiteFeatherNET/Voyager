# Proposal

**Conventional Commits:** `feat(setup)`. Squash-merge PR title: `feat(setup): start a minestom setup server that places rings where the builder looks`.

Commits on the branch follow the types the work needs and are squashed into the one `feat(setup)` commit on `main`: `build(setup)` for the module skeleton, `test(fitness)` for the architecture rules, `test(setup)` and `feat(setup)` for red and green code, `docs` for the ADR and the status documents. The platform additions (draft store, draft JSON writer, void-world copy) are `feat` commits with the `setup` scope too: no caller outside `voyager-setup` uses them.

## Why

Building a playable cup needs about 354 in-game actions for the 35-ring sample map, 350 of them ring placements (`docs/research/005-simpler-map-and-cup-setup.md`, Table 3). The owner unblocked epic E6 on 2026-10-09, approved that research as the E6.0 input, and decided that a ring stays a disc (center, normal, radius). A disc ring can be placed from the builder's pose with one click, so the first useful slice of `voyager-setup` is pose placement on Minestom. It also replaces the Paper setup server as the place where maps are authored.

## What Changes

- Add the Gradle module `voyager-setup`, a second composition root. It depends only on `voyager-api` and `voyager-platform`, uses avaje-inject through a `@Factory` in its `inject` package, and has zero `org.bukkit..` references.
- Boot a Minestom setup server (`:voyager-setup:runSetupDev`) that refuses to start when its data directory or its worlds directory is missing, as the game server does.
- Add `/map new <id>`, `/map open <id>`, `/map spawn` and `/map status`. `/map new` creates a draft and its world, in the game's own layout (see the storage decision below): the draft file at `<dataPath>/drafts/<id>.json` and the void world at `<worldsPath>/<id>`.
- **Storage decision (owner, 2026-10-09: write maps directly in the game's layout; no publish step in this change).** A draft is written to `<dataPath>/maps/<id>.json`, the flat file the game server reads, as soon as it is loadable by the game: at least one ring and a spawn. Until then it lives in `<dataPath>/drafts/<id>.json`, which the game never reads. The game server needs no change. This extends the owner's rule ("drafts with zero rings stay in drafts") with the spawn, because the game's map adapter refuses a file without `spawn`. Design, decision 4, records the rule and its one crash case.
- Add a wand. A right-click appends a ring at the builder's eye position with the look direction as its normal and the default radius. Sneak plus left-click removes the ring the look ray crosses; a plain left-click changes no ring (owner decision O2); later indices are renumbered.
- Show one display-entity preview per ring. The preview depends on a spike that confirms the Minestom 26.2 display-transformation API first.
- Autosave after every change, in the JSON shape the game server's map adapter reads (with `schemaVersion: 1`, `index` and `world` written explicitly), with an atomic replace (temp file, then move; never delete then write).
- Put the ring-from-pose and ring-picking logic in a pure inner package with no Minestom and no DI, tested with `new`.
- Record the authoring model as ADR-0018 (MADR 4.0, status `proposed`) and close the E6.0 entries in the status documents (US-6.01).
- Register `voyager-setup` in `voyager-fitness` and add its architecture rules.

**Not in this change:** test-fly (S4), the draft-to-published gate beyond the completeness rule (S5), cup commands (S6), persisted undo (S8), Interaction handles (S7), dialog forms (S9), ring presets (S10), design lint (S11), persistent action-bar checklist, lap recording (T1) and legacy import (S12). Each is a follow-up in `design.md`. Block editing, world saving and a folder-bundle layout (`maps/<id>/`) are not in this change either. The folder bundle is a `Could` follow-up.

**Removed from this change's plan (owner, 2026-10-09):** the prerequisite `align-map-folder-with-game-catalog`. No folder bundle and no publish copy exist any more, so nothing needs aligning.

## Capabilities

### New Capabilities
- `setup/server-bootstrap`: the Minestom setup server boots from its own composition root and refuses to start on a missing data directory or worlds directory.
- `setup/map-commands`: `/map new`, `/map open`, `/map spawn` and the map-id rules that protect the file system.
- `setup/ring-placement`: wand placement and removal of disc rings, index derivation and failure behaviour.
- `setup/ring-preview`: one display preview per ring, kept in step with the draft.
- `setup/map-draft-storage`: the file locations (`drafts/` and `maps/`), the completeness rule that chooses between them, the JSON shape shared with the game server, and the atomic autosave.
- `setup/map-status`: the `/map status` checklist (spawn set, ring count, blocking problems).

### Modified Capabilities
- None. `openspec/specs/` is empty, and no existing requirement changes. The greenfield design's decisions D1 to D12 are unchanged; the "Setup" section gains a pointer to ADR-0018.

## Impact

- **Code:** new module `voyager-setup` (`net.elytrarace.voyager.setup..`); new `api.mapsetup` types (`MapDraft`, `MapId`, `DraftStore`, exceptions) in `voyager-api`; a `DraftStore` implementation, a draft JSON writer and a void-template copy in `voyager-platform`; `settings.gradle.kts` and `voyager-fitness` registration.
- **Dependencies:** no new library. The existing Minestom 2026.08.28-26.2, avaje-inject 12.7 and the Minestom testing artifact are reused.
- **Docs:** ADR-0018, a Diataxis how-to for the setup server, the research spike record, status updates in `STATUS.md`, `docs/requirements/user-stories-stufe-6.md` and the greenfield epics.
- **Runtime:** the game server is not affected and needs no change. Its catalog reads only regular `*.json` files directly under `maps/` and only `worldsPath/<world>` for a map's world. Drafts in `drafts/` are never read. A complete draft in `maps/` is a file the game already reads, so the game boots with it, provided its world folder holds region data (the void template is built to hold it, spike 1.3).
- **Epic status:** this change covers the ring part of E6.1 and E6.2. It does not close E6.2's inventoried FAWE operations for terrain (external editor, research 005 section 1.3) or E6.4 (Anvil compatibility).
