# Voyager Migrationsplan: Paper zu Minestom

> **Status:** Entwurf
> **Erstellt:** 2026-03-29
> **Ziel:** Migration des ElytraRace Game-Plugins von Paper nach Minestom als Standalone-Server

## Entscheidungen

| Entscheidung | Wert |
|---|---|
| Java-Version | 25 |
| Server-Typ | Minestom Standalone (eigene `main()`) |
| Welt-Format | Anvil (bestehende Maps kompatibel) |
| Conversation API | Neu geschrieben, plattform-agnostisch |
| Setup-Plugin | Entfaellt (wird spaeter separat geloest) |

## Betroffene Module

| Modul | Aktion |
|---|---|
| `shared/common` | Bereits Bukkit-frei -- keine Aenderung noetig |
| `shared/phase` | Bereits Bukkit-frei -- keine Aenderung noetig |
| `shared/conversation-api` | **Neu schreiben** -- 7 Dateien mit Bukkit-Imports |
| `shared/database` | Unveraendert -- Hibernate/HikariCP bleibt |
| `plugins/game` | **Komplett migrieren** auf Minestom |
| `plugins/setup` | **Entfaellt** im MVP (FAWE-abhaengig) |

---

## Milestones

### Milestone 1: Foundation (Infrastruktur)

Grundlegende Build- und Server-Infrastruktur aufsetzen.

| ID | Titel | Beschreibung | Groesse | Abhaengigkeit | Agent |
|---|---|---|---|---|---|
| M1-01 | `chore: Java 25 Upgrade in Gradle konfigurieren` | `sourceCompatibility`, `targetCompatibility` und `--release`-Flag auf Java 25 setzen. Gradle Toolchain auf JDK 25 umstellen. | S | -- | voyager-build-agent |
| M1-02 | `chore: Minestom Dependency zum Version Catalog hinzufuegen` | Minestom als Library in `settings.gradle.kts` Version Catalog aufnehmen. Paper-Dependency bleibt vorerst fuer shared-Module bestehen. | S | -- | voyager-build-agent |
| M1-03 | `refactor: Neues Modul `server` fuer Standalone-Server anlegen` | Neues Gradle-Submodul `server` erstellen, das als Standalone-Minestom-Server fungiert. Abhaengigkeiten zu `shared/common`, `shared/phase`, `shared/database` konfigurieren. | M | M1-01, M1-02 | voyager-build-agent |
| M1-04 | `feat: Minestom Server-Bootstrap mit main() implementieren` | `main()`-Methode, `MinecraftServer.init()`, Standard-Instance erstellen, Server auf konfigurierbarem Port starten. Grundlegende Server-Konfiguration (MOTD, Max Players) via Datei oder Umgebungsvariablen. | M | M1-03 | voyager-core-agent |
| M1-05 | `chore: GitHub Actions CI/CD auf Java 25 und neues Modul anpassen` | Build-Matrix auf Java 25 aktualisieren. `server`-Modul in Build-Pipeline aufnehmen. Shadow-JAR-Artefakt fuer das Server-Modul erzeugen. | S | M1-03 | voyager-build-agent |
| M1-06 | `test: Server-Bootstrap Integrationstest schreiben` | Test, der den Minestom-Server startet, auf einen Port bindet und sich wieder herunterfaehrt. Verifiziert, dass der Lifecycle funktioniert. | S | M1-04 | voyager-test-agent |

---

### Milestone 2: Shared Module Cleanup

Conversation API plattform-agnostisch neu schreiben und letzte Bukkit-Abhaengigkeiten entfernen.

