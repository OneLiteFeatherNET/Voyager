# FastAsyncWorldEdit Usage in the Setup Plugin: An Operation Inventory

**Authors:** Voyager Development Team | **Date:** 2026-10-10 | **Status:** Final | **Version:** 1.0

**Research input for:** E6.0 criterion 1 (spike X1), closing US-6.01 in `docs/requirements/user-stories-stufe-6.md`
**Prior work:** [005](005-simpler-map-and-cup-setup.md), section 1.3 (terrain outside the setup server), section 7.4 (owner decisions); [006](006-minestom-26-2-setup-spikes.md)

## Abstract

E6.0 requires the FastAsyncWorldEdit (FAWE) usage of `plugins/setup` to be inventoried as exact operations, not as "FAWE"
as a whole. This record lists every use with its file and line in the tree being replaced. The setup plugin uses FAWE in
one way only: it reads and writes the player's polyhedral region selection, which it turns into the vertices and center of
a portal. It never edits blocks. No `setBlock`, paste, copy, schematic, clipboard, edit session or world undo appears in
`plugins/setup/src`. The disc ring model of this change (pose placement, [ADR-0018](../decisions/0018-pose-placement-authoring-model-for-setup.md))
replaces every selection operation. The block-level operations that a FAWE replacement would have to cover are absent, so
they need no equivalent: terrain is built in an external editor, as research 005 section 1.3 proposes.

## 1. Methodology

- **Scope.** Every Java source under `plugins/setup/src/main/java` and `plugins/setup/src/test/java`, and the message
  resources under `plugins/setup/src/main/resources`. The build wiring is `plugins/setup/build.gradle.kts` (lines 15 to 17)
  and the version catalog in `settings.gradle.kts` (the `fawe` bundle).
- **Search.** A case-insensitive search for the FAWE and WorldEdit packages (`com.fastasyncworldedit`, `com.sk89q`), their
  types (`PolyhedralRegion`, `PolyhedralRegionSelector`, `RegionSelector`, `BlockVector3`, `BukkitAdapter`,
  `ActorSelectorLimits`, `LocalSession`), the editing API (`setBlock`, `setType`, `getBlockAt`, paste, schematic,
  clipboard, `EditSession`, `Operations`, undo), and the WorldEdit command strings (`//`).
- **Classification.** Each hit is assigned to one operation. An operation is a single behaviour of the plugin against
  FAWE state (read, write, reset, validate, limit), not a call site. Hits that only name FAWE in a comment or a message
  are listed as user-facing text.
- **Verification.** A hit count per search term was taken with `grep`; the operation list was checked against the call
  sites by reading each file. The plugin's own undo (`undo/`) is portal-level and was checked separately (section 4).
- **Limit.** The inventory is static. It records what the code calls, not what a builder typed on a live server.

## 2. Inventory of FAWE Operations

