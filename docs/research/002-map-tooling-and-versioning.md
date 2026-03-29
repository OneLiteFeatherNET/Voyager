# Research: Map Tooling, Versioning, and Hot-Reload Patterns

**Date:** 2026-03-29
**Researcher:** voyager-researcher
**Status:** Complete

## Executive Summary

This research covers five areas critical to Voyager's map management pipeline: (1) how major networks handle map setup, (2) version control strategies for Minecraft worlds, (3) FAWE-based workflows, (4) ring/checkpoint placement in similar plugins, and (5) hot-reload patterns for runtime map swapping. The key finding is that Voyager's existing FAWE + PolyhedralRegion approach for ring placement is already more sophisticated than most competing plugins. For versioning, a hybrid approach combining Polar snapshots with filesystem-level incremental backups is recommended over Git for binary world data.

---

## 1. Minecraft Map Editor UX Patterns (Major Networks)

### Findings

Major networks (Hypixel, GommeHD, Hive) keep their internal tooling confidential. However, observable patterns from public information and community knowledge reveal a consistent approach:

**Common Workflow Across Networks:**

| Step | Tool | Description |
|------|------|-------------|
| 1. Build | WorldEdit / FAWE | Builders create maps in creative mode with WE tools |
| 2. Configure | Custom internal plugins | Spawn points, boundaries, game-specific metadata |
| 3. Export | Schematic / World copy | Map saved as template or schematic |
| 4. Deploy | Cloud system (CloudNet / Pterodactyl) | Template pushed to dynamic server instances |
| 5. Test | Internal QA server | Builders verify in a staging environment |

**Hypixel-specific observations:**
- Uses internal "Layout Editor" tools (confirmed for Sheep Wars game mode)
- Maps are managed as templates deployed to dynamic game instances
- Build team works on dedicated creative servers, separate from production

**Hive-specific observations:**
- Developed "Chunker" - an open-source world conversion tool (desktop app + CLI)
- Focus on automated workflows and format conversion between editions
- Available at chunker.app

**GommeHD-specific observations:**
- German network using CloudNet for server management
- Template-based deployment similar to Hypixel pattern
- Maps stored as CloudNet templates with prefix-based organization

**Key Takeaway:** No network exposes their internal setup tooling publicly. The universal pattern is: build in creative with WE/FAWE, configure metadata via custom plugin, export as template, deploy via cloud system.

### Relevance to Voyager

Voyager's setup plugin already follows this pattern (conversation-based wizard with FAWE integration). The main improvement opportunity is in the deployment pipeline (CloudNet template management) and potentially a visual preview system.

---

## 2. Version Control for Minecraft Maps

### 2a. Git for Anvil World Files

**Verdict: Not recommended for direct use.**

| Aspect | Assessment |
|--------|------------|
| Feasibility | Technically possible but impractical |
| File type | Anvil `.mca` files are binary, opaque to git diff |
| Repository size | Worlds quickly reach GBs; git performance degrades |
| Diff quality | No meaningful diffs - every change is a binary blob |
| Git LFS | Reduces repo bloat but loses git's core advantage (diffing) |

**FastBack mod** (pcal43/fastback) demonstrates Git can work for backups:
- Stores incremental snapshots using git internally
- "Only saves the parts of your world that changed"
- Faster and smaller than ZIP-based backups
- But: uses git as a storage backend, not for collaborative version control

**Practical alternative: NBT-to-text conversion** for diffing metadata (JSON configs, map definitions) while keeping world data in a separate storage system.

### 2b. Snapshot/Backup Approaches

**Recommended: rsync with hard-link snapshots**

```bash
# Incremental snapshot that only stores changed files
rsync -a --delete --link-dest=../snapshot.1 world/ snapshot.0/
```

| Approach | Pros | Cons |
|----------|------|------|
| rsync + hard links | Space-efficient, fast, point-in-time restore | Requires filesystem support, no remote |
| BTFU mod | Automatic every 5 minutes, logarithmic pruning | Mod dependency, Forge/Fabric only |
| ZIP snapshots | Simple, portable | Slow for large worlds, full copy each time |
| ZFS/Btrfs snapshots | Near-instant, copy-on-write | Requires specific filesystem |
| Database-tracked metadata + file snapshots | Best of both worlds | More complex to implement |

**Best practice for Voyager:** Store map metadata (rings, spawns, cup assignments) in the database with version numbers. Store world files as named snapshots on disk (`maps/skyrace/v1/`, `maps/skyrace/v2/`). This separates concerns cleanly.

### 2c. Polar Format for Lightweight Map Versioning

