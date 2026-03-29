# DRAFT -- needs user approval before posting

---

## 1. OpenCollective (DE)

### Variante A: DevLog (technisch)

**Voyager DevLog: Paper-zu-Minestom-Migration und ein Entwicklungsexperiment**

Hallo zusammen,

wir haben einen grossen Meilenstein erreicht: Voyager (ehemals ElytraRace) wurde vollstaendig von Paper auf Minestom migriert. Das Elytra-Racing-Minigame laeuft jetzt als eigenstaendiger Server -- kein Paper, kein Mojang-Servercode, keine schweren Abhaengigkeiten.

**Architektur und technische Details**

- **Standalone Minestom Server** mit Java 25
- **Custom Elytra-Physik**: Vanilla-akkurates Flugverhalten, komplett ohne Mojang-Code. Wir haben das Flugmodell nachgebaut und von Grund auf neu implementiert -- Auftrieb, Gleitwinkel, Geschwindigkeitsabfall, alles auf Basis der bekannten Vanilla-Formeln.
- **ECS-Architektur** (Entity-Component-System) mit 8 Components und 5 Systems. Jedes Spielkonzept (Ringe, Cups, Spielerstatus, Physik) ist ein eigenes Component/System. Neue Mechaniken = neues Component + neues System, sonst aendert sich nichts.
- **5 Ring-Typen**: Standard, Boost (1.5x Speed), Slow (0.5x Speed), Checkpoint (Pflicht fuer Rundenabschluss), Bonus (Extrapunkte)
- **3D-Ring-Kollisionserkennung** per geometrischer Berechnung statt Bounding Boxes. Ringe koennen geneigt, skaliert und in beliebigem Winkel platziert werden.
- **Cup-System** (Mario-Kart-Stil): Mehrere Maps pro Cup, kumulative Punktevergabe, Positions-Bonus
- **Spieler-HUD** mit Echtzeit-Geschwindigkeit, Punktestand, Cup-Fortschritt und Sound-Feedback
- **Phase-System**: Lobby, Game, End mit sauberem Lifecycle
- **Xerus/Aves Integration** (OneLiteFeather MiniGame Framework)
- **Docker + CloudNet v4** Deployment ready
- **Datenbank-Schema** (Hibernate ORM + MariaDB) fuer Spieler-Statistiken und Match-Ergebnisse
- **GitHub Actions CI/CD Pipeline**
- **113 automatisierte Tests**

**Zum Entwicklungsprozess: LLM-Experiment**

Wir probieren bei diesem Projekt aus, ob LLM-gestuetzte Entwicklung (in unserem Fall Claude Code) fuer ein Open-Source-Spielprojekt funktioniert. Konkret haben wir 14 spezialisierte Agenten parallel an verschiedenen Aufgaben arbeiten lassen:

- Physik-Formeln recherchieren und implementieren (Elytra-Flugmodell)
- Automatisierte Tests schreiben (113 Tests)
- Code reviewen und Architekturentscheidungen dokumentieren
- ECS-Components und -Systems implementieren
- Dokumentation erstellen (Feasibility Study, Pro/Contra-Analyse, Migrationsplan)

Das ist ein Experiment, keine Revolution. Manche Sachen funktionieren gut (Tests generieren, Boilerplate-Code, Recherche), manche weniger (komplexe Architekturentscheidungen brauchen weiterhin menschliches Urteil). Wir dokumentieren unsere Erfahrungen offen.

**Zahlen**

- 20+ Commits
- ~10.000 Zeilen neuer Code
- 113 automatisierte Tests
- Vollstaendige Dokumentation

**Warum Minestom?**

Paper ist grossartig fuer traditionelle Minecraft-Server, aber fuer ein spezialisiertes Minigame bringt es Overhead mit, den wir nicht brauchen. Minestom gibt uns volle Kontrolle ueber Physik, Netzwerk und Gameplay -- ohne Plugin-API-Einschraenkungen. Schneller, leichter, einfacher zu deployen.

**Wie geht es weiter?**

- Weitere Ring-Typen und Map-Features
- Ranking-System und Leaderboards
- Performance-Optimierung und Lasttests
- Community-Maps und Editor-Tools

Voyager ist und bleibt Open Source.

GitHub: https://github.com/OneLiteFeatherNET/Voyager
Pull Request: https://github.com/OneLiteFeatherNET/Voyager/pull/71

---

### Variante B: Casual (Community-Update)

**Voyager Update: Elytra-Racing laeuft jetzt standalone!**

Hey Leute,

kurzes Update zu Voyager -- unserem Elytra-Racing Minigame:

Wir haben das komplette Spiel umgebaut. Statt als Bukkit/Paper-Plugin laeuft Voyager jetzt als eigener Server auf Minestom. Was heisst das fuer euch?

**Was koennt ihr bald spielen?**

