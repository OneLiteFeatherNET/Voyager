# Simpler Map and Cup Setup: Reducing Builder Actions from Empty World to Playable Cup

**Authors:** Voyager Development Team | **Date:** 2026-10-09 | **Status:** Draft | **Version:** 1.0

**Research input for:** E6.0 (not approved; E6 planning requires project owner approval, see Section 7.2)
**Prior work:** [1] and [2], both dated 2026-03-29 and both numbered 002.

## Abstract

Building a playable Voyager cup is slow and fragile. For the 35-ring sample map, the setup plugin of the tree being replaced needs about 354 in-game actions, of which 350 are ring placements at 10 actions per ring. Every catalog change also needs a manual world copy and a server restart. The rebuild's JSON model stores ten values per ring, seven of them geometric; it names each map three ways (map id, world string, folder), and it aborts boot when any cup is malformed. We ran 20 investigations (10 internal code investigations, 10 external research investigations) and verified seven claims against the repository. The findings fall into six themes: authoring model, in-game user experience, data model and storage, cups, pipeline, and validation. The strongest candidate is pose-based ring placement with a wand. The rebuild's disc-shaped ring model admits it directly. It reduces ring placement to one action per ring and gives an estimated 42 actions for the sample map, about 8 times fewer than today. A record-one-lap alternative needs an estimated 9 actions. Seven changes on the rebuild's existing data model and pipeline do not depend on the blocked E6 epic. The setup slice and the replacement for FastAsyncWorldEdit (E6.0) need the project owner's decision on the authoring model and on the Polar storage question. All action counts are estimates from a counting model. No builder study has been run, so no timing result is claimed.

## 1. Introduction

### 1.1 Problem

Map and cup authoring is the bottleneck between a finished world and a flyable cup. Table 1 quantifies the problem from the investigations. Each row names its source and its evidence grade.

**Table 1.** Quantified problems in the current map and cup setup.

| ID | Problem | Measure | Source | Evidence |
|---|---|---|---|---|
| P1 | Ring placement dominates authoring | 350 in-game actions for 35 rings with 8 vertices each (2+V actions per ring) | R01 (PortalCommand.java:38-129) | Code, file:line |
| P2 | Ring records are verbose | 10 JSON values per ring, 7 geometric (center 3, normal 3, radius 1) | R05 (RingAdapter.java:34-48) | Code, file:line |
| P3 | One map has three names | Map id, world string and folder name must agree by hand | R07 (MapDefinition.java:35) | Code, verified |
| P4 | Worlds are copied by hand | Folder copy into the worlds path; no script | R07 (docs/guides/production-setup.md:73-88, old tree) | Code and docs |
| P5 | Any catalog change needs a restart | Catalogs are read once at boot; no reload | R04 (JsonMapCatalog.java:50, JsonCupCatalog.java:17,40) | Code, file:line |
| P6 | One bad cup blocks boot | Every cup in the directory is checked at boot | R03; CatalogConsistency.java:40-54 | Code, verified |
| P7 | Old setup plugin is partly dead and risks data loss | 17 conversation prompt classes unreachable; updateMap deletes files before the async re-save (MapServiceImpl.java:82-96) | R01 | Code, file:line, not reproduced |
| P8 | E6 is blocked and the alpha timing conflicts | E6.0 gates E6.1 and later; open P0 issue #120 needs a cup and map data package | R08; greenfield-epics.md:1498-1562 (verified) | Docs; issue state is a snapshot, not re-checked |
| P9 | No builder-experience requirement exists | "Map-Builder" role at line 67; NFR-007 at line 99; no test-fly, progress, or playtest requirement | R20; anforderungen-voyager-rebuild.md:67,99 (verified) | Docs, verified |
| P10 | Validate-and-exit mode is specified; implemented by `add-catalog-validate-task` | NFR-007 requires `-Dvoyager.config.check=true` and `ConfigProblem`; neither occurs in source | Repository search, verified | Docs versus code |
| P11 | Unresolved-cup hint names the wrong switch | Hint says `-DVOYAGER_CUP`; the Gradle path uses `-Pcup` | R03; UnresolvedCupException.java:85-88 | Code, file:line, not re-checked |

### 1.2 Research Questions

- **RQ1.** Which authoring model reduces builder actions for rings without dropping the geometry the game needs?
- **RQ2.** Which in-game affordances are feasible on Minestom 26.2 without a resource pack, and which need a spike first?
- **RQ3.** Which data-model and pipeline changes can ship on the rebuild now, independent of E6?

### 1.3 Scope

The scope is the authoring path from an empty world to a published, playable cup. Terrain building is out of scope because it happens in the builder's own editor. Racing-line derivation from terrain collision is out of scope for this cycle. Changes to the tree being replaced are out of scope, because `CLAUDE.md` states that the old tree gets no new features.

## 2. Related Work

### 2.1 Voyager Prior Work

Two documents dated 2026-03-29 both carry the number 002. This report takes 005.

- **[1] Map tooling and versioning.** It recommends numbered map directories, Polar as a deployment format, and pre-load with atomic swap for cup transitions. It also recommends storing map metadata in a database. R09 and the rebuild's file-based catalogs moved away from the database recommendation. Its premises are Paper and FAWE, which the greenfield design replaces (decision D6, greenfield design, decision table).
- **[2] Builder setup UX and versioning.** It proposes a single `/portal` command, auto-indexing, particle previews, test-fly, and tar.gz versioning. It keeps FAWE PolyhedralRegion shapes and bans Polar in its constraints. Two conflicts with the rebuild follow. First, the rebuild drops Paper (D6). Second, the rebuild's ring is a disc with center, normal and radius (Ring.java:14), so polyhedral shapes have no representation in the model. "Keep FAWE for ring shaping" therefore conflicts with the rebuild model. Its P0 items target the tree being replaced, which gets no new features.