**Polar** (hollow-cube/polar) is highly relevant for Voyager's Minestom migration:

| Feature | Detail |
|---------|--------|
| Format | Single binary file per world, Zstd compressed |
| Speed | ~9x faster loading than Anvil (0.066s vs 0.564s for 10x10 chunks) |
| Size | 105KB compressed for a 10x10 lobby (vs 13MB Anvil) |
| API | `PolarLoader` implements Minestom's `ChunkLoader` interface |
| Conversion | `AnvilPolar.anvilToPolar(Path)` for Anvil-to-Polar |
| Custom data | `PolarWorldAccess` for per-chunk metadata |
| Limitation | Single file = no random chunk access, bad for large worlds |

**Versioning with Polar:**
```java
// Convert current Anvil world to versioned Polar snapshot
var polar = AnvilPolar.anvilToPolar(Path.of("worlds/skyrace"));
var bytes = PolarWriter.write(polar);
Files.write(Path.of("maps/skyrace/v3.polar"), bytes);

// Load specific version into Minestom instance
instance.setChunkLoader(new PolarLoader(Path.of("maps/skyrace/v3.polar")));
```

Since Polar files are small single files, they are practical to:
- Store multiple versions side by side (v1.polar, v2.polar, ...)
- Include in CloudNet templates
- Even track in Git LFS if needed (105KB per version is manageable)
- Copy/transfer between servers quickly

**Important caveat:** Voyager decided on Anvil format (approved decision). Polar could still be used as a snapshot/deployment format while Anvil remains the authoring format on the Paper setup server.

### 2d. CloudNet Template Versioning

CloudNet v4 templates provide deployment-level versioning:

```json
{
  "templates": [
    {
      "prefix": "ElytraRace",
      "name": "skyrace-v3",
      "storage": "local",
      "alwaysCopyToStaticServices": false
    }
  ]
}
```

| Feature | Detail |
|---------|--------|
| Template structure | `prefix/name` organization (e.g., `ElytraRace/skyrace-v3`) |
| Storage | Local filesystem or SFTP (FTP removed in v4) |
| Deployment | Templates copied to services on start |
| Rollback | Change task config to point to previous template name |
| Limitations | No built-in versioning history, manual naming required |

**Recommended pattern for Voyager:**
```
templates/
  ElytraRace/
    base/           # Server JAR, configs, shared resources
    skyrace-v1/     # Map version 1 (world files only)
    skyrace-v2/     # Map version 2
    skyrace-current -> skyrace-v2  # Symlink to active version
```

Tasks reference multiple templates (base + map), allowing independent map version updates without redeploying the entire server template.

---

## 3. FAWE-Based Setup Workflows

### Current Voyager Implementation

Voyager already uses FAWE's `PolyhedralRegionSelector` for ring/portal definition:

1. Builder selects FAWE region (polyhedral with 4+ vertices)
2. `PortalSelectedFinish` validates the region type and vertex count
3. `PortalSavePrompt` extracts vertices and center from `PolyhedralRegion`
4. Vertices stored as `LocationDTO` list in `FilePortalDTO`
5. Map JSON updated via `MapService`

### FAWE Schematic API for Map Versioning

FAWE's clipboard/schematic system can complement map versioning:

```java
// Save a region as schematic snapshot
CuboidRegion region = new CuboidRegion(min, max);
BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
ForwardExtentCopy copy = new ForwardExtentCopy(world, region, clipboard, region.getMinimumPoint());
copy.setCopyingEntities(true);
Operations.complete(copy);

try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC
        .getWriter(new FileOutputStream(file))) {
    writer.write(clipboard);
}

// Load schematic back
ClipboardFormat format = ClipboardFormats.findByFile(file);
try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
    Clipboard clipboard = reader.read();
}

// Paste to world
try (EditSession editSession = WorldEdit.getInstance()
        .getEditSessionFactory().getEditSession(world, -1)) {
    Operation op = new ClipboardHolder(clipboard)
            .createPaste(editSession)
            .to(BlockVector3.at(x, y, z))
            .ignoreAirBlocks(false)
            .build();
    Operations.complete(op);
}
```

**Use cases for Voyager:**
- Save individual ring structures as schematics for reuse across maps
- Create "ring template library" (small ring, large ring, fancy ring, etc.)
- Snapshot entire map regions during setup for undo/redo
- Export map sections for sharing between setup servers

**Limitation:** Schematics are region-based, not world-based. For full world versioning, use Anvil copies or Polar snapshots instead.

---

## 4. Ring Placement Tools in Similar Games

### Existing Plugins Comparison

