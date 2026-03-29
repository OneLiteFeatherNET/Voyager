# Builder Setup UX Analysis & Map Versioning Plan

**Date:** 2026-03-29
**Status:** Proposal (revised after feedback)
**Authors:** Voyager Agent Team (Architect, Game Designer, Game Psychologist, Researcher)

---

## Abstract

This document analyzes the current builder workflow for creating cups, maps, and rings in Voyager, identifies key pain points, and proposes a redesigned system that streamlines the conversation flow, adds visual feedback and test-fly validation, and introduces Anvil-based map versioning with instant rollback. The FAWE PolyhedralRegion workflow for portals is intentionally kept — builders use it to shape rings to match the map's visual design. The redesign focuses on reducing unnecessary prompts, adding preview/undo, and making versioning safe.

**Constraints:**
- **No Polar format** — banned from the project
- **FAWE portal placement stays** — builders need PolyhedralRegionSelector to shape rings to the map design
- **Anvil stays as the world format** everywhere (setup + game server)

---

## 1. Current State Analysis

### 1.1 The Current Workflow

**Cup creation:** 4 sequential chat prompts (~30 seconds)
```
/elytrarace cup create → yes/no → name → display name → done
```

**Map creation:** 7 sequential chat prompts (~60 seconds)
```
/elytrarace map create → yes/no → select cup → name → display name → authors → select world → done
```

**Ring/Portal creation:** 6 sequential chat prompts PER RING (~45 seconds each)
```
/elytrarace portal create → yes/no → check world → FAWE PolyhedralRegion select
→ confirm → index number → save → another? → loop
```

**For a typical map with 50 rings:** 50 x 45 sec = **~37 minutes just for ring placement**, plus constant context-switching between FAWE and chat prompts.

### 1.2 What Works Well (Keep)

- **FAWE PolyhedralRegionSelector for ring shapes** — Builders use this intentionally to create rings that match the map's visual design (e.g., fitting a ring into a cave opening, matching an archway). This is a feature, not a bug.
- **Conversation API structure** — The prompt chain model is solid, just too many unnecessary steps.
- **JSON config format** — Simple, human-readable, works well.
- **Separation of cup/map/portal creation** — Correct granularity.

### 1.3 What Needs Fixing

| Problem | Impact | Root Cause |
|---|---|---|
| Unnecessary yes/no confirmation prompts | Wastes time, breaks flow | Over-cautious design |
| Manual index number entry | Error-prone, tedious | No auto-increment |
| No visual ring preview after placement | Builder can't verify placement | Missing particle rendering |
| No way to edit a ring after creation | Must delete + recreate | No edit flow |
| No undo | Fear of mistakes → slower work | Missing operation stack |
| No test-fly mode | Must join as player to test | Missing validation tool |
| No ring reordering | Must recreate everything | No reindex command |
| No map versioning | One mistake can break published map | No version control |
| No ring visibility toggle | Can't see all rings at once | Missing visualization |
| Single maps.json for all maps | Fragile, no isolation | Monolithic storage |

### 1.4 Psychology of the Current Experience

**Broken Flow State:** The yes/no → type name → yes/no → type index cycle forces builders out of spatial/creative mode into data-entry mode. Flow requires uninterrupted action-feedback loops.

**High Cognitive Load:** Builders track: FAWE selection state, conversation state, index numbers, which step they're on. Exceeds Miller's Law (7±2 items).

**Missing Safety Net:** No undo, no versioning, no preview = fear of mistakes. Loss aversion means builders avoid experimenting.

---

## 2. Proposed Design

### 2.1 Design Philosophy

> **Keep FAWE for ring shaping. Remove everything else that wastes time.**

The portal/ring creation flow using FAWE stays because builders need precise geometric control. But we eliminate: unnecessary confirmations, manual indexing, re-entering the conversation for each ring, and lack of visual feedback.

