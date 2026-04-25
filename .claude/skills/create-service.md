---
name: create-service
description: Scaffold a new domain service (sealed interface + DefaultImpl + JUnit 5 test). Use for every new service in the server or shared modules.
---

# Create Domain Service

Scaffold a new service following Voyager's ManisGame design rules: sealed interface, `DefaultXxx` final implementation, static `create()` factory, and a JUnit 5 test class.

## Input

- Service name (e.g., "Scoring", "Cup", "Player")
- Module — defaults to `server`; use `shared/common` only for platform-agnostic services
- Domain subpackage (e.g., "scoring", "cup", "player")
- Key methods to expose on the interface (name, parameters, return type)

## Steps

### 1. Determine file paths

Given service name `{Name}`, module `{module}`, and subpackage `{domain}`:

- Interface:  `{module}/src/main/java/net/elytrarace/{moduleShort}/{domain}/{Name}Service.java`
- Impl:       `{module}/src/main/java/net/elytrarace/{moduleShort}/{domain}/Default{Name}Service.java`
- Test:       `{module}/src/test/java/net/elytrarace/{moduleShort}/{domain}/{Name}ServiceTest.java`
- Pkg-info:   `{module}/src/main/java/net/elytrarace/{moduleShort}/{domain}/package-info.java`

Where `{moduleShort}` is:
- `server` module → `server`
- `shared/common` module → `common`

### 2. Create the sealed interface

```java
package net.elytrarace.{moduleShort}.{domain};

import org.jetbrains.annotations.Contract;
import java.util.Collection;
import org.jetbrains.annotations.Unmodifiable;

public sealed interface {Name}Service permits Default{Name}Service {

    @Contract(pure = true)
    static {Name}Service create() {
        return new Default{Name}Service();
    }

    // Add declared key methods here, e.g.:
    // boolean register(SomeThing thing);
    // @Unmodifiable Collection<SomeThing> getAll();
    // void remove(SomeThing thing);
}
```

Rules:
- `sealed` + `permits Default{Name}Service` — no other permits clause
- Static `create()` is the only public factory; no public constructor on the impl
- Return `@Unmodifiable` collections from any list/collection accessor

### 3. Create the `Default{Name}Service` final implementation

```java
package net.elytrarace.{moduleShort}.{domain};

import org.jetbrains.annotations.Unmodifiable;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class Default{Name}Service implements {Name}Service {

    // Use ConcurrentHashMap as backing store
    private final Map<KeyType, ValueType> store = new ConcurrentHashMap<>();

    // Implement every method declared on the interface
    // Return Collections.unmodifiableCollection(...) for collection accessors
}
```

Rules:
- `final` class — never extend it
- Backing storage: `ConcurrentHashMap` (thread-safe, no explicit synchronization)
- Wrap return values with `Collections.unmodifiableCollection()` / `Collections.unmodifiableMap()` etc.
- No Bukkit (`org.bukkit.*`) or Paper (`io.papermc.*`) imports — forbidden in `server` and `shared` modules

### 4. Check or create `package-info.java`

If `package-info.java` does not already exist for the target package, create it:

```java
@NotNullByDefault
package net.elytrarace.{moduleShort}.{domain};

import org.jetbrains.annotations.NotNullByDefault;
```

### 5. Create the JUnit 5 test class

```java
package net.elytrarace.{moduleShort}.{domain};

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class {Name}ServiceTest {

    private {Name}Service service;

    @BeforeEach
    void setUp() {
        // Given
        service = {Name}Service.create();
    }

    @Test
    void shouldReturnEmptyCollectionInitially() {
        // When
        var result = service.getAll(); // adjust to actual method name

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldRegisterEntry() {
        // Given
        var entry = /* build a test entry */;

        // When
        service.register(entry); // adjust to actual method name

        // Then
        assertThat(service.getAll()).containsExactly(entry);
    }

    // Add negative tests: duplicates, nulls, removal, thread-safety if needed
}
```

Rules:
- Use JUnit 5 (`@Test`, `@BeforeEach`) — no JUnit 4
- Use AssertJ for assertions (`assertThat(...)`) — no Hamcrest, no `assertEquals`
- Structure every test: Given / When / Then (comments required)
- Instantiate via `{Name}Service.create()` in `setUp()`, never `new Default{Name}Service()`
- Include at least: empty-state test, happy-path register test, and one edge-case test

### 6. Verify compilation

Run the build to confirm all new files compile without errors:

```
/build
```

Fix any compilation errors before declaring the skill complete.

## Rules Summary

| Rule | Requirement |
|---|---|
| Interface modifier | `public sealed interface {Name}Service permits Default{Name}Service` |
| Impl modifier | `public final class Default{Name}Service implements {Name}Service` |
| Factory | `static {Name}Service create()` on the interface, returning `new Default{Name}Service()` |
| Backing storage | `ConcurrentHashMap` — always |
| Collection return | Wrap with `@Unmodifiable` + `Collections.unmodifiable*()` |
| Forbidden imports | `org.bukkit.*` and `io.papermc.*` in `server` and `shared` modules |
| Package annotation | `@NotNullByDefault` in `package-info.java` for every new package |
| Test framework | JUnit 5 + AssertJ, Given-When-Then structure |
| Test instantiation | Always via `{Name}Service.create()` — never `new Default{Name}Service()` |

## Output

- `{Name}Service.java` — sealed interface with `create()` factory and declared methods
- `Default{Name}Service.java` — final implementation backed by `ConcurrentHashMap`
- `package-info.java` — `@NotNullByDefault` annotation (created if missing)
- `{Name}ServiceTest.java` — JUnit 5 test with at least three test cases
- Build passes with no errors
