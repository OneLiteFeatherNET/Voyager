# How to deploy the game and setup servers on CloudNet

This guide shows how to run the rebuilt game server and the setup server as CloudNet 4 services behind a Velocity proxy. It
covers the files to place, the values the node provides, and how to check that a service works. It assumes that the node runs
CloudNet 4.0.0-RC17, Velocity, and LuckPerms as the permission backend.

The layout and the node contract are decided in `openspec/changes/add-cloudnet-deployment/design.md`, and the bridge is ADR-0026.

## What goes where

| File | Source | Goes to | Notes |
|---|---|---|---|
| `voyager-server-<version>.jar` | `./gradlew :voyager:server:shadowJar` | service root, the application file | the node names the file |
| `voyager-setup-<version>.jar` | `./gradlew :voyager:setup:shadowJar` | service root, for a setup service | the same |
| `CloudNet-Bridge.jar` | the node's CloudNet 4.0.0-RC17 bridge | `extensions/` | the `CloudNet_Bridge` extension, from the node |
| `voyager-cloudnet-bridge-<version>.jar` | `./gradlew :voyager:cloudnet-bridge:jar` | `extensions/` | without it, the staff maintenance bypass does not work |
| `catalog/maps/`, `catalog/cups/` | the repository's maps and cups | service root | the game's catalogue, read from `catalog` by default |
| `worlds/` | the world directories the maps name | service root | read from `worlds` by default |
| `data/config.yml` | the network's LuckPerms configuration | the template's `data/`, see "LuckPerms" | the one file copied by hand |

The server creates the rest of `data/` on its first start. The fat jars contain no CloudNet class. The check `verifyNoCloudNetInFatJars`
runs under `./gradlew check` and fails the build if one does.

## The service directory

```
<service>/
├── voyager-server-<version>.jar   (or voyager-setup-<version>.jar)
├── extensions/
│   ├── CloudNet-Bridge.jar
│   └── voyager-cloudnet-bridge-<version>.jar
├── catalog/
│   ├── maps/
│   └── cups/
├── worlds/
└── data/
    └── config.yml                 the LuckPerms configuration, from the network template
```

## Path defaults changed

The server and the setup server now read `catalog` and `worlds` from the working directory when no property is set. Before this
change the defaults were `run/data` and `run/worlds`, and a service needed two `-D` flags.

- A deploy that passes `-DVOYAGER_DATA_PATH` and `-DVOYAGER_WORLDS_PATH` explicitly is unaffected. An explicit property always wins.
- The live ElytraRace task passes `run/data` and `run/worlds` explicitly, so it keeps working unchanged.
- Only a service started with neither property changes: it reads `catalog` and `worlds`.

## Values the node provides

| Value | How it reaches the service |
|---|---|
| Bind host and port | the system properties `service.bind.host` and `service.bind.port`, which the node sets for each service. The environment variables `SERVICE_BIND_HOST` and `SERVICE_BIND_PORT` also work |
| Velocity forwarding secret | the environment variable `VOYAGER_VELOCITY_SECRET` in the task's environment, the same secret the Velocity proxy holds. The system property `voyager.velocity.secret` also works. Never write it into a file in the service directory |
| Racer counts, cup, cup rotation and paths | the system properties `VOYAGER_CUP`, `VOYAGER_CUP_ROTATION`, `VOYAGER_CUP_ROTATION_STATE`, `VOYAGER_MIN_PLAYERS`, `VOYAGER_DATA_PATH` and `VOYAGER_WORLDS_PATH`, and the lobby pair `VOYAGER_LOBBY_WORLD` and `VOYAGER_LOBBY_SPAWN`. Each of them also works as an environment variable of the same name |
| The `stop` command | the node writes `end` and then `stop` to the service's standard input. `end` is ignored, `stop` shuts the service down cleanly |
| The statistics switch | the environment variable `VOYAGER_PERSISTENCE`: `on` collects statistics, `off` keeps none. Unset means `off` |
| The statistics database | the environment variables `VOYAGER_DB_URL`, `VOYAGER_DB_USER` and `VOYAGER_DB_PASSWORD`, or `VOYAGER_DB_PASSWORD_FILE` (see "Player statistics database") |
| The statistics tuning | the environment variables `VOYAGER_DB_POOL_SIZE` (default 10) and `VOYAGER_DB_QUEUE_CAPACITY` (default 256). Leave them unset unless you have a reason to change them |

A blank secret refuses the start: the server does not fall back to offline mode with an empty secret.

## Player statistics database

The server records race results and player statistics in PostgreSQL when `VOYAGER_PERSISTENCE` is `on`. The keys, their
defaults and the behaviour during an outage are in `docs/reference/database.md`.

The database is never a reason for the server to refuse to start. A wrong URL, a stopped database or a missing password
leads to one WARN in the log, and the game runs without statistics until the database is reachable again.

Before the release, choose one of two states for every task that runs the game:

- **Collect statistics.** Set `VOYAGER_PERSISTENCE=on`, together with `VOYAGER_DB_URL`, `VOYAGER_DB_USER` and
  `VOYAGER_DB_PASSWORD` (or `VOYAGER_DB_PASSWORD_FILE`). Put these in the CloudNet task environment as node-level secrets:
  in the environment of the node process, never in the task file or in a template. The URL has this form, with the real host
  that the operator provides:

  ```
  VOYAGER_DB_URL=jdbc:postgresql://db.example.invalid:5432/voyager
  ```

- **Keep statistics off.** Leave `VOYAGER_PERSISTENCE` unset, or set it to `off`. Each start then logs one WARN that no
  statistics are kept on the server. The game plays normally.

