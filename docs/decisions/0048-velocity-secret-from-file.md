# ADR-0048: The Velocity forwarding secret may come from a file named by `VOYAGER_VELOCITY_SECRET_FILE`, which takes precedence over the environment variable and the system property

## Status

Proposed

The owner accepts or rejects this record. The change that implements it is `read-proxy-secret-from-file` (OpenSpec). Status moves to
Accepted only on the owner's word. It extends ADR-0025, which left a file open for a later stage.

## Date

2026-11-02

## Decision makers

- TheMeinerLP (project owner), who asked for the secret to stop appearing in the process list.

## Context and problem statement

ADR-0025 signs forwarded player identity with a secret that the Velocity proxy also holds. The secret reaches the server through
the environment variable `VOYAGER_VELOCITY_SECRET` or the system property `voyager.velocity.secret`. ADR-0025 accepted one
consequence: an environment variable is visible to other processes of the same user on some systems. The system property has a
worse exposure: it is part of the command line of the process, and on a shared host every local user can read it from the process
list. ADR-0025 also rejected a secrets file for the time being, because the server had no file to protect. It named a file as the
follow-up.

The question is how an operator hands the secret to the server so that it is not in the process list, and what the server does when
that handover is broken.

## Decision drivers

- The secret must not be readable from the process list by other local users.
- A broken handover must not fall back to a weaker source, and must not start the server offline (ADR-0025).
- The live CloudNet task sets the system property today. Removing it in the same change would stop that service from starting.
- The database password already has a file convention, `VOYAGER_DB_PASSWORD_FILE`. The operator should learn one pattern.
- The secret must never reach a log line, a report or an exception message.

## Considered options

1. **A file named by `VOYAGER_VELOCITY_SECRET_FILE` (or the property `voyager.velocity.secret.file`), first in precedence.** The file
   is trimmed and read once, at boot. A missing, unreadable, non-UTF-8 or empty file refuses the start, and the message names the
   path, never the content. The environment and the property stay as lower sources. (Chosen.)
2. **Keep the environment variable and the property only, and document the risk.** Rejected: the property stays in the process list
   and the owner asked for it to go.
3. **Remove the system property.** Rejected for now: it would stop the live CloudNet task from starting until the node is changed.
   It is kept with a WARN and can be removed by a later change.
4. **Read the secret from a file in the working directory's `application.yaml`.** Rejected: a secret in a file that is also
   loaded as configuration is easy to commit and to copy into a template.

## Decision outcome

Chosen option 1.

- Precedence, highest first: `VOYAGER_VELOCITY_SECRET_FILE`, `voyager.velocity.secret.file`, `VOYAGER_VELOCITY_SECRET`,
  `voyager.velocity.secret`. The first source that is set decides, and a broken source is never bypassed.
- The file's content is trimmed of surrounding whitespace, so a newline written by an editor does not become part of the secret.
  The environment and property values keep their exact bytes, as ADR-0025 requires.
- The server logs one WARN when the secret comes from the property. The configuration check reports the same as a WARNING. The
  check reports a broken file as an ERROR and exits 1.
- The setup server follows the same rules, through the same settings type.
- The recommended place for the file is outside the service directory and outside any CloudNet template, owned by the server user,
  with mode `0400`. The template is copied into every service directory, and each stop removes that copy, so a secret in a template
  would be copied to every service. The how-to guide documents the setup.

## Consequences

- Good, because the secret no longer needs to be on the command line, and the file's permissions decide who can read it.
- Good, because a broken file cannot quietly turn signature checking off or run the server offline.
- Good, because the same convention as `VOYAGER_DB_PASSWORD_FILE` applies: a `_FILE` key names a path, read once, never printed.
- Bad, because the server does not check the file's permissions. A file readable by other users is accepted. The guide tells the
  operator to use mode `0400`; a warning for an over-permissive file is a follow-up.
- Bad, because there are now four sources for one secret. The configuration check prints the source of the secret, so an
  unexpected source is visible.
- Neutral, because the secret is still read once at boot, so a rotation needs a restart, as before.
