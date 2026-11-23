# Design

## Context

`DraftEditor` returns a new `MapDraft` for every edit (`voyager/setup/.../mapsetup/DraftEditor.java`). Appending
(`withRingAppended`, `:37-45`) and removing (`withoutRing`, `:50-69`, which renumbers the later rings) are the only ring
operations. A ring is `Ring(index, centre, normal, radius, points, type, shape)`; its index equals its list position, and
the draft check refuses any draft where it does not (`undo-draft-edits`, `map-draft-storage` "Map ids and draft invariants").

Guide points live on the order axis of `GuidePoint` (`voyager/api/.../race/GuidePoint.java`): ring `i` is at `i * 100`,
a guide point sits at another slot and `afterRing()` is `floorDiv(orderIndex, 100)`. A guide point therefore follows a
ring only if its slot is rewritten when the ring's slot changes. `MapDefinition` checks at save that every guide point
brackets a legal pair of rings, so a stale guide point is refused rather than silently drawn wrong.

## Goals / Non-Goals

Goals:

- Move, insert and reorder rings with named commands, each through `MapSession.commit`.
- A guide point stays on the ring it bends between, in the same draft.
- The mapping is a pure function, tested without Minestom.

Non-goals:

- Re-aiming a ring. Normal stays as placed.
- Keeping the look ray as a selector for the new commands. Index is explicit.

## Decisions

### Index addressing, not the look ray

`move`, `insert` and `reorder` name the ring by index. The look ray selects the ring in `edit-ring-shape-and-type`
because that command edits what the builder is looking at; a move names a ring it may not be able to see from where
the builder stands. Index also makes the command reproducible in a chat log. The cost is that the builder must know the
index, which `/map status` (see `delete-replace-and-check-maps`) and the ring previews show.

### Guide remapping as a pure function

`GuideRemap` maps a guide point's `orderIndex` from the old draft to the new one. Let `old(i)` be the slot of ring `i` in
the old draft and `new(j)` its slot in the new draft.

- A move keeps every ring's slot, so no guide point changes.
- An insert at `k` shifts every slot at or above `k * 100` by 100; a guide point on or above that slot moves by 100.
- A reorder from `from` to `to` maps each guide point by the ring on its low side: its new slot is the new slot of the ring
  it bends after, plus the offset it had inside that gap. A guide point between ring `a` and ring `a+1` stays between the
  same two rings, wherever they now sit.

The offset inside a gap is kept, so a guide point is moved with the rings and not onto the same numeric slot. The rule is
checked by a property-style test over generated orders in the pure test class.

### Commit path and undo

Each command builds a candidate draft and calls `MapSession.commit`, which saves first. No command writes the file
itself. The undo history records whole drafts (`undo-draft-edits`), so an undo of a reorder restores the guide points
with the rings and needs no inverse operation.

### Insert takes a pose

`insert <index>` places the ring from the builder's eye and look, as the wand does (`RingFromPose.create`), but at
position `index`. The pose is taken once and the index is validated before the pose is read, so a refused insert names the
bound and changes nothing.

## Dependency direction and modules

- `GuideRemap` and the new `DraftEditor` operations live in `voyager/setup/.../mapsetup`, depending on `voyager/api` only.
- `SetupCommands` and the `ring` subcommands live in `voyager/setup/.../adapter`, depending on `mapsetup`.
- No type moves into `api`; no platform import appears in `mapsetup` (`setupDomainOnlyDependsOnApiAndJdk` keeps this).

## Architecture rules

- Existing: `setupDomainOnlyDependsOnApiAndJdk` covers `GuideRemap` and the editor operations.
- New boundary: the guide remapping is reached only through `DraftEditor`, never from the adapter. Covered by a new
  ArchUnit rule in `voyager/fitness`, `allowEmptyShould(false)`.

## Decision record

Index addressing and guide remapping change how a builder authors a course, and they change the rule of ADR-0018 that a
ring comes from one click. A MADR ADR is written with the change; the number is the next free one in `docs/decisions/`.

## Open questions

- Should `move <index> here` also aim the normal at the builder's look direction? Recommendation: no; a separate
  `aim` command keeps a move from silently re-aiming a ring the builder only wanted to shift.
- What happens when a reorder moves a ring across a guide point's bracket and the bracket becomes illegal? Recommendation:
  refuse the reorder with the bracket message, naming the guide point, and change nothing. The alternative is to drop the
  guide point, which loses the builder's work silently.
- Are ring indices shown in the wand line after an insert? Recommendation: yes, the existing "Ring <n> is set" line names
  the index.
- The wand's removal already renumbers rings without touching the guide line: `DraftEditor.withoutRing` (`DraftEditor.java:50-69`)
  passes `draft.guideLine()` through unchanged, so a guide point can end up bending between the wrong rings. It is the same remap
  as a reorder. Recommendation: a separate `fix(setup)` change that reuses `GuideRemap`, because the one-type rule forbids
  bundling it into this `feat(setup)` change.
