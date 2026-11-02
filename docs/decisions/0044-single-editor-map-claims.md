# ADR-0044: Single editor per map through in-memory claims and a save guard

## Status

Proposed

The owner accepts or rejects this record. It moves to Accepted only on the owner's word. It belongs to the change
`single-builder-map-claims` (OpenSpec, design decision 1 and the ADR of its design document). The code that implements it
is in `feat(setup): allow one builder to edit a map at a time`.

## Date

2026-11-02

## Context

Two builders who open one map each hold a copy of its draft. Each save writes the whole file, so the second save replaces
the first without an error. The setup server runs as one process, so the problem is local to the process. A second setup
server pointed at the same data directory is the only way to meet it from outside.

## Decision drivers

- No save may silently replace another builder's rings.
- A refusal must say who holds the map, so the builder knows whom to ask.
- Nothing on disk may block a later start after a crash.
- The rule must cover the cups of a later change without a second mechanism.

## Decision

Use in-memory claims plus a save guard.

- A claim registry, keyed by string (`map:<id>` for a map, `cup:<id>` for a cup), holds one holder per key. It lives in
  memory only, so a start begins with no claim.
- The session lifecycle owns the claims. A claim is released by `/map close`, by opening another map (after the new claim
  is granted), by the holder's disconnect, and by `/map unlock`.
- A save is refused when the stored copy of the draft no longer equals the draft the session last loaded or saved. The
  two are compared by their canonical JSON, so the layout of the file does not matter.
- After `/map close` and after `/map unlock` ends the holder's session, the builder goes to the setup lobby and loses the
  setup wand. The proposal left this destination open. The owner decided it: the lobby is a flat world with one floor,
  and it is also where a joining player spawns.

*Rejected:* a lock file `drafts/<id>.lock` with the holder's UUID. A lock file outlives a crash, so the next start would
need a reaper that decides whether a lock is stale.

*Rejected:* a save guard alone, with no claims. A second builder would then learn of the conflict only when a save is
refused, after both have edited.

*Rejected:* claims stored in the draft file. They would survive a restart only with the same reaper as lock files, and a
claim written into the map file would reach the game server's reader.

*Rejected:* a takeover that hands the claim to the administrator, which would need the administrator to hold the map
first. *Rejected:* a restart as the only way out of a stuck claim, because a restart clears every other claim too.

## Consequences

- Good: two builders on one setup server cannot edit one map, and the refusal names the holder.
- Good: a stale copy from a second setup server or an outside edit is refused at save time, and the file is unchanged.
- Good: no lock file and no reaper.
- Good: an administrator releases a stuck claim with `/map unlock`, without a restart.
- Bad: a second setup server pointed at the same data directory is not refused at open. It is refused at save, so the
  builder finds out at the first edit.
- Bad: the guard's comparison and the write are two steps. An outside writer that acts in the gap between them is not
  refused. The gap is a few milliseconds of local file access, and the decision accepts it.
- Neutral: the registry is empty after every start. A builder who held a map before a restart opens it again.

## Confirmation

`EditClaimsTest` covers the registry. `JsonDraftStoreTest` covers the guard with two store instances on one folder, which
stands for two processes. `MapClaimsTest` covers the refusal, the release paths, the lobby move and the unlock gate.
`MapSessionTest` covers a commit over a changed draft.
