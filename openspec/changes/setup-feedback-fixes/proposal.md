# Proposal

**Type and scope:** `fix(setup)`. Ships as one code change under this title. It touches the `setup` slice only: the adapter
(`setup/adapter`), the message bundles, and the messages in `platform/text`. No change to `api`, `race`, the game server or
the stored draft format.

## Why

A builder who mistypes a command or clicks in the wrong world gets a refusal that is not written for them. The text of a
domain exception is English and is pasted into the German bundle: `SetupCommands.java:124, 147, 159, 202, 266` pass
`exception.getMessage()` to `SetupMessages.refused`, and `DraftNotFoundException` builds "no draft named '%s' exists"
(`voyager/api/.../mapsetup/exception/DraftNotFoundException.java:14`). A German builder reads "Abgelehnt: no draft named
'canyon' exists" (`voyager/setup/src/main/resources/voyager_setup_de_DE.properties:8`). The same command also answers an
invalid id in two ways: `create` uses `invalidId` (`SetupCommands.java:115`), while `open` (`:147`) and `unlock` (`:202`)
fall back to the raw exception text. A wand click in a map that is not the builder's open world is not answered at all:
`WandListener.openSession` returns null with no message (`WandListener.java:149-157`), and `place` and `remove` return quietly
(`:92`, `:122`), so the builder clicks again and sees nothing change.

The fix is small and it is visible to every builder on every refusal, so it goes first.

## What Changes

- Each domain exception that reaches a command (draft not found, draft location conflict, invalid draft, unknown world,
  invalid map id, I/O failure on create) maps to one `SetupMessages` key with its arguments, following the mapping that
  `ImportCommand.refusalLine` already uses for import refusals.
- `/map open <id>` for a missing draft replies with the not-found key and the hint "use `/map new <id>`".
- The `refused(String)` overload that takes raw text is removed from `SetupMessages` once no caller passes an exception
  message. An ArchUnit rule in `voyager-fitness` asserts that no setup adapter class passes `getMessage()` to `refused`, so the rule survives a later edit.
- An invalid id gets `invalidId` in every command that takes an id (`new`, `open`, `unlock`), never the raw text.
- A wand click while the builder stands in a world other than the open map's world is answered with one line naming the
  open map and telling the builder to stand in it. The line is sent at most once per map per 5 seconds, so a rapid
  series of clicks does not flood chat. The 5-second window is read from an injected `Clock`.
- Every new key exists in `voyager_setup_en_US.properties` and `voyager_setup_de_DE.properties` with the same order and
  placeholders, checked by `MessageBundleParityTest`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `setup/map-commands`: the requirement "Opening a map loads its draft" gains the not-found hint, and a new requirement
  states that a refusal names its cause in the builder's language and never shows raw exception text.
- `setup/ring-placement`: a new requirement covers the one-line answer to a wand click outside the open map's world.

## Out of scope

- The `importFailed(String.valueOf(failure.getMessage()))` line in `ImportCommand` (`:143`). It has the same shape and is
  listed as an open question in `design.md`; it is not changed here unless the maintainer asks.
- Localising the domain exception messages themselves. They stay English, because they are developer-facing and never
  reach the builder once the adapter maps them.
- Any change to what a command does. Only the words of a refusal and the answer to a wand click change.

## Trade-offs

### Pros

- One localised refusal per cause, the same for every command.
- Removing the raw-text overload makes the rule a compile-time property, not a convention.
- A wand click in the wrong world is answered once, so the builder knows why nothing happened.

### Cons

- Several new keys in both bundles, and each German line is a translation to review.
- The 5-second window needs a clock and a per-builder memory of the last answer, which is one more small state in the
  session.
- The `ImportCommand` path keeps raw text until its own follow-up, so the rule is not yet uniform.

## Impact

- `voyager/setup`: `SetupCommands`, `WandListener`, `SetupMessages` usage, both message bundles.
- `voyager/platform/text`: the new message methods.
- `voyager/api`: no change; the exception messages stay as they are.
- Docs: a short note in `docs/guides/how-to-build-a-map-with-setup.md` on what a refusal means and the one-line wand answer.