- Elytra-Rennen durch Ringe fliegen -- mit 5 verschiedenen Ring-Typen: normale Ringe, Boost-Ringe (schneller!), Slow-Ringe (bremsen euch aus), Checkpoints (muessen durchflogen werden) und Bonus-Ringe fuer Extrapunkte
- **Cup-System wie bei Mario Kart**: Mehrere Strecken hintereinander, Punkte sammeln, am Ende gewinnt der Beste ueber alle Runden
- Echtzeit-HUD mit Geschwindigkeit und Punktestand
- Sound-Feedback wenn ihr durch Ringe fliegt

Das Flugverhalten fuehlt sich an wie Vanilla-Minecraft -- wir haben die Elytra-Physik komplett selbst nachgebaut, damit alles sich vertraut anfuehlt.

**Noch was Interessantes zum Entwicklungsprozess**

Wir testen bei Voyager, ob man LLM-Tools (Claude Code) sinnvoll in der Open-Source-Entwicklung einsetzen kann. Heisst konkret: 14 spezialisierte Agenten, die parallel an verschiedenen Aufgaben arbeiten -- einer recherchiert Physik-Formeln, einer schreibt Tests, einer erstellt Dokumentation, und so weiter. Ist ein Experiment. Manches klappt gut, manches weniger. Wir berichten offen darueber.

**Das Projekt ist Open Source** -- wenn ihr mithelfen wollt (testen, Code beitragen, Feedback geben), seid ihr willkommen!

GitHub: https://github.com/OneLiteFeatherNET/Voyager
PR mit allen Aenderungen: https://github.com/OneLiteFeatherNET/Voyager/pull/71

---

## 2. OpenCollective (EN)

### Variante A: DevLog (technical)

**Voyager DevLog: Paper-to-Minestom migration and a development experiment**

Hey everyone,

We have reached a major milestone: Voyager (formerly ElytraRace) has been fully migrated from Paper to Minestom. The elytra racing minigame now runs as a standalone server -- no Paper, no Mojang server code, no heavy dependencies.

**Architecture and technical details**

- **Standalone Minestom server** running Java 25
- **Custom elytra physics**: Vanilla-accurate flight behavior, entirely without Mojang code. We reverse-engineered and reimplemented the flight model from scratch -- lift, glide angle, speed decay, all based on known vanilla formulas.
- **ECS architecture** (Entity-Component-System) with 8 components and 5 systems. Each game concept (rings, cups, player state, physics) is a separate component/system. Adding new mechanics means adding a component and a system -- nothing else changes.
- **5 ring types**: Standard, Boost (1.5x speed), Slow (0.5x), Checkpoint (required for lap completion), Bonus (extra points)
- **3D ring collision detection** using geometric math rather than bounding boxes. Rings can be tilted, scaled, and placed at any angle.
- **Cup system** (Mario Kart style): Multiple maps per cup, cumulative scoring, position-based bonuses
- **Player HUD** with real-time speed, score, cup progress, and sound feedback
- **Phase system**: Lobby, Game, End with clean lifecycle management
- **Xerus/Aves integration** (OneLiteFeather MiniGame Framework)
- **Docker + CloudNet v4** deployment ready
- **Database schema** (Hibernate ORM + MariaDB) for player statistics and match results
- **GitHub Actions CI/CD pipeline**
- **113 automated tests**

**On the development process: LLM experiment**

We are trying out whether LLM-assisted development (Claude Code in our case) works for an open-source game project. Specifically, we had 14 specialized agents working in parallel on different tasks:

- Researching and implementing physics formulas (elytra flight model)
- Writing automated tests (113 tests)
- Reviewing code and documenting architecture decisions
- Implementing ECS components and systems
- Creating documentation (feasibility study, pro/con analysis, migration plan)

This is an experiment, not a revolution. Some things work well (generating tests, boilerplate code, research), others less so (complex architecture decisions still need human judgment). We are documenting our experience openly.

**Numbers**

- 20+ commits
- ~10,000 lines of new code
- 113 automated tests
- Full documentation

**Why Minestom?**

Paper is great for traditional Minecraft servers, but for a specialized minigame it carries overhead we do not need. Minestom gives us full control over physics, networking, and gameplay -- without plugin API constraints. Faster, lighter, easier to deploy.

**What is next?**

- Additional ring types and map features
- Ranking system and leaderboards
- Performance optimization and load testing
- Community maps and editor tools

Voyager is and will remain open source.

GitHub: https://github.com/OneLiteFeatherNET/Voyager
Pull Request: https://github.com/OneLiteFeatherNET/Voyager/pull/71

---

### Variante B: Casual (community update)

**Voyager Update: Elytra racing now runs standalone!**

Hey everyone,

Quick update on Voyager -- our elytra racing minigame:

