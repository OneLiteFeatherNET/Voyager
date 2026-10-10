# ADR-0025: Velocity modern forwarding with a secret from the environment or a property

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. The change that
implements it is `add-luckperms-permissions`, which does not mark this record Accepted.

## Date

2026-10-10

## Decision makers

- TheMeinerLP (project owner). Confirmed the scope of forwarding on 2026-10-10.
- Drafted with the change `add-luckperms-permissions`.

## Context and problem statement

The owner's CloudNet network runs the game and setup servers behind a Velocity proxy. Without player-identity forwarding, a
server behind a proxy sees the proxy's connection, and the player UUID it reads is not the account UUID. LuckPerms and every
per-player lookup key on that UUID, so grants would not apply.

The CloudNet RC16 release notes say that the CloudNet bridge "does not enable any proxy authentication anymore. This must be
done by the Minestom server implementation". Voyager therefore has to configure forwarding itself. Minestom 2026.08.28-26.2
provides `Auth.Velocity(String secret)`, and `MinecraftServer.init(Auth)` takes it.

The open questions are where the secret comes from, what happens without one, and how a secret is kept out of output.

## Decision drivers

- A forwarded UUID must be trusted only when the signature verifies against a secret the proxy also holds.
- The same jar must start on a developer machine with no proxy, and must start under a CloudNet node with no code change.
- A secret must never reach a log line, an exception message, a configuration report or a `toString`.
- A misconfigured secret must not quietly fall back to offline authentication.

## Considered options

1. **`Auth.Velocity` with the secret from `VOYAGER_VELOCITY_SECRET`, else the property `voyager.velocity.secret` (chosen).**
   Without a secret the server starts offline and logs a WARN. A blank value refuses to start.
2. **Always offline, and rely on the proxy's own network rules.** Rejected. A player could connect to the port directly and claim
   any UUID. A public port needs the signature check.
3. **Bungee (legacy) forwarding, `Auth.Bungee`.** Rejected. The proxy is Velocity, and Bungee forwarding has no signature.
4. **Secret in `ServerSettings` only, from a config file.** Rejected for now. The CloudNet node passes environment variables and
   system properties, and the server has no secrets file to protect. A file can follow with the persistence stage.

## Decision outcome

Chosen option: **1**.

- `ProxyForwardingSettings` (`net.elytrarace.voyager.platform.proxy`) reads the environment variable first, then the property. A
  non-blank value is used as the secret; no value means offline; a blank value throws before the server starts.
- The record's `toString` masks the secret, and no log line, exception message or configuration report prints it.
- The composition roots map the record to Minestom's `Auth`: `new Auth.Velocity(secret)` when present, `MinecraftServer.init()`
  (offline) otherwise. The platform keeps the record free of Minestom types.
- Without a secret, the server logs one WARN line that says player UUIDs are not forwarded.

## Consequences

- Good: a player connecting through the proxy with a valid signature keeps the account UUID that LuckPerms keys on. A connection
  with a wrong signature is refused.
- Good: the same jar runs offline on a developer machine and behind a node, with only the environment changed.
- Good: a blank secret cannot silently disable the signature check.
- Bad: the operator must set the same secret on the proxy and on each proxy-facing server, and a rotation needs both sides changed
  together. The how-to guide `docs/guides/how-to-run-behind-a-proxy.md` describes the steps.
- Bad: an environment variable is visible to other processes of the same user on some systems. Accepted for now; the secret
  should be treated as a credential.
- Neutral: the Velocity handshake itself is not exercised by the test environment, which skips the login handshake. Acceptance task
  12.6 checks it against a real Velocity proxy.

## More information

- `openspec/changes/add-luckperms-permissions/design.md`, decision 6, and `specs/server/proxy-forwarding/spec.md`.
- ADR-0024 (permission port), which depends on the UUID that forwarding provides.
- Minestom 26.2 `Auth.Velocity` (`net.minestom.server.Auth$Velocity`).