### 2.2 Competing and Adjacent Tools

| Tool or pattern | Authoring pattern | Relevance | Source, tier |
|---|---|---|---|
| Ely-sky (Paper 26.2) | Left-click places a ring at the current position and facing; revisions archive old times | Pose placement and revisions | R12; [11] T4 |
| IceBoatRacing | Shovel selection; `autotrace start` drives one lap, then a preview is accepted | Record-one-lap generation | R12; [12] T4 |
| ElytraRace (WorldGuard) | Rings from WorldGuard zones | Region-based capture | R12; [13] T4 |
| SkyWarsReloaded | Ordered prerequisites; save separate from enable | Draft versus published | R11; [16] T4 |
| New Bedwars | Status command lists configured and missing items | Status checklist | R11; [14] T4 |
| Parkour | Unfinished courses hidden from players | Publish gate | R11; [15] T4 |
| Hollow Cube MapMaker | Builder menu, collaborators, map type, publish, quality rating; MIT code, CC BY-NC art | Creator workflow | R14; [17], [18] medium |
| Trackmania campaigns and playlists | Ordered lists are separate from playlist sources | Cup versus selection | R19; [30] low-medium |
| TGM and MapCycle | Rotation lists map names; cups reference maps, never copy | Reference by id | R19; [31] low-medium |
| Fortnite race checkpoint | Ordered checkpoints; gaps rejected before publish | Validation rules | R18; [28] T2 |
| Trackmania author medal | A mandatory reference run gives the par time and proves flyability | Par time | R18; [27] medium-low |

### 2.3 Formats and Tooling

Polar is a single-file world format with a Minestom loader. Its documentation and the R13 review report no release since the last commit on 2026-06-13, and Modrinth lists versions 26.3 to 1.21.11 (R15), so 26.2 support is unverified [19]. JSON Schema validators exist for Java (networknt, drafts up to 2020-12) [20], and a schema generator for records is untested (victools) [21]. Minestom ships display, interaction, and dialog metadata types [22].

## 3. Methodology

### 3.1 Approach

The 20 investigations were run as parallel angles. R01 to R10 are internal code investigations with file:line evidence. R11 to R20 are external research with URLs and stated confidence. R20 is a psychology analysis of the requirements document and cites theories by name; it has no URLs. The orchestrating session read all 20 summaries and the prior documents. It then verified seven claims directly against the repository (Section 3.4). Conflicts between summaries and documents are reported, not silently merged.

No investigation measured builder behavior. All time and action values are model estimates.

### 3.2 Investigation Register

**Table 2.** The 20 investigations.

| ID | Angle | Type | Confidence as stated | Used in |
|---|---|---|---|---|
| R01 | Current setup plugin: flow, action cost, friction | Internal, code | file:line cited | 1, 4.5, 5 |
| R02 | Map format and schema pain points | Internal, code | file:line cited | 4.3 |
| R03 | Cup format, selection, boot validation | Internal, code | file:line cited | 4.4 |
| R04 | Data pipeline, restart, reload | Internal, code | file:line cited | 4.5 |
| R05 | Ring model, capture, author burden | Internal, code | file:line cited | 4.1 |
| R06 | Racing line and guide points | Internal, code | file:line cited | 4.1 |
| R07 | World lifecycle and naming | Internal, code | file:line cited | 4.3 |
| R08 | Requirements, epics, issues | Internal, docs | Docs and issue snapshot | 1, 6.2 |
| R09 | Persistence: files versus database | Internal, code | file:line cited | 4.3 |
| R10 | Trace recorder and simulator | Internal, code | file:line cited | 4.6 |
| R11 | Minigame setup UX patterns | External | Medium; community sources T4 | 4.2 |
| R12 | Racing plugin authoring | External | Medium-high | 4.1, 4.2 |
| R13 | Minestom editing tooling | External | Medium | 4.3 |
| R14 | Hollow Cube MapMaker | External | Medium features, low internals | 4.2 |
| R15 | Formats and JSON Schema | External | Medium; versions to verify | 4.3 |
| R16 | In-game authoring UX on Minestom | External | Medium; existence T1 | 4.2 |
| R17 | Path recording to rings and racing line | External | Medium; parameters T6 | 4.1 |
| R18 | Track editors | External | Medium-low | 4.1, 4.2 |
| R19 | Cup and playlist management | External | Low-medium | 4.4 |
| R20 | Creator UX psychology | External, theory plus internal doc | Not stated; treated as T6 | 4.2, 5, 6 |

### 3.3 Confidence Tiers

The summaries use T-tiers without defining them in the working set. The reading below is an interpretation, not a project standard. T1 is a primary source, such as a code line or an official API declaration. T2 is official vendor documentation. T4 is a third-party listing or community page. T6 is a parameter, heuristic, or unverified assumption. T3 and T5 do not occur in the inputs.

### 3.4 Verification Performed for This Report

Sources are [9] and [10] in Section 8.2, plus the project documents listed there.

