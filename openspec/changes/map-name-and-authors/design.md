# Design

## Context

The setup server's `/map` root has `new`, `open`, `spawn` and `status` (`voyager/setup/.../adapter/SetupCommands.java:71-78`).
Each subcommand is gated per subcommand by `voyager.setup.use` (`SetupCommands.java:85-91`), and each edit goes through
`MapSession.commit`, which saves before it keeps the new draft (`voyager/setup/.../adapter/MapSession.java:52-56`).

The draft carries the fields this change sets: `displayName` and `authors` (`voyager/api/.../api/mapsetup/MapDraft.java:32-34`),
and `referenceTimeSeconds`, which `MapDraft` validates as finite and above zero (`MapDraft.java:68-70`). The reference time is
set to 60 seconds by default (`voyager/setup/.../mapsetup/RingDefaults.java:25`).

Three facts from the code shape this design:

1. **`DraftEditor.with` drops the metadata.** It rebuilds the draft through the constructor that resets `displayName` and
   `authors` (`voyager/setup/.../mapsetup/DraftEditor.java:76-79`, `MapDraft.java:43-46`). Change 6 fixes it. This change
   needs the fix: a name set by `/map name` would be gone after the next ring.
2. **The catalogue refuses a long display name when it reads the file.** `JsonFields.displayName` refuses more than
   `DISPLAY_NAME_LIMIT` = 40 characters, counting the text as the file holds it, MiniMessage tags included
   (`voyager/platform/.../catalog/adapter/JsonFields.java:21-25, 104-117`). A name the setup server saved over 40 characters
   would make the game refuse the map. The 40-character limit is therefore binding; the 32 visible characters of the brief
   is a stricter rule on top of it.
3. **Authors are data, and a lookup is I/O.** `Author` checks the name shape and the count (`voyager/api/.../api/race/Author.java:21-23,
   40-51`). The catalogue load does no network I/O (`openspec/changes/add-catalogue-keys-and-authors/proposal.md`), so the
   network call belongs to the setup server, behind a port.

## Goals / Non-Goals

Goals:

- A builder can set the display name, add and remove authors, and set the reference time, from the setup server.
- Every edit is saved before the reply, keeps every other field of the draft, and is refused without `voyager.setup.use`.
- The file the setup server writes is one the catalogue reads without a refusal.
- No lookup runs on the tick thread, and no test touches the network.

Non-Goals:

- Undo, claims, cups, rendering of names in the game, an author cache, and the author list of a cup (see the proposal).

## Decisions

1. **Command grammar: the name is the rest of the line.** `/map name <MiniMessage>` takes the whole remaining text, so a name may
   contain spaces. `/map name clear` removes the name. The word `clear` alone is therefore reserved; a name that is the word
   itself is written `<white>clear`. Rejected: `/map name --clear` (a flag nobody else in the setup server uses) and a separate
   `/map name reset` (a second reserved word with the same problem).

2. **The stored name is the source text, parsed strictly.** The file keeps the text as written (`JsonFields.displayName`), so
   the setup server stores the MiniMessage source it parsed. The parse uses MiniMessage's strict mode, so an unknown tag
   is a parse error with a position. The first Red task asserts this on the library itself, so a library default that accepts
   unknown tags fails before any message is written. Two limits apply: the stored source has at most 40 characters (the reader's limit, read
   from one constant), and the visible text has at most 32 characters (owner to confirm, open question 1). Rejected: storing the
   rendered plain text, which would lose the colours that `show-colored-map-and-cup-names` is about to show.

3. **A blank or clear name removes the name.** This matches the reader: a blank `displayName` is the same as an absent one
   (`JsonFields.displayName`, the blank branch). Both removal forms write no `displayName` key.

4. **Online players are resolved from the server; offline players from the profile service.** The order is fixed:
   (a) an online player with that name, read on the tick thread, with the UUID and the current name the server holds;
   (b) otherwise, `GET https://api.mojang.com/users/profiles/minecraft/<name>` through the port `PlayerLookup`, run on a worker
   thread with a timeout of 5 seconds (owner to confirm, open question 2). The service returns the UUID undashed; the adapter
   dashes it, because `Author` and the catalogue use the dashed form. The name stored is the one the service returns, so its
   letter case matches the account. Rejected: a lookup for online players too (a network round trip for a player the server
   already knows); rejected: blocking the tick for the call (a stall of up to five seconds at 20 TPS).

5. **A lookup result is applied only to the map it was asked for.** The worker completes a `CompletableFuture`; the completion
   is handed back to the tick thread through the server's scheduler. On the tick thread the result is applied only if the
   builder's open session is the same `MapSession` instance that issued the lookup. Otherwise the result is discarded and the
   builder is told. Rejected: applying to whatever map is open then (it could add the author to a different map).

6. **Removal is by name, ignoring case.** Minecraft usernames are compared without case, and a builder types the name he
   remembers. Removal by UUID is not offered, because a builder cannot read a UUID in the game. Rejected: removal by index
   (the list order is not shown anywhere a builder reads).

7. **Reference time is a decimal in seconds, from above zero to 3600.** The draft holds a `double`, so a decimal is accepted,
   and `NaN` and infinities are refused by the same check `MapDraft` makes. The maximum of 3600 seconds keeps a reference lap
   from being set to an hour by a typo (owner to confirm, open question 3).

8. **All edits are pure functions over the draft, and every edit saves through `MapSession.commit`.** A new `MetadataEditor`
   (an abstract utility class, `@ApiStatus.Internal`, private constructor, `@Contract(pure = true)`, like `DraftEditor`) builds
   the next draft with the canonical `MapDraft` constructor, so it keeps every field. The commands call `commit`, so a
   failed save keeps the previous draft, and change 4's undo history will see each edit without a second path.

