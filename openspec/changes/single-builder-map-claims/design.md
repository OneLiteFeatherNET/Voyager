# Design

## Context

The setup server is one Minestom process. A builder's open map is a `MapSession` (`voyager/setup/.../adapter/MapSession.java`),
which holds the draft last saved and the instance the builder stands in. `BuilderSessions` maps each builder's UUID to one
session (`BuilderSessions.java:18`). `open` replaces the builder's session and does not look at any other builder
(`BuilderSessions.java:34-39`). `SetupCommands.open` loads the draft from the store and enters it without a check
(`SetupCommands.java:119-134`). Two builders can therefore hold two copies of one draft.

`JsonDraftStore.save` replaces the stored file atomically (`voyager/platform/.../catalog/JsonDraftStore.java:124-151`).
Each save writes the whole draft, so the last save wins. The second builder's stale draft is written over the first
builder's rings, and no error is shown.

`MapSession`'s class javadoc states that player events run on Minestom's main thread, so a session needs no lock
(`MapSession.java:11-12`). The claim registry follows the same rule and needs no lock either.

Every subcommand is gated by `voyager.setup.use` before it runs (`SetupCommands.java:85-91`). The permission port is
`api/permission/PermissionPolicy`; the node is `PermissionNode.VOYAGER_SETUP_USE` (`api/permission/PermissionNode.java:24`).

## Goals / Non-Goals

Goals:

- One editor per map inside the setup server. A second builder is refused, and the refusal names the holder.
- A save never replaces a draft that changed since this session loaded or saved it, whatever process changed it.
- No lock file on disk, so a crash cannot leave a claim that blocks the next start.

Non-goals:

- Claims across processes and persisted claims. A stuck claim is released by an administrator with `/map unlock` (decision 9).
- Changing what a builder may do with a map once it holds it.

## Decisions

1. **The claim is an in-memory registry keyed by string.** The type is `EditClaims` in `setup/mapsetup`. It holds one
   `Holder` per key; a `Holder` is the builder's UUID and the builder's name as it was when the claim was taken. The
   brief's working name was `MapClaims`. The name changes because change 8 reuses the registry for cups, and a registry
   named after maps would be wrong there. Keys are `map:<id>` and `cup:<id>`, so the two namespaces stay apart.
   Rejected: a lock file `drafts/<id>.lock` holding the builder's UUID. A lock file outlives a crash, and the next start
   would then need a reaper that decides whether a lock is stale. The registry is empty after every start, which is the
   behaviour wanted.

2. **`BuilderSessions` owns the claim lifecycle.** `BuilderSessions` already runs the two events that end a session (a
   replacement by `open` and the disconnect in `register`, `BuilderSessions.java:25-27`). It takes the claim and gives it
   back in the same methods, so no path can leave a claim behind. Rejected: claims taken and released in `SetupCommands`.
   The disconnect path would then need its own copy of the release.