| Check | Result |
|---|---|
| `CatalogConsistency.requireEveryCupMapResolves` iterates over `cups.cupNames()` and fails on any unresolved map (CatalogConsistency.java:40-54) | Confirmed (R03) |
| `MapDefinition` rejects rings whose index differs from list position (MapDefinition.java:49-53) | Confirmed (R02, R05) |
| `GuidePoint` rejects order indices on a ring slot; `afterRing()` uses `floorDiv` by stride 100 (GuidePoint.java:41-58) | Confirmed (R02, R06) |
| `Ring` record fields are index, center, normal, radius, points, type (Ring.java:14) | Confirmed (R05) |
| NFR-007 specifies `-Dvoyager.config.check` and `ConfigProblem`; neither occurs in Java sources | Confirmed; docs only |
| E6.0 acceptance criteria require a FAWE inventory and an owner decision (greenfield-epics.md:1544-1559) | Confirmed |
| Decisions D6 (Paper dropped) and D10 (avaje-inject) | Confirmed (greenfield design) |

Not re-checked: the individual file:line citations of R01, R04, R07, R09, R10 and R11 to R19. The issue state of #120 was not re-checked against the tracker.

### 3.5 Action Count Definition

One action is one chat command or one click that the builder performs. The count excludes terrain building, flight time, waiting, and retries. The counting model has one ring per placement and 35 rings with 8 vertices for the sample map, taken from R01. It is an estimate, not a measurement.

## 4. Implementation: Findings and Design Decisions by Theme

Each finding carries an evidence label. **Verified** means the claim was checked in this report. **Reported** means a summary cites file:line or a URL that was not re-checked. **Inference** means this report's own reasoning from the evidence.

### 4.1 (a) Authoring Model

| ID | Finding | Evidence | Label | Confidence |
|---|---|---|---|---|
| A1 | Ring placement is the cost driver. Today each ring takes 2+V actions: select, one click per vertex, then the portal command. | R01 (PortalCommand.java:38-129) | Reported | High |
| A2 | The rebuild ring is a disc. The hit test checks plane crossing and distance within the radius. Direction and normal sign are not checked at runtime. Community reports on gate hitbox direction agree that direction matters for players (MultiGP, [29]). | R05 (RingPass.java:33-45), Ring.java:14 [9] | Reported, Ring verified | High |
| A3 | Pose-based click-to-place maps directly onto the disc model. The author stands at the ring center facing the flight direction and clicks once. The radius defaults and can be adjusted. | R05 idea 1; R12 Ely-sky (T4) | Inference | Medium-high |
| A4 | Fly-through capture uses the segment-plane crossing for center and direction, and crossing order for the index. It costs flight time, not actions. | R05 idea 3; RingPass.crosses | Inference, not prototyped | Medium |
| A5 | Record-one-lap generation: resample, smooth, align laps, simplify, fit a centripetal Catmull-Rom curve [25], place gates every 20 to 40 blocks, then validate coverage. Prior art: IceBoatRacing autotrace (T4), demonstration flights as gate priors [23], and trajectory generation from tracking [24]. Pitfalls: smoothing shaves apexes; figure-eights need arc-length matching; closed laps need a cyclic shift. | R17 (parameters T6); R12 (T4) | Reported | Medium |
| A6 | The racing line is computed from ring centers by centripetal Catmull-Rom. Zero guide points is legal. Guides are hand-placed in legacy `guides.json`; deriving them needs terrain collision. | R06 (RacingLine.java:66-83; CatmullRom.java:46) | Reported | High (code) |

Recommendation for the authoring model: pose placement is the default. Fly-through and lap generation are later options. Polyhedral ring shapes are not in the model and would need a decision of their own.

### 4.2 (b) In-Game User Experience

| ID | Finding | Evidence | Label | Confidence |
|---|---|---|---|---|
| B1 | A wand as a vanilla item with name and lore needs no resource pack. Minestom text, item, and block display metadata exist (T1). Interaction handles have width, height, and response setters (T1). Transform setters are unconfirmed. | R16 (minecraft.wiki Display, Interaction) [22] | Reported | Medium; transforms need spike X2 |
| B2 | Dialogs: Minestom has a sealed `Dialog` hierarchy and `ShowDialogPacket`. The build-and-send path and server-side click handling are unverified. | R13, R16 [22] | Reported | Medium-low; spike X3 |
| B3 | A persistent status checklist, such as "spawn set, 35/35 rings, 0 errors, 2 warnings", follows the New Bedwars and SkyWarsReloaded pattern. The old wizard's boss bar never progressed (R01, WizardManager.advance has no callers). | R11 (T4); R20 #2; R01 | Reported | Medium |
| B4 | Draft and published states: unfinished courses are hidden from play (Parkour, T4). Saving and enabling are separate steps (SkyWarsReloaded, T4). Editing a published course creates a revision and archives the old times (Ely-sky, T4). | R11 #2, #8; R12 | Reported | Medium |
| B5 | Test-fly from the editor, placed in the air in under 10 seconds, is ranked first by the creator-UX analysis. The old tree has a test-fly command (R01 "keep"). The rebuild has none. The 10-second target is a heuristic. | R20 #1 (T6); R01 | Reported | Medium |
| B6 | Undo in the old tree is in-memory, capped at 20 entries, wiped on quit, and pushed before the save succeeds. The rebuild needs persisted, bounded undo. | R01 (UndoStack.java:12; PortalCommand.java:112) | Reported; old code | High (code) |

### 4.3 (c) Data Model and Storage

