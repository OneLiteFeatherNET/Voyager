package net.elytrarace.api.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_results")
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

    public GameResultEntity() {
    }

    public GameResultEntity(ElytraPlayerEntity player, String cupName, String mapName, int ringPoints,
                            int positionBonus, int totalPoints, int placement, LocalDateTime playedAt) {
        this.player = player;
        this.cupName = cupName;
        this.mapName = mapName;
        this.ringPoints = ringPoints;
        this.positionBonus = positionBonus;
        this.totalPoints = totalPoints;
        this.placement = placement;
        this.playedAt = playedAt;
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
}
