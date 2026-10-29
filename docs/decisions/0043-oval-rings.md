# ADR-0043: Oval rings are an optional ring shape in a version 3 file

## Status

Proposed

The owner accepts or rejects this record. The change that implements it is `oval-ring-model` (OpenSpec). It amends the
disc of [ADR-0018](0018-pose-placement-authoring-model-for-setup.md) for the shape of a ring only, and does not edit that
record. Status moves to Accepted only on the owner's word.

## Date

2026-10-29

## Decision makers

- TheMeinerLP (project owner). The owner decided on 2026-10-11 that a ring may be an oval, and chose the file format
  described here (option A of the design, owner decision O1).

## Context and problem statement

A ring is a disc. Several rings of the old maps are clearly elliptical, and a circle of the mean rim distance is too generous
on the short axis, so a racer can pass a ring the course meant to be narrower. The file format must state an oval without
breaking the servers that read it.

An older server ignores a key it does not know, so a new key on a ring would be read as a circle of the semi-major radius,
silently. The schema version is the existing way for a file to say that it needs a newer reader: `catalog/file-versioning`
refuses a file that declares a version above the server's maximum.

## Decision drivers

- A server that predates this change must refuse an oval, never play it as a circle.
- A circle keeps its file, its layout hash and its behaviour.
- The API keeps one ring model, with no nullable set of fields.

## Considered options

- **A: optional fields and version 3 (chosen).** A ring may state `radiusMinor` and `majorAxis`, which must come together.
  A file that holds an oval declares `"schemaVersion": 3`. A file that holds no oval keeps its version.
- **B: a `shape` discriminator** (`"CIRCLE"` or `"ELLIPSE"`) with the fields of the shape. Rejected: every circle would then
  change its file to carry a new key.
- **C: optional fields without a version bump.** Rejected: a server before this change would read each oval as a circle of
  its semi-major radius, with no refusal.

## Decision outcome

Option A. `radius` stays the semi-major radius, so every existing reader sees the outer bound. The API models the ring with
a sealed `RingShape`, a circle or an ellipse, and not with nullable fields. Every radius of a ring lies in `[0.5, 32]`
blocks and every point count in `[0, 100]`; a file outside these bounds is refused.

## Consequences

- Good: an older server refuses an oval with the file, the ring's position and the version named. It does not play a wrong
  course.
- Good: circle-only files and their layout hashes do not change, so no leaderboard resets.
- Bad: game servers must be deployed before any setup server writes an oval. This is an operator step.
- Bad: a file declaring version 3 is refused by any server built before this change, including a map that has no oval. The
  writer avoids this by declaring version 3 only for a file that holds an oval.

## Confirmation

`RingShapeTest` and `RingAdapterTest` pin the containment and the refusals. `MapDraftJsonWriterTest` pins the version a
draft writes. `ShippedLayoutVersionTest` pins the layout version of every shipped map, which must not change.