| ID | Finding | Evidence | Label | Confidence |
|---|---|---|---|---|
| C1 | One folder per map (`maps/<id>/map.json` plus `world/`), with the world name derived from the id, removes the three-way naming. | R07; R15; MapDefinition.java:42-44 requires a non-blank world | Reported, world check verified | Medium-high |
| C2 | Ring index is redundant. Position in the list is the index, and the record enforces it. | MapDefinition.java:49-53 (verified) | Verified | High |
| C3 | Add `schemaVersion` and a JSON Schema referenced by `$schema`. The shipped map has no version field (R02). `JsonFields` makes every field required (JsonFields.java:230-236). The author rejected silent defaults (MapDefinitionAdapter.java:80-84). Recommendation: optional fields get explicit defaults that the validator reports, never silent ones. | R02; R15 (editor support T6) [20][21] | Reported | Medium |
| C4 | The guide `orderIndex` stride of 100 is deliberate and documented. Changing it renumbers committed data (GuidePoint.java:13-39). Ring-relative anchors would change the schema; `afterRing()` already derives the ring. | GuidePoint.java (verified); R02; R06 | Verified | High |
| C5 | Keep the catalog as files in git, which gives diff, review, and portability. Use the database only for player data. Keep string ids as join keys. | R09 (JsonCupCatalog.java:39-40); ADR-0003:66 | Reported | High (code) |
| C6 | Polar is contested. It offers single-file worlds with per-chunk user data and a `PolarLoader` (R13, R14). Its maintenance and 26.2 support are unverified (R13, R15). [2] bans it; [1] recommends it for deployment. | R13; R14; R15; [1]; [2] [19] | Contested | Medium; spike X4 |
| C7 | Marker entities are fragile. Keep blocks in the world and metadata in a sidecar file. | R15 (T6 reasoning) | Reported | Medium |

### 4.4 (d) Cups

| ID | Finding | Evidence | Label | Confidence |
|---|---|---|---|---|
| D1 | Cups reference maps by string id. A typo surfaces only at boot. | R03; CatalogConsistency.java:40-54 (verified) | Verified | High |
| D2 | Validate the selected cup fully and report other cups as warnings. Selection today is `-DVOYAGER_CUP` or `-Pcup`. An absent setting works only when one cup exists, and otherwise fails. | R03 (ServerSettings.java:59-60,116; CupResolution.java:39-54); R04 (CupResolution.java:43-53) | Reported | High (code) |
| D3 | Mode is required today. A default of `RACE` removes a required field. | R03 | Reported | High (code) |
| D4 | Keep an ordered `mapNames` list as the cup definition. Optional `select{tags, exclude, count}` may be generated at load, and explicit ids win. | R19; datapack tags [32]; TGM [31] | Reported | Low-medium |
| D5 | Admin commands (`/cup create`, `add`, `remove`, `reorder`, `validate`) write the same JSON file. A GUI is deferred. | R19; [2] section 2.3 | Reported | Medium |

### 4.5 (e) Pipeline

| ID | Finding | Evidence | Label | Confidence |
|---|---|---|---|---|
| E1 | There is no reload. A restart is needed for any change. Watch `maps/` and `cups/`, and apply changes only at round boundaries. | R04 (JsonMapCatalog.java:50; JsonCupCatalog.java:17,40); R20 #11 | Reported; proposal is Inference | High (code); Medium (proposal) |
| E2 | Copying does not prune. Deleted maps still load. | R04 | Reported | High (code) |
| E3 | Implemented by `add-catalog-validate-task`. The validate task was specified but not built. NFR-007 requires all configuration errors in one report and a validate-and-exit mode. Neither exists in source. | anforderungen-voyager-rebuild.md:99 (verified); P10 | Verified | High |
| E4 | There are duplicate data sources: the legacy converter, the resources catalog, and the copy into `run/run/data` by `prepareRunData` (build.gradle.kts:62-71). A single source of truth is the target. | R04 | Reported | High (code) |
| E5 | The rebuild already reports all cup-to-map problems in one exception (CatalogConsistency.java:41-52). Per-file errors are wrapped as "`<file>` is not a valid definition" (CatalogDirectory.java:103,109-114), which names the file but not the ring. | R02; R03 (verified for CatalogConsistency) | Verified in part | High |

### 4.6 (f) Validation and Par Time

| ID | Finding | Evidence | Label | Confidence |
|---|---|---|---|---|
| F1 | `ElytraSimulator.tick` is pure and usable offline (ElytraSimulator.java:166). The live `FlightTickDriver` already emits a per-tick stream (FlightTickDriver.java:86-93). A recorded lap can be replayed headlessly. The collision space built from map blocks outside Minestom is unverified. | R10 | Reported; replay is Inference | Medium |
| F2 | `referenceTime` is required and must be positive (MapDefinition.java:54-56). A simulator-derived par time is a candidate initial value, as the Trackmania author medal shows [27]. | MapDefinition.java (verified); R09 (ADR-0003:66); R18 | Verified; par source is Inference | Medium |
| F3 | Design lint warnings are proposed: ring spacing above 3 s, dead zone above 5 s, first ring more than 30 s away, no finishable route for a first-time player. These are heuristics (T6) and have not been validated for this game. | R20 #6 (T6) | Reported | Low |
| F4 | Flight is client-authoritative for normal elytra movement (CLAUDE.md). A replay is therefore a reconstruction, not proof. The validation gate must accept simulator estimates within a stated tolerance. | CLAUDE.md, Elytra velocity authority; R10 risk | Verified | High |

## 5. Evaluation

### 5.1 Recommended Target Workflow

**Assumptions.** The builder has built the terrain in a separate editor. The setup server is Minestom. The sample map has 35 rings.

