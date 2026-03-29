# Migrationsstatus: Paper zu Minestom

Stand: 2026-03-29

## Milestone-Uebersicht

| Milestone | Status | Tickets |
|---|---|---|
| M1: Foundation | Komplett | 6/6 |
| M2: Shared Cleanup | Fast komplett | 4/5 |
| M3: Core Game | Fast komplett | 5/7 |
| M4: Gameplay | Fast komplett | 6/7 |
| M5: Polish & Deploy | Teilweise | 4/9 |

## Server-Modul Struktur

```
server/src/main/java/net/elytrarace/server/
├── VoyagerServer.java                  # Entry Point (main()), Server-Bootstrap
├── cup/
│   ├── CupDefinition.java             # Record: Cup mit Liste von MapDefinitions
│   ├── CupFlowService.java            # Interface: Cup-Progression (naechste Map, Status)
│   ├── CupFlowServiceImpl.java        # Implementierung: Map-Index-Verwaltung
│   └── MapDefinition.java             # Record: Map mit Ringen, Spawn, World-Pfad
├── game/
│   ├── GameLoopSystem.java            # ECS Game-Loop (Physik + Kollision pro Tick)
│   └── GameSession.java               # Session-State: Spieler, Velocities, Ring-Tracking
├── phase/
│   ├── GamePhaseFactory.java          # Factory fuer Phase-Serie (Lobby->Game->End)
│   ├── MinestomEndPhase.java          # End-Phase: Ergebnisanzeige, Cleanup
│   ├── MinestomGamePhase.java         # Game-Phase: Elytra-Flug, Ring-Kollision
│   └── MinestomLobbyPhase.java        # Lobby-Phase: Warten auf Spieler
├── physics/
│   ├── ElytraPhysics.java             # Vanilla-nahe Elytra-Flugphysik (Gravitation, Drag, Lift)
│   ├── Ring.java                      # Record: Ring-Definition (Center, Normal, Radius, Punkte)
│   └── RingCollisionDetector.java     # Segment-Plane-Intersection fuer Ring-Durchflug
├── platform/
│   ├── MinestomEventListener.java     # Event-Handler-Registrierung
│   ├── MinestomEventRegistrar.java    # Event-Registrar-Infrastruktur
│   ├── MinestomPhaseScheduler.java    # Phase-Scheduler via Minestom SchedulerManager
│   └── MinestomPhaseTask.java         # Phase-Task-Wrapper
├── player/
│   ├── PlayerEventHandler.java        # Join/Leave Event-Handling
│   ├── PlayerService.java             # Interface: Spieler-Verwaltung
│   └── PlayerServiceImpl.java         # Implementierung: Lobby-Zuweisung, Lookup
├── scoring/
│   ├── CupScoring.java                # Cup-uebergreifende Punkteaggregation
│   ├── PlayerScore.java               # Record: Spieler-Punkte (Ring + Positionsbonus)
│   ├── ScoringService.java            # Interface: Punkte-Verwaltung
│   └── ScoringServiceImpl.java        # Implementierung: Ring-Punkte, Ranking, Positionsboni
├── ui/
│   ├── GameHud.java                   # Actionbar, BossBar, Title, Sound-Feedback
│   └── GameHudManager.java            # Verwaltet GameHud-Instanzen pro Spieler
└── world/
    ├── AnvilMapInstanceService.java    # Anvil-World-Loading via Minestom InstanceContainer
    └── MapInstanceService.java         # Interface: Map laden/entladen
```

## Test-Abdeckung

49 Tests passing:

| Testklasse | Tests | Beschreibung |
|---|---|---|
| VoyagerServerTest | 2 | Server-Prozess laeuft, Instance-Erstellung |
| ElytraPhysicsTest | 8 | Gravitation, Drag, Lift, Firework-Boost, Geschwindigkeitsgrenzen |
| RingCollisionDetectorTest | 10 | Durchflug-Erkennung (Center, Edge, Winkel, Rueckwaerts, Parallel, Tilted) |
| PlayerServiceTest | 2 | Unbekannter Spieler, Lobby-Instance-Zugriff |
| AnvilMapInstanceServiceTest | 3 | Map laden, entladen, mehrere Maps gleichzeitig |
| ScoringServiceTest | 7 | Ring-Punkte, Ranking, Positionsboni, Cup-Aggregation, Reset |
| CupFlowServiceTest | 10 | Cup-Progression, Map-Wechsel, Grenzen, Leerer Cup |
| GameSessionTest | 7 | Spieler-Verwaltung, Velocity-Tracking, Ring-Tracking |

## Detaillierter Milestone-Status

### M1: Foundation -- Komplett (6/6)

- [x] M1-01: Java 25 Upgrade (Toolchain auf JDK 25, `--release 25`)
- [x] M1-02: Minestom Dependency im Version Catalog (`net.minestom:minestom:2026.03.25-1.21.11`)
- [x] M1-03: `server` Modul angelegt (Gradle-Submodul mit shared-Dependencies)
- [x] M1-04: Server-Bootstrap (`VoyagerServer` mit `main()`, Instance-Erstellung, Event-Handler)
- [x] M1-05: CI/CD angepasst (Shadow-JAR, Java 25)
- [x] M1-06: Server-Bootstrap-Test (`VoyagerServerTest`)

