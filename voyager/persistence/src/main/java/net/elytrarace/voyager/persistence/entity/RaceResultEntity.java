package net.elytrarace.voyager.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One racer's result in one finished map. The unique key (race session, player) is in the schema. A racer who did not
 * finish has no finish position and no finish time. Table {@code race_result}.
 */
@Entity
@Table(name = "race_result")
public class RaceResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "race_result_id", nullable = false)
    private Long raceResultId;

    @Column(name = "race_session_id", nullable = false)
    private long raceSessionId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Nullable
    @Column(name = "finish_position")
    private Short finishPosition;

    @Nullable
    @Column(name = "finish_time_millis")
    private Integer finishTimeMillis;

    @Column(name = "ring_points", nullable = false)
    private int ringPoints;

    @Column(name = "placement_bonus", nullable = false)
    private int placementBonus;

    @Column(name = "medal_points", nullable = false)
    private int medalPoints;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    @Column(name = "rings_passed", nullable = false)
    private int ringsPassed;

    @Column(name = "boosts_used", nullable = false)
    private int boostsUsed;

    @Column(name = "resets", nullable = false)
    private int resets;

    @Column(name = "flight_millis", nullable = false)
    private int flightMillis;

    @Column(name = "medal_tier", nullable = false, length = 16)
    private String medalTier;

    @Column(name = "validation_state", nullable = false, length = 16)
    private String validationState;

    @Nullable
    @Column(name = "plausibility_error")
    private Double plausibilityError;

    protected RaceResultEntity() {
    }
}
