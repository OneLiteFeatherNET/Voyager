# Spec Delta

## Purpose

The runnable artifacts of the rebuilt servers. Each composition root ships as one fat jar with a manifest `Main-Class`, so a
CloudNet service can start it with `-cp` and no classpath assembly. The jars must never contain a CloudNet class, because
the bridge has to load from its own extension classloader.

## ADDED Requirements

### Requirement: voyager-setup ships as a fat jar
**Priority:** MoSCoW Must

THE SYSTEM SHALL build `voyager-setup` as a fat jar with the manifest attribute `Main-Class` set to
`net.elytrarace.voyager.setup.SetupServer`, with its service files merged, as `voyager-server` already does.

#### Scenario: Setup jar starts
- **WHEN** the fat jar is run with `java -jar` from a service directory, with `VOYAGER_DATA_PATH=catalog` and `VOYAGER_WORLDS_PATH=worlds`
- **THEN** the setup server starts and reads its catalogue and worlds from those paths

### Requirement: No fat jar contains a CloudNet class
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT include any `eu/cloudnetservice` class or resource in the `voyager-server` or `voyager-setup` fat jar.

#### Scenario: Check the server jar
- **WHEN** the Gradle check runs after the fat jars are built
- **THEN** the check fails if any jar holds an `eu/cloudnetservice` entry, and names the jar

### Requirement: Service files survive shadowing
**Priority:** MoSCoW Must

THE SYSTEM SHALL merge the `META-INF/services` files of its dependencies into each fat jar, so that avaje-inject's generated
wiring is found at runtime.

#### Scenario: Wiring from the setup jar
- **WHEN** the setup fat jar starts
- **THEN** the setup beans are wired without a missing-service error