3. **The order of an open is fixed.** `/map open <id>` does these steps, and any refusal stops the chain before the next
   step runs:
   1. the gate `voyager.setup.use`, as today;
   2. if the builder's current session is for this map, reply that the map is open and stop (same-builder no-op);
   3. load the draft from the store;
   4. look up the map's world with `instances.forWorld` (`SetupCommands.java:183`), which can refuse;
   5. claim the key; a claim held by another builder refuses with the holder's name;
   6. install the new session (`BuilderSessions.open`, which releases the previous map's claim), move the builder into
      the world and give the wand.

   Step 5 comes after step 4 so that a world that cannot be opened takes no claim. Step 6 comes last, so a refused open
   leaves the builder on the map they had. `/map new` uses the same order; a new id has no holder.
   Rejected: claiming first and releasing on failure. It is correct, but every failure path would need a release, and the
   order above has no such path.

4. **The same builder opening the same map is a no-op.** The session is kept, and so is its draft and, after
   `undo-draft-edits`, its history. The reply is the same message as a fresh open. Today the open reloads the draft and
   replaces the session (`SetupCommands.java:132`), which would drop any history; this change keeps it.

5. **The refusal names the holder by the name recorded at the claim.** The holder's name is stored with the claim, so the
   refusal needs no lookup on the main thread. A holder who disconnects releases the claim, so a recorded name is never
   stale while the claim exists. Rejected: asking the connection manager for the holder's online name. The holder can be
   offline when the refusal is written.

6. **The save guard is a sibling method on the port, not a change of `save`.** `DraftStore` gains
   `saveIfUnchanged(MapDraft expected, MapDraft next)` (`voyager/api/.../api/mapsetup/DraftStore.java`, the port is lines
   8-37). `JsonDraftStore` loads the stored copy and compares it with `expected` by the canonical JSON of
   `MapDraftJsonWriter.toJson` (`writer/MapDraftJsonWriter.java:39`) on both sides. A difference, or a stored copy that is
   missing, refuses with `DraftChangedException`. Otherwise the method calls `save(next)`. The brief named
   `JsonDraftStore.save` as the place; a sibling keeps `create` and the existing store tests on the unguarded path, where
   no previous draft exists. Comparing canonical JSON rather than records means a hand-edited file with other whitespace
   or key order is accepted when its values are the same, and that the comparison does not depend on how doubles are
   read back.

7. **`DraftChangedException` is a new final exception, not a subtype of `DraftWriteFailedException`.** That class is `final`
   (`api/mapsetup/exception/DraftWriteFailedException.java`). The callers (`SetupCommands.spawn`, `WandListener.place` and
   `WandListener.remove`) catch `DraftChangedException` first and show a message that says the map changed on disk, and
   they catch `DraftWriteFailedException` as before.

8. **`MapSession.commit` takes the guard.** `commit` calls `saveIfUnchanged(draft, next)` instead of `save(next)`, so every
   edit of this change and of the later changes goes through the guard. If `undo-draft-edits` has landed first, the save sits
   in its private `persist` method, which `commit` calls, and the change is the same one line there. The session's draft is
   the one it last saved, so the guard passes for every edit the builder makes, and only an outside change refuses it.

9. **The release paths, and the administrator's unlock.** A claim is released by `/map close`, by opening another map (after
   the new claim is granted), by the holder's disconnect, and by a failed import. `/map close` is kept: without it, a colleague
   waits for the holder to open another map or to disconnect. `/map unlock <id>` is the one release by another builder. It is
   gated by `voyager.setup.unlock` alone, so an administrator who is not a builder can use it. It releases the claim of
   `map:<id>` whoever holds it, and it ends the holder's session on that map, so the holder cannot keep editing a map it no
   longer holds. The holder is not messaged, as on a disconnect. The unlock does not open the map for the administrator, who
   runs `/map open` like any builder. An unlock of a map that nobody holds replies that the map is not held and changes
   nothing. `EditClaims` has a separate method `unlock(key)` that returns the released holder, so that `BuilderSessions` can end
   that holder's session; `release(key, builder)` keeps its rule that only the holder releases.
   Rejected: a takeover that hands the claim to the administrator, which would also need the administrator to hold the map
   first. Rejected: a restart as the only way out of a stuck claim, because a restart clears every other claim as well.

## ADR-0044: Single editor per map through in-memory claims and a save guard

Status: **Proposed**. The text moves to `docs/decisions/0044-single-editor-map-claims.md` in the code pull request.

Number: 0044 is reserved for this change. The numbers 0040 and 0041 are taken, and 0042, 0043 and 0045 belong to changes 1,
2 and 8.

### Context and problem statement

Two builders who open one map each hold a copy of its draft. Each save writes the whole file, so the second save replaces
the first without an error. The setup server runs as one process, so the problem is local to the process, and a second
setup server pointed at the same data directory is the only way to meet it from outside.

### Decision drivers

- No save may silently replace another builder's rings.
- A refusal must say who holds the map, so the builder knows whom to ask.
- Nothing on disk may block a later start after a crash.
- The rule must cover the cups of change 8 without a second mechanism.

### Considered options

1. **In-memory claims plus a save guard** (chosen). A claim registry keyed by string, owned by the session lifecycle, and
   a save that compares the stored copy with the session's copy.
2. **Lock files** `drafts/<id>.lock` with the holder's UUID. They work across processes, but a crash leaves a stale file
   and a reaper would have to decide when a lock is stale.
3. **The save guard alone**, with no claims. A second builder would then only learn of the conflict when a save is
   refused, after both have edited.
4. **Claims stored in the draft file.** They would survive a restart, but a restart would then need the same reaper as
   option 2, and a claim written into the map file would reach the game server's reader.

### Decision outcome