The FAWE types used are `PolyhedralRegionSelector` (selection), `PolyhedralRegion` (its region value), `RegionSelector`
(the session's selector interface), `BukkitAdapter.adapt` (player to WorldEdit actor), `LocalSession` (the actor's
selection state), `BlockVector3` (a vertex coordinate) and `ActorSelectorLimits` (the actor's selection size limit).

| # | Operation | FAWE types | Call sites (file:line) | Replaced by this change | Replaced by |
|---|---|---|---|---|---|
| O-1 | Read the player's current polyhedral selection | `BukkitAdapter`, `LocalSession.getRegionSelector`, `PolyhedralRegionSelector` | `util/FaweHelper.java:26-36`; `command/PortalCommand.java:72`; `command/PortalSaveCommand.java:58` | Yes | Pose placement: `RingFromPose.create` from the eye and the look direction (`voyager-setup`, `setup.mapsetup`) |
| O-2 | Read a selection's vertices and check that there are more than three | `PolyhedralRegionSelector.getVertices`, `getRegion` | `conversation/portal/PortalSelectedFinish.java:42-46` | Yes | A ring needs one center, one normal and one radius; a degenerate pose is refused (`InvalidPoseException`) |
| O-3 | Extract vertices and the region center into portal locations | `PolyhedralRegion.getVertices`, `PolyhedralRegion.getCenter` | `util/FaweHelper.java:51-60`; `command/PortalCommand.java:87-88`; `command/PortalSaveCommand.java:71-72`; `conversation/portal/PortalSavePrompt.java:34` | Yes | The ring's center, normal and radius (`api.race.Ring`) |
| O-4 | Write stored portal vertices into a new selector, to edit them | `BlockVector3.at`, `PolyhedralRegionSelector.selectPrimary` and `selectSecondary` (with `ActorSelectorLimits.forActor`), `LocalSession.setRegionSelector` | `util/FaweHelper.java:66-84`; `command/PortalEditCommand.java:66-67` | Yes, in part | No edit of a stored ring in this change. Removal and re-placement (wand) replace it; handles are follow-up S7 (`add-setup-ring-handles`) |
| O-5 | Clear or reset the player's selection | `LocalSession.setRegionSelector`, `RegionSelector.clear` | `util/FaweHelper.java:40-45`; `listener/SetupListener.java:81`; `command/MapLoadCommand.java:46,78`; `command/MapTeleportCommand.java:63`; `command/PortalCommand.java:75,127`; `conversation/portal/PortalSelectedFinish.java:31-39` | Yes | Nothing to reset: the wand holds no selection. A new ring starts from the pose, not from a previous selection |
| O-6 | Apply the actor's selection size limit to a loaded selection | `ActorSelectorLimits.forActor` | `util/FaweHelper.java:71` | Not needed | A disc ring has a fixed size of one radius; no selection size exists |
| O-7 | Tell the builder to type `//wand` to get the WorldEdit wand | none (command string) | `main/resources/elytrarace.properties:34-35` (`prompt.portal.fawe`, `prompt.portal.fawe.continue`); `conversation/portal/PortalInformationFawePrompt.java:10-16`; `conversation/portal/PortalPrompt.java:54` | Yes | The wand item (`adapter/Wand.java`), given by `/map new` and `/map open` |
| O-8 | User-facing text that names FAWE selection | none | `elytrarace.properties:85` (`portal.edit.start`), `:169` (`wizard.hint.place_portals`); `ElytraRace.java:208,220,222`; `command/HelpCommand.java:31,38,39`; `command/PortalEditCommand.java:18` | Yes | Texts of the new setup server name the wand and `/map` commands (`platform.text`, setup bundle) |

Operations O-1 to O-5 and O-7 are the whole FAWE selection workflow of the portal commands (`/elytrarace portal`,
`portal edit`, `portal save`) and of the conversation prompts. They all act on the polyhedral selection and never on
blocks.

## 3. Operations That Need No Equivalent

The setup server does not need a replacement for these, because the plugin does not perform them:

- **Block edits.** No `setBlock`, `setType`, `setBlockData`, `getBlockAt` or block-state write exists in `plugins/setup/src`.
  Terrain is not edited by the plugin at all.
- **Copy, paste, rotate, flip and schematic files.** No clipboard, `ClipboardHolder`, schematic loader or saver, or `//copy`,
  `//paste` or `//rotate` command string is used.
- **Edit sessions and world undo.** No `EditSession`, `EditSessionBuilder` or `Operations` call exists, so the plugin
  never changes a world through FAWE's queue and never undoes one.
- **Region fill, replace and set.** The `//set`, `//replace` and `//fill` family does not appear.

Terrain is built in an external editor and loaded by the game server as a finished world, as research 005 section 1.3
proposes. The setup server therefore needs no block-editing tool of its own. Block placement and breaking in an open map are
cancelled (spec `setup/ring-placement`, decision O4 of the change), so an edit the server cannot save is never made.

## 4. Items Checked and Found Not to Be FAWE

- **Portal undo.** `undo/UndoStack`, `UndoOperation` and `UndoManager` undo portal placements, deletions and edits. They
  hold `FilePortalDTO` values and never call FAWE. Their replacement is the follow-up `add-setup-undo` (S8).
- **Portal overlap and numbering.** `util/FaweHelper.findOverlappingPortal` (`FaweHelper.java:91`) and
  `nextPortalIndex` (`FaweHelper.java:119`) live in the FAWE helper class but use only `LocationDTO` and `PortalDTO`.
  They are portal logic. The new model has no overlap rule yet; the ring placement has none either, and that gap is
  recorded for the design lint follow-up (`add-setup-design-lint`, S11).
- **Dependency.** `plugins/setup/build.gradle.kts:15-17` declares the FAWE BOM and the `fawe` bundle as `compileOnly`
  or `implementation`. The `fawe` bundle is used only by the classes listed in section 2.

## 5. Conclusions

1. The setup plugin's FAWE dependency is a selection dependency: eight operation groups (O-1 to O-8) use
   `PolyhedralRegionSelector` or the `//wand` command, and none edits a block.
2. Every selection operation that the builder performs is replaced by the disc ring model: pose placement (O-1, O-2), the
   ring as the stored value (O-3), removal and re-placement instead of vertex editing (O-4), and no reset (O-5). O-6 has no
   counterpart because a disc has no selection size. This change implements the placement and removal path (task 7.x).
3. The polyhedral import path is not in the setup server. Existing polyhedral portals reach the rebuild only through
   `tools/map-converter` (research 005, section 7.4; issue #120). The inventory found no other reader of polyhedral data.
4. No block-level FAWE operation needs an equivalent on the setup server. US-6.03 ("the FAWE-equivalent operations of E6.0
   executable on the setup server") is therefore satisfied for the operations this plugin actually uses: the ring operations
   are done, and the rest of the list is empty. Terrain editing stays in the external editor.

## 6. Reproduction

```bash
grep -rn -i "fastasyncworldedit\|com.sk89q\|PolyhedralRegion\|RegionSelector\|BukkitAdapter\|BlockVector3\|ActorSelectorLimits\|LocalSession" plugins/setup/src
grep -rn "setBlock\|setType\|getBlockAt\|paste\|Schematic\|Clipboard\|EditSession" plugins/setup/src/main
grep -rn '"//' plugins/setup/src/main
```

The first command lists the selection hits of section 2. The second returns no line, which is the block and clipboard
result of section 3. The third finds the one WorldEdit command string, `//wand` (`elytrarace.properties:34`).
