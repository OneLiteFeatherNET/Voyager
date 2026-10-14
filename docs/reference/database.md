# Player statistics database

This page lists the settings of the player statistics database, the behaviour of the server at boot and during an outage,
and the two commands that read the statistics. The decision and its rationale are in ADR-0037. The deployment steps are in
`docs/guides/how-to-deploy-on-cloudnet.md`.

The server reads each setting through the configuration loader (ADR-0036). The precedence rules are in
`docs/reference/configuration.md`. A value can be set as a system property or as an environment variable, with the names in
the tables below.

## Keys

| Key (system property) | Environment variable | Default | Accepted values |
|---|---|---|---|
| `voyager.persistence` | `VOYAGER_PERSISTENCE` | unset, which means `off` | `off` or `on`. Any other value is a problem: one WARN at boot names the setting, and the server runs with statistics off |
| `voyager.db.url` | `VOYAGER_DB_URL` | none | A JDBC URL of the form `jdbc:postgresql://host:port/database`. Required when the switch is `on` |
| `voyager.db.user` | `VOYAGER_DB_USER` | none | The database role. Required when the switch is `on`. No default |
| `voyager.db.password` | `VOYAGER_DB_PASSWORD` | none | The password of the role. Required when the switch is `on` and no password file is set. Set it in the environment; a system property works but puts the value in the process command line |
| `voyager.db.password.file` | `VOYAGER_DB_PASSWORD_FILE` | none | A path to a file whose content is the password. The file is read once, at boot. When this variable and `VOYAGER_DB_PASSWORD` are both set, the file wins |
| `voyager.db.pool.size` | `VOYAGER_DB_POOL_SIZE` | `10` | A whole number from 2 to 20. A value outside the range is a problem: one WARN, and statistics stay off |
| `voyager.db.queue.capacity` | `VOYAGER_DB_QUEUE_CAPACITY` | `256` | A whole number from 16 to 4096. The number of race or cup completions buffered in memory during an outage |

The server never prints the password. The configuration check and the logs name the setting that is wrong, never its value.

The switch is read once, at boot. A change takes effect on the next start of the service.

## Behaviour

The table shows what the server does in each state. The game never refuses to start because of the database.

| State | Behaviour |
|---|---|
| The switch is unset or `off` | No statistics are recorded. One WARN at boot says that no statistics are kept on the server. `/stats` and `/leaderboard` answer that statistics are not recorded on this server. |
| The switch is `on`, and a setting is missing or invalid | One WARN at boot names the setting. The server runs with statistics off. |
| The switch is `on`, and the database is unreachable at boot | The server runs without statistics. Completions are buffered in memory. A background attempt retries the connection and the migration with a backoff that starts at 1 s, doubles, and is capped at 60 s. One WARN at boot names the failing setting, never the password. |
| The switch is `on`, and the database is lost while the server runs | Completions queue up, up to the queue capacity. The writer retries with the same backoff. One WARN when the outage starts and one when it ends. The race is not affected. |
| The queue is full | The newest completion is dropped. One WARN per drop names the cup and map keys. The drop is counted. |
| A data error (SQLSTATE class 22 or 23) | The completion is dropped after one attempt. One ERROR names the keys. The writer goes on with the next completion. |
| Shutdown | The writer drains the queue for at most 10 s. The number of completions still queued is logged. The connection pool closes after the writer stops. |

Each completion is one transaction. A finished map, or a finished cup, is either written completely or not at all.

Computer racers are never stored. Practice runs are stored with their mode, but they do not count in a player's profile or in
the leaderboards.

## Commands

Both commands are open to all players. No permission node is required.

| Command | Reply |
|---|---|
| `/stats [player]` | The profile of the named player, or of the sender when no name is given. It shows cups played, cup wins, the medals per tier, the total flight time and the personal bests. |
| `/leaderboard <map>` | The ten fastest personal bests of the current layout of the map, in ranked mode, fastest first. |

The replies are chat messages. They are not clickable. A read that takes longer than 2 s is answered with a message that
statistics are unavailable right now.

## Schema

The schema is `voyager`, created by one Flyway migration, `V1__baseline.sql`. It runs once per database under an advisory lock.
The tables are `player`, `player_statistics`, `race_session`, `race_result`, `race_split`, `cup_session`, `cup_result` and
`map_personal_best`. Maps and cups are identified by their keys as text; their geometry stays in the catalogue files.

Records are kept indefinitely. Erasure of a player's records is not yet available.