We rebuilt the entire game from the ground up. Instead of running as a Bukkit/Paper plugin, Voyager now runs as its own server on Minestom. What does that mean for you?

**What can you play soon?**

- Elytra races through rings -- with 5 different ring types: standard rings, boost rings (faster!), slow rings (slow you down), checkpoints (must fly through), and bonus rings for extra points
- **Cup system like Mario Kart**: Multiple tracks back to back, collect points, best overall score wins
- Real-time HUD with speed and score
- Sound feedback when you fly through rings

The flight feel matches vanilla Minecraft -- we rebuilt the elytra physics from scratch so everything feels familiar.

**Something interesting about how we built this**

We are testing whether LLM tools (Claude Code) can be used productively in open-source development. In practice that means: 14 specialized agents working in parallel on different tasks -- one researching physics formulas, one writing tests, one creating documentation, and so on. It is an experiment. Some things work well, others not so much. We are reporting on it openly.

**The project is open source** -- if you want to help out (testing, contributing code, giving feedback), you are welcome!

GitHub: https://github.com/OneLiteFeatherNET/Voyager
PR with all changes: https://github.com/OneLiteFeatherNET/Voyager/pull/71

---

## 3. Discord (DE)

### Variante A: DevLog

**Voyager Dev-Update: Paper -> Minestom Migration abgeschlossen**

Die Migration ist durch. Voyager laeuft jetzt standalone auf Minestom mit Java 25.

**Technische Highlights:**
- Custom Elytra-Physik (Vanilla-akkurat, kein Mojang-Code)
- ECS-Architektur: 8 Components, 5 Systems
- 5 Ring-Typen (Standard, Boost, Slow, Checkpoint, Bonus)
- 3D-Kollisionserkennung per Geometrie statt Bounding Boxes
- Cup-System (Mario-Kart-Stil) mit Punktevergabe
- Docker + CloudNet v4 ready
- 113 automatisierte Tests, ~10k Zeilen Code

**Zum Prozess:** Wir testen ob LLM-gestuetzte Entwicklung (Claude Code) fuer ein Open-Source-Spielprojekt funktioniert. 14 spezialisierte Agenten haben parallel gearbeitet -- Physik-Code, Tests, Code-Reviews, Doku. Ist ein Experiment, kein Wundermittel. Funktioniert gut fuer manche Aufgaben, fuer andere weniger.

PR: https://github.com/OneLiteFeatherNET/Voyager/pull/71
GitHub: https://github.com/OneLiteFeatherNET/Voyager

---

### Variante B: Casual

**Voyager laeuft! Elytra-Racing jetzt als Standalone-Server**

Leute, grosses Update:

Voyager ist jetzt ein eigenstaendiger Minestom-Server -- kein Paper-Plugin mehr! Elytra-Rennen durch 5 verschiedene Ring-Typen (Boost fuer Speed, Slow zum Ausbremsen, Checkpoints, Bonus-Ringe...) mit einem Cup-System wie bei Mario Kart. Mehrere Strecken, Punkte sammeln, Gesamtwertung.

Die Elytra-Physik fuehlt sich an wie Vanilla -- alles selbst gebaut.

Fun Fact: Wir testen gerade, ob man mit LLM-Tools (Claude Code) sinnvoll an einem Open-Source-Spiel arbeiten kann. 14 Agenten parallel -- einer rechnet Physik-Formeln aus, einer schreibt Tests, einer macht Doku. Laeuft als Experiment, wir berichten offen was klappt und was nicht.

Alles Open Source! Kommt vorbei, testet, gebt Feedback.
PR: https://github.com/OneLiteFeatherNET/Voyager/pull/71

---

## 4. Twitter/X (EN)

### Variante A: DevLog

Migrated Voyager (elytra racing) from Paper to standalone Minestom. Custom physics, ECS architecture (8 components, 5 systems), 113 tests, ~10k lines. Also experimenting with Claude Code -- 14 agents on parallel tasks (physics, tests, docs). Open source.

github.com/OneLiteFeatherNET/Voyager/pull/71

---

### Variante B: Casual

Elytra racing minigame Voyager now runs standalone on Minestom -- 5 ring types, Mario Kart-style cups, custom flight physics. Built partly as an LLM dev experiment with Claude Code (14 agents, parallel tasks). Fully open source!

github.com/OneLiteFeatherNET/Voyager

---

## 5. Reddit r/admincraft (EN)

### Variante A: DevLog

**Title:** [Dev Log] Migrating an elytra racing minigame from Paper to Minestom -- technical details and an LLM development experiment

**Body:**

Hey r/admincraft,

We just finished migrating **Voyager**, our elytra racing minigame, from a Paper plugin to a standalone Minestom server. Sharing technical details and some notes on our development process for anyone interested.

**Why we left Paper**

