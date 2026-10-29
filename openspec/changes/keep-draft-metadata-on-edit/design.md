# Design

## Context

The setup server edits a draft through `DraftEditor` (`voyager/setup/.../setup/mapsetup/DraftEditor.java`). Three public
operations exist: `withRingAppended` (`:37-47`), `withoutRing` (`:55-69`) and `withSpawn` (`:71-74`). Each builds a new
immutable `MapDraft`, and the private helper `with` (`:76-79`) does the construction for all three.

`MapDraft` has two constructors that matter here. The canonical constructor takes the ten components, including
`displayName` and `authors` (`MapDraft.java:32-34`). The eight-argument constructor (`:43-46`) states that the draft has no
display name and no authors, and `with` calls it. The result is that every edit clears the two fields, and the
`MapSession.commit` path (`MapSession.java:52-56`) saves whatever draft the editor returned, so the cleared fields reach
the file.

The save path is correct. `MapDraftJsonWriter` writes `displayName` and `authors` when they are set, and the draft store
writes what it is given (`add-catalogue-keys-and-authors`). The defect is only in the rebuild.

## Goals / Non-Goals

Goals:

- Every edit keeps the display name and the authors of the draft it edits.
- The fix is visible in the saved file, not only in the in-memory record.
- The fix needs no change outside the setup slice.

Non-Goals:

- Setting or changing the name and authors. That is change 7.
- A new API on `MapDraft`. Option 2 below would add one; it is not needed for this fix.
- Changing what a new map holds. `skeleton` stays as it is.

## Decisions

1. **`with` builds the draft with the canonical constructor and the edited draft's metadata.** `with` passes
   `draft.displayName()` and `draft.authors()` to the ten-argument constructor, so the edit copies the two fields the
   edit does not change. This is the smallest change that makes the defect impossible for the three edits that exist.
   *Rejected:* (a) keep the eight-argument call and set the fields afterwards; a record is immutable, so this is not
   possible. (b) An overload of `with` that takes the metadata; each caller would then have to repeat the rule, and a
   new edit could forget it.

2. **No wither methods on `MapDraft` in this change.** A `MapDraft.withRings(...)` or `MapDraft.withSpawn(...)` method in
   `voyager-api` would make the rule hold by construction for every future edit. It is the stronger design, and it is
   an API change for a bug fix. *Rejected for this change:* it grows the API in a fix, and the undo change
   (`undo-draft-edits`) is the place that will need the same operations. The owner may ask for it there; see the open
   questions.

3. **Tests cover each edit and the saved file.** One unit test per edit proves that the returned draft holds the name and
   the authors. One round trip through `JsonDraftStore` in a temporary directory proves that the file holds them. The
   record test alone would not show a dropped field in the file, because the writer writes what it is given.

4. **No ADR and no docs change.** The change restores behaviour the code already promised; it makes no architecture
   decision. No page in `docs/` describes the lost fields.

## Dependency direction

The change stays inside `voyager-setup`, in the pure package `net.elytrarace.voyager.setup.mapsetup`, which depends on
`voyager-api` and the JDK only. `DraftEditor` already reads `MapDraft`, a type of `voyager-api`, and needs nothing new.

The round-trip test uses `JsonDraftStore` from `voyager-platform`, which the setup slice already depends on
(`voyager/setup/build.gradle.kts`). It sits outside the pure package, so no new dependency edge appears.

Fitness coverage: the existing rules cover the change without a new rule. `ApiPurityTest.setupDomainOnlyDependsOnApiAndJdk`
keeps the `mapsetup` package to the API and the JDK, so `DraftEditor` may not reach the platform, and the round-trip test
therefore lives in a setup test package outside `mapsetup`. `DesignRuleTest` keeps the record and exception rules. The
change adds no static state.

## Testing

All tests follow F.I.R.S.T. and "Red/Green TDD".

- `DraftEditorTest` builds its own `MapDraft` fixture in each test, with a display name "Blue & Red" and two authors. It
  has no shared static state and reads no clock.
- The round trip uses `@TempDir` for the draft folder. It writes through `JsonDraftStore`, reads the file back, and compares
  the name and the authors. It uses no network and no path outside the temporary directory.
- Each test has one behaviour and an assertion message that names the field (`displayName`, `authors`).
- The Red run must fail for the stated reason: the returned draft has a null display name and an empty author list.

## Open questions

1. **Wither methods on `MapDraft`.** Should the operations that rebuild a draft move to `MapDraft` in `voyager-api`, so
   that a future edit cannot drop a field? Recommendation: decide this with `undo-draft-edits` (change 4), which needs
   the same operations for its history. Not decided here, and this change does not depend on the answer.