### 2.2 Streamlined Portal Creation (From 6 Steps to 3)

**Current per ring (6 steps):**
```
1. PortalPrompt: "Create a portal?" → yes
2. PortalInformationFawePrompt: "Use FAWE to select region" (message)
3. PortalSelectedFinish: "Done selecting?" → yes (validates region)
4. PortalIndex: "Enter index number" → 14
5. PortalSavePrompt: saves
6. PortalSetupNextPrompt: "Another one?" → yes → loop
```

**Proposed per ring (3 steps):**
```
1. Builder selects PolyhedralRegion with FAWE (as before)
2. /portal                ← single command, auto-detects FAWE region
   → Validates region (3+ vertices)
   → Auto-assigns next index
   → Saves immediately
   → Shows particle preview of the placed ring
   → Actionbar: "Portal #14 saved | 14/50 placed"
3. Builder selects next region with FAWE, types /portal again
```

**Key changes:**
- **No conversation flow at all** — single command replaces entire prompt chain
- **Auto-index** — system assigns next available index number
- **Auto-detect FAWE region** — reads player's current WorldEdit session
- **Immediate particle preview** — ring outline rendered as particles after save
- **No "another one?" prompt** — builder just keeps going, `/portal` again when ready

**The FAWE workflow stays identical.** Builders still use `//sel poly`, click vertices, shape the ring to match the architecture. Only the "register this region as a portal" part gets simplified.

### 2.3 Streamlined Cup Creation (From 4 Steps to 2)

**Current:**
```
/elytrarace cup create → "Create?" yes → name → display name → done
```

**Proposed:**
```
/cup create <name> <displayName>

Example: /cup create nether_cup "<yellow>Nether Cup"
→ Cup created. Open /cup to manage maps.
```

Single command with arguments. No conversation needed for something this simple.

### 2.4 Streamlined Map Creation (From 7 Steps to 3)

**Current:** 7 prompts across cup selection, name, display name, author, world.

**Proposed:**
```
/map create <name> <displayName>

Example: /map create sky_fortress "<aqua>Sky Fortress"
→ Map created in current world.
→ Author auto-set to executing player.
→ Actionbar: "Setup mode active | /map info for details"
```

- **World auto-detected** from player's current world
- **Author auto-set** from player name (editable later via `/map author`)
- **Cup assignment** via the Cup Manager GUI (drag map into cup)

### 2.5 Portal/Ring Management

**Commands for managing placed portals:**

| Command | Action |
|---|---|
| `/portal` | Save current FAWE selection as next portal |
| `/portal edit <n>` | Re-select portal #n (loads region, builder adjusts with FAWE, `/portal save`) |
| `/portal save` | Save edited portal |
| `/portal delete <n>` | Delete portal (undoable) |
| `/portal undo` | Undo last portal operation |
| `/portal reindex` | Re-number all portals sequentially (fill gaps) |
| `/portal list` | Show all portals with teleport links |
| `/portal show` | Toggle particle preview for all portals |
| `/portal tp <n>` | Teleport to portal center |
| `/portals` | Open Portal Manager GUI |

### 2.6 Portal Particle Preview

When a builder is in setup mode, all portals are rendered as particle outlines:

- **Vertices** from the PolyhedralRegion rendered as connected particle lines
- **Center point** shown as a bright particle cluster
- **Index number** shown as floating text (armor stand or display entity)
- **Color by position:** early portals = green, middle = yellow, late = red (shows progression)

```
/portal show             Toggle all portal particles
/portal show <n>         Highlight specific portal
```

### 2.7 Portal Edit Flow

**Problem:** Currently, editing a portal means deleting it and recreating from scratch.

**Proposed:**
```
/portal edit 14
→ Portal #14's FAWE region loaded into player's WorldEdit session
→ Particles turn yellow (editing indicator)
→ BossBar: "Editing Portal #14 | Adjust with FAWE, then /portal save"
→ Builder adjusts vertices using FAWE tools
→ /portal save
→ Portal updated in place (same index, new geometry)
```

