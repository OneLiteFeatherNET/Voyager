package net.elytrarace.voyager.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A player who took part in a finished race or cup. Table {@code player}. */
@Entity
@Table(name = "player")
public class PlayerEntity {

    @Id
    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "last_known_name", nullable = false, length = 16)
    private String lastKnownName;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    protected PlayerEntity() {
    }
}