1. Run `/map new skyfortress`. This creates `maps/skyfortress/` with a `map.json` skeleton and a `world/` folder from a void template, and grants the wand. *Actions: 1.*
2. Walk to the start and run `/map spawn`. *Actions: 1.*
3. For each ring, stand at the ring center facing the flight direction and right-click the wand. The index is assigned automatically, the radius takes the map default, and a Display preview appears. *Actions: 35.*
4. Optional: run `/map radius <n>` for a ring that is wrong. *Actions: 0 by default.*
5. The status checklist stays in view, for example "spawn set, 35/35 rings, 0 errors, 2 warnings". *Actions: 0.*
6. Run `/map validate`. Errors name the exact ring index or the cup. *Actions: 1.*
7. Run `/map testfly`. The flight takes time but no further actions. A per-ring pass or miss report follows. *Actions: 1.*
8. Run `/cup create skycup "Sky Cup"`. *Actions: 1.*
9. Run `/cup add skyfortress`. *Actions: 1.*
10. Run `/map publish`. The gate refuses publication and lists every failing slot if validation fails. The map becomes available to the cup without a restart. *Actions: 1.*

**Lap-generation alternative.** Steps 3 and 4 are replaced by `/map record start`, one lap of flight, and `/map record accept` after the preview. The total is 9 actions for a first preview that is accepted.

**Table 3.** Action counts for the 35-ring sample map.

| Metric | Today (R01, tree being replaced) | Target, pose placement | Target, lap generation |
|---|---|---|---|
| In-game actions | 354 | 42 | 9 |
| Ring-related actions | 350 (10 per ring) | 35 (1 per ring) | 2 (record start and accept) |
| Manual file operations | 1 folder copy | 0 | 0 |
| Server restarts per catalog change | 1 | 0 | 0 |
| Hand-typed values per ring | 10 (7 geometric) | 0 required; the type is optional | 0 |
| Ratio to today (in-game actions) | 1 | 1/8.4 | 1/39 |

Today's total is the sum of `/elytrarace setup` (1), `cup create` (1), `map load` (1), `map create` (1) and 35 rings of 10 actions each (350), giving 354. The target total is 42 with steps 1 to 10 above. The ratios follow from these totals and are estimates. The lap path depends on the gate count the generator produces, which may differ from 35.

**Table 4.** Mapping of the target workflow to EARS requirements (Section 5.3).

| Step | Requirement |
|---|---|
| 3 | EARS-B1 |
| 5 | EARS-B2 |
| 6 and 10 | EARS-B4 |
| 7 | EARS-B3 |
| 10 | EARS-B5 and EARS-B6 |

### 5.2 Phased Roadmap as Candidate OpenSpec Changes

Each candidate names one Conventional Commits type and scope, as the OpenSpec rules in `openspec/config.yaml` require. A change that mixes types is split. Specs use "EARS-Requirements", and priority uses "MoSCoW". Each candidate states its vertical slice and its dependencies.

**Phase 0: rebuild data model and pipeline (independent of E6).**

| ID | Candidate change | Type(scope) | Slice | One-line scope | Depends on | MoSCoW | E6 relation |
|---|---|---|---|---|---|---|---|
| Q1 | Single load point for catalogs | refactor(platform) | Map, cup | Route all catalog reads through `CatalogDirectory.readAll`. **Implemented by `unify-catalog-loading`:** `CatalogLoader` is the one entry point and calls `CatalogDirectory.readAll`; `JsonMapCatalog` and `JsonCupCatalog` are removed. | none | Must | Independent |
| Q2 | Derive ring index and default world | feat(platform) | Map | Index from list position; world defaults to map id; breaking for shipped files. **Implemented by `simplify-map-data-format`:** a declared index must equal its position; world defaults to the map `name` exactly (not a lowercased id) and is not breaking, since no shipped file changes value | none | Should | Independent |
| Q3 | Schema version and JSON Schema | feat(platform) | Map, cup | Add `schemaVersion`; publish `$schema` and validate on load. **Implemented by `simplify-map-data-format`:** the version is refused above 1; the schema is published for editors and checked in a test, and is not validated on load (design decision 5) | none | Should | Independent |
| Q4 | Scope cup consistency to the selected cup | feat(platform) | Cup | Fully validate the selected cup; warn for the others | none | Must | Independent |
| Q5 | Name both selection switches in errors | fix(platform) | Cup | Hint names `-Pcup` and `-DVOYAGER_CUP` | none | Should | Independent |
| Q6 | Validate task for maps, cups and worlds (implemented by `add-catalog-validate-task`) | feat(build) | All | Implements NFR-007's validate-and-exit mode; reports every problem | Q4 | Must | Independent; closes the NFR-007 gap |
| Q7 | Hot reload at round boundaries | feat(platform) | Map, cup | Watch `maps/` and `cups/`; apply valid changes between rounds. **Implemented by `hot-reload-catalogs`, without a watcher:** the operator command `/race reload` validates the data directory, and a valid change plays from the next round. A round pins its catalogue at start (ADR-0019). A fingerprint poll, default off, is the deferred follow-up | Q1, Q4 | Should | Independent |

**Phase 1: voyager-setup MVP on Minestom (gated by E6 approval).**

