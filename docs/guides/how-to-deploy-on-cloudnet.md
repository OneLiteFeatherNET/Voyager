# How to deploy the game and setup servers on CloudNet

This guide shows how to run the rebuilt game server and the setup server as CloudNet 4 services behind a Velocity proxy. It
covers the files to place, the values the node provides, and how to check that a service works. It assumes that the node already
runs CloudNet 4.0.0-RC16, Velocity, and LuckPerms as the permission backend.

The layout and the node contract are decided in `openspec/changes/add-cloudnet-deployment/design.md`, and the bridge is ADR-0026.

## What goes where

| File | Source | Goes to | Notes |
|---|---|---|---|
| `voyager-server-<version>.jar` | `./gradlew :voyager:server:shadowJar` | service root, the application file | the node names the file; check it in task 10.1 |
| `voyager-setup-<version>.jar` | `./gradlew :voyager:setup:shadowJar` | service root, for a setup service | the same |
| `CloudNet-Bridge.jar` | the node's CloudNet 4.0.0-RC16 bridge | `extensions/` | the `CloudNet_Bridge` extension, from the node |
| `voyager-cloudnet-bridge-<version>.jar` | `./gradlew :voyager:cloudnet-bridge:jar` | `extensions/` | optional copy: without it, the staff maintenance bypass does not work |
| `catalog/maps/`, `catalog/cups/` | the repository's maps and cups | service root | the game's catalogue, read from `catalog` by default |
| `worlds/` | the world directories the maps name | service root | read from `worlds` by default |

Nothing else is needed in the service directory. The server creates `data/` on its first start: it holds the LuckPerms
configuration and its H2 database, and it is per service, so no two services may share it.

The fat jars contain no CloudNet class. The check `verifyNoCloudNetInFatJars` runs under `./gradlew check` and fails the build if one
does.

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
└── data/                          created on the first start
```

The server reads `catalog` and `worlds` from the working directory by default, so no `-DVOYAGER_DATA_PATH` or
`-DVOYAGER_WORLDS_PATH` is needed for a service started from this layout.

## Values the node provides

| Value | How it reaches the service |
|---|---|
| Bind host and port | the system properties `service.bind.host` and `service.bind.port`, which the node sets for each service |
| Velocity forwarding secret | the environment variable `VOYAGER_VELOCITY_SECRET` in the task's environment, the same secret the Velocity proxy holds. The system property `voyager.velocity.secret` also works. Do not write it into a file in the service directory |
| The `stop` command | the node writes `end` and then `stop` to the service's standard input. `end` is ignored, `stop` shuts the service down cleanly |

Set the secret once in the task's environment. A blank value refuses the start: the server does not fall back to offline mode with an
empty secret.

## The one JVM flag

Both servers refuse to start without `-Dminestom.automatic-component-translation=true`, because without it every message reaches the
player as a raw translation key. Set it in the task's JVM options until the launcher change is made (design.md, Decision 10). A
control run shows the refusal when the flag is missing.

## LuckPerms

LuckPerms starts before the port binds, and a start that fails refuses to listen. Without any configuration it stores grants in H2
under `data/`, which is per service. A network that shares grants across services needs a MariaDB configuration in
`data/config.yml` with a server context. How that configuration reaches the services is an open owner decision (design.md, Owner
decision 1). Until it is decided, place the file by hand in each service's `data/` and never copy the credentials into the repository.

## Start a service locally

To check the layout on a developer machine, run the built jar from a directory that holds `catalog/` and `worlds/`:

```
java -Dminestom.automatic-component-translation=true -jar voyager-setup-<version>.jar
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

## Known limits

- The RC16 bridge is documented for Minecraft 1.21.11 and not for 26.2. Whether it works on 26.2 is decided by the node acceptance run,
  and the RC17 node runs a patched bridge that this repository has not read.
- A broken `CloudNet_Bridge` stops the start, because the extension loader does not isolate a failing extension. A missing one does not.
  Keep the bridge jar at the version the node runs.
- The service is named `ElytraRace` on the node. The name only matters for CloudNet and LuckPerms contexts, which use `server=elytrarace`
  once the owner has chosen the LuckPerms configuration.
