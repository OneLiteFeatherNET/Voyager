package net.elytrarace.api.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "elytra_players")
public class ElytraPlayerEntity {

    @Id
    private UUID playerId;

    @Column
    private String lastKnownName;

    @Column(nullable = false)
    private int totalGamesPlayed;

    @Column(nullable = false)
    private int totalWins;

    @Column(nullable = false)
    private int totalRingsPassed;

    @Column
    private LocalDateTime lastPlayed;

    @OneToMany(mappedBy = "player")
    private List<GameResultEntity> gameResults = new ArrayList<>();

    public ElytraPlayerEntity(UUID playerId) {
        this.playerId = playerId;
    }

    public ElytraPlayerEntity() {
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public int getTotalGamesPlayed() {
        return totalGamesPlayed;
    }

    public void setTotalGamesPlayed(int totalGamesPlayed) {
        this.totalGamesPlayed = totalGamesPlayed;
    }

    public int getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(int totalWins) {
        this.totalWins = totalWins;
    }

    public int getTotalRingsPassed() {
        return totalRingsPassed;
    }

    public void setTotalRingsPassed(int totalRingsPassed) {
        this.totalRingsPassed = totalRingsPassed;
    }

    public LocalDateTime getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(LocalDateTime lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public List<GameResultEntity> getGameResults() {
        return gameResults;
    }

    public void setGameResults(List<GameResultEntity> gameResults) {
        this.gameResults = gameResults;
    }
}
