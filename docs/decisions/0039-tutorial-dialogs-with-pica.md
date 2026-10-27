# ADR-0039: Tutorial dialogs use the Pica library

## Status

Proposed, on 2026-10-27, with the change `cygnus-style-lobby`. Not accepted until the owner accepts it.

## Date

2026-10-27

## Decision makers

- TheMeinerLP (project owner), who asked for the tutorial book and for Pica.

## Context and problem statement

A waiting racer opens a tutorial from a hotbar book. The tutorial has five pages with Back, Next and Close buttons, in the
racer's language. Minestom 2026.10.07-26.2 can send dialogs, but building a multi-page dialog with buttons is verbose, and the
rest of the OneLiteFeather games (Cygnus) build the same tutorial with Pica.

## Decision drivers

- Buttons with actions for Back, Next and Close, per page, in the racer's language.
- The same pattern as Cygnus, so one team maintains one way to build dialogs.
- One new dependency with a clear version pin and a clear repository.
- No new state: a dialog is built per racer and not stored.

## Considered options

1. **Pica 0.1.6 (`net.onelitefeather:pica`), used only in `platform.tutorial` (chosen).** The same library Cygnus uses.
2. **Hand-built dialog packets through the Minestom API.** No dependency, but the page model, the buttons and the packet
   layout are written and tested here.
3. **A vanilla written book.** Native paging, no buttons, no per-page actions, and the book text is an item stored in the
   player's inventory.
4. **Chat pages.** Simple, but there are no buttons, and the pages scroll away.

## Decision outcome

Option 1. The library is pinned at `0.1.6` in the programmatic version catalogue of `settings.gradle.kts`, because the
`aonyx-bom` (0.8.9) that the platform already imports could not be read anonymously to confirm that it manages Pica. The
dependency resolves from `https://repo.onelitefeather.dev/releases`; the Pica 0.1.6 POM answers HTTP 200 there without
credentials and depends on the Minestom version the platform resolves. Pica is imported only by
`net.elytrarace.voyager.platform.tutorial`, enforced by the `voyager-fitness` rule `PicaBoundaryTest`.

### Consequences

- Good: one dialog library across the OneLiteFeather games, with the buttons and the actions provided.
- Good: the page model stays pure and testable; the library is confined to one package.
- Bad: a new dependency to keep current. The version is pinned and changed only by a pull request.
- Bad: the library's Minestom version must match the platform's Minestom version. The spike checks that the dialog opens on
  this server.
- Neutral: the owner's Cygnus reference uses Pica 0.1.6 on `origin/main`.

## Links

- `openspec/changes/cygnus-style-lobby/` (proposal, design, specs, tasks).
- `voyager/platform/src/main/java/net/elytrarace/voyager/platform/tutorial/` (the code that uses it).
