# Proposal

**Type and scope:** `feat(server)`. The change touches the proxy-forwarding settings in `voyager/platform` (`platform.proxy`)
and the two composition roots that read them, `voyager/server` and `voyager/setup`. It ships as one code change under the
title `feat(server): read the Velocity secret from a file`. Breaking: no. Every existing source keeps working; a server that
sets none of the new keys behaves as before, apart from one new WARN when the secret comes from the system property.

## Why

The Velocity forwarding secret reaches the game server and the setup server as a JVM system property,
`-Dvoyager.velocity.secret=...`, or as the environment variable `VOYAGER_VELOCITY_SECRET`. A system property is part of the
command line of the process. On a shared host, every local user can read it from the process list, so the secret that signs
the forwarded player identity is visible to anyone who can run `ps`. The secret should come from a file that only the server
user can read, and the operator should be told when the property is the source.

## What Changes

- A new setting `VOYAGER_VELOCITY_SECRET_FILE`, read through the configuration loader (ADR-0036) with the property name
  `voyager.velocity.secret.file`. Its value is the path of a file. The file's content, trimmed of surrounding whitespace, is the
  secret.
- Precedence of the secret sources, highest first: the file named by `VOYAGER_VELOCITY_SECRET_FILE`, then the file named by
  `voyager.velocity.secret.file`, then `VOYAGER_VELOCITY_SECRET`, then `voyager.velocity.secret`. Within the file keys the
  environment variable wins over the property, as it already does for the secret.
- A file that is missing, unreadable, not UTF-8, or empty after trimming refuses the start. The message names the setting and the
  path, and never the content. The server does not fall back to a lower source or to offline mode.
- A secret taken from the system property `voyager.velocity.secret` keeps working. The server logs one WARN for it, and the
  configuration check reports one WARNING that names `VOYAGER_VELOCITY_SECRET_FILE`. The report never prints the value.
- The setup server reads the same settings through the same type, so it gets the same behaviour, the same refusals and the same
  WARN.
- The offline WARN names the file setting as one of the ways to set a secret.
- The configuration check reads the two new keys, so an unreadable file is reported as an ERROR with exit status 1, and a secret
  from the property as a WARNING with exit status 0.
- Documentation: the configuration reference, the proxy how-to and the CloudNet how-to name the file as the recommended source,
  with its permissions and location.

## Capabilities

### Modified Capabilities

- `server/proxy-forwarding`: the secret sources and their precedence, the file, the refusals, and the property WARN. The rule that
  the secret never appears in output now also covers the file's content.

## Decisions taken in this proposal

- **The file wins over the environment variable and the property.** A file is the source the operator chooses on purpose, and it
  is the one that is not in the process list. An environment variable is read by the process only; the property is in the
  process list. The order is file, environment, property.
- **The file is read once, at boot.** Rotation of the secret needs a restart, as the secret already does. A file that changes
  while the server runs has no effect until the next start.
- **A broken file refuses the start.** ADR-0025 requires that a misconfigured secret never quietly turns signature checking off or
  falls back to offline mode. A file that cannot be read is a misconfiguration of the same kind, so it refuses like a blank
  secret.
- **The property is kept and warned about, not removed.** Existing nodes and the CloudNet task set the property today. Removing
  it in the same change would stop a live service from starting. A later change can remove it once the nodes have moved.
- **The file's content is trimmed; the environment and property values are not.** A file written with an editor or `echo` ends
  with a newline, which must not become part of the secret. The environment and property values keep their exact bytes, as the
  specification states today.
- **No permission check in this change.** The server could warn when the file is readable by other users. That check is
  platform-specific and is not needed for the refusal rules, so it is a follow-up.
- **Design authority.** The greenfield design (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, D14) names the two
  existing sources. This change adds a third source and keeps D14's rule that the environment wins over the property. ADR-0025
  left a file open for the persistence stage; a Proposed ADR (0048) records the decision. The ADR stays Proposed until the owner
  accepts it.

## Dependencies

- ADR-0025 (Velocity modern forwarding with a secret) and ADR-0036 (avaje-config). The loader reads the new keys, and the
  environment-over-property rule for the secret is unchanged.
- `server/proxy-forwarding` and `server/config-check-mode`. The check reports through the existing `ConfigProblem` shape.
- The persistence password file (`VOYAGER_DB_PASSWORD_FILE`, `database.md`). This change follows its convention: a `_FILE` key
  names a path, the content is read once, and no value is printed.

## Out of scope

- A permission or owner check on the secret file, and a warning for a file readable by other users (see above).
- Runtime reload of the secret, and rotation without a restart.
- The Velocity proxy's own configuration (`forwarding-secret-file` in `velocity.toml`). The operator sets the same secret on both
  sides, as today.
- Removing the system property. It stays as a compatibility source in this change.
- Kubernetes secret mounts and Agones. A mounted secret works through `VOYAGER_VELOCITY_SECRET_FILE` without a code change, but
  the deployment manifests are a later stage.
- Changing the CloudNet task on the live node. Moving the live service to the file is an operator action after the change is
  merged, not part of the pull request.
- The `ServerSettings` record and `ServerSettings.KEYS`. The forwarding keys are read by `ProxyForwardingSettings`, as the secret
  already is.
- Any player-facing text. The change adds log and report lines for operators only.
