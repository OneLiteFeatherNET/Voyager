# Run behind a Velocity proxy

This guide sets up a game or setup server behind a Velocity proxy, so that the server reads each player's real UUID from
the proxy. LuckPerms and every per-player lookup key on that UUID, so without forwarding a proxied player's grants do not
apply.

## Set the secret on the server

The server reads the forwarding secret from one of two places:

1. The environment variable `VOYAGER_VELOCITY_SECRET`. It wins when both are set.
2. The system property `voyager.velocity.secret`, passed as `-Dvoyager.velocity.secret=<secret>`.

```text
VOYAGER_VELOCITY_SECRET=<secret> java -jar voyager-server.jar 127.0.0.1 25565
```

The secret is never printed. Not in the log, not in the configuration check, not in an error message. Keep it out of shell
history and out of files that are committed.

## Match the proxy

On the proxy, set Velocity's modern forwarding and the same secret. In `velocity.toml`:

```toml
player-info-forwarding-mode = "MODERN"
forwarding-secret-file = "forwarding.secret"
```

Write the same value into `forwarding.secret`. The value on the proxy and the value on the server must be identical.

## Check the result

Start the server and look at the first lines of the log:

- With a valid secret, the server starts with forwarding enabled and logs no secret-related line.
- With no secret set, the server runs in offline mode and logs this `WARN` line:

  ```text
  No Velocity secret is set (VOYAGER_VELOCITY_SECRET or -Dvoyager.velocity.secret): the server runs in offline mode and player UUIDs are not forwarded from a proxy. ...
  ```

  Offline mode accepts any player name without proof. Do not expose an offline server to the public network.

- With a blank secret, for example `VOYAGER_VELOCITY_SECRET=" "`, the server refuses to start with this error and exits with
  status 1:

  ```text
  VOYAGER_VELOCITY_SECRET must not be blank: set the secret, or unset the variable to run offline
  ```

  A blank value is never treated as "no secret". Unset the variable if you want offline mode.

- A connection signed with a different secret is refused. The player does not join.

## Set the bind address

A proxy usually reaches the server on a private address. Set the address and port with the arguments or the properties
`service.bind.host` and `service.bind.port`:

```text
java -Dservice.bind.host=10.0.0.5 -Dservice.bind.port=25571 -jar voyager-server.jar
```

Arguments win over the properties, and the properties win over the defaults `0.0.0.0` and `25565`. A port outside 1 to
65535 refuses to start and names `service.bind.port`.

## Stop the server

Type `stop` on the server's standard input. The server shuts down cleanly and exits with status 0. An operator with the
`voyager.command.stop` node may also run `/stop` in game. The console line `end` is not a stop command and gets an
`Unknown command` reply.
