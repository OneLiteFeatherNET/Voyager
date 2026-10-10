# Configuration check

The configuration check validates the settings, the map and cup catalogue, and every world the maps
name, reports every problem in one pass, and exits. It starts no game server and binds no socket.

## Commands

| Command | Effect |
|---|---|
| `./gradlew :voyager-server:validateCatalog` | Runs the check against the run directories. |
| `./gradlew :voyager-server:validateCatalog -PdataPath=<dir> -PworldsPath=<dir> -Pcup=<name>` | Runs the check against other directories. |
| `./gradlew :voyager-server:runServerDev` | Runs the check first, then starts the server. |
| `./gradlew :voyager-server:runServerDev -PskipCatalogCheck` | Starts the server without the check, and prints one warning line. |

The same check runs without Gradle as `java -Dvoyager.config.check=true -jar <server jar>`, with
`-DVOYAGER_DATA_PATH`, `-DVOYAGER_WORLDS_PATH`, `-DVOYAGER_CUP` and `-DVOYAGER_MIN_PLAYERS` as needed.

A normal boot runs the same check before it builds the object graph, and refuses with the same report.
`-PskipCatalogCheck` skips only the Gradle dependency. It does not skip the server's own check.

## Exit codes

| Code | Meaning |
|---|---|
| `0` | No error. Warnings may be listed. |
| `1` | At least one error. |

No other code is used.

## Output

One line per problem, sorted by source and then key, followed by a summary line:

```text
ERROR <absolute source> <key>: <message>
config check failed: <n> error(s)
```

`source` is the absolute path of the file the problem concerns. For a setting it is the setting's
name or the command-line argument. `key` is the JSON field, or the setting name. A file that does not
parse has the key `file`. A directory that is missing has the directory's name as its key.

Standard output carries the report. A normal boot logs the same lines at error level.

## What is checked

- The settings: the data and worlds directories exist, the port is a number from 1 to 65535, and a
  named cup exists.
- The minimum racer count, `VOYAGER_MIN_PLAYERS`: when set, it is a whole number of at least 1. It
  defaults to 2, or to 1 under `voyager.dev`, and an explicit value wins in both modes. An unset value
  is no problem. A value that is not a number, or is 0, is an error under the key `VOYAGER_MIN_PLAYERS`,
  and a normal boot refuses with it.
- The catalogue: every map and cup file parses, names are unique, and every map a cup plays exists.
- Each map's world: the folder exists under the worlds directory and holds region data in the
  `region/` or `dimensions/` layout.
- Each world's health: every chunk the region files cover is read, and the world must be sound. A
  world with refused chunks, with no chunk read, or with unknown blocks is an error. A world that
  cannot be read is an error naming the reason. The other worlds are still checked.

A cup entry that names an unknown map is reported only when every map and cup file parses. A malformed
file hides that check, as it does in boot.

## Runtime cost

Measured on an AMD Ryzen 9 5900XT (16 cores, 62 GB RAM), Linux, OpenJDK 25.0.3, with the
JVM flags the run tasks use. Each figure is the median of three runs of `java -jar`.

| Measure | Time |
|---|---|
| Full check on the shipped world `ElytraraceBlueAndRed` (9,429 chunks read) | 3.13 s |
| Start-up up to the catalogue check, which fails for missing directories | 1.38 s |
| Deep step alone, from Falco's open to close on the shipped world | 1.78 s |

The start-up figure covers the JVM start, Minestom's registry initialisation and the catalogue read.
The deep step grows with the number of region files. A normal boot pays the full check time on every
start.
