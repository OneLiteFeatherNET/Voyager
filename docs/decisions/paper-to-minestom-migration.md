# Entscheidungsdokument: Paper-zu-Minestom Migration

**Datum:** 2026-03-29
**Status:** In Bewertung
**Betrifft:** Game-Plugin (`plugins/game`) — Migration von Paper API zu Minestom

---

## 1. Ausgangslage (Vorher)

Das Voyager-Projekt (intern "ElytraRace") ist aktuell als Paper-Plugin aufgebaut:

- **Paper API 1.21.5** als Server-Plattform
- **Java 21** mit `--release 21`
- **Bukkit-Abhaengigkeiten in `shared/conversation-api`** — 7 Dateien mit direkten Bukkit-Imports
- **40+ Dateien in `plugins/game`** mit Bukkit-Imports (Events, Scheduler, Player-API, etc.)
- **Kein eigener Server** — das Game-Plugin laeuft als Plugin innerhalb eines Paper-Servers
- **Vanilla Elytra-Physik** wird vollstaendig vom Server bereitgestellt (Glide-Mechanik, Boost, Collision)
- **MockBukkit** als Test-Framework fuer Unit-Tests
- **plugin.yml** wird automatisch via `plugin-yml` Gradle-Plugin generiert
- **Cloud (Incendo)** als Command-Framework mit Paper-Integration
- **Adventure API** ueber Paper bereitgestellt

---

## 2. Vorgeschlagene Aenderung

Das Game-Plugin soll komplett auf Minestom umgeschrieben werden. Das Setup-Plugin bleibt unveraendert auf Paper.

| Aspekt | Beschreibung |
|--------|-------------|
| **Game-Plugin** | Kompletter Neubau auf Minestom |
| **Setup-Plugin** | Bleibt auf Paper (benoetigt FastAsyncWorldEdit) |
| **Java-Version** | Java 25 (Minestom-Anforderung) |
| **Server-Modell** | Standalone-Server mit eigener `main()` |
| **World-Format** | Anvil World Format (direktes Laden ohne Vanilla-Server) |
| **Conversation API** | Komplett neu geschrieben, plattform-agnostisch |
| **Elytra-Physik** | Eigenimplementierung erforderlich |

---

## 3. Bereits getroffene Entscheidungen

Die folgenden Punkte wurden bereits beschlossen und stehen nicht mehr zur Diskussion:

- **Java 25** als Ziel-Version fuer das Game-Plugin
- **Standalone Server** — keine Minestom-Extensions, sondern eigene `main()`
- **Anvil Format** fuer das Laden der Welten
- **Neue Conversation API** — plattform-agnostisch, ohne Bukkit-Abhaengigkeiten

---

## 4. Pro-Argumente fuer Minestom

### 4.1 Rechtliche Sicherheit
Kein Vanilla-Code enthalten bedeutet keine EULA- oder Copyright-Probleme. Minestom ist eine saubere Neuimplementierung des Minecraft-Protokolls ohne Mojang-Code.

### 4.2 Leichtgewichtig
Deutlich weniger RAM-Verbrauch und schnellerer Start als ein vollstaendiger Paper-Server. Fuer ein Minigame, das keine Vanilla-Mechaniken benoetigt, ist der volle Paper-Stack Overhead.

### 4.3 Volle Kontrolle ueber Physik und Gameplay
Elytra-Physik, Collision-Detection und alle Gameplay-Mechaniken koennen exakt auf die Anforderungen des Rennens zugeschnitten werden — ohne Workarounds gegen Vanilla-Verhalten.

### 4.4 Native Adventure API
Minestom verwendet Adventure nativ. Kein Adapter oder Wrapper noetig — die gesamte Text-/Chat-API ist direkt verfuegbar.

### 4.5 CloudNet v4 Unterstuetzung
CloudNet v4 unterstuetzt Minestom als Plattform. Deployment und Skalierung ueber das bestehende CloudNet-Setup ist grundsaetzlich moeglich.

### 4.6 Bessere Isolation
Minestom bietet das Konzept von Instances (vergleichbar mit separaten Welten). Jedes Rennen kann in einer eigenen Instance laufen — vollstaendig isoliert, ohne gegenseitige Beeinflussung.

### 4.7 Kein Plugin-Overhead
Kein Plugin-Classloader, keine Plugin-Lifecycle-Verwaltung, keine Abhaengigkeits-Konflikte mit anderen Plugins. Der Server ist die Anwendung.

---

## 5. Contra-Argumente gegen Minestom

### 5.1 Alles selbst implementieren
Elytra-Physik, Collision-Detection, Boost-Mechaniken — alles muss von Grund auf selbst gebaut werden. Das ist erheblicher Entwicklungsaufwand und eine potenzielle Fehlerquelle. Die Vanilla-Elytra-Physik ist komplex (Glide-Winkel, Feuerwerkraketen-Boost, Kollision mit Bloecken).

