# Production Setup Guide

This guide covers deploying Voyager to production using CloudNet v4 as the orchestrator.

## Architecture Overview

```
CloudNet v4
├── Task: ElytraRace   (Minestom standalone — game server, players connect here)
└── Task: ElytraSetup  (Paper 1.21.5 — admin-only, map/cup creation)
```

CloudNet manages service lifecycle for both tasks. The game server reads worlds from its
template directory and connects to a shared MariaDB instance. The Setup Server writes world
files that are then copied into the game server template.

## Prerequisites

- CloudNet v4 RC16 or newer installed and running
- MariaDB 11.8 accessible from the node(s) that run the ElytraRace task
- Game server JAR built: `./gradlew :server:shadowJar`

## Step 1 — Create the CloudNet task

In the CloudNet console:

```
tasks create task ElytraRace
```

This creates `local/tasks/ElytraRace.json` with defaults. Replace its contents with the
template from `docs/deployment/cloudnet-task.json`:

```json
{
  "name": "ElytraRace",
  "runtime": "jvm",
  "javaCommand": "java",
  "minServiceCount": 0,
  "maintenance": false,
  "autoDeleteOnStop": true,
  "startPort": 44955,
  "processConfiguration": {
    "environment": "MINECRAFT_SERVER",
    "maxHeapMemorySize": 512,
    "jvmOptions": [
      "-XX:+UseZGC",
      "-XX:+UseCompactObjectHeaders",
      "-Xms256M",
      "-Xmx512M"
    ]
  },
  "templates": [
    {
      "prefix": "ElytraRace",
      "name": "default",
      "storage": "local"
    }
  ]
}
```

The `environment` field must be `"MINECRAFT_SERVER"`. CloudNet v4 RC16 removed the `MINESTOM`
environment type. Using the old value causes the task to fail at service start.

## Step 2 — Populate the template

CloudNet loads `ElytraRace/default/` as the working directory of each started service.

```
local/templates/ElytraRace/default/
  app.jar          # Shadow JAR renamed from server/build/libs/Voyager-<version>.jar
  worlds/          # Anvil world directories, one per map
    my-race-map/
      level.dat
      region/
```

Copy the JAR:

```bash
cp server/build/libs/Voyager-*.jar \
  local/templates/ElytraRace/default/app.jar
```

Copy world directories exported from the Setup Server into `worlds/`. Each subdirectory must
be a valid Anvil world root (contains `region/`).

## Step 3 — Configure environment variables

Set the following variables on the CloudNet task or on the node environment so the game server
can reach MariaDB:

| Variable      | Description           | Default           |
|---------------|-----------------------|-------------------|
| `DB_HOST`     | MariaDB host          | `localhost`       |
| `DB_PORT`     | MariaDB port          | `3306`            |
| `DB_NAME`     | Database name         | `voyager-project` |
| `DB_USER`     | Database user         | `voyager-project` |
| `DB_PASSWORD` | Database password     | —                 |

In `local/tasks/ElytraRace.json`, add an `environmentVariables` block:

```json
"environmentVariables": {
  "DB_HOST": "your-mariadb-host",
  "DB_PORT": "3306",
  "DB_NAME": "voyager-project",
  "DB_USER": "voyager-project",
  "DB_PASSWORD": "your-password"
}
```

Do not store the password in the task file if the file is version-controlled. Use a secrets
manager or inject via node environment instead.

## Step 4 — Set up MariaDB

Pin the MariaDB version to `11.8` in production. Using `latest` risks a major-version upgrade
on the next container pull, which can break schema compatibility.

Connect to your MariaDB instance and run:

```sql
CREATE DATABASE `voyager-project`;
CREATE USER 'voyager-project'@'%' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON `voyager-project`.* TO 'voyager-project'@'%';
FLUSH PRIVILEGES;
```

The game server runs Hibernate with `hbm2ddl.auto = update`, so the schema is created
automatically on first startup. No manual migration step is needed for a fresh database.

## Step 5 — Start the service

In the CloudNet console:

```
service start ElytraRace
```

CloudNet picks a node, copies the template, and starts the JVM with the configured flags.
To verify the service came up:

```
service list
```

The ElytraRace service should appear with state `RUNNING`. Connect a Minecraft 1.21.11 client
to the reported address to confirm.

## JVM Flags Reference

| Flag                          | Purpose                                                      |
|-------------------------------|--------------------------------------------------------------|
| `-XX:+UseZGC`                 | Z Garbage Collector — sub-millisecond pauses, critical for 20 TPS |
| `-XX:+UseCompactObjectHeaders`| Reduces object header size, ~10-20% heap savings (Java 25+)  |
| `-Xms256M`                    | Initial heap — set equal to `-Xmx` to avoid resize pauses    |
| `-Xmx512M`                    | Maximum heap — increase based on player count (see table)    |

Do not add `-XX:+ZGenerational`. It is the default mode in Java 25 and specifying it
explicitly triggers a deprecation warning.

### Heap sizing

| Players | Recommended `-Xmx` |
|---------|--------------------|
| 1-20    | 512M               |
| 20-50   | 1G                 |
| 50+     | 2G                 |

Set `-Xms` to the same value as `-Xmx` to pre-allocate the full heap and prevent resizing
under load. Update `maxHeapMemorySize` in the CloudNet task JSON to match the `-Xmx` value
(in megabytes) so CloudNet can account for it in node memory scheduling.
