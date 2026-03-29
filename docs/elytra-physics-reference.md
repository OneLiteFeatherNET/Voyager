# Elytra-Flugphysik: Vollstaendige Referenz fuer Server-seitige Implementierung

Dieses Dokument dient als technische Referenz fuer die Implementierung von Elytra-Flugphysik
in Minestom (ohne Vanilla-Code). Alle Formeln basieren auf decompiliertem Vanilla-Code
(Snapshot 15w41b/15w42a, verifiziert bis 1.21.x) und dem Minecraft Wiki.

---

## 1. Konstanten

### 1.1 Elytra-Flugphysik

| Konstante | Wert | Beschreibung |
|---|---|---|
| `GRAVITY` | -0.08 | Gravitation pro Tick (blocks/tick^2) |
| `PITCH_LIFT` | 0.06 | Auftriebskoeffizient (multipliziert mit cos^2(pitch)) |
| `DOWNWARD_GLIDE_FACTOR` | -0.1 | Daempfungsfaktor bei Abwaertsbewegung |
| `UPWARD_PITCH_BASE` | 0.04 | Basis-Beschleunigung bei Aufwaertsneigung |
| `UPWARD_PITCH_MULTIPLIER` | 3.2 | Y-Boost-Multiplikator bei Aufwaertsneigung (3.5 in 15w41b, 3.2 ab 15w42a) |
| `DIRECTION_ALIGNMENT_RATE` | 0.1 | Rate der Ausrichtung auf Blickrichtung |
| `DRAG_HORIZONTAL` | 0.99 | Horizontaler Reibungskoeffizient pro Tick |
| `DRAG_VERTICAL` | 0.98 | Vertikaler Reibungskoeffizient pro Tick |
| `MIN_FALL_DISTANCE` | 1.0 | Minimale Fallhoehe zum Aktivieren (Blocks) |
| `TICKS_PER_SECOND` | 20 | Server-Tickrate |

### 1.2 Allgemeine Entity-Physik

| Konstante | Wert | Beschreibung |
|---|---|---|
| `ENTITY_GRAVITY` | 0.08 | Standard-Gravitation fuer Entities (blocks/tick^2) |
| `AIR_DRAG` | 0.98 | Vertikaler Drag in der Luft |
| `GROUND_FRICTION` | 0.546 | Horizontale Reibung am Boden (0.6 * 0.91) |
| `AIR_FRICTION` | 0.91 | Horizontale Reibung in der Luft |
| `PLAYER_HITBOX_GLIDING` | 0.6 x 0.6 x 0.6 | Hitbox waehrend des Gleitens |

### 1.3 Firework-Boost

| Konstante | Wert | Beschreibung |
|---|---|---|
| `FIREWORK_HORIZONTAL_ACCEL` | 1.15 | Multiplikator fuer X/Z-Geschwindigkeit pro Tick |
| `FIREWORK_VERTICAL_ACCEL` | 0.04 | Additive vertikale Beschleunigung pro Tick |
| `FIREWORK_INITIAL_VY` | 0.05 | Initiale vertikale Geschwindigkeit |
| `FIREWORK_INITIAL_VXZ_STDDEV` | 0.001 | Standardabweichung der initialen X/Z-Geschwindigkeit |

### 1.4 Schadens-Konstanten

| Konstante | Wert | Beschreibung |
|---|---|---|
| `COLLISION_DAMAGE_MULTIPLIER` | 10 | Multiplikator fuer horizontale Geschwindigkeitsaenderung |
| `COLLISION_DAMAGE_OFFSET` | -3 | Abzug von der Schadensberechnung |
| `DURABILITY_DRAIN_INTERVAL` | 20 | Ticks zwischen Haltbarkeitsverlust (= 1/Sekunde) |
| `MAX_DURABILITY` | 432 | Maximale Haltbarkeit (Java Edition) |
| `CRITICAL_IMPACT_ANGLE` | 50 deg | Ungefaehre kritische Aufprallwinkel |

---

## 2. Tick-fuer-Tick Pseudocode der Elytra-Physik

```
Quelle: Decompilierter Vanilla-Code (samsartor Gist, verifiziert mit Yarn-Mappings)
Methode: LivingEntity.travel() -> Elytra-Zweig wenn isFallFlying() == true
```