### 5.2 Java 25 Anforderung
Java 25 ist neuer als der bisherige Projekt-Standard (Java 21). Build-Pipelines, CI/CD und Deployment-Infrastruktur muessen aktualisiert werden. Java 25 ist moeglicherweise noch kein LTS-Release.

### 5.3 Kleineres Ecosystem
Paper hat eine grosse Community, umfangreiche Dokumentation und viele Bibliotheken. Minestom hat ein deutlich kleineres Ecosystem — bei Problemen gibt es weniger Ressourcen und Erfahrungsberichte.

### 5.4 Keine Vanilla-Mechaniken out-of-the-box
Alles, was ueber das reine Protokoll hinausgeht, muss selbst implementiert oder ueber Community-Extensions nachgeruestet werden. Das betrifft auch scheinbar triviale Dinge wie Block-Placement, Inventar-Handling oder Partikel-Effekte.

### 5.5 CloudNet-Integration hat bekannte Probleme
Die CloudNet-Minestom-Integration hat bekannte Issues, insbesondere beim Shutdown-Verhalten (siehe CloudNet Issue #1304). Das kann zu Problemen im Produktivbetrieb fuehren.

### 5.6 API-Stabilitaet
Die Minestom-API aendert sich haeufiger als die Paper-API. Breaking Changes zwischen Versionen sind moeglich und erfordern regelmaessige Anpassungen am Code.

---

## 6. Vorher/Nachher Vergleich

| Bereich | Vorher (Paper) | Nachher (Minestom) |
|---------|---------------|-------------------|
| **Server-Typ** | Paper-Server mit Plugin | Standalone-Server mit eigener `main()` |
| **Java-Version** | Java 21 | Java 25 |
| **World-Format** | Paper laedt Welten automatisch | Anvil-Format direkt laden via `AnvilLoader` |
| **Physik** | Vanilla Elytra-Physik vom Server | Eigenimplementierung erforderlich |
| **Events** | Bukkit Event-System (`@EventHandler`) | Minestom Event-Nodes (typsicheres Event-System) |
| **Scheduler** | `BukkitScheduler` / `BukkitRunnable` | Minestom `Scheduler` / `TaskSchedule` |
| **Commands** | Cloud mit `cloud-paper` Integration | Cloud mit `cloud-minestom` Integration |
| **Config** | plugin.yml (auto-generiert via `plugin-yml`) | Entfaellt — Konfiguration in `main()` |
| **Tests** | MockBukkit + JUnit 5 | Direkte Minestom-Instanz + JUnit 5 (kein MockBukkit) |
| **Deployment** | Plugin-JAR in Paper-Server `plugins/` Ordner | Fat-JAR als eigenstaendiger Prozess |
| **Dependencies** | Paper API, plugin-yml, MockBukkit | Minestom Core, keine Plugin-Infrastruktur |

---

## 7. Migrationsumfang

### Betroffene Module

| Modul | Aktion | Aufwand |
|-------|--------|---------|
| `plugins/game` | Komplett neu auf Minestom | Hoch |
| `shared/conversation-api` | Neu schreiben (plattform-agnostisch) | Mittel |
| `shared/common` (ECS) | Bleibt unveraendert (keine Bukkit-Abhaengigkeit) | Keiner |
| `shared/phase` | Bleibt unveraendert (keine Bukkit-Abhaengigkeit) | Keiner |
| `shared/database` | Bleibt unveraendert | Keiner |
| `plugins/setup` | Bleibt auf Paper | Keiner |

### Dateien mit Bukkit-Imports (zu migrieren)

- **`plugins/game`**: 40+ Dateien — Events, Listener, Scheduler, Player-Handling, World-API
- **`shared/conversation-api`**: 7 Dateien — Komplett neu zu implementieren

---

## 8. Risikobewertung

| Risiko | Wahrscheinlichkeit | Auswirkung | Mitigation |
|--------|-------------------|------------|------------|
| Elytra-Physik weicht von Vanilla ab | Hoch | Mittel | Referenz-Dokumentation erstellen, iteratives Tuning |
| CloudNet-Shutdown-Problem | Mittel | Hoch | Issue #1304 verfolgen, eigenen Shutdown-Hook implementieren |
| Minestom Breaking Changes | Mittel | Mittel | Version pinnen, Updates kontrolliert durchfuehren |
| Java 25 Kompatibilitaet in CI/CD | Niedrig | Niedrig | Build-Pipeline fruehzeitig umstellen |
| Laengere Entwicklungszeit als geplant | Hoch | Mittel | MVP definieren, schrittweise migrieren |