| ID | Titel | Beschreibung | Groesse | Abhaengigkeit | Agent |
|---|---|---|---|---|---|
| M2-01 | `refactor: Plattform-Abstraktionen fuer Conversation API definieren` | Interfaces fuer `ConversationPlayer`, `ConversationScheduler` und `ConversationMessenger` definieren, die keine Bukkit-Typen verwenden. Diese ersetzen die direkten Bukkit-Referenzen (`Plugin`, `Player`, `Bukkit.getScheduler()`). | M | -- | voyager-core-agent |
| M2-02 | `refactor: Conversation API von Bukkit-Imports befreien` | Alle 7 Dateien in `shared/conversation-api` migrieren: `Conversation`, `ConversationContext`, `ConversationFactory`, `ConversationTracker`, `InactivityConversationCanceller`, `PlayerNamePrompt`, `PluginNameConversationPrefix`. Bukkit-Typen durch die neuen Abstraktionen aus M2-01 ersetzen. | L | M2-01 | voyager-core-agent |
| M2-03 | `feat: Minestom-Adapter fuer Conversation API implementieren` | Implementierungen von `ConversationPlayer`, `ConversationScheduler` und `ConversationMessenger` fuer Minestom erstellen. Diese leben im `server`-Modul. | M | M2-02, M1-03 | voyager-core-agent |
| M2-04 | `test: Unit-Tests fuer plattform-agnostische Conversation API` | Tests fuer `Conversation`, `ConversationFactory` und `ConversationTracker` mit Mock-Implementierungen der neuen Interfaces. Sicherstellen, dass Prompt-Ketten, Timeout und Abbruch korrekt funktionieren. | M | M2-02 | voyager-test-agent |
| M2-05 | `refactor: Paper-Dependency aus shared/conversation-api build.gradle.kts entfernen` | Nach erfolgreicher Migration die `paper-api`-Dependency aus dem Conversation-API-Modul entfernen. Kompilierung verifizieren. | S | M2-02 | voyager-build-agent |

---

### Milestone 3: Core Game (Minestom)

Kern-Infrastruktur des Spiels auf Minestom portieren: Events, Scheduler, Welten, Phasen, Spieler.

| ID | Titel | Beschreibung | Groesse | Abhaengigkeit | Agent |
|---|---|---|---|---|---|
| M3-01 | `feat: Minestom Event-Handler Infrastruktur aufbauen` | Event-Listener-Registrierung fuer Minestom-Events (PlayerLoginEvent, PlayerDisconnectEvent, PlayerMoveEvent, PlayerStartFlyingWithElytraEvent etc.) im Server-Modul aufsetzen. Modularer Aufbau analog zu `DefaultListener`. | M | M1-04 | voyager-core-agent |
| M3-02 | `feat: Scheduler-Adapter fuer ECS EntityManager implementieren` | Minestom `SchedulerManager` nutzen, um den ECS-Game-Loop (`EntityManager.update(deltaTime)`) mit 20 TPS zu takten. Der bestehende `EntityManager` soll unveraendert bleiben. | M | M1-04 | voyager-core-agent |
| M3-03 | `feat: Anvil World-Loading mit InstanceContainer implementieren` | `AnvilLoader` von Minestom verwenden, um bestehende Anvil-Maps in eine `InstanceContainer` zu laden. Bestehende `WorldComponent`/`SimpleWorldComponent` auf Minestom `Instance`-Referenzen umstellen. | L | M1-04 | voyager-core-agent |
| M3-04 | `refactor: Phase-System an Minestom-Lifecycle anpassen` | `LobbyPhase`, `PreparationPhase`, `GamePhase`, `EndPhase` auf Minestom-Events und -APIs umschreiben. `LinearPhaseSeries` bleibt unveraendert (plattform-agnostisch in `shared/phase`). | L | M3-01, M3-02 | voyager-core-agent |
| M3-05 | `feat: Spieler-Management (Join, Leave, Teleport) implementieren` | Spieler-Join-Flow: Instance zuweisen, Gamemode setzen, an Spawn teleportieren. Leave-Flow: Cleanup, Session-Update. Teleport-Logik fuer Phasenwechsel (Lobby-Spawn, Start-Position). | M | M3-01, M3-03 | voyager-core-agent |
| M3-06 | `refactor: GameSession und Components auf Minestom-Typen umstellen` | `GameSession`, `GameStateComponent`, `SessionComponent` und weitere Components von Bukkit-Typen (`World`, `Location`, `Player`) auf Minestom-Aequivalente (`Instance`, `Pos`, `Player`) migrieren. | L | M3-03, M3-05 | voyager-core-agent |
| M3-07 | `test: Integrationstests fuer Phase-Lifecycle und Spieler-Management` | Tests, die einen vollstaendigen Phase-Durchlauf (Lobby -> Game -> End) mit Mock-Spielern simulieren. Verifiziert korrekte Event-Ausloesung und Zustandsuebergaenge. | L | M3-04, M3-05 | voyager-test-agent |

---

### Milestone 4: Gameplay