### 2.1 Voraussetzungen pruefen

```pseudocode
function canStartGliding(player):
    if player.isCreative:
        return player.glideTime > 0
    else:
        return !player.isOnGround AND player.fallDistance >= 1.0
```

### 2.2 Hauptphysik-Tick (einmal pro Tick, 20x/Sekunde)

```pseudocode
function elytraPhysicsTick(player, deltaTime = 1):
    // --- Schritt 1: Haltbarkeit ---
    if !player.isCreative:
        if (player.glideTime + 1) % 20 == 0:
            player.elytraDurability -= 1

    // --- Schritt 2: Blickrichtung berechnen ---
    yaw   = player.yaw      // Horizontale Rotation in Radians
    pitch = player.pitch     // Vertikale Rotation in Radians

    yawCos   = cos(-yaw - PI)
    yawSin   = sin(-yaw - PI)
    pitchCos = cos(pitch)
    pitchSin = sin(pitch)

    // Normalisierter Blickvektor
    lookX = yawSin * (-pitchCos)
    lookY = -pitchSin
    lookZ = yawCos * (-pitchCos)

    // --- Schritt 3: Hilfswerte ---
    hVel       = sqrt(velX^2 + velZ^2)    // Horizontale Geschwindigkeit
    hLook      = pitchCos                   // Horizontale Komponente der Blickrichtung
    sqrPitchCos = pitchCos * pitchCos       // cos^2(pitch)

    // --- Schritt 4: Gravitation + Auftrieb ---
    // Gravitation: -0.08
    // Auftrieb:    +0.06 * cos^2(pitch)
    // -> Bei pitch=0 (horizontal): netto = -0.08 + 0.06 = -0.02 (langsames Sinken)
    // -> Bei pitch=90 (senkrecht nach unten): netto = -0.08 + 0 = -0.08 (volle Gravitation)
    velY += GRAVITY + sqrPitchCos * PITCH_LIFT
    // velY += -0.08 + cos^2(pitch) * 0.06

    // --- Schritt 5: Abwaerts-Gleiten Daempfung ---
    // Wenn der Spieler sinkt UND horizontal schaut (hLook > 0)
    // -> Teile der Abwaertsbewegung werden in Vorwaertsbewegung umgewandelt
    if velY < 0 AND hLook > 0:
        yAcc = velY * DOWNWARD_GLIDE_FACTOR * sqrPitchCos
        // yAcc = velY * (-0.1) * cos^2(pitch)
        velY += yAcc                          // Reduziert Sinkrate
        velX += lookX * yAcc / hLook          // Konvertiert zu horizontaler Bewegung
        velZ += lookZ * yAcc / hLook

    // --- Schritt 6: Aufwaerts-Pitch Boost ---
    // Wenn Spieler nach oben schaut (pitch < 0), wird horizontale
    // Geschwindigkeit in Hoehe umgewandelt
    if pitch < 0:
        yAcc = hVel * (-pitchSin) * UPWARD_PITCH_BASE
        // yAcc = hVel * (-sin(pitch)) * 0.04
        velY += yAcc * UPWARD_PITCH_MULTIPLIER   // velY += yAcc * 3.2
        velX -= lookX * yAcc / hLook              // Reduziert horizontale Geschwindigkeit
        velZ -= lookZ * yAcc / hLook

    // --- Schritt 7: Richtungsausrichtung ---
    // Richtet die horizontale Bewegung langsam auf die Blickrichtung aus
    if hLook > 0:
        velX += (lookX / hLook * hVel - velX) * DIRECTION_ALIGNMENT_RATE
        velZ += (lookZ / hLook * hVel - velZ) * DIRECTION_ALIGNMENT_RATE
        // Interpolation mit Faktor 0.1 zwischen aktueller und gewuenschter Richtung

    // --- Schritt 8: Reibung/Drag ---
    velX *= DRAG_HORIZONTAL    // velX *= 0.99
    velY *= DRAG_VERTICAL      // velY *= 0.98
    velZ *= DRAG_HORIZONTAL    // velZ *= 0.99

    // --- Schritt 9: Position aktualisieren ---
    posX += velX
    posY += velY
    posZ += velZ

    // --- Schritt 10: Kollisionserkennung ---
    performCollisionDetection(player)

    player.glideTime += 1
```