| Plugin | Ring Definition | Setup Method | Ring Type |
|--------|----------------|--------------|-----------|
| **Voyager (current)** | FAWE PolyhedralRegion (4+ vertices) | Conversation wizard + FAWE selection | 3D polyhedral volumes |
| **ElytraRacing** | WorldGuard regions (sequential naming) | `/ermap` commands, region import | WorldGuard cuboid/poly regions |
| **ElytraRace (Kartik)** | WorldGuard regions | `/er setup addring` + WorldGuard import | Named WG regions (ring1, ring2, ...) |
| **Elytra Parkour** | Particle rings (center + radius) | `/ElytraParkour createring <map> <radius> <number>` | Circular particle rings |
| **Lineation** | Checkpoint regions | Command-based setup | Region checkpoints |

### Key Observations

**ElytraRacing (most popular, now discontinued):**
- `/ermap` command for map configuration without editing config files
- Sequential ring ordering enforced by anti-cheat
- WorldGuard integration for auto-importing regions named `ring1`, `ring2`, etc.

**Elytra Parkour (simplest approach):**
- `/ElytraParkour createring <mapname> <radius> <ringNumber>` - creates ring at player position
- `/ElytraParkour testring <radius>` - preview ring with particles before committing
- Rings are purely particle-based (no physical blocks) - center point + radius
- Particle locations cached on startup for performance

**Voyager's advantage:** The PolyhedralRegion approach allows irregular ring shapes (not just circles), which is more flexible for creative map design. Most competitors use simple circles or axis-aligned cuboids.

### Recommended Improvements for Voyager