Kern-Gameplay: Elytra-Physik, Kollisionserkennung, Cup-System und Spieler-Feedback.

| ID | Titel | Beschreibung | Groesse | Abhaengigkeit | Agent |
|---|---|---|---|---|---|
| M4-01 | `feat: Elytra-Flugphysik implementieren` | Vanilla-nahe Elytra-Physik in Minestom implementieren. Minestom hat keine eingebaute Elytra-Physik -- Gravitation, Gleitwinkel, Geschwindigkeitsberechnung und Raketen-Boost muessen manuell ueber Velocity-Manipulation umgesetzt werden. Referenz: `docs/elytra-physics-reference.md`. | XL | M3-02, M3-05 | voyager-physics-agent |
| M4-02 | `feat: Ring-Kollisionserkennung auf Minestom portieren` | `CollisionSystem` und Spline-basierte Ring-Erkennung (`SplineSystem`, `SimpleSplineSystem`) auf Minestom `Pos`-Typen umstellen. Bestehende `commons-geometry-euclidean`-Logik bleibt erhalten. | L | M3-06 | voyager-core-agent |
| M4-03 | `feat: Cup-System (Map-Rotation, Scoring) migrieren` | `CupSystem`/`SimpleCupSystem` und `CupService`/`CupServiceImpl` auf Minestom portieren. Map-Wechsel ueber Instance-Management. Scoring-Logik (Punkte pro Ring, Gesamtwertung) beibehalten. | L | M3-03, M4-02 | voyager-core-agent |
| M4-04 | `feat: Scoreboard und BossBar UI implementieren` | Minestom `Sidebar` fuer Scoreboard (aktuelle Platzierung, verbleibende Ringe) und `BossBar` fuer Timer/Fortschritt einrichten. | M | M3-05 | voyager-ui-agent |
| M4-05 | `feat: Sound- und Partikel-Effekte hinzufuegen` | Ring-Durchflug-Sound, Countdown-Sounds, Ziel-Partikel ueber Minestom `SoundEvent` und `ParticleCreator`/`sendGroupedPacket` umsetzen. | M | M4-02 | voyager-ui-agent |
| M4-06 | `test: Elytra-Physik Unit-Tests` | Tests fuer Geschwindigkeitsberechnung, Gleitwinkel, Gravitation und Raketen-Boost. Grenzwerte und Edge-Cases (max. Geschwindigkeit, Boden-Kollision) abdecken. | L | M4-01 | voyager-test-agent |
| M4-07 | `test: Kollisions- und Cup-System Tests` | Tests fuer Ring-Durchflug-Erkennung (korrekt, knapp daneben, rueckwaerts), Cup-Rotation und Scoring-Berechnung. | M | M4-02, M4-03 | voyager-test-agent |

---

### Milestone 5: Polish und Deploy

Integration, Persistenz, Performance und Deployment.

| ID | Titel | Beschreibung | Groesse | Abhaengigkeit | Agent |
|---|---|---|---|---|---|
| M5-01 | `feat: CloudNet v4 Integration implementieren` | CloudNet v4 Bridge-Modul integrieren: Service-Registrierung, Spieler-Routing, Server-Status-Updates. Automatisches Herunterfahren nach Spielende. | L | M3-04 | voyager-infra-agent |
| M5-02 | `feat: Datenbank-Schema um Scores und Statistiken erweitern` | Neue Hibernate-Entities fuer Match-Ergebnisse, Rundenzeiten und Spieler-Statistiken. Migrations-Skripte fuer das erweiterte Schema. | M | M4-03 | voyager-core-agent |
| M5-03 | `feat: Ergebnisse und Statistiken persistieren` | Nach Spielende Match-Ergebnisse, Rundenzeiten und Spieler-Statistiken in die Datenbank schreiben. Bestehenden `shared/database`-Layer nutzen. | M | M5-02 | voyager-core-agent |
| M5-04 | `chore: Docker-Image fuer Standalone-Server erstellen` | Multi-Stage Dockerfile: Build mit Java 25, Runtime als minimales JRE-Image. `docker-compose.yml` erweitern um Server-Service neben MariaDB. | M | M1-05 | voyager-infra-agent |
| M5-05 | `perf: Performance-Profiling und Optimierung` | Physik-Loop, Kollisionserkennung und Instance-Management unter Last testen (20+ Spieler). Hotspots identifizieren und optimieren. | L | M4-01, M4-02 | voyager-core-agent |
| M5-06 | `docs: Migrations-Dokumentation und Betriebshandbuch` | Dokumentation fuer Deployment, Konfiguration, Map-Format und CloudNet-Setup. Aenderungen gegenueber der Paper-Version dokumentieren. | M | M5-01, M5-04 | voyager-docs-agent |
| M5-07 | `feat: Boost-Ringe und Power-Ups implementieren` | Spezielle Ring-Typen (Speed-Boost, Slow, Hoehenaenderung) als erweiterbare Ring-Komponenten im ECS. | L | M4-02 | voyager-core-agent |
| M5-08 | `feat: Ghost-Replay System` | Bestzeiten als Geisterfahrer aufzeichnen und wiedergeben. Positionen seriell in Datei oder Datenbank speichern, als unsichtbare Entities replayed. | XL | M4-01, M5-03 | voyager-core-agent |
| M5-09 | `feat: Leaderboard-System` | Globale und pro-Map Bestenlisten aus der Datenbank laden und als Hologramme oder Chat-Kommandos anzeigen. | M | M5-03 | voyager-core-agent |