| ID | Candidate change | Type(scope) | Slice | One-line scope | Depends on | MoSCoW | E6 relation |
|---|---|---|---|---|---|---|---|
| S0 | Authoring-model decision | docs(adr) | Setup | MADR record: disc model, pose placement, Polar outcome | X1 to X5 | Must | Gate; needs owner approval |
| S1 | voyager-setup Walking Skeleton | feat(setup) | Setup | Composition root; depends only on api and platform; opens a draft map | S0, Q1 | Must | E6.1 |
| S2 | Click-to-place rings with the wand | feat(setup) | Setup | EARS-B1; default radius; automatic index | S1, Q2 | Must | E6.2 |
| S3 | Status checklist and validation | feat(setup) | Setup | EARS-B2; checklist in the action bar | S1, Q6 | Must | E6.2 |
| S4 | Test-fly from the editor | feat(setup) | Setup, race | EARS-B3; per-ring report; Tracer Bullet candidate | S1, S2 | Must | E6.2 |
| S5 | Draft-to-published gate | feat(setup) | Setup, cup | EARS-B4 and B5; cups list published maps only | S3, Q4 | Must | E6.2 |
| S6 | Cup and map admin commands | feat(setup) | Setup, cup | `/cup` and `/map` subcommands writing the same JSON | S1, Q3 | Should | E6.2 |
| S7 | Display previews and Interaction handles | feat(setup) | Setup | Select, move, delete ring handles | S2, X2 | Should | E6.2 |
| S8 | Persisted, bounded undo | feat(setup) | Setup | Undo for ring edits across quit and restart | S2 | Should | E6.2 |
| S9 | Dialog forms | feat(setup) | Setup | Ring type and cup name without chat | S1, X3 | Could | E6.3 |
| S10 | Ring size presets | feat(setup) | Setup | Standard gate sizes | S2 | Could | E6.2 |
| S11 | Design lint warnings | feat(setup) | Setup | Spacing, dead zone, and first-ring distance warnings (T6 thresholds) | S3 | Could | E6.2 |
| S12 | Import of a legacy Paper world folder | feat(setup) | Setup | Migration of existing maps | E6.4 | Won't (this cycle) | E6.4 |

**Phase 2: automation (fly-through, lap generation, simulator validation).**

| ID | Candidate change | Type(scope) | Slice | One-line scope | Depends on | MoSCoW | E6 relation |
|---|---|---|---|---|---|---|---|
| T1 | Record-one-lap generation | feat(setup) | Setup | Generate gates and centers from a recorded lap; preview and accept | S4; live FlightTick stream | Should | After E6.2; approach needs owner approval |
| T2 | Headless ghost replay | feat(physics) | Physics | Replay a lap through `ElytraSimulator` for reachability and par | T1; collision spike | Should | Independent of E6 |
| T3 | Par time from replay with revisions | feat(race) | Race | Derive `referenceTime`; archive times on edit | T2 | Could | Independent of E6 |
| T4 | Derived racing-line guides | feat(race) | Race | Guides from terrain collision | T2 | Won't (this cycle) | None |

**Research spikes (E6.0 inputs).**

| ID | Spike | Type(scope) | MoSCoW | Output |
|---|---|---|---|---|
| X1 | FAWE operation inventory for `plugins/setup` | docs(research) | Must | Exact operations used; E6.0 criterion 1; the R01 inventory covers only PolyhedralRegionSelector |
| X2 | Display transforms and Interaction handles on 2026.08.28-26.2 | docs(research) | Should | Confirmed setters; resolves B1 |
| X3 | Dialog build and send path on Minestom 26.2 | docs(research) | Could | Confirmed API shape; resolves B2 |
| X4 | Polar 26.2 round trip: size, load time, Anvil conversion | docs(research) | Should | Decides the Polar question (C6) |
| X5 | Pro and contra of at least two editing approaches | docs(research) | Must | E6.0 deliverable, presented through AskUserQuestion |

**E6 relation.** E6.0 has no code dependency (greenfield-epics.md:1564). It is gated by its own acceptance criteria. This report is input to E6.0, not its completion. Spikes X1 and X5 are the E6.0 deliverables. X2 to X4 are inputs. None of the Phase 0 changes unblocks E6.0, because they do not touch the FAWE question. Phase 1 changes wait for owner approval of S0. A reduction in E6.0 scope is possible if ring capture stops using region selection (Inference, Section 6.2). X1 must confirm that before any scope is cut.

### 5.3 Proposed EARS-Requirements (builder experience)

These are proposals for owner review, not accepted requirements. Each follows an EARS pattern.

- **EARS-B1** (event-driven). When the builder uses the wand at a position while in setup mode, the setup server shall create a ring with its center at the builder position, its normal along the look direction, the map default radius, and the next free index. *Suggested MoSCoW: Must.* Basis: R05, R12, R16.
- **EARS-B2** (state-driven). While a map is in draft state, the setup server shall display a status checklist that lists each missing required item and the warning count. *Suggested MoSCoW: Must.* Basis: R11, R20 #2.
- **EARS-B3** (event-driven). When the builder issues the test-fly command, the setup server shall start the flight within 10 seconds and shall report a pass or miss for each ring when the flight ends. *Suggested MoSCoW: Must.* Basis: R20 #1 (the 10-second target is T6).
- **EARS-B4** (unwanted behavior). If a map fails validation, then the setup server shall refuse publication and shall list every failing ring index or cup slot in one message. *Suggested MoSCoW: Must.* Basis: R20 #5, NFR-007.
- **EARS-B5** (state-driven). While a map is not published, the cup catalog shall exclude that map from play. *Suggested MoSCoW: Must.* Basis: R11 #8, R12.
- **EARS-B6** (event-driven). When a map or cup file changes on disk and the change validates, the server shall make it available to new rounds without a restart. *Suggested MoSCoW: Should.* Basis: R04, R20 #11.

## 6. Discussion

### 6.1 Interpretation

The dominant cost is the translation from a pose in the world into ten JSON values. Format changes alone do not reduce it. Deriving the index (Q2) removes one value per ring and no actions. Pose placement removes nine of the ten actions per ring. The authoring surface therefore matters more than the file format. The disc model is what makes pose placement possible without new geometry.

