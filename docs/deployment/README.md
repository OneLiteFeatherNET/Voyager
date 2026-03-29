# Deployment Guide

## Lokale Entwicklung mit Docker

### Voraussetzungen

- Docker und Docker Compose installiert
- Java 25 (oder kompatibel) fuer lokale Builds ohne Docker

### Server + Datenbank starten

Aus dem Projektverzeichnis:

```bash
docker compose -f docker/compose.yml up -d
```

Dies startet:
- **MariaDB** auf Port `3306` (Benutzer/Passwort/Datenbank: `voyager-project`)
- **Game-Server** (Minestom) auf Port `25565`

Der Game-Server wird automatisch per Multi-Stage Build kompiliert.

### Nur Datenbank starten

Falls nur die Datenbank benoetigt wird (z.B. bei lokaler Entwicklung mit IDE):

```bash
docker compose -f docker/mariadb/compose.yml up -d
```

### Umgebungsvariablen

Der Game-Server akzeptiert folgende Umgebungsvariablen:

| Variable      | Beschreibung         | Standardwert      |
|---------------|----------------------|--------------------|
| `DB_HOST`     | Datenbank-Host       | `mariadb`          |
| `DB_PORT`     | Datenbank-Port       | `3306`             |
| `DB_NAME`     | Datenbankname        | `voyager-project`  |
| `DB_USER`     | Datenbank-Benutzer   | `voyager-project`  |
| `DB_PASSWORD` | Datenbank-Passwort   | `voyager-project`  |

## CloudNet v4 Deployment

### Task einrichten

1. Die Datei `docs/deployment/cloudnet-task.json` als Vorlage verwenden.
2. In CloudNet den Task anlegen:
   ```
   tasks create task ElytraRace
   ```
3. Die Task-Konfiguration unter `local/tasks/ElytraRace.json` entsprechend anpassen.
4. Das Server-JAR (Shadow-JAR aus `:server:shadowJar`) in das Template `ElytraRace/default` kopieren.

### Template-Struktur

```
ElytraRace/default/
  app.jar          # Shadow-JAR des Servers
```

## JVM Flags Empfehlung

Fuer den Produktivbetrieb werden folgende JVM-Flags empfohlen:

```
-XX:+UseZGC
-XX:+ZGenerational
-Xms256M
-Xmx512M
```

**ZGC** (Z Garbage Collector) mit generational Mode bietet niedrige Latenzen und ist ideal fuer Minecraft-Server. Die Heap-Groesse kann je nach Spieleranzahl angepasst werden:

| Spieleranzahl | Empfohlener Heap |
|---------------|------------------|
| 1-20          | 512M             |
| 20-50         | 1G               |
| 50+           | 2G               |