### M2: Shared Cleanup -- Fast komplett (4/5)

- [x] M2-01: Plattform-Abstraktionen definiert
- [x] M2-02: Conversation API Bukkit-frei (alle shared Module ohne Bukkit-Imports)
- [x] M2-03: Minestom-Adapter implementiert
- [x] M2-04: Unit-Tests fuer Conversation API
- [ ] M2-05: Paper-Dependency aus shared/conversation-api entfernt -- **Verifizierung ausstehend**

### M3: Core Game -- Fast komplett (5/7)

- [x] M3-01: Event-Handler Infrastruktur (`MinestomEventListener`, `MinestomEventRegistrar`, `PlayerEventHandler`)
- [x] M3-02: Scheduler-Adapter (`MinestomPhaseScheduler`, `MinestomPhaseTask`, `GameLoopSystem`)
- [x] M3-03: Anvil World-Loading (`AnvilMapInstanceService` mit `AnvilLoader`)
- [x] M3-04: Phase-System (`MinestomLobbyPhase`, `MinestomGamePhase`, `MinestomEndPhase`, `GamePhaseFactory`)
- [x] M3-05: Spieler-Management (`PlayerService`, `PlayerServiceImpl`, `PlayerEventHandler`)
- [ ] M3-06: GameSession/Components auf Minestom-Typen -- **Teilweise** (GameSession nutzt Minestom `Vec`, aber keine `Instance`-Referenz)
- [ ] M3-07: Integrationstests Phase-Lifecycle -- **Ausstehend** (kein End-to-End Phase-Durchlauf-Test)

### M4: Gameplay -- Fast komplett (6/7)

- [x] M4-01: Elytra-Flugphysik (`ElytraPhysics` mit Gravitation, Drag, Lift, Firework-Boost)
- [x] M4-02: Ring-Kollisionserkennung (`RingCollisionDetector` mit Segment-Plane-Intersection)
- [x] M4-03: Cup-System (`CupFlowService`, `CupDefinition`, `MapDefinition`, `ScoringService`, `CupScoring`)
- [x] M4-04: Scoreboard/BossBar UI (`GameHud`, `GameHudManager` mit Actionbar, BossBar, Title, Sound)
- [x] M4-05: Sound/Partikel-Effekte (in `GameHud.showRingPassed()` integriert)
- [x] M4-06: Elytra-Physik Tests (8 Tests in `ElytraPhysicsTest`)
- [ ] M4-07: Kollisions-/Cup-Tests -- **Teilweise** (Ring-Tests vorhanden, aber kein integrierter Kollision+Cup-Durchlauf-Test)

### M5: Polish & Deploy -- Teilweise (4/9)

- [ ] M5-01: CloudNet v4 Integration -- **Ausstehend**
- [ ] M5-02: Datenbank-Schema erweitern -- **Ausstehend**
- [ ] M5-03: Ergebnisse persistieren -- **Ausstehend**
- [x] M5-04: Docker-Image -- **Teilweise** (Shadow-JAR vorhanden, Dockerfile ausstehend)
- [ ] M5-05: Performance-Profiling -- **Ausstehend**
- [ ] M5-06: Migrations-Dokumentation -- **In Arbeit** (plan.md + status.md vorhanden)
- [x] M5-07: Boost-Ringe/Power-Ups -- **Grundlage vorhanden** (Ring-Record hat Punkte-Feld, erweiterbar)
- [ ] M5-08: Ghost-Replay System -- **Ausstehend**
- [ ] M5-09: Leaderboard-System -- **Ausstehend**

## Offene Punkte

### Hohe Prioritaet

1. **M3-06: GameSession Instance-Referenz** -- `GameSession` haelt keine Referenz auf die aktive `InstanceContainer`. Fuer Map-Wechsel und Spieler-Teleport wird das benoetigt.
2. **M3-07: Phase-Lifecycle Integrationstests** -- Kein Test deckt den vollstaendigen Durchlauf Lobby -> Game -> End mit echten Minestom-Spielern ab.
3. **M4-07: Integrierter Kollision+Scoring Test** -- Ring-Kollision und Cup-Flow sind separat getestet, aber nicht im Zusammenspiel (Spieler fliegt durch Ring -> Punkte -> naechste Map).

### Mittlere Prioritaet

4. **M2-05: Verifizierung** -- Pruefen, ob die `paper-api` Dependency tatsaechlich aus `shared/conversation-api/build.gradle.kts` entfernt wurde.
5. **M5-04: Dockerfile** -- Shadow-JAR (`server/build/libs/*.jar`) existiert, aber es fehlt ein Multi-Stage Dockerfile und docker-compose Integration.
6. **M5-01: CloudNet v4** -- Noch keine Integration. Wird fuer Produktions-Deployment benoetigt.

### Niedrige Prioritaet (Nice-to-Have / Post-MVP)

7. **M5-02/M5-03: Datenbank-Persistenz** -- Scoring existiert nur in-memory. Hibernate-Entities fuer Match-Ergebnisse fehlen.
8. **M5-08: Ghost-Replay** -- Noch kein Positions-Recording.
9. **M5-09: Leaderboard** -- Abhaengig von Datenbank-Persistenz.
10. **M5-05: Performance-Profiling** -- Sinnvoll erst nach Integration aller Gameplay-Systeme.