This requires storing the original PolyhedralRegion vertices and loading them back into FAWE's session — which is possible via `PolyhedralRegionSelector.selectPrimary/Secondary`.

### 2.8 Test-Fly Mode

```
/portal testfly            Start from portal #1
/portal testfly 8          Start from portal #8
```

**During flight:**
- Builder gets elytra + firework rockets automatically
- Portal collision detection active (same logic as game)
- BossBar: `TEST FLY | Portal 14/52 | Time: 23.4s`
- Portals flash green on passthrough, red on miss
- Flight path recorded as particle trail

**After flight — results in chat (clickable):**
```
TEST FLY RESULTS
Time:    67.3s
Portals: 48/52 hit (92%)
Missed:  #8, #23, #31, #49
Avg gap: 1.3s between portals
Longest: 4.1s (Portal #22→#23)
[Re-fly]  [Edit #8]  [Exit]      ← clickable Adventure components
```

### 2.9 Map Overview & Validation

```
/map info
```

```
MAP: Sky Fortress
Author: TheMeinerLP | World: world_sky_fortress
Portals: 52 | Est. time: ~72s

PORTAL TYPES: 52 total
VALIDATION:
  [OK] Portal count in range (40-80)
  [OK] No duplicate indices
  [OK] No gaps in index sequence
  [WARN] Gap >3s between #22 and #23
  [WARN] Gap <0.5s between #6 and #7
  [OK] Estimated time in target range (60-90s)

[Test Fly]  [Portal Manager]  [Versions]    ← clickable
```

### 2.10 Cup Management GUI

```
/cup                     Opens Cup Manager (inventory GUI)
```

**Cup List:** One item per cup. Click to open cup editor.

**Cup Editor:**
- Top rows: Maps in this cup (ordered, drag to reorder)
- Bottom rows: Available maps not in any cup
- Click to add/remove, click+click to swap positions

### 2.11 Full Command Summary

| Command | Prompts | Replaces |
|---|---|---|
| `/cup create <name> <display>` | 0 | 4-prompt conversation |
| `/cup` | 0 (GUI) | — |
| `/map create <name> <display>` | 0 | 7-prompt conversation |
| `/map info` | 0 | — |
| `/map validate` | 0 | — |
| `/portal` | 0 | 6-prompt conversation per ring |
| `/portal edit <n>` | 0 | delete + recreate |
| `/portal delete <n>` | 0 | — |
| `/portal undo` | 0 | — |
| `/portal testfly` | 0 | manually joining as player |
| `/portals` | 0 (GUI) | — |
| `/portal show` | 0 | — |

**Prompt count for full map setup (50 portals):** ~0 prompts vs current ~300+.
The builder just uses FAWE + `/portal` repeatedly.

---

## 3. Map Versioning & Rollback (Anvil-Based)

### 3.1 Core Concept

Since Polar is off the table, versioning uses **Anvil world directory snapshots**. Each version is a compressed archive of the world directory + the portal config JSON.

### 3.2 Storage Layout

```
data/maps/
  sky-fortress/
    manifest.json              # Version registry + state tracking
    current/                   # Active working copy (Anvil world)
      region/
        r.0.0.mca
        r.0.-1.mca
        ...
    versions/
      v1.tar.gz                # Compressed Anvil snapshot + config
      v1-config.json           # Portal config for v1 (also inside tar.gz)
      v2.tar.gz
      v2-config.json
      v3.tar.gz
      v3-config.json
```

**Why tar.gz:**
- Anvil worlds are directories of `.mca` files — can't version as a single file without archiving
- tar.gz is fast to create/extract and reduces storage (region files compress well)
- A 10x10 chunk world: ~13MB Anvil → ~3-5MB compressed
- Java has built-in tar/gzip support (`java.util.zip` + commons-compress)