### 2.3 Gleichgewichts-Analyse

Bei konstantem Pitch (Gleichgewichtszustand, velY = 0):

```
Aus Schritt 4:  0 = -0.08 + cos^2(pitch) * 0.06
                cos^2(pitch) = 0.08 / 0.06 = 1.333...

Da cos^2(pitch) maximal 1.0 ist, gibt es KEIN echtes Gleichgewicht.
Bei pitch = 0 (horizontal): netto-Y-Beschleunigung = -0.02 (sinkt immer)
```

Das bedeutet: Ohne Firework-Boost sinkt der Spieler IMMER. Durch periodisches
Hochziehen und Abtauchen kann man durch Geschwindigkeitsumwandlung die Hoehe halten.

**Gleitverhaeltnis**: ~10.06 Blocks horizontal pro 1 Block Hoehenverlust (bei optimalem Pitch).

**Minimale Geschwindigkeit**: ~7.2 m/s (bei ~30 Grad Aufwaertsneigung an der Hoehen-Obergrenze).

**Stall-Bedingung**: Ueber 30 Grad Aufwaertsneigung -> Geschwindigkeit sinkt rapide.
Bei 60 Grad -> Fallschaden moeglich. Bei 90 Grad -> freier Fall.

---

## 3. Firework-Boost Berechnung

### 3.1 Firework-Lebensdauer

```pseudocode
function calculateFireworkLifetime(gunpowderCount):
    // gunpowderCount = Flight Duration (1-3)
    lifetime = 10 * (gunpowderCount + 1) + random(0, 5) + random(0, 6)
    return lifetime  // in Ticks

// Beispiele:
// Flight Duration 1: 20 + 0..11 = 20-31 Ticks (~1.0-1.55 Sekunden)
// Flight Duration 2: 30 + 0..11 = 30-41 Ticks (~1.5-2.05 Sekunden)
// Flight Duration 3: 40 + 0..11 = 40-51 Ticks (~2.0-2.55 Sekunden)
```

### 3.2 Boost-Physik pro Tick

```pseudocode
function fireworkBoostTick(player, fireworkEntity):
    if !player.isFallFlying():
        return

    // Blickrichtung des Spielers als normalisierter Vektor
    lookDir = player.getLookDirection()  // (lookX, lookY, lookZ) normalisiert

    // Geschwindigkeit um Blickrichtungskomponente erhoehen
    // Die Rakete beschleunigt den Spieler in Blickrichtung
    player.velX += lookX * 0.1 + (lookX * 1.5 - player.velX) * 0.5
    player.velY += lookY * 0.1 + (lookY * 1.5 - player.velY) * 0.5
    player.velZ += lookZ * 0.1 + (lookZ * 1.5 - player.velZ) * 0.5

    // ALTERNATIVE (vereinfachte Vanilla-Naeherung):
    // Pro Tick wird die Geschwindigkeit mit dem Richtungsvektor beschleunigt:
    //   deltaV = lookDir * boostStrength
    //   player.velocity += deltaV
    // Resultierende Geschwindigkeit: ~33.5 blocks/second (1.675 blocks/tick)
```

### 3.3 Vereinfachte Boost-Implementierung fuer Minestom

```pseudocode
function applyFireworkBoost(player, flightDuration):
    lifetime = calculateFireworkLifetime(flightDuration)
    boostPerTick = 1.675 / lifetime  // Gesamtgeschwindigkeit / Lebensdauer

    // Jeden Tick waehrend der Lebensdauer:
    function onTick():
        if ticksRemaining <= 0: return

        lookDir = normalize(player.getLookDirection())
        // Addiere Boost in Blickrichtung
        player.velocity += lookDir * boostPerTick

        ticksRemaining -= 1
```

**Maximale Geschwindigkeiten mit Fireworks:**
- Sustained: ~33.5 blocks/second
- Peak-Bursts: 60-80+ blocks/second (bei anhaltendem Boosting)

---

## 4. Kollisions-Schadensformel

### 4.1 Aufprallschaden