The ring model and the gate check agree. The runtime hit test checks plane crossing and radius, and it ignores direction and the sign of the normal (RingPass.java:33-45; R05). A placement that records the look direction is therefore sufficient for the current rules.

Two claims in the earlier documents do not survive the rebuild. "Keep FAWE for ring shaping" depends on polyhedral regions, which the disc model does not represent. The P0 items of [2] depend on Paper and the tree being replaced.

### 6.2 Relation to E6 and Prior Work

E6 frames FAWE replacement as the blocker. The findings suggest the blocker shrinks for rings. Ring capture would no longer need region selection, so FAWE's remaining role is block editing of the terrain, which the builder can do in an external editor (Inference). Spike X1 must confirm which FAWE operations the setup workflow actually uses; R01 documents only PolyhedralRegionSelector and does not inventory copy, paste or undo. Until X1 is complete, no scope reduction for E6.0 is claimed.

[1] recommends a database for map metadata. That conflicts with C5 and should be marked superseded in that part. Its Polar recommendation conflicts with the ban in [2]. Both documents rest on Paper and should be read as history for the rebuild.

Issue #120 asks for a cup and map data package with at least one cup, three maps, and at least eight rings per map, placed in `server/src/main/resources/maps/` (R08). That path belongs to the tree being replaced. The Phase 0 changes can validate a package in the rebuild format before E6, but whether #120 targets the old or the new format is an owner decision (Section 6.4).

arc42 Architecture Documentation is a candidate for the system overview (semantic anchors page). For this paper, the context is the requirements document, the decisions are the greenfield design table, and the runtime view is the test-fly flow. The mapping is noted here and not applied.

### 6.3 Threats to Validity

**Internal validity.** The 20 investigations are summaries of other work. Four claims were checked directly, and seven in total were verified (Section 3.4). Individual file:line citations in R01, R04, R07, R09, R10 and R11 to R19 were not re-checked. The issue state of #120 is a snapshot.

**Construct validity.** The action count is a model. It assumes one action per ring, 35 rings and 8 vertices per ring for today. Time spent building, flying, and retrying is excluded. The claimed reduction is a count, not a measured speed. No builder took part in any investigation.

**Source validity.** Competitor plugins were studied through listings, README files, and community pages (T4). Several are discontinued. Ely-sky and IceBoatRacing were not examined in code. Minestom display, interaction, and dialog APIs are partly unverified (R16). Polar's version support for 26.2 is unverified (R13, R15). The JSON Schema library versions were not confirmed against Maven Central.

**Parameter validity.** The record-one-lap parameters (0.5-block resampling, RDP epsilon 0.5, 20 to 40 block gates, 30 degree crossing angle, 90 percent lap coverage) are T6 values from R17. The design lint thresholds (3 s, 5 s, 30 s) are heuristics from R20. Psychology claims cite goal-gradient, self-determination theory, loss aversion, Miller's law and Nielsen's response thresholds by name only. No sources are included in the working set, so these are hypotheses.

**Conclusion validity.** Phase 0 is judged cheap and safe on the basis of code reading, not on tests of the proposed changes. Hot reload was judged from R04 and has no prototype.

**Scope validity.** The findings apply to the rebuild. The old tree's friction (R01) informs requirements but is not an implementation target.

### 6.4 Open Questions

1. **Polar.** Does the project accept Polar as a deployment format, given the ban in [2] and the recommendation in [1]? Owner decision, after spike X4.
2. **Ring shape.** Are circular discs sufficient for all planned rings? If not, polyhedral shapes need a new model, and that is an architecture decision.
3. **Issue #120.** Is the alpha data package produced in the rebuild format through Phase 0, or by the tree being replaced? The second option conflicts with "no new features" in `CLAUDE.md`.
4. **FAWE inventory.** Which FAWE operations does `plugins/setup` use beyond PolyhedralRegionSelector? Spike X1.
5. **Hot reload semantics.** What happens to a round that is in progress when its map changes on disk? Q7 must define this before implementation.
6. **Versioning.** Are world snapshots and rollbacks needed for the first release? [1] and [2] propose tar.gz and numbered directories, and R09 keeps the catalog in git. Owner decision.
7. **Publishing rights.** Who may publish a map? This is not covered by the inputs.
8. **Replay trust.** How much tolerance does a par-time gate allow for client-authoritative flight (F4)?
9. **Thresholds.** Do the design lint thresholds fit this game? They need playtest data.

## 7. Conclusion

### 7.1 Contributions

1. A quantified baseline for setup cost: 354 in-game actions for the 35-ring sample map, against an estimated 42 for pose placement and 9 for lap generation.
2. Six proposed EARS-Requirements for builder experience, with suggested MoSCoW priorities (Section 5.3).
3. A phased roadmap of candidate OpenSpec changes with Conventional Commits types, slices, dependencies and E6 relations (Section 5.2).
4. Four conflicts that need owner decisions: the Polar ban, the FAWE-versus-disc ring model, the NFR-007 validate mode that is specified but absent, and the #120 data-package path.

### 7.2 Recommendation (for the project owner)

1. Approve the Phase 0 changes Q1 to Q7 as rebuild work. They are independent of E6. Shared modules need owner approval before any PR, as `CLAUDE.md` requires.
2. Put the authoring-model decision to the owner through AskUserQuestion, with the trade-offs: pose placement as default with polyhedral shapes out of the model, or keep polyhedral shapes and accept a larger model. Include the Polar question with options.
3. Run spikes X1 to X5 as E6.0 inputs. Keep E6.1 and later unscheduled until the owner approves the E6.0 recommendation, as the epics require.
4. Do not implement the P0 list of [2] in the tree being replaced.
5. Decide the path for #120 before the alpha date.