Option 1. The claim gives the builder a refusal before any edit. The save guard covers what a claim cannot see. Nothing is
written for a claim, so nothing can be stale after a crash. The cost is that a claim does not cross processes, which the
guard covers.

### Consequences

- Good: two builders in one server cannot edit one map, and the refusal names the holder.
- Good: a stale copy from a second setup server or an outside edit is refused at save time, with the file unchanged.
- Good: no lock file and no reaper.
- Good: an administrator releases a stuck claim with `/map unlock`, without a restart.
- Bad: a second setup server pointed at the same data directory is not refused at open; it is refused at save. The
  builder finds out at the first edit, not at the open.
- Bad: the guard's compare and the write are two steps. An outside writer that acts in the gap between them is not
  refused. The gap is a few milliseconds of local file access, and the decision accepts it.
- Neutral: the registry is empty after every start. A builder who held a map before a restart opens it again.

### Confirmation

`EditClaimsTest` covers the registry. `JsonDraftStoreTest` covers the guard with two store instances on one `@TempDir`,
which stands for two processes. The `Env` tests in the setup module cover the refusal, the release paths and the gate.

## Dependency direction

- `api`: the `DraftStore` method `saveIfUnchanged`, the exception `DraftChangedException` in
  `net.elytrarace.voyager.api.mapsetup.exception`, and the `PermissionNode` value `VOYAGER_SETUP_UNLOCK`
  (`voyager.setup.unlock`) in `api/permission`. No new dependency.
- `setup/mapsetup`: `EditClaims` and its nested `Holder` record, in `net.elytrarace.voyager.setup.mapsetup`. It depends on
  `java.util` only. Its fitness rule is `setupDomainOnlyDependsOnApiAndJdk` (`ApiPurityTest`, the rule over
  `net.elytrarace.voyager.setup.mapsetup..`).
- `setup/adapter`: `BuilderSessions` takes the registry in its constructor; `SetupCommands` adds `close`, `unlock` and the
  order of the open; `MapSession.commit` calls the guard; `WandListener` catches the new exception. These are Minestom-facing
  adapters and depend inward on `setup/mapsetup` and `api`, as they do today.
- `setup/inject`: `SetupBeans` creates the `EditClaims` bean and hands it to `BuilderSessions`. The DI annotations stay in
  the composition root; `onlyServerAndSetupDependOnDiContainer` (`ApiPurityTest`) keeps that rule.
- `platform/catalog`: `JsonDraftStore` implements the new port method and uses `MapDraftJsonWriter`, which it already
  uses. The platform does not depend on the setup slice (`serverAndPlatformAndApiDoNotDependOnSetup`).

No new edge between two slices is added. Every new boundary is covered by an existing rule: the registry by
`setupDomainOnlyDependsOnApiAndJdk`, the new API type by `DesignRuleTest` (exception naming and hierarchy) and
`ApiPurityTest` (no file I/O in `api`). No new fitness rule is proposed.

## Testing

Every test follows F.I.R.S.T.

- `EditClaimsTest` (plain JUnit, `setup/mapsetup`): a fresh `EditClaims` per test; fixed UUID constants, no random values;
  no clock is read, because the registry has no time. No shared static state. It covers `unlock(key)`, which frees a held key
  whoever holds it and returns the released holder, and which returns nothing for a free key.
- `JsonDraftStoreTest` (platform, `@TempDir`): two `JsonDraftStore` instances over one directory play two setup servers.
  The guard refuses the second writer and the file keeps its bytes. A hand-written file with other whitespace and key
  order is accepted when its values are equal.
- Minestom tests (`@EnvTest`, one `Env` per test, `env.tick()` driven, no sleeps): two `TestConnection` builders, the
  refusal message read through the translation key as `SetupPermissionTest` does, and the disconnect through the
  testing connection.
- `SetupPermissionTest` gains one test per subcommand (`new`, `open`, `close`, `unlock`, `spawn`, `status`) for a builder
  without the node: no draft is read, no claim is taken, no session is opened.
- Save guard in `MapSession`: a draft changed by a second store makes `commit` throw `DraftChangedException`, and the
  session keeps the draft it had.

No test reads the system time, sleeps, or touches a path outside its `@TempDir`.

## Open questions

1. **Whether the refused builder should be told that the holder can be asked.** **Recommendation: no.** The refusal names
   the holder and nothing more. Owner decision.
