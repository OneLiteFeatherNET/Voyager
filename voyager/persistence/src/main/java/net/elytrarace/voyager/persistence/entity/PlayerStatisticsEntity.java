package net.elytrarace.voyager.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** The running totals of one player, one row each, written by atomic increments. Table {@code player_statistics}. */
@Entity
@Table(name = "player_statistics")
public class PlayerStatisticsEntity {

    @Id
    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "cups_played", nullable = false)
    private long cupsPlayed;

    @Column(name = "cups_won", nullable = false)
    private long cupsWon;

    @Column(name = "races_played", nullable = false)
    private long racesPlayed;

    @Column(name = "races_won", nullable = false)
    private long racesWon;

    @Column(name = "rings_passed", nullable = false)
    private long ringsPassed;

    @Column(name = "boosts_used", nullable = false)
    private long boostsUsed;

    @Column(name = "resets", nullable = false)
    private long resets;

    @Column(name = "flight_millis", nullable = false)
    private long flightMillis;

    protected PlayerStatisticsEntity() {
    }
}