### 7.3 Future Work

- A timed builder study: baseline against the target workflow, with a fixed map and a fixed set of builders.
- A fly-through capture prototype to measure how many flights a ring needs.
- Lap generation accuracy against hand-placed gates on the sample map.
- Validation of the design lint thresholds through playtests.

### 7.4 Owner decisions (2026-10-09)

- Phase 0 starts now; E6 is unblocked: this report serves as the E6.0 research input, and `voyager-setup` (Phase 1) is planned next.
- The ring stays a disc (center, normal, radius) as in `Ring.java:14`. FAWE polyhedral selections remain an import source through `tools/map-converter` only.
- Issue #120 (alpha data package) uses the converter path: alpha maps are still built with the old Paper setup plugin and converted into the rebuild format.

## 8. References

### 8.1 Internal Investigation Inputs

R01 to R10 are code investigations and R11 to R20 are external research. They are summaries in the working set of this session (not committed to the repository). They are cited by number in the text. R20 is a theory-based analysis of the requirements document.

### 8.2 Project Documents

[1] Voyager Development Team, "Map tooling, versioning, and hot-reload patterns," `docs/research/002-map-tooling-and-versioning.md`, 2026-03-29.

[2] Voyager Agent Team, "Builder setup UX analysis and map versioning plan," `docs/research/002-builder-setup-ux-and-versioning.md`, 2026-03-29.

[3] Voyager Development Team, "Voyager greenfield design," `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, decisions D6 and D10.

[4] Voyager Development Team, "Voyager greenfield epics," `docs/superpowers/specs/2026-09-09-voyager-greenfield-epics.md`, E6 and E6.0, lines 1498 to 1640.

[5] Voyager Development Team, "Anforderungen Voyager rebuild," `docs/requirements/anforderungen-voyager-rebuild.md`, lines 67 and 99 (NFR-007).

[6] Voyager Development Team, "Semantic anchors," `docs/reference/semantic-anchors.md`.

[7] Voyager Development Team, "OpenSpec project configuration," `openspec/config.yaml`.

[8] Voyager Development Team, "CLAUDE.md," project root, including the section on elytra velocity authority.

[9] Voyager source files verified for this report: `voyager-api/src/main/java/net/elytrarace/voyager/api/race/Ring.java`, `MapDefinition.java`, `GuidePoint.java`.

[10] Voyager source file verified for this report: `voyager-platform/src/main/java/net/elytrarace/voyager/platform/catalog/CatalogConsistency.java`.

### 8.3 External Sources (from the investigations)

[11] Ely-sky, Hangar. https://hangar.papermc.io/SunflowerAges/Ely-sky (R12).

[12] IceBoatRacing, Modrinth. https://modrinth.com/plugin/ibr (R12).

[13] ElytraRace, Modrinth. https://modrinth.com/plugin/elytrarace (R12).

[14] New Bedwars, Modrinth. https://modrinth.com/plugin/new-bedwars (R11).

[15] Parkour, CurseForge tutorial part 9. https://dev.curseforge.com/projects/parkour/pages/tutorial/part9 (R11).

[16] SkyWarsReloaded, creating arenas. https://www.gcnt.net/skywars/creating-arenas (R11).

[17] Hollow Cube, early access release. https://hollowcube.net/news/early-access-release (R14).

[18] Hollow Cube, MapMaker repository (MIT). https://github.com/hollow-cube/mapmaker (R14).

[19] Polar, repository and format specification. https://github.com/hollow-cube/polar and https://github.com/hollow-cube/polar/blob/main/FORMAT.md (R13, R14, R15).

[20] networknt, JSON Schema validator. https://github.com/networknt/json-schema-validator (R15).

[21] victools, jsonschema-generator. https://github.com/victools/jsonschema-generator (R15).

[22] Minecraft Wiki, Display, Interaction and Dialog. https://minecraft.wiki/w/Display, https://minecraft.wiki/w/Interaction, https://minecraft.wiki/w/Dialog (R16).

[23] Kaufmann et al., demonstration flights as gate priors, arXiv:1810.06224 (R17).

[24] TOGT, trajectory generation from tracking, arXiv:2309.06837 (R17; the expansion of the acronym is not given in the working set).

[25] Centripetal Catmull-Rom parameterization. https://cemyuksel.com/research/catmullrom_param/ (R17).

[26] Eilers and Marx (1996, P-splines); Petitjean (2011, DBA); Richter (2013, minimum-snap). No URL in the working set; not verified (R17).

[27] Trackmania author medals guide. https://ggrecon.com/guides/trackmania-author-medals (R18).

[28] Epic Games, Race Checkpoint devices in Fortnite Creative. https://dev.epicgames.com/documentation/fortnite/using-race-checkpoint-devices-in-fortnite-creative (R18).

[29] MultiGP, gate hitbox direction. https://www.multigp.com/?p=47242 (R18).

[30] Trackmania (2020), Wikipedia; Openplanet Map Playlist. https://en.wikipedia.org/wiki/Trackmania_(2020_video_game) and https://openplanet.dev/plugin/mapplaylist (R19).

[31] MapCycle, Modrinth, and TGM, WarzoneMC. https://modrinth.com/plugin/mapcycle and https://github.com/WarzoneMC/tgm (R19).

[32] Datapack wiki, tags. https://datapack.wiki/wiki/files/tags (R19).