```pseudocode
function calculateCollisionDamage(velocityBefore, velocityAfter):
    // Nur HORIZONTALE Geschwindigkeitsaenderung zaehlt
    deltaHVel = sqrt(velocityBefore.x^2 + velocityBefore.z^2)
              - sqrt(velocityAfter.x^2 + velocityAfter.z^2)

    // Formel aus dem Minecraft Wiki:
    damage = ceil(COLLISION_DAMAGE_MULTIPLIER * deltaHVel - COLLISION_DAMAGE_OFFSET)
    // damage = ceil(10 * deltaHorizontalVelocity - 3)

    if damage < 0: damage = 0
    return damage
```

### 4.2 Regeln

- **Horizontale Oberflaechen** (Decken): KEIN Schaden (nur vertikale Geschwindigkeitsaenderung).
- **Vertikale Oberflaechen** (Waende): Schaden proportional zur horizontalen Geschwindigkeitsaenderung.
- **Diagonale Oberflaechen**: Schaden basiert nur auf der horizontalen Komponente.
- **Kritischer Aufprallwinkel**: ~50 Grad zur Wand -> maximaler Schaden.
- **Feather Falling**: Hat KEINEN Effekt auf Elytra-Kollisionsschaden.
- **Slow Falling Potion**: Reduziert horizontale und vertikale Geschwindigkeit drastisch.

### 4.3 Beispielberechnungen

```
Bei 20 blocks/second horizontal (1.0 blocks/tick):
  Vollstopp: damage = ceil(10 * 1.0 - 3) = 7 Herzen (14 HP)

Bei 33.5 blocks/second (1.675 blocks/tick):
  Vollstopp: damage = ceil(10 * 1.675 - 3) = 14 Herzen (27.5 -> 28 HP) -> Tod

Bei 10 blocks/second (0.5 blocks/tick):
  Vollstopp: damage = ceil(10 * 0.5 - 3) = 2 Herzen (4 HP)

Minimalschaden (damage > 0): deltaHVel > 0.3 blocks/tick (6 blocks/second)
```

---

## 5. Ring-Durchflug-Erkennung (Geometrischer Algorithmus)

### 5.1 Ring-Definition

Ein Ring (Checkpoint) im 3D-Raum wird definiert durch:
- **Center** `C = (cx, cy, cz)`: Mittelpunkt des Rings
- **Normal** `N = (nx, ny, nz)`: Normalvektor der Ring-Ebene (normalisiert)
- **Radius** `R`: Radius des Rings

### 5.2 Algorithmus: Liniensegment-durch-Ring-Erkennung

```pseudocode
function checkRingPassthrough(
    P0,     // Spielerposition am Anfang des Ticks (vec3)
    P1,     // Spielerposition am Ende des Ticks (vec3)
    C,      // Ring-Mittelpunkt (vec3)
    N,      // Ring-Normalvektor (vec3, normalisiert)
    R       // Ring-Radius (float)
) -> boolean:

    // --- Schritt 1: Liniensegment-Ebene-Schnitt ---
    // Die Ring-Ebene ist definiert als: dot(N, P - C) = 0
    // Das Liniensegment ist: P(t) = P0 + t * (P1 - P0), t in [0, 1]

    D = P1 - P0                         // Richtungsvektor
    denom = dot(N, D)                    // Nenner

    // Wenn denom ~= 0: Segment ist parallel zur Ebene -> kein Durchflug
    if abs(denom) < EPSILON:
        return false

    // Parameter t fuer den Schnittpunkt
    t = dot(N, C - P0) / denom

    // Pruefen ob Schnittpunkt innerhalb des Segments liegt
    if t < 0.0 OR t > 1.0:
        return false

    // --- Schritt 2: Schnittpunkt berechnen ---
    intersection = P0 + t * D

    // --- Schritt 3: Abstand zum Ring-Mittelpunkt pruefen ---
    distToCenter = length(intersection - C)

    // Spieler fliegt durch den Ring wenn der Abstand kleiner als der Radius ist
    if distToCenter <= R:
        return true

    return false
```

### 5.3 Erweiterte Version mit Spieler-Hitbox

```pseudocode
function checkRingPassthroughWithHitbox(
    P0, P1,         // Start/End-Position (Spieler-Mittelpunkt)
    C, N, R,         // Ring-Parameter
    playerRadius     // Halbe Spieler-Hitbox-Breite (0.3 bei Gliding)
) -> boolean:
    // Effektiver Ring-Radius = Ring-Radius minus Spieler-Radius
    // (Spieler muss komplett durch den Ring passen)
    effectiveR = R - playerRadius

    if effectiveR <= 0:
        return false  // Ring zu klein

    return checkRingPassthrough(P0, P1, C, N, effectiveR)
```

