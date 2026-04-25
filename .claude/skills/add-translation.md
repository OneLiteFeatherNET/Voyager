---
name: add-translation
description: Add a new i18n translation key to Voyager correctly, enforcing <arg:N> placeholder syntax.
---
# Add Translation

Guide for adding a new user-facing string to Voyager's i18n system. Follows the rules in `docs/guides/how-to-add-a-translation.md` and enforces the `<arg:N>` placeholder format.

## Inputs required

Before starting, collect:

- **Translation key** — must be in the `voyager.` namespace (e.g. `voyager.game.ring.collected`)
- **English text** — the full string, with placeholders written as `<arg:0>`, `<arg:1>`, etc.
- **Argument count** — how many runtime values are injected (0 if none)
- **Usage location** — the Java class and method where `Component.translatable(...)` will be called

## Critical rule — placeholder format

NEVER use `{0}` or `{N}` (MessageFormat syntax). `PluginTranslationRegistry` disables the MessageFormat path, so `{0}` renders as the literal text `{0}` in-game.

ALWAYS use `<arg:N>` (zero-indexed MiniMessage syntax):

```properties
# CORRECT
voyager.game.ring.collected=<green>Ring collected! Points: <arg:0>

# WRONG — {0} appears as literal text in-game
voyager.game.ring.collected=<green>Ring collected! Points: {0}
```

## Steps

### 1. Add the key to the properties file

Choose the correct file for the module where the string is displayed:

| Module | Properties file |
|---|---|
| `server` (game/Minestom) | `server/src/main/resources/elytrarace_en_US.properties` |
| `plugins/setup` | `plugins/setup/src/main/resources/elytrarace.properties` |
| `plugins/game` (legacy) | `plugins/game/src/main/resources/elytrarace.properties` |

Append a new line at the end of the appropriate file. Use the `voyager.` key prefix and `<arg:N>` for every placeholder:

```properties
# No arguments
voyager.game.ring.collected=<green>Ring collected!

# One argument
voyager.game.ring.collected=<green>Ring collected! Points: <arg:0>

# Multiple arguments
voyager.game.ring.collected=<green><arg:0> collected a ring! Points: <arg:1>
```

Existing keys in `server/src/main/resources/elytrarace_en_US.properties` serve as a style reference:

```properties
phase.lobby.player.join=<lang:plugin.prefix> <green><arg:0> <white>joined the game (<arg:1>/<arg:2>)
hud.actionbar=<white>Speed: <arg:0> m/s | Points: <arg:1>
end.score_line=#<arg:0> <arg:1> — <arg:2> pts
```

### 2. Use the key in Java

Call `Component.translatable(String key, Component... args)`. Each argument must be a `Component`:

```java
// No arguments
Component msg = Component.translatable("voyager.game.ring.collected");

// One argument
Component msg = Component.translatable("voyager.game.ring.collected",
    Component.text(points));

// Multiple arguments
Component msg = Component.translatable("voyager.game.ring.collected",
    Component.text(playerName),
    Component.text(points));
```

### 3. Special render contexts (Paper plugins only)

Two Paper contexts do not use automatic translation and need an explicit `GlobalTranslator.render()` call:

**Inventory titles** (constructed once at creation time):

```java
Component title = GlobalTranslator.render(
    Component.translatable("voyager.gui.some.title", arg0),
    Locale.US
);
Inventory inv = Bukkit.createInventory(null, 54, title);
```

**`TextDisplay` entity labels**:

```java
textDisplay.customName(GlobalTranslator.render(
    Component.translatable("voyager.entity.some.label", Component.text(index)),
    Locale.US
));
```

This step is not needed in the `server` (Minestom) module — `MinestomAdventure.AUTOMATIC_COMPONENT_TRANSLATION = true` handles rendering automatically.

### 4. Run the build to verify

```bash
./gradlew build
```

A clean build confirms that:
- The key exists in the properties file (no missing-key warning at runtime)
- The Java code compiles with the correct `Component.translatable(...)` signature
- The key-coverage tests (`TranslationKeysCoverageTest`, `SetupTranslationKeysTest`) pass — these tests fail if any value still contains `{N}` syntax

## Output

- One new line in the correct `.properties` file
- One or more `Component.translatable(...)` call sites in Java
- A green build with passing translation coverage tests

## Common mistakes to avoid

| Mistake | Correct approach |
|---|---|
| `{0}` placeholder | Use `<arg:0>` |
| Key without `voyager.` prefix | Always start with `voyager.` |
| Passing a raw `String` as argument | Wrap with `Component.text(value)` |
| Adding the key to the wrong module's file | Match the file to the module where the string is displayed |
| Skipping the build step | Always run `./gradlew build` to catch coverage test failures |
