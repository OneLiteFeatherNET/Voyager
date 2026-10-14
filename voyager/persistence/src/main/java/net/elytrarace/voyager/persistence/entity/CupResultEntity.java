package net.elytrarace.voyager.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** One racer's result in one finished cup, with the medal count per tier. Table {@code cup_result}. */
@Entity
@Table(name = "cup_result")
public class CupResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cup_result_id", nullable = false)
    private Long cupResultId;

    @Column(name = "cup_session_id", nullable = false)
    private long cupSessionId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "final_position", nullable = false)
    private short finalPosition;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    @Column(name = "maps_finished", nullable = false)
    private int mapsFinished;

    @Nullable
    @Column(name = "best_time_millis")
    private Integer bestTimeMillis;

    @Column(name = "medals_diamond", nullable = false)
    private int medalsDiamond;

    @Column(name = "medals_gold", nullable = false)
    private int medalsGold;

    @Column(name = "medals_silver", nullable = false)
    private int medalsSilver;

    @Column(name = "medals_bronze", nullable = false)
    private int medalsBronze;

    protected CupResultEntity() {
    }
}