1. **Ring preview command** - Show particle outline of the ring before saving (like Elytra Parkour's `/testring`)
2. **Ring template system** - Pre-defined ring shapes (circle, oval, diamond) that can be placed at player position + rotation
3. **Sequential ordering UI** - Visual display of ring order with easy reordering
4. **Ring cloning** - Copy a ring and place it at a new position with offset

---

## 5. Hot-Reload Patterns for Minecraft Maps

### Minestom Instance-Based Map Swapping

Minestom's architecture makes map swapping straightforward compared to Bukkit/Paper:

```java
InstanceManager instanceManager = MinecraftServer.getInstanceManager();

// Pattern 1: Create new instance, teleport players, destroy old
InstanceContainer newInstance = instanceManager.createInstanceContainer();
newInstance.setChunkLoader(new AnvilLoader("worlds/skyrace-v2"));
// or: newInstance.setChunkLoader(new PolarLoader(Path.of("maps/skyrace-v2.polar")));

for (Player player : oldInstance.getPlayers()) {
    player.setInstance(newInstance, spawnPosition);
}

// Unload old instance
instanceManager.unregisterInstance(oldInstance);
```

```java
// Pattern 2: SharedInstance for zero-copy arenas
InstanceContainer template = instanceManager.createInstanceContainer();
template.setChunkLoader(new AnvilLoader("worlds/skyrace"));

// Each game gets its own SharedInstance (shared blocks, separate entities)
SharedInstance game1 = instanceManager.createSharedInstance(template);
SharedInstance game2 = instanceManager.createSharedInstance(template);
// Players in game1 and game2 see same blocks but different entities
```

### Hot-Reload Strategy Comparison

| Strategy | Downtime | Complexity | Data Safety |
|----------|----------|------------|-------------|
| New instance + teleport | ~1s (chunk loading) | Low | High - old instance intact until verified |
| Replace ChunkLoader on existing instance | None for unloaded chunks | Medium | Medium - loaded chunks stay in memory |
| SharedInstance rotation | ~1s | Low | High - template unchanged |
| CloudNet service cycling | 5-30s | Low (infra-level) | High - new service from template |
| Pre-load + atomic swap | Near-zero | High | High - both instances live simultaneously |

### Recommended Pattern for Voyager

**Pre-load + atomic swap** for seamless map transitions during cup flow:

```java
public class MapSwapService {
    private final Map<String, InstanceContainer> preloadedMaps = new ConcurrentHashMap<>();

    // Pre-load next map during current game phase
    public CompletableFuture<InstanceContainer> preloadMap(String mapName) {
        return CompletableFuture.supplyAsync(() -> {
            InstanceContainer instance = instanceManager.createInstanceContainer();
            instance.setChunkLoader(new AnvilLoader("worlds/" + mapName));
            // Force-load spawn chunks
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    instance.loadChunk(x, z).join();
                }
            }
            preloadedMaps.put(mapName, instance);
            return instance;
        });
    }

    // Swap players to pre-loaded instance (near-instant)
    public void swapToPreloaded(String mapName, Collection<Player> players, Pos spawn) {
        InstanceContainer target = preloadedMaps.remove(mapName);
        for (Player player : players) {
            player.setInstance(target, spawn);
        }
    }
}
```

This aligns with Voyager's cup flow: while players are in the "End" phase of map N, pre-load map N+1 in the background. Transition is near-instant when the next game phase starts.

---

## Sources

- [Polar GitHub Repository](https://github.com/hollow-cube/polar) -- Format specification, benchmarks, API examples
- [Polar FORMAT.md](https://github.com/hollow-cube/polar/blob/main/FORMAT.md) -- Binary format specification
- [FastBack (Git-based backups)](https://github.com/pcal43/fastback) -- Git as backup storage for Minecraft worlds
- [Minecraft Forum: Worlds and Git](https://www.minecraftforum.net/forums/minecraft-java-edition/discussion/117516-worlds-and-git) -- Community discussion on git for world files
- [Anvil file format - Minecraft Wiki](https://minecraft.wiki/w/Anvil_file_format) -- Anvil format reference
- [ElytraRacing GitHub](https://github.com/ElytraRacing/ElytraRacing) -- Competing elytra racing plugin (discontinued)
- [ElytraRace (Kartik-Fulara)](https://github.com/Kartik-Fulara/ElytraRace) -- Competing plugin with WorldGuard integration
- [Elytra Parkour (SpigotMC)](https://www.spigotmc.org/resources/%E2%9C%A6-elytra-parkour-%E2%9C%A6-create-parkour-courses-for-players-to-fly-through-%E2%9C%A6.63784/) -- Particle-based ring system
- [WorldEdit Schematic API](https://madelinemiller.dev/blog/how-to-load-and-save-schematics-with-the-worldedit-api/) -- Clipboard/schematic code examples
- [CloudNet Tasks Documentation](https://cloudnetservice.eu/docs/next/components/tasks/) -- Template and deployment configuration
- [Minestom Instances Documentation](https://minestom.net/docs/world/instances) -- Instance management API
- [Minestom AnvilLoader Documentation](https://minestom.net/docs/world/anvilloader) -- World loading in Minestom
- [Minestom Chunk Management](https://minestom.net/docs/world/chunk-management) -- Chunk lifecycle
- [Hypixel Developer Update Jan 2025](https://hypixel.net/threads/minigame-developer-update-january-2025.5794198/) -- Internal tooling hints
- [Chunker by Hive](https://www.minecraft.net/en-us/creator/tools) -- Open-source world conversion tool
- [BTFU Backup Mod](https://www.curseforge.com/minecraft/mc-mods/btfu-continuous-rsync-incremental-backup) -- Incremental rsync-based backups
- [rsync Snapshot Backups](http://www.mikerubel.org/computers/rsync_snapshots/) -- Hard-link snapshot technique

## Open Questions

- **Polar + Anvil hybrid:** Can the setup server (Paper/FAWE) export to Polar for deployment while keeping Anvil as the authoring format? Needs testing of `AnvilPolar.anvilToPolar()` with typical Voyager map sizes.
- **CloudNet template size limits:** Are there practical limits on template count or total size for CloudNet v4 local storage?
- **FAWE on Minestom:** FAWE only runs on Paper. If the setup plugin stays on Paper (confirmed), this is fine. But if any setup functionality moves to Minestom in the future, an alternative region selection approach would be needed.
- **SharedInstance block mutability:** SharedInstances share blocks with their parent -- does this cause issues if a racing game needs to modify blocks (e.g., ring activation effects)?

## Recommendations

1. **Keep Anvil as authoring format** on Paper setup server (already decided). Consider Polar as the deployment/snapshot format for the Minestom game server -- 9x faster loading and tiny file sizes are ideal for racing maps.

2. **Version maps with numbered directories**, not Git. Use the pattern `maps/<mapname>/v<N>/` with a metadata JSON tracking the active version. Store map configuration (rings, spawns) in the database with version references.

3. **Use CloudNet multi-template tasks** for deployment: one base template (server JAR + configs) plus per-map templates. Update maps independently by swapping template names.

4. **Implement pre-load + atomic swap** for cup transitions. During the End phase of the current map, pre-load the next map's instance in the background. Player teleport on phase transition is near-instant.

5. **Add a ring preview command** to the setup plugin. Show particle outlines of the PolyhedralRegion before saving -- this is the most-requested UX feature in competing plugins.

6. **Consider a ring template library** using FAWE schematics. Pre-built ring shapes that builders can place and adjust, rather than manually selecting polyhedral regions every time.