---

## Abhaengigkeitsgraph (vereinfacht)

```
M1-01 (Java 25) ──┐
M1-02 (Minestom) ──┼── M1-03 (Server-Modul) ── M1-04 (Bootstrap) ── M1-06 (Test)
                   │                                    │
                   │                                    ├── M3-01 (Events)
                   │                                    ├── M3-02 (Scheduler)
                   │                                    └── M3-03 (Anvil Loading)
                   │
M1-05 (CI/CD) ─────┘

M2-01 (Abstraktionen) ── M2-02 (Migration) ── M2-03 (Adapter)
                                │                      │
                                ├── M2-04 (Tests)      │
                                └── M2-05 (Cleanup)    │

M3-01 + M3-02 ── M3-04 (Phasen)
M3-01 + M3-03 ── M3-05 (Spieler) ── M3-06 (Components)
M3-04 + M3-05 ── M3-07 (Tests)

M3-02 + M3-05 ── M4-01 (Physik) ── M4-06 (Tests)
M3-06 ── M4-02 (Kollision) ── M4-03 (Cup) ── M4-07 (Tests)
M4-02 ── M4-05 (Effekte)
M3-05 ── M4-04 (UI)

M4-03 ── M5-02 (DB-Schema) ── M5-03 (Persistenz) ── M5-09 (Leaderboard)
M5-03 + M4-01 ── M5-08 (Ghost-Replay)
M3-04 ── M5-01 (CloudNet)
M1-05 ── M5-04 (Docker)
M4-01 + M4-02 ── M5-05 (Performance)
```

## Schaetzung Gesamtaufwand

| Milestone | Tickets | Geschaetzter Aufwand |
|---|---|---|
| M1: Foundation | 6 | ~2 Wochen |
| M2: Shared Cleanup | 5 | ~2 Wochen |
| M3: Core Game | 7 | ~4 Wochen |
| M4: Gameplay | 7 | ~5 Wochen |
| M5: Polish & Deploy | 9 | ~5 Wochen |
| **Gesamt** | **34** | **~18 Wochen** |

> M1 und M2 koennen teilweise parallel bearbeitet werden (unterschiedliche Module).
> M5-07, M5-08, M5-09 sind Nice-to-Have und koennen nach dem MVP priorisiert werden.

## Risiken

| Risiko | Auswirkung | Mitigation |
|---|---|---|
| Elytra-Physik weicht von Vanilla ab | Spielgefuehl leidet | Fruehes Prototyping in M4-01, Vergleichstests gegen Paper |
| Minestom API-Aenderungen | Breaking Changes | Minestom-Version pinnen, Adapter-Pattern nutzen |
| Java 25 Kompatibilitaet mit Dependencies | Build-Fehler | Frueh testen in M1-01, ggf. auf Java 24 fallback |
| Anvil-Loader Limitierungen | Maps laden nicht korrekt | Bekannte Maps frueh in M3-03 testen |
| Performance bei vielen Spielern | Lag bei Physik/Kollision | Profiling in M5-05, ggf. raeumliche Partitionierung |
