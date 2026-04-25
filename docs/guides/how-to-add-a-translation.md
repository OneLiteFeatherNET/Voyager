# How to add a translation

This guide explains how the Voyager translation system works and how to add new translatable strings.

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
| `server` | `server/src/main/resources/elytrarace_en_US.properties` |
| `plugins/setup` | `plugins/setup/src/main/resources/elytrarace.properties` |
| `plugins/game` | `plugins/game/src/main/resources/elytrarace.properties` |

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