Paper is excellent for general-purpose servers, but for a dedicated minigame we were fighting the API more than using it. We needed custom elytra physics, tight control over the game loop, and lightweight deployment. Paper's plugin model added complexity we did not need.

**The new stack**

- **Minestom** as the server -- no Mojang server code, no NMS, no plugin API constraints
- **Java 25** for the latest language features
- **ECS architecture** (Entity-Component-System) with 8 components and 5 systems. Each game concept (rings, cups, player state, physics) is a separate component/system. Adding new mechanics means adding a component and a system -- nothing else changes.
- **Custom elytra physics** that replicate vanilla flight behavior. We reverse-engineered the flight model and reimplemented it from scratch -- lift, glide angle, speed decay. No Mojang code involved.
- **3D ring collision detection** using geometric math rather than bounding boxes. Rings can be tilted, scaled, and placed at any angle.

**Game features**

- 5 ring types: Standard, Boost (1.5x speed multiplier), Slow (0.5x), Checkpoint (required for lap completion), Bonus (extra points)
- Cup system similar to Mario Kart: multiple maps per cup, cumulative points, position-based bonuses
- Player HUD showing real-time speed, score, cup progress, and audio feedback on ring passes
- Phase system (Lobby -> Game -> End) with clean lifecycle management

**Infrastructure**

- Docker deployment ready
- CloudNet v4 integration for scaling
- Hibernate ORM + MariaDB for player stats and match history
- GitHub Actions CI/CD pipeline
- 113 automated tests

**By the numbers**

- 20+ commits in the migration PR
- ~10,000 lines of new code
- 113 automated tests
- Full documentation: feasibility study, pro/con analysis, migration plan

**On the development process: LLM experiment**

We want to be transparent about this: we are experimenting with using LLM-assisted development (Claude Code) for this project. Specifically, we had 14 specialized agents working in parallel on different tasks:

- One agent researching elytra physics formulas and implementing the flight model
- Another writing automated tests
- Others reviewing code, implementing ECS components, creating documentation

This is an experiment, not a sales pitch. Here is what we observed:

- Works well for: generating test scaffolding, boilerplate code, researching known algorithms, writing documentation
- Works less well for: complex architecture decisions, novel design problems, subtle bug diagnosis
- Human oversight was necessary throughout -- the agents do not replace judgment calls on design trade-offs

We are documenting our experience openly and plan to share more detailed findings. Take it for what it is: one data point from one project.

**Integration**

Voyager plugs into **Xerus/Aves**, our OneLiteFeather MiniGame Framework, which handles lobby management, matchmaking, and server orchestration across multiple game types.

**What we learned**

1. Minestom gives you freedom but also responsibility. There is no `PlayerMoveEvent` that just works -- you build your own event pipeline.
2. ECS was the right call. Decoupling game logic into components and systems made the codebase dramatically easier to reason about.
3. Custom physics are hard but worth it. We can now tune flight behavior without being locked to Mojang's implementation.
4. The migration was larger than expected but the result is a cleaner, more maintainable codebase.

The project is fully open source. PRs, issues, and feedback are welcome.

- **GitHub**: https://github.com/OneLiteFeatherNET/Voyager
- **Migration PR**: https://github.com/OneLiteFeatherNET/Voyager/pull/71
- **Organization**: https://github.com/OneLiteFeatherNET

Happy to answer questions about the migration, technical decisions, or the LLM experiment.

---

### Variante B: Casual (Show & Tell)

**Title:** [Show & Tell] Voyager -- open-source elytra racing with Mario Kart-style cups, now running on Minestom

**Body:**

Hey r/admincraft,

Wanted to share **Voyager**, an elytra racing minigame we have been building. We just moved it from a Paper plugin to a standalone Minestom server and it is starting to come together.

**What it does**

You fly through rings using elytra. There are 5 ring types -- standard, boost (speeds you up), slow (slows you down), checkpoints, and bonus rings for extra points. Rings are placed in 3D space and can be tilted at any angle.

The cup system works like Mario Kart: multiple tracks per cup, points accumulate across races, best overall score wins.

**What is under the hood**

- Standalone Minestom server (Java 25, no Paper needed)
- Custom elytra physics -- feels like vanilla, but we built it ourselves without Mojang code
- ECS architecture so adding new game mechanics is straightforward
- Docker and CloudNet v4 ready for deployment
- 113 automated tests

**One more thing**

We are running this partly as an experiment with LLM-assisted development using Claude Code. 14 agents working in parallel on different tasks -- one figures out physics formulas, one writes tests, one does documentation. Some of it works really well, some of it needs a lot of human steering. It is an experiment, we are documenting what works and what does not.

It is open source, so if any of this sounds interesting:

- **GitHub**: https://github.com/OneLiteFeatherNET/Voyager
- **Migration PR**: https://github.com/OneLiteFeatherNET/Voyager/pull/71

Feedback and contributions welcome.
