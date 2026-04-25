---
name: db-start
description: Start the MariaDB Docker container for local development, check health, and show connection info.
---

# Start Development Database

Start the Voyager MariaDB container, verify it is healthy, and display connection details.

## Steps

1. Check if the container is already running:
```bash
docker compose -f /mnt/projects/oss/onelitefeather/Voyager/docker/mariadb/compose.yml ps
```
If the `voyager-mariadb` container already shows `running`, skip step 2 and go straight to step 4.

2. If not running, start the container:
```bash
docker compose -f /mnt/projects/oss/onelitefeather/Voyager/docker/mariadb/compose.yml up -d
```

3. Show the last log lines to confirm the server is ready:
```bash
docker compose -f /mnt/projects/oss/onelitefeather/Voyager/docker/mariadb/compose.yml logs --tail=5
```
Look for `ready for connections` in the output. If the container is still initialising, wait a few seconds and repeat.

4. Report the connection details to the user:

| Property  | Value             |
|-----------|-------------------|
| Host      | `localhost`       |
| Port      | `3306`            |
| Database  | `voyager-project` |
| User      | `voyager-project` |
| Password  | `voyager-project` |

5. Remind the user of the stop command:
```bash
docker compose -f /mnt/projects/oss/onelitefeather/Voyager/docker/mariadb/compose.yml down
```

## Output

- Whether the container was already running or was freshly started
- Last 5 log lines confirming readiness
- Connection details table
- Stop command for reference