### 5.4 Robuste Version mit Mehrfach-Sampling

Bei sehr hohen Geschwindigkeiten kann der Spieler den Ring in einem Tick komplett
durchfliegen. Fuer zusaetzliche Robustheit:

```pseudocode
function checkRingPassthroughRobust(
    P0, P1, C, N, R, playerRadius, subSteps = 4
) -> boolean:
    // Primaerer Check: Liniensegment
    if checkRingPassthroughWithHitbox(P0, P1, C, N, R, playerRadius):
        return true

    // Sekundaerer Check: Naechster-Punkt-Abstand
    // (fuer den Fall, dass der Ring sehr nah am Pfad liegt aber nicht geschnitten wird)
    closestPoint = closestPointOnSegment(P0, P1, C)
    if length(closestPoint - C) <= R * 1.5:  // Grosszuegiger Check
        // Projiziere auf Ring-Ebene und pruefe nochmal
        projected = closestPoint - dot(closestPoint - C, N) * N
        if length(projected - C) <= R:
            return true

    return false
```

### 5.5 Hilfsfunktionen

```pseudocode
function closestPointOnSegment(A, B, P) -> vec3:
    AB = B - A
    t = dot(P - A, AB) / dot(AB, AB)
    t = clamp(t, 0.0, 1.0)
    return A + t * AB

function dot(a, b) -> float:
    return a.x * b.x + a.y * b.y + a.z * b.z

function length(v) -> float:
    return sqrt(v.x^2 + v.y^2 + v.z^2)

function normalize(v) -> vec3:
    l = length(v)
    return vec3(v.x/l, v.y/l, v.z/l)
```

---

## 6. Server-Client Synchronisation (Packets)

### 6.1 Relevante Packets

#### Serverbound (Client -> Server)

| Packet | ID (1.21.x) | Beschreibung |
|---|---|---|
| **Player Action** (Entity Action) | 0x25 | Enthaelt Action ID `8` = "Start Elytra Flying" |
| **Player Position** | 0x1A | X, Y, Z (Double), On Ground (Boolean) |
| **Player Position and Rotation** | 0x1B | X, Y, Z, Yaw, Pitch, On Ground |
| **Player Rotation** | 0x1C | Yaw, Pitch, On Ground |

#### Clientbound (Server -> Client)

| Packet | ID (1.21.x) | Beschreibung |
|---|---|---|
| **Entity Velocity** (Set Entity Motion) | 0x58 | Entity ID (VarInt), Velocity X/Y/Z (Short, in 1/8000 blocks/tick) |
| **Entity Metadata** (Set Entity Data) | 0x58 | Entity ID, Metadata Entries |
| **Player Position** (Synchronize Player Position) | 0x40 | X, Y, Z, Yaw, Pitch, Flags, Teleport ID |
| **Entity Position** (Update Entity Position) | 0x2E | Entity ID, Delta X/Y/Z (Short), On Ground |

### 6.2 Entity Metadata: Elytra-Flag

```
Entity Base Class -> Index 0 (Byte):
  Bit 0x01: Is on Fire
  Bit 0x02: Is Crouching
  Bit 0x04: Is Sprinting (unused for players)
  Bit 0x08: Is Swimming
  Bit 0x10: Is Invisible
  Bit 0x20: Has Glowing Effect
  Bit 0x80: Is Flying with Elytra (Fall Flying)
```

**Wichtig**: Das Bit `0x80` an Index 0 steuert die Elytra-Fluganimation beim Client.

### 6.3 Velocity-Packet Encoding

```pseudocode
// Geschwindigkeit wird als Short (16-bit signed) in 1/8000 blocks/tick uebertragen
function encodeVelocity(blocksPerTick) -> short:
    return clamp(round(blocksPerTick * 8000), -32768, 32767)

function decodeVelocity(rawShort) -> double:
    return rawShort / 8000.0

// Beispiel: 1.675 blocks/tick -> 13400 (Short-Wert)
// Maximale darstellbare Geschwindigkeit: 32767/8000 = 4.096 blocks/tick = 81.9 blocks/second
```

