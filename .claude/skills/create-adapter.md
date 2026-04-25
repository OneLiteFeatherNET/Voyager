---
name: create-adapter
description: Create a new Gson JsonDeserializer adapter for a JSON-backed domain type. Use when adding new map format fields, cup properties, or component types.
---

# Create Gson Deserializer Adapter

Guide through creating a Gson `JsonDeserializer` adapter following the ManisGame Rule 8 conventions enforced in this project.

## Input Required

Before starting, collect:
- **Type name** — the Java type being deserialized (e.g., `Ring`, `CupDefinition`, `PlayerStats`)
- **Adapter kind** — regular domain adapter or component adapter (determines subpackage)
- **JSON structure** — list of expected JSON fields and their Java types

## Steps

### 1. Determine the correct subpackage

| Adapter kind | Package | Directory |
|---|---|---|
| Regular domain adapter | `net.elytrarace.common.adapter` | `shared/common/src/main/java/net/elytrarace/common/adapter/` |
| Component adapter | `net.elytrarace.common.adapter.component` | `shared/common/src/main/java/net/elytrarace/common/adapter/component/` |

The class name must follow the pattern `{TypeName}Adapter` (e.g., `RingAdapter`, `TitleComponentAdapter`).

### 2. Create the adapter class

Create `{TypeName}Adapter.java` in the correct directory using this template:

```java
package net.elytrarace.common.adapter; // or adapter.component

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public final class XxxAdapter implements JsonDeserializer<Xxx> {

    @Override
    public Xxx deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        // Extract each field — use context.deserialize() for nested types
        // e.g. String name = obj.get("name").getAsString();
        //      int count   = obj.get("count").getAsInt();
        return new Xxx(/* parsed fields */);
    }
}
```

Rules to follow:
- The class is `final` — no subclassing.
- No public constructor needed beyond the implicit default.
- Use `context.deserialize(element, NestedType.class)` for any nested domain objects rather than inlining manual parsing.
- Throw `JsonParseException` (not `IllegalArgumentException`) when required fields are missing or invalid.

### 3. Register the adapter on GsonBuilder

Locate where the relevant `GsonBuilder` is configured (usually the service that owns the domain type, e.g., `MapService`, `CupService`, or a dedicated factory). Pass the built `Gson` to `GsonFileHandler`:

```java
Gson gson = new GsonBuilder()
        .registerTypeAdapter(Xxx.class, new XxxAdapter())
        // ... other adapters
        .create();
GsonFileHandler handler = new GsonFileHandler(gson);
```

If no custom `GsonBuilder` exists yet for that service, introduce one now. Do NOT modify the zero-arg `GsonFileHandler()` constructor — it is intentionally left with the default Gson for simple cases.

### 4. Add a `package-info.java` if it does not exist

Every package must declare `@NotNullByDefault`. If `adapter` or `adapter.component` is a new package, create:

```java
@NotNullByDefault
package net.elytrarace.common.adapter;

import net.elytrarace.common.annotation.NotNullByDefault;
```

(Adjust the package declaration for `adapter.component` as needed.)

### 5. Write the JUnit 5 test

Create the test at:
- Regular: `shared/common/src/test/java/net/elytrarace/common/adapter/{TypeName}AdapterTest.java`
- Component: `shared/common/src/test/java/net/elytrarace/common/adapter/component/{TypeName}AdapterTest.java`

Minimum test coverage:

```java
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XxxAdapterTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Xxx.class, new XxxAdapter())
            .create();

    @Test
    void deserialize_validJson_returnsExpectedObject() {
        String json = """
                {
                  "field1": "value1",
                  "field2": 42
                }
                """;
        Xxx result = gson.fromJson(json, Xxx.class);
        assertThat(result.field1()).isEqualTo("value1");
        assertThat(result.field2()).isEqualTo(42);
    }

    @Test
    void deserialize_missingRequiredField_throwsJsonParseException() {
        String json = "{}";
        assertThatThrownBy(() -> gson.fromJson(json, Xxx.class))
                .isInstanceOf(com.google.gson.JsonParseException.class);
    }
}
```

### 6. Verify with /build

Run `/build` to confirm compilation succeeds and the new test passes.

## Output

- `{TypeName}Adapter.java` in the correct `adapter` or `adapter.component` subpackage
- `{TypeName}AdapterTest.java` with happy-path and error-path coverage
- Updated `GsonBuilder` registration in the owning service
- `package-info.java` if the package is new
- Clean build with all tests green
