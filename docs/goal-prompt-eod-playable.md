# Goal-Prompt: spielbarer Minestom-Stand bis Feierabend

Für eine **frische** Claude-Code-Session gedacht, nicht für die laufende Neubau-Session.
Von `/mnt/projects/oss/onelitefeather/Voyager` aus starten und den Block unterhalb der Linie
vollständig einfügen.

---

## Ziel

Bis heute Abend läuft ein Minestom-Server aus diesem Repo, auf dem ein Spieler ein vollständiges
Rennen fliegen kann: einloggen, Rennen startet, durch Ringe fliegen, Punkte bekommen, Ergebnis
sehen. Funktionsumfang mindestens auf dem Stand der alten Paper-Version — Phasen Lobby → Rennen →
Ende, Ringe geben Punkte, Endwertung mit den besten drei Spielern.

**Arbeite im Modul `server/`.** Nicht in `voyager-*`. Die `voyager-*`-Module sind ein separater,
parallel laufender Greenfield-Neubau, der heute nicht spielbar wird und den du nicht anfassen
sollst. `server/` ist ein weitgehend fertiges Minestom-Modul und der einzige Weg, der heute trägt.

## Definition of Done, in dieser Reihenfolge

Arbeite sie von oben nach unten ab. Jede Zeile ist beobachtbar — „implementiert" zählt nicht,
„ich habe es laufen sehen" zählt.

1. `./gradlew :server:shadowJar` erzeugt ein Jar, und `java -jar` startet daraus einen Server,
   der auf einen Port bindet, ohne zu crashen.
2. Ein Client verbindet sich und landet in einer geladenen Welt, nicht im Void.
3. Ein Rennen lässt sich starten und durchläuft Lobby → Rennen → Ende bis zum Schluss.
4. Ringdurchflüge werden erkannt und geben Punkte; der Spieler sieht das während des Flugs.
5. Am Ende steht eine Wertung mit den besten drei Spielern.
6. Ein Cup mit mehreren Maps schaltet weiter.
7. Ein zweiter Spieler kann gleichzeitig mitfliegen.

Punkte 1 bis 5 sind das Ziel. 6 und 7 sind Zugabe.

## Harte Grenzen

- **Fass `voyager-api`, `voyager-fitness`, `buildSrc` und alles unter `docs/superpowers/` nicht an.**
  Dort läuft parallel eine andere Session. Änderungen dort kollidieren.
- Arbeite auf einem eigenen Branch, nicht auf `main` und nicht auf
  `feat/greenfield-e1-foundation`.
- Kein Refactoring, das nicht unmittelbar ein Ziel aus der Liste freischaltet. Das Modul hat
  bekannte Altlasten — toter Parallelcode, doppelte Spline-Implementierungen, 0 von 10 erfüllten
  Design-Regeln. **Das ist heute egal.** Der Neubau räumt das auf; du machst etwas spielbar.
- Bestehende Tests dürfen nicht rot werden. Neue Tests nur, wo sie dir beim Vorankommen helfen.

## Wenn die Zeit knapp wird

Streiche in dieser Reihenfolge, und sage explizit, was du gestrichen hast:
Cup-Weiterschaltung → Mehrspielerbetrieb → HUD-Feinschliff → Datenbank-Persistenz
(`VOYAGER_PERSISTENCE=off`, falls vorhanden, sonst Persistenz überspringen) → Sound und Partikel.

**Nicht streichen:** Weltladen, Ringkollision, Phasenablauf. Ohne die drei ist nichts spielbar.

## Wenn du blockiert bist

Nicht zurückfragen und warten — entscheide und schreib auf, was du entschieden hast. Ein
falscher Weg, den ich abends sehe, kostet mich eine Rückabwicklung; eine Session, die auf eine
Antwort wartet, kostet mich den Tag.

Ausnahme, nur hier hältst du an: etwas Irreversibles, etwas Sicherheitsrelevantes, oder eine
Wirkung außerhalb dieses Repos (Push auf einen geteilten Branch, ein Deployment).

## Was du zuerst lesen solltest

| Datei | Wofür |
|---|---|
| `docs/migration/status.md` | **Mit Vorsicht.** Seit ihrem Erstellungs-Commit nie aktualisiert und in weiten Teilen falsch. Prüfe jede Aussage gegen den Code. |
| `server/src/main/java/net/elytrarace/server/VoyagerServer.java` | Einstiegspunkt, Bootstrap, Verdrahtung |
| `server/src/main/java/net/elytrarace/server/game/GameOrchestrator.java` | Der lebende Spielablauf |
| `server/src/main/java/net/elytrarace/server/phase/` | Lobby-, Game-, End-Phase |
| `docs/reference/elytra-physics-26.2.md` | Verifizierte Elytra-Physik aus dem dekompilierten 26.2-Quelltext |

## Bekannte Fallen, die dich sonst Stunden kosten

- **Toter Parallelcode.** `GameLoopSystem`, `GameSession`, `CupFlowServiceImpl` und `CupScoring`
  werden nirgends instanziiert und nur von ihren eigenen Tests am Leben gehalten. Der lebende Pfad
  läuft über `GameOrchestrator` und die ECS-Komponenten. Wenn du eine dieser vier Klassen änderst,
  änderst du nichts am laufenden Spiel.
- **`isGliding` existiert doch.** Ein Kommentar im Code behauptet, Minestom biete es nicht, und
  nutzt `isOnGround()` als Ersatz. Tatsächlich gibt es `entityMeta.isFlyingWithElytra()` und
  `PlayerStopFlyingWithElytraEvent`.
- **NaN friert Spieler dauerhaft ein.** Minestom-Issue 1335: ein einziges NaN in `setVelocity`
  deaktiviert den Velocity-Kanal des Spielers für den Rest der Sitzung, auch für alle späteren
  gültigen Aufrufe. Bei Pitch ±90° kann das in einer Normalisierung entstehen. Guard davor.
- **Instanzwechsel lässt Spieler unter die Map fallen** (Minestom-Issue 2017). Trifft den Übergang
  von Map zu Map im Cup.
- **Minestom-Version.** `settings.gradle.kts` pinnt `2026.04.13-1.21.11`. Für heute reicht das.
  Wechsle nicht auf 26.2 — das sind rund 148 Binärinkompatibilitäten und kostet dich den Tag.

## Rhythmus

Melde alle 60 bis 90 Minuten in drei Zeilen: welcher Punkt der Liste steht, woran du gerade
arbeitest, was dich aufhält. Keine Fortschrittsberichte darüber hinaus, keine
Zwischennachfragen.

Wenn Punkt 5 steht, hör auf und melde dich — auch wenn noch Zeit ist. Ab da ist alles Zugabe,
und ich will entscheiden, ob sie sich lohnt.
