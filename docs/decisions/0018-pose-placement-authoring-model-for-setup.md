# ADR-0018: Pose placement of disc rings as the authoring model of the setup server

## Status

Accepted

## Date

2026-10-09

## Decision makers

- TheMeinerLP (project owner; approved the disc ring, the draft layout and the authoring model on 2026-10-09)
- Atlas (architect)

## Context and problem statement

A playable cup needs a map with rings. Research 005 (`docs/research/005-simpler-map-and-cup-setup.md`) measured the
tree being replaced: about 354 in-game actions for the 35-ring sample map, 350 of them ring placements at ten actions
per ring, plus a manual world copy and a server restart per catalogue change. The owner approved research 005 as the
E6.0 input on 2026-10-09 (its section 7.4).

The setup server of the rebuild (`voyager-setup`) needs one authoring model before code is written: how a builder
turns a place in the world into a ring, and where the resulting map is stored. The ring type is fixed by
`net.elytrarace.voyager.api.race.Ring`: a centre, a unit normal, a radius, points and a type. A polyhedral selection
of blocks is not a `Ring`, and the owner decided that FAWE polyhedra stay an import source through `tools/map-converter`
only.

## Decision drivers

- Ring placement costs one action per ring (research 005, target workflow step 3, requirement EARS-B1)
- The ring stays the disc of `Ring`; no new geometry enters the race domain (owner decision, 2026-10-09)
- A draft the builder edits must never be a file the game server cannot load, and the game server must not need a
  change to read it (research 005, EARS-B1 and EARS-B2 basis)
- The writes must not lose the last action on a crash (greenfield design, persistence rules)
- The decision logic stays pure and testable with `new`, and Minestom stays in the adapter (greenfield design decision D6;
  ADR-0016 for the composition root)
- The setup server depends on `voyager-api` and `voyager-platform` only (greenfield design decision D8)

## Considered options

- **A. Pose placement of disc rings.** The builder right-clicks a wand while standing at the ring centre and looking
  along the flight direction. The eye position is the centre, the look direction the normal, the map default radius
  the radius. Sneak plus left-click removes the ring the look ray crosses; a plain left-click changes no ring (owner decision O2).
- **B. Record one lap and derive the gates.** The builder flies one lap; the server records the path and proposes rings.
  Fewer actions for a first preview (research 005 estimates nine in total), but it needs a live flight stream and a
  race run in the setup server. It is a follow-up (`add-setup-record-one-lap`), not a replacement.
- **C. Fly-through capture.** Rings are created when a flying player passes a point. It needs the same live flight
  stream as B, and it cannot place a ring the builder does not fly through.
- **D. Polyhedral FAWE selections stored in the game format.** Rejected. `Ring` has no polyhedral form, and the owner
  restricted FAWE polyhedra to an import path through `tools/map-converter`.

## Decision outcome

Chosen option: **A, pose placement of disc rings**, because it reduces the ring cost to one action, it needs nothing
the rebuild lacks today (a pose and a click), and it keeps `Ring` as the single geometry of the race. B and C remain
open as follow-ups that add the flight stream; they do not change the stored format.

**Storage decision (owner, 2026-10-09).** A draft is written in the game server's own map format, with no publish step
and no conversion:

- A draft with at least one ring and a spawn is game-loadable, and it lives at `<dataPath>/maps/<id>.json`, the flat
  file the game server reads.
- Every other draft lives at `<dataPath>/drafts/<id>.json`, a folder the game server never reads. The first ring alone
  does not make a draft loadable: `MapDefinitionAdapter` requires a spawn, so a file without one must wait.
- The world of every map is `<worldsPath>/<id>`, and the file names it with `world` explicitly.
- A save writes a temporary file in the target folder, forces it to disk and moves it over the target atomically. The
  copy in the other folder is deleted after the new copy exists. A save never deletes the file it is about to replace
  before the new content exists.
- A draft found in both folders, which a crash between the move and the delete can leave, is refused with a message that
  names both files. The server does not choose one of them, because either choice can lose the last action.

**Domain split.** `MapDraft` is not `MapDefinition`. A draft may have no rings and no spawn; a `MapDefinition` may not.
The status of a draft lists the problems that keep it from being game-loadable.

**Consequences for the dependency graph.** The contract of the draft store is a port in `voyager-api`
(`net.elytrarace.voyager.api.mapsetup`). It holds records, an interface and exceptions. The implementation is in
`voyager-platform`, which owns the file system and the JSON writer. `voyager-setup` calls the port and depends on
`voyager-api` and `voyager-platform` only.

## Pros and cons of the options

### A. Pose placement

- Good, because one action per ring, and the builder's own position is the ring's position.
- Good, because the stored ring is the existing `Ring`, validated by the existing constructor.
- Bad, because the builder must stand in the ring's place. A ring placed from the wrong place is removed and placed again.
- Bad, because there is no undo in this slice. A sneak plus left-click removes a ring for good (follow-up `add-setup-undo`; the
  removal gesture is an open owner question, O2 in the change's design).

### B. Record one lap

- Good, because a first preview of a 35-ring map takes about nine actions (research 005, Table 3).
- Bad, because the gates depend on the lap, and a lap from the wrong line gives wrong rings.
- Bad, because it needs a live flight stream and a race run inside the setup server. Neither exists in the setup server.

### C. Fly-through capture

- Good, because it is the lap idea without a separate recording step.
- Bad, because a ring the builder never flies through cannot be created, and a missed pass creates a wrong ring.

### D. Polyhedral FAWE selections

- Good, because the builder works in the familiar external editor.
- Bad, because `Ring` has no polyhedral form, so the format would change, and the owner decided against that on
  2026-10-09.

## More information

- Research 005: `docs/research/005-simpler-map-and-cup-setup.md`, sections 4.1 and 5.1 and section 7.4 (owner decisions).
- Spike record: `docs/research/006-minestom-26-2-setup-spikes.md`.
- Greenfield design: `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, decisions D6 (Minestom everywhere),
  D8 (module edges) and D10 (dependency injection, superseded by ADR-0016).
- ADR-0016: `docs/decisions/0016-replace-guice-with-avaje-inject.md`, the composition root of the setup server.
- Open and out of scope for this decision: the Polar round trip (research spike X4) and the FAWE operation inventory
  (research spike X1). Neither is decided here.
- Follow-ups that build on this model: `add-setup-test-fly`, `add-setup-record-one-lap` and `add-map-folder-bundle`
  (the last one would change the game's layout and needs its own owner approval).