9. **No lock is added for these commands.** A command acts on the builder's own open map. Change 5 makes sure that two builders
   do not have one map open, so the commands need no claim check of their own.

**No ADR.** None of these decisions alters the greenfield design (D1 to D16 in
`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`), the catalogue format or a permission node. The profile lookup
is a port with one adapter, and it is recorded in this design rather than in a separate ADR.

## Dependency direction

| Type | Module | Package | Note |
|---|---|---|---|
| `PlayerLookup` (interface, `Author resolve(String name)`, blocking) | `voyager-api` | `net.elytrarace.voyager.api.mapsetup` | pure port, no I/O in the API |
| `PlayerLookupFailedException`, `PlayerLookupTimeoutException`, `PlayerNotFoundException` | `voyager-api` | `net.elytrarace.voyager.api.mapsetup.exception` | `RuntimeException`, domain-named (design rule 9) |
| `DISPLAY_NAME_LIMIT` | `voyager-api` | `net.elytrarace.voyager.api.race` | moved from `JsonFields`; the reader and the setup server read one constant |
| `MojangProfileLookup` (implements `PlayerLookup`, JDK `HttpClient`, 5 s timeout) | `voyager-platform` | `net.elytrarace.voyager.platform.profile` | new package, with `package-info.java` and `@NotNullByDefault` |
| `MetadataEditor`, `MapName`, `ReferenceTime` (parsers and rules) | `voyager-setup` | `net.elytrarace.voyager.setup.mapsetup` | pure; MiniMessage parse is an Adventure call, which the setup slice already uses |
| `AuthorLookup` (runs the port off the tick, applies the result on the tick) | `voyager-setup` | `net.elytrarace.voyager.setup.adapter` | uses no Minestom type; the online-player check and the main-thread hand-back are two small interfaces that `SetupServer` implements (see the fitness rules) |
| new subcommands, messages | `voyager-setup` | `net.elytrarace.voyager.setup.adapter`, `voyager/setup/src/main/resources` | |

Dependency direction: `voyager-setup` depends on `voyager-api` and `voyager-platform` (as it does today); `voyager-platform`
depends on `voyager-api`; `voyager-api` depends on nothing here. The profile adapter lives in the platform because it is an
outbound HTTP adapter, and the setup slice sees only the port. The setup slice does not depend on the game server.

### Fitness rules

- Covered by existing rules: `ApiPurityTest` (the port and the exceptions have no I/O and no platform types);
  `NullabilityConventionTest` (`platform.profile` and the new API package need `package-info.java`);
  `DesignRuleTest` (exceptions are runtime exceptions with domain names); `PermissionBoundaryTest.commandsAskOnlyThePolicy`
  (the new subcommands ask the policy and nothing else); the setup bootstrap rule that setup does not import the game server.
- **Frozen rule, kept clean:** `SliceBoundaryRulesTest.r6_setupAdapterDoesNotUseMinestom` is frozen. `AuthorLookup` is new and
  must not add a violation, so it uses no `net.minestom..` type. `SetupServer` (the composition root, which r6 exempts) implements
  the two interfaces it needs, one that answers whether a player with a name is online and one that runs a task on the tick
  thread. `SetupCommands` already uses Minestom and is already one of the recorded violations; the new subcommands add none.
- **Proposed:** `TickBoundaryTest.noBlockingOnFuturesOnTheTick` covers only `net.elytrarace.voyager.race..` and
  `net.elytrarace.voyager.platform..`. This change adds the setup slice's lookup runner to that rule, so that a future
  `join()` on the tick in the setup server fails the build. Adding a package to a frozen rule may need a store entry; if it
  does, the apply stops and reports, as `add-catalogue-keys-and-authors` requires.

## Testing

Every test follows F.I.R.S.T.:

- **Fast and Repeatable:** the lookup is a fake `PlayerLookup` that returns, refuses or throws the timeout exception; no test
  makes an HTTP call. The worker runs on a direct executor in tests, so no test waits on a thread. No test reads the system
  time; the 5-second timeout is a value in the adapter, asserted on the request the adapter builds, not a wall-clock wait.
- **Independent:** each test builds its own `MapSession`, `BuilderSessions`, fake lookup and `JsonDraftStore` over a
  `@TempDir`. No static state.
- **Self-validating:** every test asserts the draft, the file or the reply text, not a log line.
- **Timely:** each behaviour starts with a failing test (Red/Green TDD).
- **Pyramid:** the parsers and the editor are plain JUnit tests; `SetupCommandsTest` and `SetupPermissionTest` drive the
  commands; one test round-trips a named, credited draft through `JsonDraftStore` and reads it back with the game's
  `MapDefinitionAdapter`.

## Open questions

Each question is for the owner. The recommendation is the option in the decision text above.

1. **Visible name length.** The brief proposes 32 visible characters; the file limit of 40 stored characters is binding in
   any case. Recommendation: keep 32 visible characters, and the 40-character stored limit on top of it.
2. **Lookup timeout.** Recommendation: 5 seconds, as decided in the brief. A longer wait holds the builder's command open.
3. **Reference time maximum.** Recommendation: 3600 seconds. A value above it is refused, and the builder can ask the owner
   for more.
4. **Name rendering.** Does `show-colored-map-and-cup-names` render `displayName` as MiniMessage? If it renders plain text, a
   builder's colours would appear as raw tags. Recommendation: render as MiniMessage, which is what this change stores; the
   display change must confirm it before this change ships.
5. **Author cache.** Recommendation: no cache in this change; each offline `add` is one request. Revisit if the profile service
   rate-limits the setup server.
6. **`clear` as a reserved word.** Recommendation: keep the rule in decision 1 and document the `<white>clear` form in the guide.
