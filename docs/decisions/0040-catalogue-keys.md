# ADR-0040: Catalogue identifiers are Adventure keys

## Status

Proposed, on 2026-10-28, with the change `add-catalogue-keys-and-authors`. Not accepted until the owner accepts it.

## Date

2026-10-28

## Decision makers

- TheMeinerLP (project owner), who decided that maps and cups are identified by a key in the `elytrarace` namespace and
  that they carry their authors, and who approved the shipped keys and authors.

## Context and problem statement

A map and a cup are identified by a flat string such as `elytraraceblueandred`. That one string is four things at once: the
key of the catalogue, the reference from a cup to its maps, the name an operator types, and the key the statistics store.
A flat string cannot say which namespace it belongs to, so it cannot be told apart from another source of the same name.

Players also see the flat id, because a display name exists only when a file states one. A map has no author, so the game
cannot say who built the course a racer is flying.

## Decision drivers

- One identifier format for the whole game, shared with Adventure's own keys: the tutorial dialogs already use
  `net.kyori.adventure.key.Key` (`TutorialPages.CLOSE_KEY`).
- The API module stays free of platform and of text libraries (`ApiPurityTest`, ADR-0017).
- No file that loads today stops loading.
- Statistics history is kept.

## Considered options

1. **Adventure `Key` in `voyager-api`** (chosen). The records use `net.kyori.adventure.key.Key` directly.
2. **An own record `CatalogKey(namespace, value)` in `voyager-api`**, with the same string form, converted to an Adventure
   `Key` in the platform.
3. **Keep a flat `String` and prefix it in the platform**, with no type in the domain.

## Decision outcome

Option 1. The key is a value type from a library that is already on the classpath of every module that needs it, and it is
the type Adventure's APIs take for translation and dialog identifiers. Option 2 keeps the API free of the library, but it
duplicates the validation Adventure already does and needs a converter wherever a key reaches Adventure. Option 3 keeps the
domain string-typed, which is the problem this decision removes.

The rules are these. A key's namespace is `elytrarace`. Its value is `[a-z0-9][a-z0-9_-]{0,31}`, the rule the setup server
already applies to a map id. A version 1 file's flat `name` is read as `elytrarace:<name>`, and a version 2 file states its
`key` in full. The API keeps Adventure to its key package, enforced by `ApiPurityTest`.

## Consequences

- Good: a key is a single value with one string form, and the same type is used in the API, the platform and the translation
  layer.
- Good: a flat id from before this decision maps to a key by one rule, `elytrarace:<id>`.
- Good: a map or cup carries its authors as a UUID and a name, so the game can name its builders without a lookup at load.
- Bad: `voyager-api` gains a library dependency. It is a single package of Adventure, so the cost is small, and the fitness
  rule keeps it at that.
- Bad: the file format changes, and the statistics rows of the flat ids are rewritten once by Flyway version 2.
- Neutral: operators type the flat form during the transition. The owner decides when it is removed.
- Known debt: the setup server's platform writer and adapter (`MapDraftJsonWriter`, `MapDraftAdapter`) sit outside
  `platform.mapsetup`, and the architecture baseline records their edges to the draft types. Moving them is a later refactor.

## Confirmation

`ApiPurityTest.apiUsesAdventureOnlyForKeys` forbids Adventure types other than the key package in `voyager-api`, and
`ApiPurityRulesSeededTest` proves the rule catches a violation. `CatalogKeysTest` covers the namespace and value rules,
`MapDefinitionAdapterTest` and `CupDefinitionAdapterTest` cover both file versions, and `CatalogueKeysMigrationIT` runs the
statistics rewrite over rows written at version 1 on PostgreSQL.
