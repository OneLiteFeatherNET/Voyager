# Configuration

The game server reads every setting through one loader (ADR-0036). This page lists each key with its system
property, its environment variable, its default and what the server refuses. The precedence and the one exception
are at the end.

## Keys

| Key (system property) | Environment variable | Default | Accepted values and refusals |
|---|---|---|---|
| `service.bind.host` | `SERVICE_BIND_HOST` | `0.0.0.0` | Any host name or address. Blank means the default. The first command-line argument overrides it. |
| `service.bind.port` | `SERVICE_BIND_PORT` | `25565` | A whole number from 1 to 65535. Anything else refuses the start and names the key. The second command-line argument overrides it. |
| `VOYAGER_DATA_PATH` | `VOYAGER_DATA_PATH` | `catalog` | A directory holding `maps/` and `cups/`. A path that is not a directory refuses the start. |
| `VOYAGER_WORLDS_PATH` | `VOYAGER_WORLDS_PATH` | `worlds` | A directory holding one Anvil world folder per map world. A path that is not a directory refuses the start. |
| `VOYAGER_IMPORT_PATH` | `VOYAGER_IMPORT_PATH` | `imports` | The folder that world folders are imported from with `/map import` on the setup server, relative to the working directory unless absolute. The server does not create it; a missing folder refuses the import and names the path. |
| `VOYAGER_IMPORT_MAX_BYTES` | `VOYAGER_IMPORT_MAX_BYTES` | `268435456` (256 MiB) | The largest total of region-file bytes one import may copy. A value that is not a whole number of at least 1 refuses the start and names the key. |
| `VOYAGER_IMPORT_MAX_REGIONS` | `VOYAGER_IMPORT_MAX_REGIONS` | `1024` | The largest number of region files one import may copy. A value that is not a whole number of at least 1 refuses the start and names the key. |
| `VOYAGER_CUP` | `VOYAGER_CUP` | none | The name of the cup to play. Blank means none. A name that matches no cup refuses the start. With more than one cup file and no name, the start is refused as ambiguous. |
| `voyager.dev` | `VOYAGER_DEV` | `false` | `true` turns on dev mode: a shorter lobby and results screen, and the operator subcommands. |
| `VOYAGER_MIN_PLAYERS` | `VOYAGER_MIN_PLAYERS` | `2`, or `1` in dev mode | A whole number of at least 1. A value of 0 or text refuses the start. Blank means the default. |
| `VOYAGER_MAX_PLAYERS` | `VOYAGER_MAX_PLAYERS` | `16` | A whole number not below `VOYAGER_MIN_PLAYERS`. It is a soft limit: it sets the join line and the full-lobby countdown, and refuses no one. |
| `VOYAGER_LOBBY_WORLD` | `VOYAGER_LOBBY_WORLD` | none | The world folder waiting racers stand in. It must be set together with `VOYAGER_LOBBY_SPAWN`, and it must not be a world a map plays. |
| `VOYAGER_LOBBY_SPAWN` | `VOYAGER_LOBBY_SPAWN` | none | The lobby spawn as `x,y,z` in block coordinates, three finite numbers. Set together with `VOYAGER_LOBBY_WORLD`. |
| `voyager.config.check` | `VOYAGER_CONFIG_CHECK` | `false` | `true` runs the configuration check, prints every problem and exits. |
| `voyager.velocity.secret` | `VOYAGER_VELOCITY_SECRET` | none | The Velocity forwarding secret. Unset means the server runs offline, with a warning. A set value that is blank refuses the start, and the message names the variable, never the value. The environment variable wins over the property (see below). |

The velocity secret is never printed, by the server or by the configuration check.

## Where a value can come from

Lowest to highest. A higher source replaces a lower one for the same key.

| Rank | Source | Notes |
|---|---|---|
| 1 | Defaults in code | The table above. |
| 2 | `application.yaml` (or `application.properties`) in the working directory | New in this release. A service directory of a CloudNet task is its working directory. Values in the file are trimmed. |
| 3 | The file named by `CONFIG_FILE` (or the system property `config.file`) | Loaded after the working-directory file, so it replaces the same keys in it. |
| 4 | The environment variable | Read for every key, by the name in the table. |
| 5 (highest) | The system property | Read for every key, by the name in the table. |

Each source is applied in this order. A key set only in the environment is found; a key set only in a file is found.

### The one exception: the velocity secret

For `voyager.velocity.secret` the environment variable `VOYAGER_VELOCITY_SECRET` wins over the system property, when both are
set and they differ. This is required by the forwarding specification (`openspec/specs/server/proxy-forwarding`). Every other key
uses the order in the table above. The exception is written in one place, `ProxyForwardingSettings.from`, and covered by tests.

A secret taken from the environment or from a system property is used exactly as written: it is not trimmed and `${...}` is not
expanded. A secret that a `application.yaml` file also sets is subject to the file rules above.

## What else avaje-config reads

The loader uses avaje-config's own readers, so a few environment variables and properties that belong to that library also
take effect when they are set. Do not set these for another purpose on a server node:

- `CONFIG_FILE` and the system property `config.file`: name a configuration file to load.
- `CONFIG_PROFILES` and `AVAJE_PROFILES`, and the system properties `config.profiles` and `avaje.profiles`: load profile files.
- `POD_NAME`, `POD_NAMESPACE`, `POD_VERSION` and `POD_IP`: set the application's instance, environment, version and address
  names. The server does not read them.

## Values from a file

A value in `application.yaml` may refer to another key with `${NAME}`; the reference is resolved from the environment, the
system properties and the file. Values taken from the environment or from a system property are not expanded when no file
sets that key.

## Keys that gained an environment name

Before this release only `VOYAGER_VELOCITY_SECRET` was read from the environment. Every key in the table now also reads its
environment variable. Existing deployments are unaffected when they use system properties, as the CloudNet task does. A
variable with one of these names that is set on a node for another purpose now takes effect. The configuration check prints
the source of each problem, so an unexpected value shows where it came from.
