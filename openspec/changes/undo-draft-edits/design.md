# Design

## Context

Every change to a draft goes through `MapSession.commit` (`voyager/setup/.../adapter/MapSession.java:52-56`). It saves the
new draft, keeps it, and shows the previews of the new rings. The session keeps no earlier draft, so nothing can be
restored (`MapSession.java:14-62`).

`DraftEditor` already returns a new `MapDraft` for every edit and never changes the draft it is given
(`voyager/setup/.../mapsetup/DraftEditor.java:33-79`). A `MapDraft` is an immutable record and its rings are copied into an
unmodifiable list (`voyager/api/.../api/mapsetup/MapDraft.java:56-61`). An earlier draft therefore stays valid after the
session has moved on, and a snapshot costs one reference.

Removal renumbers the rings after the removed one (`DraftEditor.withoutRing`, lines 55-69). An undo of a removal cannot be
the inverse of that operation without also knowing the renumbering, so the history stores whole drafts, not inverse
operations.

## Goals / Non-Goals

Goals:

- Undo and redo of the open map's draft through the same save path as an edit.
- A history that is a pure value, tested without Minestom.
- A failed save of an undo changes nothing, as a failed save of an edit already changes nothing.

Non-goals:

- A history that survives a restart or crosses maps.
- Undo of cup edits, and undo of block edits in the setup world.

## Decisions

1. **The history stores whole drafts.** Each edit records the draft it replaces. Rejected: a command pattern with an inverse
   for each edit. Every inverse would have to restore renumbered indices, the spawn and, after change 3, each ring field.
   A snapshot is exact and needs no per-edit code.

2. **`DraftHistory` is an immutable value in `setup/mapsetup`.** It holds the current draft, the earlier drafts (oldest
   first, at most the limit) and the undone drafts. Its operations return a new `DraftHistory`: `record(next)`, `undo()` and
   `redo()`, and `canUndo()` and `canRedo()` for the replies. The class imports `MapDraft` and nothing from Minestom.
   Rejected: a mutable deque that pops before the save and pushes back if the save fails. A value that is replaced only
   after the save succeeds needs no push-back, and a failed save simply keeps the old value.

3. **One save path for edits, undo and redo.** `MapSession` holds one `DraftHistory`. A private `persist(DraftHistory
   candidate)` saves `candidate.current()` through the store, then installs the candidate and shows its rings. `commit(next)`
   calls `persist(history.record(next))`, and `undo` and `redo` call `persist` with the candidate of the history operation.
   The previews come from the installed draft in all three cases. Rejected: a separate save path for undo, because the
   guard of `single-builder-map-claims` would then have to be added in two places. Until that change lands, `persist` calls
   `DraftStore.save`; once it lands, `persist` calls `saveIfUnchanged(current, candidate.current())`, and no other line changes.

4. **The limit is 100 earlier drafts.** A draft of thirty rings is a few kilobytes in memory, so the limit bounds the memory
   of an open map to a small constant. When the earlier drafts exceed 100, the oldest is dropped. Owner to confirm (open
   question 1).

5. **A new edit clears the redo history.** After an undo, the redo history holds the drafts that were undone. A new edit
   starts a new branch, and a redo across it would restore a draft that no longer follows from the current one.

6. **The history belongs to the open map.** It is dropped with the session: when the builder opens a different map
   (`BuilderSessions.open` replaces the session), closes the map, disconnects (`BuilderSessions.forget`), or the server
   restarts. Opening the same map again replaces the session and so drops the history, until `single-builder-map-claims`
   lands; that change keeps the session for the holder (its decision 4). Open question 3 asks whether the history should
   survive the close.

7. **A failed save keeps everything.** `persist` saves before it installs, so a failed save throws `DraftWriteFailedException`
   (or `DraftChangedException` after `single-builder-map-claims`) with the session's draft and history unchanged. The
   command replies with the save-failed message, which already says the draft is as it was.

8. **Undo and redo are gated.** `/map undo` and `/map redo` run through `SetupCommands.gated`, like every subcommand
   (`SetupCommands.java:85-91`). Opening a map and a builder's session are not edits and are never recorded.

Messages: the four keys `voyager.setup.undo.done`, `voyager.setup.undo.none`, `voyager.setup.redo.done` and
`voyager.setup.redo.none` are methods of `SetupMessages` (`voyager/platform/.../platform/text/SetupMessages.java`). The
existing `voyager.setup.save.failed` covers a failed undo.

## Dependency direction

- `setup/mapsetup`: `DraftHistory`, a record with static factory `DraftHistory.start(MapDraft)` and the operations above.
  It depends on `api` (`MapDraft`) and `java.util`.
- `setup/adapter`: `MapSession` holds the history; `SetupCommands` adds the two subcommands; both depend inward on
  `setup/mapsetup`, as they do today.
- `platform/text`: the four messages. The rule `onlyTheTextPackageBuildsAUserFacingString` (`ApiPurityTest`, line 319) keeps
  the text there.
- No new dependency between slices.

Fitness: `setupDomainOnlyDependsOnApiAndJdk` (`ApiPurityTest`, line 438) covers `DraftHistory`, so it cannot import Minestom.
`MessageBundleParityTest` checks the English and German keys. No new fitness rule is proposed.

## Testing

Every test follows F.I.R.S.T.

- `DraftHistoryTest` (plain JUnit, `setup/mapsetup`): no `Env`, no clock, no file. Each test builds its own history from
  fixture drafts that `DraftEditor` produces, so the test uses the real edits.
- `MapSession` with a fake `DraftStore` (a test double in the test source, no file): one test where the store fails the
  save, asserting the draft and the history are unchanged.
- Minestom tests (`@EnvTest`, `env.tick()`, no sleeps): `/map undo` after a placed ring restores the earlier file bytes and
  ring count; `/map undo` with no history replies the none message and writes nothing; a refused builder changes nothing.
- A spawn undo with a display name is covered once `keep-draft-metadata-on-edit` has landed.
- Temporary files only in `@TempDir`.

## Open questions

1. **The limit of 100 snapshots.** Owner to confirm. **Recommendation: 100.** It is a constant, and a smaller number would
   lose the edits of one careful session.

2. **Whether undo should survive a restart.** **Recommendation: no.** The file on disk is always the last saved state, so a
   restart loses only the steps the builder could have taken back. Writing history to disk would be a second file format.

3. **Whether the history of a map should survive the builder closing it.** **Recommendation: no.** A session is per open
   map, as the setup server already treats it. Owner decision.
