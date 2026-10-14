# Run the game server with PostgreSQL locally

This guide starts a throwaway PostgreSQL 17 database on your machine and runs the rebuilt game server against it, so that
race results and player statistics are recorded. The database is for development only. The production setup is in
`docs/guides/how-to-deploy-on-cloudnet.md`, and the settings are listed in `docs/reference/database.md`.

The database listens on `127.0.0.1:55432`. It does not share the port, the volume or the container of the MariaDB database
in `docker/mariadb/`.

## Prerequisites

- Docker with the Compose plugin (`docker compose version` prints a version)
- Java 25 on `PATH` (`java --version` reports `25.x`)

## Step 1: Choose a password for the local database

The compose file takes the password from the shell and never stores it. Choose a password and export it in the shell you use
to start the server. Do not write it into a file in the repository.

```bash
export VOYAGER_LOCAL_DB_PASSWORD='choose-a-local-password'
```

Compose stops with the message `set VOYAGER_LOCAL_DB_PASSWORD` if the variable is unset or empty.

## Step 2: Start PostgreSQL

From the project root:

```bash
docker compose -f docker/postgres/compose.yml up -d
```

The first start creates the volume `voyager-postgres-data` and the database `voyager`, owned by the role `voyager`. Check that the
container is healthy with `docker compose -f docker/postgres/compose.yml ps`.

## Step 3: Set the database variables for the server

In the same shell, set the switch and the connection. The password comes from the variable of Step 1, so it stays out of the
command line and out of any file:

```bash
export VOYAGER_PERSISTENCE=on
export VOYAGER_DB_URL=jdbc:postgresql://127.0.0.1:55432/voyager
export VOYAGER_DB_USER=voyager
export VOYAGER_DB_PASSWORD="$VOYAGER_LOCAL_DB_PASSWORD"
```

Leave `VOYAGER_DB_PASSWORD_FILE` unset. When it is set, the server reads the password from that file and ignores
`VOYAGER_DB_PASSWORD`.

## Step 4: Run the server

```bash
./gradlew :voyager:server:runServer
```

The server applies the schema on its first start. The log shows no WARN about statistics. A WARN that names a database
setting means that the value is wrong; fix it and start again. The server does not stop because of the database, so a
failed connection shows up only as a WARN.

Race results and personal bests are recorded when a map or a cup finishes. Use `/stats` and `/leaderboard <map>` in the game
to read them.

## Step 5: Stop, and remove the data

Stop the server with `stop` in its console. To stop the database and keep its data:

```bash
docker compose -f docker/postgres/compose.yml stop
```

To stop the database and delete the volume with every recorded row:

```bash
docker compose -f docker/postgres/compose.yml down
docker volume rm voyager-postgres-data
```

`down` removes the containers and the network but keeps the volume. `docker volume rm` deletes it, and the data cannot be
recovered afterwards. The next start of the database then begins with an empty `voyager` database.

## Run without statistics

Leave `VOYAGER_PERSISTENCE` unset, or run `unset VOYAGER_PERSISTENCE`. The server runs normally and logs one WARN at boot that no
statistics are kept.
