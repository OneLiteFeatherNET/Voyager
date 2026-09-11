## 1. Kontext & Ausgangslage

Voyager ist ein Minecraft-Elytra-Rennspiel nach Mario-Kart-Vorbild: Spieler fliegen durch
Cups aus mehreren Karten, jede Karte vergibt Punkte über Ringe. Produktiv läuft bis heute
der Kotlin-Stand aus dem Jahr 2023 (Commit `85d77c6a8309fa1f2848229c9b8f8e634b6b2c69`) —
stabil, aber seit langem nicht mehr weiterentwickelt. Seitdem wurde das Projekt als
Multi-Modul-Gradle-Build in Java neu geschrieben und teilweise von Paper auf Minestom
migriert. Dieser Java-Baum ist der, der gerade durch den in diesem Dokument beschriebenen
Rebuild ersetzt wird — nicht der produktive Kotlin-Stand.

Der aktuelle Java-Baum erfüllt den Standard nicht, den das Projekt sich selbst gesetzt hat.
Ein Audit des `server`-Moduls gegen die zehn verbindlichen Design-Regeln aus `CLAUDE.md`
ergab **null vollständig erfüllte Regeln** (drei teilweise, sieben nicht erfüllt). Darüber
hinaus enthält das Modul eine komplette, tote Parallel-Implementierung seiner eigenen
Spiellogik (`GameLoopSystem`, `GameSession`, `CupFlowServiceImpl`, `CupScoring` — nie
außerhalb ihrer eigenen Tests instanziiert), Spline-Logik existiert in bis zu drei Kopien,
und die vorhandenen ArchUnit-Regeln laufen teils leer (`allowEmptyShould(true)`) oder
scannen gar nicht den vollständigen Modulbaum. Details siehe Abschnitt "Evidence" der
Design-Spec (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`).

Weder der aktuelle Java-Baum noch der Rebuild stehen unter Produktionsdruck, da weiterhin
der Kotlin-Stand produktiv läuft. Der Cut-over-Zeitplan wird daher von Reife getrieben, nicht
von einem Ausfallrisiko.

Dieses Dokument spezifiziert einen **Greenfield-Rebuild**: einen neuen, achtteiligen
Modulbaum im selben Repository, der `server`, `plugins/game`, `plugins/setup` und alle vier
`shared/*`-Module vollständig ersetzt. Beide Modulbäume koexistieren während der
Rebuild-Phase; `main` bleibt durchgehend baubar (Entscheidung D2).

## 2. Ziele & Nicht-Ziele

### Ziele

- Strukturelle Schulden werden nicht in den bestehenden Modulgrenzen nachgebessert,
  sondern durch einen neuen, domänenorientierten Achtmodul-Schnitt ersetzt (D1, D8).
- Vanilla-Elytra-Parität wird zuerst bewiesen (Trace-Vergleich gegen echte Vanilla-Flüge,
  D3, D4), bevor irgendetwas Spielbares gebaut wird — das zentrale Projektrisiko muss
  innerhalb von Wochen sichtbar werden, falls es unerreichbar ist.
- Der Client behält während des normalen Flugs die Bewegungsautorität; der Server prüft
  nur Plausibilität (D5) — kein Rubber-Banding in einem Flugspiel.
- Der Setup-Server wechselt vollständig auf Minestom; Paper entfällt komplett aus dem
  Projekt (D6), damit Java 25 in allen Modulen einsetzbar ist.
- `io.airlift:guice:10` wird als DI-Container verwendet, mit Annotationen ausschließlich in
  den Composition Roots (D10).
- `CLAUDE.md` wird durch diese Spezifikation abgelöst (D12) und in Stufe 1 neu geschrieben
  — nicht erst beim Cut-over.
- Der Rebuild startet mit einer leeren Datenbank; es gibt keine Datenübernahme aus dem
  2023er-Kotlin-Stand (D11).

### Nicht-Ziele

- **Feature-Parität mit dem aktuellen Java-Baum vor dem Cut-over.** Die Messlatte ist D3:
  Physik-Parität plus ein spielbarer Rennlauf (Stufe 4). Persistenz, Setup und Feinschliff
  folgen danach.
- **Elytra-Haltbarkeitssimulation.** Beim Rennen wird keine Elytra verbraucht.
- **Ein generisches Anti-Cheat-System.** Die Plausibilitätsprüfung validiert Läufe; sie
  überwacht keine Spieler.
- **Virtuelle Threads im Tick-Pfad.** Eine Fixed-Budget-20-TPS-Schleife profitiert davon
  nicht.

## 3. Stakeholder & Rollen

| Rolle | Beschreibung |
|---|---|
| Entwickler | Baut und pflegt die acht neuen Module, die Build-Konventionen, die Architektur-Fitness-Regeln und die Tests. Größte Anzahl der Anforderungen in diesem Dokument betrifft diese Rolle direkt. |
| Betreiber | Betreibt die Server-Infrastruktur (CloudNet v4, MariaDB, Deployment), verantwortet Konfiguration, Secrets und den laufenden Servicebetrieb. |
| Map-Builder | Erstellt und pflegt Karten, Cups und Portale über den Setup-Server bzw. den Konversations-Wizard. |
| Game-Designer | Definiert Gameplay-Tuning (Boost, Scoring, Plausibilitätstoleranzen) sowie Ring-/Map-/Cup-Balancing. |
| Spieler | Nutzt das fertige Spiel — fliegt Rennen, sieht das HUD, erlebt Boost- und Ringeffekte. |
| Projektinhaber (Genehmigungsinstanz) | Keine der fünf Story-Rollen im engeren Sinne, aber die zwingende Freigabeinstanz für alle als "requires explicit user approval"/"requires user decision" markierten Punkte (u. a. `CLAUDE.md`-Neufassung, E6.0-Empfehlung, Cut-over-Ankündigung). |

## 4. Ausbaustufen-Übersicht

| Stufe | Epic | Ziel | Fertig, wenn (laut Spec) | User Stories |
|---|---|---|---|---|
| 1 | E1 | Foundation: Build-Konventionen, `voyager-api`, `voyager-fitness`, `CLAUDE.md`-Neufassung | ArchUnit importiert jedes Modul; keine Regel läuft leer | [user-stories-stufe-1.md](user-stories-stufe-1.md) |
| 2 | E2 | Vanilla-Parität: Recorder, Trace-Fixtures, `voyager-physics` | Trace-Suite für alle acht Flugprofile innerhalb der Toleranz grün | [user-stories-stufe-2.md](user-stories-stufe-2.md) |
| 3 | E3 | `voyager-race`: Zustandsautomat, Ringe, Scoring | Vollständiges Rennen ohne Server spielbar | [user-stories-stufe-3.md](user-stories-stufe-3.md) |
| 4 | E4 | `voyager-platform` + `voyager-server` | Erster flugfähiger Build | [user-stories-stufe-4.md](user-stories-stufe-4.md) |
| 5 | E5 | `voyager-persistence` | Rekorde und Profile überleben einen Neustart | [user-stories-stufe-5.md](user-stories-stufe-5.md) |
| 6 | E6 | `voyager-setup` auf Minestom — **blockiert durch E6.0** | Eine Karte ist ohne Paper konfigurierbar | [user-stories-stufe-6.md](user-stories-stufe-6.md) |
| 7 | E7 | Cut-over | Alter Baum entfernt; Java 25 überall | [user-stories-stufe-7.md](user-stories-stufe-7.md) |

**Cut-over-Gate (wörtlich, nicht abschwächen):** Der Trigger für Stufe 7 ist **Stufe 4
erreicht plus eine grüne Trace-Suite aus Stufe 2** — nicht Feature-Parität mit dem
aktuellen Java-Baum. Stufe 5 und Stufe 6 dürfen bei Beginn von Stufe 7 noch offen sein; sie
sind keine Gate-Bedingung.

## 5. Nicht-funktionale Anforderungen

| ID | Anforderung | Messkriterium | Quelle (Design-Spec) |
|---|---|---|---|
| NFR-001 | Tick-Budget bei 20 TPS | Der Tick-Thread wartet nie auf JDBC oder Futures; IO-Completions laufen über eine begrenzte MPSC-Queue, die zu Tick-Beginn unter einem festen Budget verarbeitet wird; ein Burst von 50 gleichzeitigen Persistence-Completions darf keinen Tick auslassen, nur den Backlog vergrößern. | Abschnitt "Tick safety" |
| NFR-002 | Java 25 überall | Alle acht neuen Module kompilieren mit `--release 25`, deklariert genau einmal in `buildSrc`; nach Stufe 7 existiert kein `--release 21`-Block mehr im gesamten Build. | Abschnitt "Target platform", "Build" |
| NFR-003 | Modulisolation (strikter Abhängigkeitsgraph) | `voyager-api`, `voyager-physics`, `voyager-race`, `voyager-persistence` referenzieren kein `net.minestom..`; kein Modul referenziert `org.bukkit..`; `voyager-persistence` referenziert keine race- oder platform-Pakete; ein Verstoß (z. B. `race` hängt von `platform` ab) lässt bereits die Gradle-Konfiguration fehlschlagen, nicht erst ArchUnit. | Abschnitt "Module architecture", "voyager-fitness" |
| NFR-004 | Keine leer laufenden Architekturregeln | Jede `@ArchTest`-Regel nutzt `allowEmptyShould(false)` als Modul-Default; jede ausgeführte Regel prüft mindestens eine reale Klasse, verifiziert durch eine Importzahl-Assertion. | Abschnitt "voyager-fitness" |
| NFR-005 | Trace-Toleranzen | Positionsabweichung pro Tick < `1e-6` Blöcke, kumulative Abweichung über 200 Ticks < `0.01` Blöcke — beide gegen reale Vanilla-26.2-Traces kalibriert; Bestätigung oder Korrektur mit Begründung ist im Ticket dokumentiert, nicht stillschweigend angepasst. | Abschnitt "Trace acceptance" |
| NFR-006 | Secrets nie im Repository | Datenbank-Zugangsdaten erscheinen nie im Repository, im Jar, in einem CloudNet-Template oder einer Task-JSON; sie werden ausschließlich über Umgebungsvariablen oder die `VOYAGER_DB_PASSWORD_FILE`-Konvention übergeben, nie als `-D`-Systemproperty. | Abschnitt "Secrets" |
| NFR-007 | Fail-Fast bei ungültiger Konfiguration | Der Loader sammelt alle Konfigurationsfehler und meldet sie gemeinsam als `ConfigProblem(key, source, message)` in einer Exception; kein Construct-and-die beim ersten fehlerhaften Feld. `-Dvoyager.config.check=true` validiert und beendet den Prozess mit Exit-Code 0/1, ohne einen Socket zu binden. | Abschnitt "Validation" |
| NFR-008 | Kein DB-Zugriff im Tick-Pfad | Keine Klasse in `..race..` oder `..platform..system..` ruft `CompletableFuture.get`, `join` oder `Future.get` auf; Schreibzugriffe laufen ausschließlich fire-and-forget über einen einzigen `voyager-db-write`-Executor. | Abschnitt "Tick safety" |
| NFR-009 | Kein Rubberbanding | Der Client behält während des normalen Flugs die Bewegungsautorität; `setVelocity` wird im gesamten Baum von genau einer Klasse und nur für Boost-Burn, Ring-BOOST/SLOW und Out-of-Bounds-Reset aufgerufen; eine Plausibilitätsverletzung korrigiert nie die Position, sondern verwirft nur Punkte/Rekord. | Abschnitt "Velocity authority", Entscheidung D5 |
| NFR-010 | Datenbank-Query-Performance | Die vier kritischen Queries (Leaderboard, Cup-Standings, Profil-bei-Join, Map-Record) zeigen in `EXPLAIN` kein `Using filesort`, kein `Using temporary` und kein `type: ALL` auf einer Tabelle über 1000 Zeilen. | Abschnitt "Query design" |
| NFR-011 | DI-Annotationen nur in Composition Roots | `@Inject`, `@Singleton` und `jakarta.inject`-Importe erscheinen außerhalb von `voyager-server`/`voyager-setup` nicht; kein Code ruft `bindInterceptor` auf. | Abschnitt "Dependency injection" |

## 6. Offene Fragen / Risiken

### Risikoregister

| Risiko | Auswirkung | Auflösung | Wo bearbeitet |
|---|---|---|---|
| FAWE hat kein Minestom-Äquivalent | Blockiert Stufe 6 | Eigene Recherche-Epic vor Planung von Stufe 6 | E6.0 |
| `air_drag_modifier` könnte den 26.2-Elytra-Drag-Pfad verändern | Drag-Konstanten falsch, gesamtes Tracking driftet | Decompile-Check von `LivingEntity.travel()` in 26.2, vor Abschluss von Stufe 2 | E2.3 |
| Trace-Toleranzen zu eng oder zu locker gesetzt | Falsche Fehlschläge, oder Parität ohne Beweis behauptet | Kalibrierung gegen die ersten realen Traces | E2.6 |
| Plausibilitäts-Schwellenwerte verwerfen legitime, schnelle Piloten | Gültige Rekorde werden verworfen | Log-only in v1; Scharfschaltung erst auf Basis gemessener Verteilungen | E4.4 |
| Minecraft 26.3 erscheint während des Rebuilds | Mögliche doppelte Migration | Minestom bleibt auf `voyager-platform` beschränkt; `releases.atom` wird bei jeder Stufe-4-Sprintplanung erneut geprüft | Laufender Punkt (empfohlen bei Flightplan/voyager-project-manager) |
| Vanilla-26.2-Recording-Setup ist aufwändiger als geschätzt | Stufe 2 verzögert sich und blockiert alles Weitere | Recorder vor Festlegung des Stufe-2-Umfangs prototypisch erproben | E2.1 |
| Serverseitiges Recording sieht die interne Client-Geschwindigkeit nicht | Manche Abweichungsklassen bleiben unsichtbar | Akzeptiertes Risiko: die Positionssequenz ist genau das, was auch in Produktion gemessen wird | E2.2 (dokumentierte akzeptierte Einschränkung) |
| `io.airlift:guice` ist ein Single-Vendor-Fork, an Trinos Bedarf ausgerichtet | Aufgabe des Forks würde eine DI-Migration erzwingen | Annotationen bleiben auf zwei Composition Roots beschränkt; Version exakt gepinnt; erneute Prüfung vor jeder JDK-26-Migration | E4.8 |

### Offene Konfigurationsfragen

| Frage | Auswirkung | Auflösung | Wo bearbeitet |
|---|---|---|---|
| Verteilt der CloudNet-Wrapper Node-Umgebungsvariablen an jeden Service oder nur an im Task deklarierte? | Entscheidet, ob Datenbank-Zugangsdaten überhaupt auf dem Node liegen können | Verifikation gegen die gepinnte RC auf einem Staging-Node vor Stufe 5 | E4.12 |
| Welche CloudNet-v4-RC pinnt die Organisation tatsächlich? | Falsche Koordinaten lassen sich nicht auflösen; ggf. sind Snapshot-Repositories nötig | Abgleich gegen die Inhalte von `aonyx-bom`/`manis-bom` vor Stufe 4 | E4.0 |
| Wird `voyager-cloudnet-bridge` für das Lobby-Routing nach einem Cup benötigt, oder deckt die bestehende Bridge das ohne eigenen Code ab? | Entscheidet, ob ein neuntes Modul in den Baum aufgenommen wird | Entscheidung während der Stufe-4-Planung; die `compileOnly`-Grenze ist so oder so spezifiziert | E4.13 |
| Sind Tuning-Revisionen es wert, Leaderboards danach zu segmentieren, oder nur, sie aufzuzeichnen? | Produktentscheidung | Ab Stufe 5 wird in jedem Fall aufgezeichnet; Segmentierung ist ein späteres, zurückgestelltes Query-seitiges Anliegen | E5.1 |

## 7. Abnahmekriterien

- [ ] `voyager-fitness` importiert alle acht Module; keine Architekturregel läuft mit `allowEmptyShould(true)` oder ohne mindestens eine geprüfte Klasse
- [ ] Die Trace-Suite ist für alle acht Flugprofile (steady glide, climb into stall, dive and pull-out, single firework boost, chained firework boosts, pitch at ±90°, glancing wall collision, landing) innerhalb der kalibrierten Toleranz grün, ohne laufende Serverinstanz
- [ ] Ein vollständiges Rennen (Lobby → Preparation → Game → End, inklusive Practice-Retry-Loop) läuft in einem einzigen JUnit-Test ohne Minestom-Instanz und ohne Netzwerk-I/O durch
- [ ] Ein Spieler kann auf dem neuen Minestom-Server ein Rennen fliegen, Ringe passieren, das Rennen beenden und einen Score sehen — dokumentiert durch einen manuellen Playtest sowie mindestens einen Cyano-Integrationstest
- [ ] `setVelocity` wird im gesamten Baum ausschließlich von genau einer Klasse aufgerufen, und nur für Boost-Burn, Ring-BOOST/SLOW und Out-of-Bounds-Reset
- [ ] Cut-over-Gate erreicht: Stufe 4 ist erreicht UND die Trace-Suite aus Stufe 2 ist grün — nicht Feature-Parität mit dem alten Java-Baum
- [ ] Ein Spieler beendet ein Rennen, der Server startet neu, Rekord und Profil sind danach lesbar
- [ ] Stufe-6-Folgetickets (E6.1 und später) werden erst zur Umsetzung freigegeben, nachdem die Empfehlung aus E6.0 vom Projektinhaber genehmigt wurde
- [ ] Eine Karte ist auf dem Minestom-Setup-Server ohne jede Paper-Abhängigkeit erstellbar, editierbar und speicherbar
- [ ] `server/`, `plugins/game/`, `plugins/setup/` und alle vier `shared/*`-Module sind entfernt; `./gradlew build` läuft ausschließlich mit den acht neuen Modulen, `--release 25` durchgängig
- [ ] Das Cut-over-Gate (Stufe 4 + grüne Trace-Suite aus Stufe 2) wurde bei der Umsetzung nicht stillschweigend zu "wenn alles fertig ist" abgeschwächt