**Why `config.json` is also stored separately:**
- Quick access without extracting the archive
- Used by the game server (doesn't need the world files to know portal positions)
- Enables config-only rollbacks (keep world, change ring layout)

### 3.3 Version States

| State | Meaning | Game servers see? |
|---|---|---|
| `draft` | Builder editing in `current/` | No |
| `published` | Live on game servers | Yes |
| `archived` | Previous version, rollback target | No |

### 3.4 Manifest Format

```json
{
  "mapId": "550e8400-...",
  "name": "elytrarace:sky-fortress",
  "displayName": "Sky Fortress",
  "author": "TheMeinerLP",
  "worldName": "world_sky_fortress",
  "publishedVersion": 2,
  "versions": [
    {
      "version": 1,
      "createdAt": "2026-03-15T14:30:00Z",
      "createdBy": "BuilderSteve",
      "label": "Initial layout",
      "state": "archived",
      "archiveSize": 4200000
    },
    {
      "version": 2,
      "createdAt": "2026-03-20T10:00:00Z",
      "createdBy": "BuilderSteve",
      "label": "Wider rings in cave section",
      "state": "published",
      "archiveSize": 4350000
    },
    {
      "version": 3,
      "createdAt": "2026-03-28T16:45:00Z",
      "createdBy": "BuilderAlex",
      "label": "Testing new shortcut",
      "state": "draft",
      "archiveSize": null
    }
  ]
}
```

### 3.5 Builder Commands

| Command | Action |
|---|---|
| `/map version save "label"` | Snapshot current world + config as new version |
| `/map version publish` | Promote latest version to published |
| `/map version rollback <n>` | Extract version n into `current/`, set as published |
| `/map version list` | Show all versions with labels and states |
| `/map version discard` | Delete unpublished latest version |
| `/map version diff <a> <b>` | Compare portal configs between two versions |

### 3.6 Create Version Flow

```
Builder: /map version save "Added cave section rings"

System:
1. Verify builder is in setup mode for this map
2. Create tar.gz of current/ world directory
3. Copy current portal config as v{n}-config.json
4. Store both in versions/
5. Update manifest.json: new entry with state "draft"
6. Actionbar: "Version 3 saved | /map version publish to go live"
```

### 3.7 Rollback Flow

```
Builder: /map version rollback 2

System:
1. Unload the world from Paper (kick builders, save chunks)
2. Delete current/ directory
3. Extract v2.tar.gz into current/
4. Load v2-config.json as active portal config
5. Reload world in Paper
6. Update manifest: v2 → published, previous published → archived
7. Builder is teleported back into the world
8. Actionbar: "Rolled back to v2: 'Wider rings in cave section'"
```

**Rollback is NOT instant** (world unload + extract + reload takes a few seconds), but it's fully automated and safe.

### 3.8 Deployment to Game Servers

The game server (Minestom) loads maps via `AnvilLoader`. Deployment:

```
/map version publish

System:
1. Mark current version as "published" in manifest
2. Copy current/ world directory to CloudNet template: ElytraRace/maps/sky-fortress/
3. Copy current config.json to CloudNet template
4. CloudNet picks up the template on next service start
```

The game server only sees the flat `maps/{name}/` directory — no version history.

### 3.9 Data Model

```java
// In shared/common (framework-agnostic)
enum MapVersionState { DRAFT, PUBLISHED, ARCHIVED }

record MapVersionEntry(
    int version, Instant createdAt, String createdBy,
    String label, MapVersionState state, Long archiveSize
) {}

record MapManifest(
    UUID mapId, Key name, String displayName, String author,
    String worldName, int publishedVersion,
    List<MapVersionEntry> versions
) {}

interface MapVersionService {
    MapManifest getManifest(String mapName);
    CompletableFuture<MapVersionEntry> saveVersion(String mapName, String createdBy, String label);
    CompletableFuture<MapManifest> publishVersion(String mapName);
    CompletableFuture<MapManifest> rollbackToVersion(String mapName, int version);
    CompletableFuture<Void> discardLatest(String mapName);
    CompletableFuture<Path> exportPublished(String mapName, Path targetDir);
}
```

### 3.10 Config-Only vs Full Rollback

Sometimes a builder only wants to change ring placement without touching the world:

```
/map version save-config "Adjusted ring #8 radius"
```

This saves ONLY the portal config as a new version entry (no tar.gz). On rollback, only the config is restored — the world stays as-is. This is much faster and covers the common case of ring adjustments.

Manifest entry has a `type` field: `"full"` or `"config-only"`.

---

## 4. Psychology-Driven Design Rationale

### 4.1 Preserving the FAWE Creative Flow
The FAWE PolyhedralRegion workflow is the core creative act — builders shape rings to match architecture. We protect this flow by removing everything around it that interrupts: no yes/no prompts, no manual indexing, no "another one?" questions. Just FAWE → `/portal` → FAWE → `/portal`.

### 4.2 Visual Feedback Loop
Particle preview of placed portals gives immediate spatial feedback. Builders see what they placed without having to test-fly. This closes the perception-action loop that flow state requires.

### 4.3 Safety Net
- **Undo** removes fear of mistakes during ring placement
- **Versioning** removes fear of breaking published maps
- **Draft/Published** separation means experiments never affect live servers
- **Config-only saves** encourage rapid ring iteration without touching the world
- **Rollback is automated** — no manual file management

### 4.4 Reduced Decision Fatigue
- Auto-index eliminates the "what number?" decision 50 times
- Auto-world eliminates the "which world?" question
- Auto-author eliminates the "who made this?" question
- GUI-based cup management replaces text-based cup assignment

---

## 5. Implementation Priority

| Priority | Feature | Impact | Effort |
|---|---|---|---|
| **P0** | `/portal` single-command (replace 6-step conversation) | Eliminates 80% of the pain | S |
| **P0** | Auto-index for portals | Removes manual numbering | S |
| **P1** | Portal particle preview (`/portal show`) | Visual feedback | M |
| **P1** | `/portal delete` + `/portal undo` | Safety net | M |
| **P1** | `/portal edit` (reload FAWE region) | Enables iteration | M |
| **P2** | `/portal testfly` with results | Validation | L |
| **P2** | `/map create` + `/cup create` as single commands | Streamline creation | S |
| **P2** | Map versioning (tar.gz snapshots) | Safe rollback | L |
| **P3** | `/portals` inventory GUI | Bulk management | L |
| **P3** | `/cup` inventory GUI | Cup organization | M |
| **P3** | `/map info` + validation | Quality assurance | M |
| **P4** | Config-only versioning | Fast ring iteration | M |
| **P4** | Path visualization | Map quality feedback | S |
| **P4** | Test-fly ghost path recording | Polish | M |

**P0 is a one-day change** that transforms the experience. Replace the 6-step conversation with a single command that reads the FAWE session.

---

## 6. Updated Open Questions

1. **Portal edit — FAWE region reload:** Can we reliably load vertices back into a `PolyhedralRegionSelector`? Need to verify FAWE API supports this.
2. **Particle rendering on Paper:** Paper has `World.spawnParticle()` — should we render ring outlines per-tick or use scheduled tasks? Performance with 50+ rings?
3. **Test-fly on Paper:** The game collision detection runs on Minestom. Should test-fly use a simplified Paper-based detection, or should builders test on a Minestom test server?
4. **GUI library:** Build minimal custom inventory GUI or use an existing library?
5. **Tar.gz performance:** For a large map, tar.gz creation could take 1-2 seconds. Run async? Show progress bar?
6. **Per-map directories:** Migrating from single `maps.json` to per-map directories is a breaking change. Migration strategy?
