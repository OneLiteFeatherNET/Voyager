---
name: create-repository
description: Scaffold a new Hibernate repository in shared/database following the sealed-interface + DefaultImpl pattern.
---

# Create Hibernate Repository

Scaffold a new Hibernate ORM repository in `shared/database` following the ManisGame sealed-interface pattern exactly.

## Input

Collect the following before starting:

- **Entity/domain name** (e.g., `PlayerStats`, `CupResult`) — used to derive all file names
- **Fields and their types** — used for the `@Entity` class
- **Key query methods needed** — e.g., `findByPlayerId(UUID playerId)`, `findAll()`

## Steps

### 1. Create the entity class

File: `shared/database/src/main/java/net/elytrarace/database/entity/{Name}.java`

```java
package net.elytrarace.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "{table_name}")         // snake_case plural, e.g. "player_stats"
public class {Name} {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private {IdType} id;              // typically UUID or Long

    // Add annotated fields here, e.g.:
    // @Column(name = "player_id", nullable = false)
    // private UUID playerId;

    protected {Name}() {}             // required no-arg constructor for Hibernate

    public {Name}({IdType} id /*, other fields */) {
        this.id = id;
    }

    public {IdType} getId() { return id; }

    // Getters for each field — NO public setters unless update operations are needed
}
```

Rules for entities:
- No-arg constructor must be `protected` (Hibernate only).
- Prefer immutable fields; add setters only when the repository update path needs them.
- UUID primary keys: add `@Column(columnDefinition = "CHAR(36)")` for MariaDB compatibility.
- Use `@Column(nullable = false)` on every field that must be NOT NULL in the schema.

### 2. Check / create `package-info.java` for the entity package

File: `shared/database/src/main/java/net/elytrarace/database/entity/package-info.java`

```java
@NotNullByDefault
package net.elytrarace.database.entity;

import org.jetbrains.annotations.NotNullByDefault;
```

Only create this file if it does not already exist.

### 3. Create the sealed repository interface

File: `shared/database/src/main/java/net/elytrarace/database/repository/{Name}Repository.java`

```java
package net.elytrarace.database.repository;

import net.elytrarace.database.entity.{Name};
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Optional;

public sealed interface {Name}Repository permits Default{Name}Repository {

    /**
     * Creates a new repository backed by the given SessionFactory.
     */
    static {Name}Repository create(SessionFactory sessionFactory) {
        return new Default{Name}Repository(sessionFactory);
    }

    // --- declare query methods here, examples:

    Optional<{Name}> findById({IdType} id);

    List<{Name}> findAll();

    void save({Name} entity);

    void delete({IdType} id);
}
```

Rules:
- The interface is `sealed` and names only `Default{Name}Repository` in `permits`.
- The static `create(SessionFactory)` factory is the only way callers obtain an instance.
- Methods return `Optional<T>` for single-result queries that may find nothing.
- Collections are `List<T>`; never return raw arrays.
- No checked exceptions — let Hibernate runtime exceptions propagate.

### 4. Check / create `package-info.java` for the repository package

File: `shared/database/src/main/java/net/elytrarace/database/repository/package-info.java`

```java
@NotNullByDefault
package net.elytrarace.database.repository;

import org.jetbrains.annotations.NotNullByDefault;
```

Only create this file if it does not already exist.

### 5. Create the `Default{Name}Repository` implementation

File: `shared/database/src/main/java/net/elytrarace/database/repository/Default{Name}Repository.java`

```java
package net.elytrarace.database.repository;

import net.elytrarace.database.entity.{Name};
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Optional;

public final class Default{Name}Repository implements {Name}Repository {

    private final SessionFactory sessionFactory;

    Default{Name}Repository(SessionFactory sessionFactory) {    // package-private — use factory method
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Optional<{Name}> findById({IdType} id) {
        try (var session = sessionFactory.openSession()) {
            return Optional.ofNullable(session.get({Name}.class, id));
        }
    }

    @Override
    public List<{Name}> findAll() {
        try (var session = sessionFactory.openSession()) {
            return session.createQuery("FROM {Name}", {Name}.class).list();
        }
    }

    @Override
    public void save({Name} entity) {
        var transaction = sessionFactory.getCurrentSession().beginTransaction();
        try (var session = sessionFactory.openSession()) {
            session.persist(entity);
            session.getTransaction().commit();
        }
    }

    @Override
    public void delete({IdType} id) {
        try (var session = sessionFactory.openSession()) {
            var tx = session.beginTransaction();
            var entity = session.get({Name}.class, id);
            if (entity != null) {
                session.remove(entity);
            }
            tx.commit();
        }
    }
}
```

