# Local Testing Guide

This guide walks through running the Voyager game server locally against a world that was
already created with the Setup Server. It does not cover map creation — that is handled
separately by the Setup Server (`plugins/setup`).

## Prerequisites

- Java 25 installed and on `PATH` (`java --version` must report `25.x`)
- An Anvil world directory produced by the Setup Server (contains a `region/` subfolder)
- Docker and Docker Compose for MariaDB, or a locally running MariaDB 11.8 instance

## Step 1 — Build the server JAR

From the project root:

```bash
./gradlew :server:shadowJar
```

Output artifact: `server/build/libs/Voyager-<version>.jar`

The shadow JAR bundles all runtime dependencies. No additional classpath setup is needed.

## Step 2 — Start MariaDB

```bash
docker compose -f docker/mariadb/compose.yml up -d
```

This starts MariaDB on `localhost:3306` with credentials `voyager-project / voyager-project`
and database `voyager-project`. The server reads these via `DB_HOST`, `DB_PORT`, `DB_NAME`,
`DB_USER`, and `DB_PASSWORD` environment variables (see Step 4 for overrides).

Verify it is up:

```bash
docker compose -f docker/mariadb/compose.yml ps
```

## Step 3 — Locate the world directory

The game server loads worlds in Anvil format via `AnvilMapInstanceService`, which delegates to
Minestom's `AnvilLoader`. The loader requires the root of the world directory — the folder that
directly contains `region/`, `level.dat`, etc.

Example layout:

```
/home/user/worlds/my-race-map/
  level.dat
  region/
    r.0.0.mca
    r.-1.0.mca
```

The path `/home/user/worlds/my-race-map` is what you pass to the server at runtime.

## Step 4 — Start the game server

```bash
java -XX:+UseZGC -XX:+UseCompactObjectHeaders -Xms256M -Xmx512M \
  -jar server/build/libs/*.jar
```

By default the server binds to `0.0.0.0:25565`. To use a different address or port pass them
as positional arguments:

```bash
java -XX:+UseZGC -XX:+UseCompactObjectHeaders -Xms256M -Xmx512M \
  -jar server/build/libs/*.jar 0.0.0.0 25566
```

To override database credentials at runtime:

```bash
DB_HOST=localhost DB_PORT=3306 DB_NAME=voyager-project \
DB_USER=voyager-project DB_PASSWORD=voyager-project \
  java -XX:+UseZGC -XX:+UseCompactObjectHeaders -Xms256M -Xmx512M \
  -jar server/build/libs/*.jar
```

## Step 5 — Connect

Open Minecraft Java Edition and add a server at `localhost:25565` (or the port you chose).
The required client version is **1.21.11**, which matches Minestom 2026.03.25-1.21.11.

You should land in the lobby phase. If the cup flow starts immediately, no players other
than you are present — that is expected behavior in a single-player local test.

## Troubleshooting

**Port already in use**

```
java.net.BindException: Address already in use
```

Pass a different port as the second argument:

```bash
java ... -jar server/build/libs/*.jar 0.0.0.0 25566
```

**World not loading / AnvilLoader exception**

The path must point to an Anvil world root, not a parent directory. Confirm the layout:

```bash
ls /path/to/world/
# must show: region/  level.dat  (and optionally entities/, poi/, etc.)
```

If `region/` is missing, the directory is either not a world or is a Bedrock-format world,
which is not supported.

**Database connection refused**

```
com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
```

Check that MariaDB is running on port 3306:

```bash
docker compose -f docker/mariadb/compose.yml ps
```

If the container exited, check its logs:

```bash
docker compose -f docker/mariadb/compose.yml logs mariadb
```

**Wrong Minecraft version**

Minestom does not support version negotiation. The client must be exactly 1.21.11. Using any
other version results in a "Outdated client" or "Outdated server" disconnect.