### 6.4 Synchronisations-Strategie fuer Minestom

```pseudocode
// Empfohlene Server-seitige Implementierung:

function onPlayerStartElytraFlying(player):
    // 1. Validierung: Hat der Spieler eine Elytra ausgeruestet?
    if !hasElytraEquipped(player): return
    // 2. Validierung: Ist der Spieler in der Luft mit genug Fallhoehe?
    if player.isOnGround OR player.fallDistance < 1.0: return
    // 3. Elytra-Flag setzen
    player.setFallFlying(true)
    // 4. Metadata-Packet an alle Spieler senden
    broadcastEntityMetadata(player, index=0, bit=0x80, value=true)

function onServerTick():
    for each player in flyingPlayers:
        // 1. Client-Position und -Rotation empfangen (Serverbound)
        //    -> Player Position/Rotation Packets
        // 2. Server-seitige Physik berechnen
        elytraPhysicsTick(player)
        // 3. Berechnete Geschwindigkeit an Client senden (alle 1-5 Ticks)
        if player.ticksSinceLastVelocityUpdate >= VELOCITY_SYNC_INTERVAL:
            sendEntityVelocity(player, player.velocity)
            player.ticksSinceLastVelocityUpdate = 0
        // 4. Position validieren (Anti-Cheat)
        validatePlayerPosition(player)

// VELOCITY_SYNC_INTERVAL:
// - 1 Tick:  Praezise, aber hohe Bandbreite
// - 3 Ticks: Guter Kompromiss
// - 5 Ticks: Sparsam, aber spuerbarer Lag
```

### 6.5 Anti-Cheat Ueberlegungen

```
- Der Vanilla-Server erlaubt maximal 80 Ticks Floating bevor Kick
- Elytra-Flug muss als erlaubtes "Floating" markiert sein
- Geschwindigkeits-Obergrenze pruefen (>81.9 blocks/s ist verdaechtig)
- Firework-Boost-Zeitfenster validieren
```

---

## 7. Zusammenfassung der Physik-Pipeline

```
Jeder Tick (50ms):

1. INPUT:     Client sendet Position + Rotation
2. VALIDATE:  Pruefen ob Elytra-Flug aktiv und gueltig
3. PHYSICS:   Elytra-Physik berechnen:
              a) Gravitation + Auftrieb
              b) Abwaerts-Gleiten -> Vorwaerts umwandeln
              c) Aufwaerts-Pitch -> Hoehe aus Geschwindigkeit
              d) Richtungsausrichtung
              e) Drag/Reibung
4. BOOST:     Falls Firework aktiv: Boost addieren
5. COLLISION: Kollisionen mit Welt pruefen
              -> Schaden berechnen falls Aufprall
6. RING:      Ring-Durchflug-Erkennung
              -> Liniensegment P0->P1 gegen Ring pruefen
7. POSITION:  Neue Position = alte Position + Geschwindigkeit
8. SYNC:      Velocity-Packet an Client senden (periodisch)
              Metadata aktualisieren falls noetig
9. DAMAGE:    Haltbarkeits-Update alle 20 Ticks
```

---

## 8. Quellen

- Decompiled Elytra Code (Snapshot 15w41b): https://gist.github.com/samsartor/a7ec457aca23a7f3f120
- Minecraft Wiki - Elytra: https://minecraft.wiki/w/Elytra
- Minecraft Wiki - Firework Rocket: https://minecraft.wiki/w/Firework_Rocket
- Entity Physics: https://github.com/ddevault/TrueCraft/wiki/Entity-Movement-And-Physics
- Minecraft Protocol - Entity Metadata: https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata
- Minecraft Protocol - Packets: https://minecraft.wiki/w/Java_Edition_protocol/Packets
- wiki.vg Protocol Reference: https://wiki.vg/Protocol
- Minestom Elytra Discussion: https://github.com/Minestom/Minestom/discussions/1427
- 3D Collision Detection (MDN): https://developer.mozilla.org/en-US/docs/Games/Techniques/3D_collision_detection
- Circle-Line Intersection: https://www.baeldung.com/cs/circle-line-segment-collision-detection
