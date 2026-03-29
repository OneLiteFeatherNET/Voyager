# Deployment Guide

## Local Development with Docker

### Prerequisites

- Docker and Docker Compose installed
- Java 25 (or compatible) for local builds without Docker

### Start Server + Database

From the project directory:

```bash
docker compose -f docker/compose.yml up -d
```

This starts:
- **MariaDB** on port `3306` (user/password/database: `voyager-project`)
- **Game Server** (Minestom) on port `25565`

The game server is automatically compiled via multi-stage build.

### Start Database Only

If only the database is needed (e.g., for local development with IDE):

```bash
docker compose -f docker/mariadb/compose.yml up -d
```

### Environment Variables

The game server accepts the following environment variables:

| Variable      | Description         | Default Value      |
|---------------|----------------------|--------------------|
| `DB_HOST`     | Database host       | `mariadb`          |
| `DB_PORT`     | Database port       | `3306`             |
| `DB_NAME`     | Database name        | `voyager-project`  |
| `DB_USER`     | Database user        | `voyager-project`  |
| `DB_PASSWORD` | Database password    | `voyager-project`  |

## CloudNet v4 Deployment

### Set Up Task

1. Use the file `docs/deployment/cloudnet-task.json` as a template.
2. Create the task in CloudNet:
   ```
   tasks create task ElytraRace
   ```
3. Adjust the task configuration under `local/tasks/ElytraRace.json` accordingly.
4. Copy the server JAR (shadow JAR from `:server:shadowJar`) into the template `ElytraRace/default`.

### Template Structure

```
ElytraRace/default/
  app.jar          # Shadow JAR of the server
```

## JVM Flags Recommendation

The following JVM flags are recommended for production:

```
-XX:+UseZGC
-XX:+ZGenerational
-Xms256M
-Xmx512M
```

**ZGC** (Z Garbage Collector) with generational mode provides low latencies and is ideal for Minecraft servers. Heap size can be adjusted based on player count:

| Player Count | Recommended Heap |
|---------------|------------------|
| 1-20          | 512M             |
| 20-50         | 1G               |
| 50+           | 2G               |