Rules:
- Constructor is package-private; callers use `{Name}Repository.create(sessionFactory)`.
- Always open a new `Session` per method via `sessionFactory.openSession()` inside a try-with-resources block.
- Wrap write operations (save, delete) in explicit transactions: `session.beginTransaction()` / `tx.commit()`.
- Roll back on exceptions: add `catch (Exception e) { tx.rollback(); throw e; }` to write methods.
- Use HQL (`FROM {Name}`) not raw SQL unless a native query is necessary.

### 6. Register the entity with Hibernate

Open the Hibernate configuration class / `SessionFactory` builder in `shared/database` (or in the module that initializes the database) and add the new entity class:

```java
configuration.addAnnotatedClass({Name}.class);
```

If no central configuration exists yet, create one and document it. Do not skip this step — Hibernate will not see the entity otherwise.

### 7. Write a JUnit 5 test

File: `shared/database/src/test/java/net/elytrarace/database/repository/{Name}RepositoryTest.java`

Use Mockito to mock `SessionFactory` and `Session` for unit tests, or an embedded H2 database for integration tests. The build only ships `mariadb` at runtime, so H2 must be added as `testImplementation` if used.

Minimal test structure:

```java
package net.elytrarace.database.repository;

import net.elytrarace.database.entity.{Name};
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class {Name}RepositoryTest {

    private SessionFactory sessionFactory;
    private Session session;
    private {Name}Repository repository;

    @BeforeEach
    void setUp() {
        sessionFactory = mock(SessionFactory.class);
        session = mock(Session.class);
        when(sessionFactory.openSession()).thenReturn(session);
        repository = {Name}Repository.create(sessionFactory);
    }

    @Test
    void findById_returnsEmpty_whenEntityDoesNotExist() {
        when(session.get({Name}.class, /* test id */)).thenReturn(null);
        Optional<{Name}> result = repository.findById(/* test id */);
        assertThat(result).isEmpty();
    }

    @Test
    void findById_returnsEntity_whenEntityExists() {
        var entity = new {Name}(/* test id, fields */);
        when(session.get({Name}.class, /* test id */)).thenReturn(entity);
        Optional<{Name}> result = repository.findById(/* test id */);
        assertThat(result).contains(entity);
    }

    // Add tests for each declared query method
}
```

Add Mockito as `testImplementation` to `shared/database/build.gradle.kts` if not already present:

```kotlin
testImplementation("org.mockito:mockito-core:5.+")
```

### 8. Build and verify

Run:

```bash
./gradlew :shared:database:build
```

Resolve all compilation errors before declaring the skill done.

## Output

- `shared/database/src/main/java/net/elytrarace/database/entity/{Name}.java`
- `shared/database/src/main/java/net/elytrarace/database/entity/package-info.java` (if new)
- `shared/database/src/main/java/net/elytrarace/database/repository/{Name}Repository.java`
- `shared/database/src/main/java/net/elytrarace/database/repository/Default{Name}Repository.java`
- `shared/database/src/main/java/net/elytrarace/database/repository/package-info.java` (if new)
- `shared/database/src/test/java/net/elytrarace/database/repository/{Name}RepositoryTest.java`
- Updated Hibernate configuration with `addAnnotatedClass({Name}.class)`

## Invariants (never break these)

- The interface is `sealed`; the implementation is `final` and package-private-constructed.
- `Default{Name}Repository` constructor is **package-private** — never `public`.
- Every package has a `package-info.java` annotated with `@NotNullByDefault`.
- `shared/database` must NOT import `net.minestom.*` or `org.bukkit.*`.
- `Optional` for nullable single results; `List` for collections.
- All write operations are wrapped in explicit Hibernate transactions with rollback on failure.