To roll back a `on` deployment, set `VOYAGER_PERSISTENCE=off` in the task and restart the service. The database schema and
every recorded row stay in place, and nothing else changes. A later switch back to `on` uses the same schema.

### Precedence

The server reads one loader (ADR-0036). When a key is set in more than one place, the highest source wins:

| Rank | Source | On the CloudNet node |
|---|---|---|
| 1 (lowest) | Defaults in code | used when nothing else sets the key |
| 2 | `application.yaml` in the service directory | optional; the node does not write it |
| 3 | The file named by `CONFIG_FILE` | not set on the node |
| 4 | Environment variables | the velocity secret, and any key set in the task's environment |
| 5 (highest) | System properties | what the node sets for each service today |

The velocity secret is the one exception: the environment variable wins over the system property for that key only, as the
forwarding specification requires. The full list of keys and names is in `docs/reference/configuration.md`.

## The JVM translation flag

Neither server needs a JVM flag for translation. Both set `minestom.automatic-component-translation` themselves, first thing in `main`
(#299 for the setup server, #295 for the game server), so the task's JVM options need no such flag. Do not add
`-Dminestom.automatic-component-translation` to a task: the code sets it, and the start guard still refuses a start in which the
ordering was broken.

## LuckPerms

**Decided 2026-10-11: like Cygnus.** The LuckPerms storage configuration is a `data/config.yml` that is placed by hand in the service
template, as Cygnus's is. No repository code writes it, and no credential is stored in the repository or in a JAR.

How to set it up:

1. Take the network's LuckPerms `config.yml` from the CloudNet template that the owner keeps for Cygnus. Do not copy it from a
   repository or a JAR.
2. Put it into the service template's `data/` directory, so that every service the node creates from the template starts with it.
3. Start one service and check the log: the line `Loading storage provider...` names the storage. The configured storage is used.
   With MariaDB configured, no `luckperms-h2-v2.mv.db` should appear in `data/`; that is expected, not yet observed.

The file contains the network's storage credentials. Keep it out of the repository and out of logs. Without the file, LuckPerms creates
its default configuration with H2 storage, and then each service keeps its own grants.

This relaxes the rule of `cloudnet/service-template` that `data/` is per service: the template may hold `data/config.yml`. The H2 file
stays per service, and it is unused when the storage is MariaDB.

## Start a service locally

To check the layout on a developer machine, run the built setup jar from a directory that holds `catalog/` and `worlds/`:

```
java -jar voyager-setup-<version>.jar
```

The setup server listens on `0.0.0.0:25566` by default. Write `stop` and a newline to its standard input to stop it. The process exits
with status 0.

## Check a CloudNet deployment

1. Start one game service and read the log. The line `Listening on <host>:<port>` names the bind address the node gave. If the
   bridge's dependency is missing, the loader is expected to skip the extension and log one line that names `CloudNet_Bridge`. That
   line has not been observed yet; the node acceptance run records it. The server still starts when the dependency is missing.
2. Run `stop <service>` on the node. The process must exit on its own, and the log must end with the shutdown lines, not a kill.
3. Put the task into maintenance and join with an account that holds `cloudnet.bridge.maintenance` in LuckPerms. Getting through
   confirms the whole permission chain. Being refused points back to step 1, and to the grant in LuckPerms.

Repeat the three steps for a setup service. The acceptance results are recorded in the change's design.md, Risk R8.

## The hub handoff needs a fallback on the proxy

When a cup's results have lingered, the server sends every racer to the network hub. It does that by asking CloudNet to connect
the racer to the best matching fallback (`connectToFallback`, bridge-api 4.0.0-RC17). CloudNet chooses the target from the proxy's
fallback configuration, not from Voyager. Voyager names no group and no task.

- Configure a fallback for the game's proxy group, so that a fallback exists for the racers to go to. Without one, CloudNet has
  no target, and what a racer sees is the proxy's behaviour, not Voyager's. Voyager does not detect it and does not retry.
- A node without the bridge disconnects the racers with the translated message instead. That is the local-development path.

## Cup rotation and the state file

A server can play several cups in turn. Set `VOYAGER_CUP_ROTATION` to the cups in play order, such as `alpha_cup,legacy_cup`, and
the server keeps running between cups: when a cup's results have lingered and its racers are with the hub, the next cup of the
list is armed for the next round, and the first cup follows the last. Do not set `VOYAGER_CUP` with it; the start is refused.

The position lives in the server process, so a restart or a redeploy begins again at the first cup unless a state file keeps it.
Set `VOYAGER_CUP_ROTATION_STATE` to a file outside the service directory, which CloudNet clears on every stop:

1. Create the directory on the node, owned by the user the service runs as: `/home/cloud/cloudnet/local/voyager-state/`.
2. Set `VOYAGER_CUP_ROTATION_STATE=/home/cloud/cloudnet/local/voyager-state/rotation` in the task's environment.

A missing directory refuses the start and names the path. A cup whose world folders are not on the node is skipped with one
warning, so the server plays the cups that are complete. The configuration check (`voyager.config.check=true`) reports a skipped
cup as a warning and exits with 0 while another cup can be played. Decision record: ADR-0044.

## Known limits

- The bridge builds against CloudNet 4.0.0-RC17, which the node runs. Whether it works on Minecraft 26.2 on the node is what the
  acceptance run decides. The version question is resolved.
- A broken `CloudNet_Bridge` stops the start, because the extension loader does not isolate a failing extension. A missing one does not.
  Keep the bridge jar at the version the node runs.
- The service is named `ElytraRace` on the node. The LuckPerms context is `server=elytrarace`, which the network's `config.yml` sets.
