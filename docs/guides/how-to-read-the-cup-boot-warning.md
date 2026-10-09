# How to read the cup boot warning

The server plays one cup. Boot checks that cup in full and refuses to start if it cannot be played. The
other cups in the data directory are not played, so a problem in one of them does not stop the server. It
is reported once, in a single `WARN` line, and the server starts without it.

## Read the warning

A broken unplayed cup produces one line in the log at boot:

```text
WARN [net.elytrarace.voyager.platform.catalog.CatalogReloader] 2 cup(s) are not playable and were skipped: /data/cups/bad.json is not a valid definition: ...; cup 'other_cup' plays 'no-such-map'
```

Each item names what is wrong:

- A cup file that does not parse. The item gives its full path and the reason.
- A map entry a cup plays that no map file provides, as `cup '<name>' plays '<map>'`.

The warning lists every problem in one line, because a renamed map usually breaks several cups at once.
A cup that is listed is never played.

## Fix an unplayable cup

1. Find the cup named in the warning, or the file path it gives.
2. For a file that does not parse, correct its JSON. The file must have a `name`, a `mode` (`RACE` or
   `PRACTICE`, required) and a `mapNames` list.
3. For a map entry no map file provides, either add the map file under `maps/`, or change the cup's
   `mapNames` to a map that exists.
4. Restart the server. The warning disappears once no cup has a problem.

To play a cup on purpose, name it. Gradle takes `-Pcup=<name>`, and `java -jar` takes
`-DVOYAGER_CUP=<name>`. The name is the one inside the file, not the file name.

## What stops boot

Boot refuses, and does not start, in these cases:

- The played cup has a problem: its file does not parse, or it plays a map no map file provides. The
  refusal lists only that cup's entries.
- No cup is named and the cup directory holds more than one cup file, even if all but one of them do not
  parse. The refusal names every cup file and both switches.
- A name is given that matches no cup. The refusal lists the cups that exist.
- A name is given that matches a file that does not parse. The refusal names that file.
- A map file does not parse, or the `maps/` or `cups/` directory is missing or holds no `.json` file.
