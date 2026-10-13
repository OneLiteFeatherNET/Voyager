# How to add a translation

This guide explains how the Voyager translation system works and how to add new translatable strings.

## The rebuild (`voyager-*`): two locales

The rebuild ships German (`de_DE`) and English (`en_US`). The client's locale picks the language, and a key a
German bundle lacks is shown in English. Decision and rationale: ADR-0038.

| Product | English (required) | German |
|---|---|---|
| Game server | `voyager/server/src/main/resources/voyager_en_US.properties` | `voyager/server/src/main/resources/voyager_de_DE.properties` |
| Setup server | `voyager/setup/src/main/resources/voyager_setup_en_US.properties` | `voyager/setup/src/main/resources/voyager_setup_de_DE.properties` |

To add a key:

1. Name it in `Messages` (game) or `SetupMessages` (setup). Use a key constant, never a literal in the method body.
2. Add the line to the English file, then the German file, with the same key, the same `<arg:N>` indices and the same
   `<c-*>`/`<m-*>` tags. Write German with informal "du". Keep `.` as the decimal separator for times.
3. Run `./gradlew :voyager:fitness:test`. `MessageBundleParityTest` fails when a key is missing in either locale, is
   defined in one locale only, changes a placeholder or a tag, uses `{N}`, or lacks a plural form.

Rules the build enforces (`ApiPurityTest`): no `Component.translatable`, no `MiniMessage.deserialize` and no `String`
sent to a `CommandSender` outside `net.elytrarace.voyager.platform.text`. Console output and the `/race` operator
diagnostic are the named exceptions and stay English.

Plurals: a key with a `.one` sibling needs `.one` and `.other` in every locale. The factory picks the form from the count.
No English key uses plurals yet, so the rule is in place but not yet exercised.

The server logs one warning per key and locale at boot for every English line a locale does not translate. The
translation is per key: a missing German line shows English, never the dotted key.

Legacy tree (`legacy/`): the sections below describe it and are unchanged.

## Architecture

The translation pipeline has three layers:

1. **`LanguageService`** — loads `.properties` files from the `lang/` data folder (or falls back to classpath) and registers them with Adventure's `GlobalTranslator`.
2. **`PluginTranslationRegistry`** — wraps Adventure's `TranslationRegistry` and parses values using MiniMessage, so color tags and gradient tags work in translation strings.
3. **`GlobalTranslator`** — Adventure's global translation source. When Minestom has `MinestomAdventure.AUTOMATIC_COMPONENT_TRANSLATION = true`, it renders every outgoing `Component.translatable()` automatically. Paper's Adventure integration does the same for Paper plugins.

## Placeholder format

Translation values use MiniMessage `<arg:N>` syntax (zero-indexed). **Never use `{N}` (MessageFormat syntax)** — `PluginTranslationRegistry` disables the MessageFormat path, so `{0}` renders as the literal text `{0}`.

```properties
# correct
greeting=<green>Hello, <arg:0>!

# wrong — {0} appears literally in-game
greeting=<green>Hello, {0}!
```

## Add a translation key

### Step 1 — add the key to the properties file

Each module has its own properties file:

| Module | File |
|---|---|
| `server` | `legacy/server/src/main/resources/elytrarace_en_US.properties` |
| `legacy/plugins/setup` | `legacy/plugins/setup/src/main/resources/elytrarace.properties` |
| `legacy/plugins/game` | `legacy/plugins/game/src/main/resources/elytrarace.properties` |

Add a new line:

```properties
my.new.key=<yellow>Some text with <arg:0> and <arg:1>
```

### Step 2 — use the key in Java

```java
// No arguments
Component msg = Component.translatable("my.new.key");

// With arguments (each arg must be a Component)
Component msg = Component.translatable("my.new.key",
    Component.text(playerName),
    Component.text(score));
```

### Step 3 — verify

Run the module's tests. The key-coverage tests (`TranslationKeysCoverageTest`, `SetupTranslationKeysTest`) will fail if any value still uses `{N}` syntax.

## Render contexts that bypass automatic translation

Two contexts in Paper plugins cannot rely on automatic rendering:

**Inventory titles** — Paper resolves the component at packet send time using the player's locale, but `Bukkit.createInventory()` needs a pre-rendered title at construction time:

```java
Component title = GlobalTranslator.render(
    Component.translatable("gui.portal.title", mapName),
    Locale.US
);
Inventory inv = Bukkit.createInventory(null, 54, title);
```

**`TextDisplay` entity labels** — entities are not sent through the normal chat pipeline:

```java
textDisplay.customName(GlobalTranslator.render(
    Component.translatable("portal.label.name", Component.text(index)),
    Locale.US
));
```

## Add a new language

1. Create a file named `elytrarace_<language>_<COUNTRY>.properties` (e.g. `elytrarace_de_DE.properties`) in the plugin's `lang/` data folder (created at first startup under `plugins/ElytraRace-Setup/lang/` etc.).
2. Copy all keys from the English file and translate the values.
3. Restart the server — `LanguageServiceImpl.discoverLanguagesFromFolder()` scans the `lang/` folder automatically and loads every file whose name matches the base name pattern.

The locale tag is derived from the filename: `elytrarace_de_DE.properties` → locale `de-DE`. Underscores in the locale part are converted to hyphens.

## Verify

After adding keys and restarting:

- Strings with `Component.translatable("key")` render correctly in-game.
- Missing keys show the raw key name (e.g. `my.new.key`) — a symptom of a typo or a missing rebuild.
- `<arg:N>` placeholders are replaced with the runtime values in order.

## Related topics

- [MiniMessage format reference](https://docs.advntr.dev/minimessage/format.html)
