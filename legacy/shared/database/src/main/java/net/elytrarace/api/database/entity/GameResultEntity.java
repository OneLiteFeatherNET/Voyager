package net.elytrarace.api.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.common.game.scoring.MedalTier;
import org.hibernate.annotations.ColumnDefault;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "game_results",
        indexes = {
                // "my last 10 races/practices" — most-recent-first lookup per player+mode.
                // playedAt is the existing finish timestamp on this table.
                @Index(name = "idx_game_results_player_mode_played",
                        columnList = "player_id, game_mode, playedAt DESC")
        }
)
public class GameResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private ElytraPlayerEntity player;

    @Column(nullable = false)
    private String cupName;

    @Column(nullable = false)
    private String mapName;

    @Column(nullable = false)
    private int ringPoints;

    @Column(nullable = false)
    private int positionBonus;

    @Column(nullable = false)
    private int totalPoints;

    @Column(nullable = false)
    private int placement;

    @Column(nullable = false)
    private LocalDateTime playedAt;

    /**
     * Discriminator between competitive races and solo practice runs.
     * STRING enum mapping: stable across enum reordering and human-readable in DB dumps.
     * Defaults to {@link GameMode#RACE} so existing rows remain valid after the column is added.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false, length = 16)
    @ColumnDefault("'RACE'")
    private GameMode gameMode = GameMode.RACE;

    /**
     * Medal tier earned in a practice run; {@code null} for race results.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "medal_tier", nullable = true, length = 16)
    private @Nullable MedalTier medalTier;

    /**
     * Wall-clock map completion time in milliseconds.
     * {@code null} represents a DNF (did-not-finish): the player never crossed
     * the final ring before the map ended. Used by Race Mode tie-breakers and
     * personal-best logic; see Epic #168.
     */
    @Column(name = "completion_time_ms", nullable = true)
    private @Nullable Long completionTimeMs;

    public GameResultEntity() {
    }

    public GameResultEntity(ElytraPlayerEntity player, String cupName, String mapName, int ringPoints,
                            int positionBonus, int totalPoints, int placement, LocalDateTime playedAt) {
        this(player, cupName, mapName, ringPoints, positionBonus, totalPoints, placement, playedAt,
                GameMode.RACE, null, null);
    }

    public GameResultEntity(ElytraPlayerEntity player, String cupName, String mapName, int ringPoints,
                            int positionBonus, int totalPoints, int placement, LocalDateTime playedAt,
                            GameMode gameMode, @Nullable MedalTier medalTier) {
        this(player, cupName, mapName, ringPoints, positionBonus, totalPoints, placement, playedAt,
                gameMode, medalTier, null);
    }

    public GameResultEntity(ElytraPlayerEntity player, String cupName, String mapName, int ringPoints,
                            int positionBonus, int totalPoints, int placement, LocalDateTime playedAt,
                            GameMode gameMode, @Nullable MedalTier medalTier,
                            @Nullable Long completionTimeMs) {
        this.player = player;
        this.cupName = cupName;
        this.mapName = mapName;
        this.ringPoints = ringPoints;
        this.positionBonus = positionBonus;
        this.totalPoints = totalPoints;
        this.placement = placement;
        this.playedAt = playedAt;
        this.gameMode = gameMode;
        this.medalTier = medalTier;
        this.completionTimeMs = completionTimeMs;
    }

    public Long getId() {
        return id;
    }

    public ElytraPlayerEntity getPlayer() {
        return player;
    }

    public void setPlayer(ElytraPlayerEntity player) {
        this.player = player;
    }

    public String getCupName() {
        return cupName;
    }

    public void setCupName(String cupName) {
        this.cupName = cupName;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public int getRingPoints() {
        return ringPoints;
    }

    public void setRingPoints(int ringPoints) {
        this.ringPoints = ringPoints;
    }

    public int getPositionBonus() {
        return positionBonus;
    }

    public void setPositionBonus(int positionBonus) {
        this.positionBonus = positionBonus;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public int getPlacement() {
        return placement;
    }

    public void setPlacement(int placement) {
        this.placement = placement;
    }

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(LocalDateTime playedAt) {
        this.playedAt = playedAt;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public @Nullable MedalTier getMedalTier() {
        return medalTier;
    }

    public void setMedalTier(@Nullable MedalTier medalTier) {
        this.medalTier = medalTier;
    }

    public @Nullable Long getCompletionTimeMs() {
        return completionTimeMs;
    }

    public void setCompletionTimeMs(@Nullable Long completionTimeMs) {
        this.completionTimeMs = completionTimeMs;
    }
}
