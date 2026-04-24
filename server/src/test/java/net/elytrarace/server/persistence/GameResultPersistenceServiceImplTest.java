package net.elytrarace.server.persistence;

import net.elytrarace.api.database.entity.ElytraPlayerEntity;
import net.elytrarace.api.database.entity.GameResultEntity;
import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import net.elytrarace.api.database.repository.GameResultRepository;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.minestom.server.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GameResultPersistenceServiceImpl} using Mockito-mocked
 * repositories. Verifies that entities are built with the correct fields,
 * placement bonuses feed through, and failures never propagate upward.
 */
class GameResultPersistenceServiceImplTest {

    private ElytraPlayerRepository playerRepo;
    private GameResultRepository resultRepo;
    private GameResultPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        playerRepo = mock(ElytraPlayerRepository.class);
        resultRepo = mock(GameResultRepository.class);
        service = new GameResultPersistenceServiceImpl(playerRepo, resultRepo);
    }

    @Test
    void persistsOneResultPerRankedPlayer() {
        UUID winnerId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Entity winner = playerEntity(winnerId, "Alice", 100, 50);
        Entity second = playerEntity(secondId, "Bob", 60, 30);

        when(playerRepo.getElytraPlayerById(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(playerRepo.saveElytraPlayer(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(resultRepo.saveResult(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.persistResults("Test Cup", "Test Map", List.of(winner, second)).join();

        ArgumentCaptor<GameResultEntity> results = ArgumentCaptor.forClass(GameResultEntity.class);
        verify(resultRepo, org.mockito.Mockito.times(2)).saveResult(results.capture());

        List<GameResultEntity> captured = results.getAllValues();
        assertThat(captured).hasSize(2);
        assertThat(captured).allSatisfy(r -> {
            assertThat(r.getCupName()).isEqualTo("Test Cup");
            assertThat(r.getMapName()).isEqualTo("Test Map");
            assertThat(r.getPlayedAt()).isNotNull();
        });
        // Winner gets placement 1 with the position bonus from ScoreComponent.
        var winnerResult = captured.stream().filter(r -> r.getPlacement() == 1).findFirst().orElseThrow();
        assertThat(winnerResult.getRingPoints()).isEqualTo(100);
        assertThat(winnerResult.getPositionBonus()).isEqualTo(50);
        assertThat(winnerResult.getTotalPoints()).isEqualTo(150);
    }

    @Test
    void updatesExistingPlayerAndIncrementsWinOnFirstPlace() {
        UUID playerId = UUID.randomUUID();
        Entity entity = playerEntity(playerId, "Alice", 40, 50);

        ElytraPlayerEntity existing = new ElytraPlayerEntity(playerId);
        existing.setLastKnownName("OldName");
        existing.setTotalGamesPlayed(5);
        existing.setTotalWins(2);
        existing.setTotalRingsPassed(100);

        when(playerRepo.getElytraPlayerById(playerId)).thenReturn(CompletableFuture.completedFuture(existing));
        when(playerRepo.updateElytraPlayer(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(resultRepo.saveResult(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.persistResults("Cup", "Map", List.of(entity)).join();

        ArgumentCaptor<ElytraPlayerEntity> captor = ArgumentCaptor.forClass(ElytraPlayerEntity.class);
        verify(playerRepo).updateElytraPlayer(captor.capture());
        verify(playerRepo, never()).saveElytraPlayer(any());

        ElytraPlayerEntity updated = captor.getValue();
        assertThat(updated.getLastKnownName()).isEqualTo("Alice");
        assertThat(updated.getTotalGamesPlayed()).isEqualTo(6);
        assertThat(updated.getTotalWins()).isEqualTo(3);
        assertThat(updated.getTotalRingsPassed()).isEqualTo(140);
    }

    @Test
    void doesNotIncrementWinsForNonFirstPlacement() {
        UUID playerId = UUID.randomUUID();
        Entity entity = playerEntity(playerId, "Runner", 20, 30);

        ElytraPlayerEntity existing = new ElytraPlayerEntity(playerId);
        existing.setTotalWins(1);

        when(playerRepo.getElytraPlayerById(playerId)).thenReturn(CompletableFuture.completedFuture(existing));
        when(playerRepo.updateElytraPlayer(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(resultRepo.saveResult(any())).thenReturn(CompletableFuture.completedFuture(null));

        // Two entries so this player is ranked 2nd, not 1st.
        Entity first = playerEntity(UUID.randomUUID(), "Winner", 100, 50);
        when(playerRepo.getElytraPlayerById(first.getComponent(PlayerRefComponent.class).getPlayerId()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(playerRepo.saveElytraPlayer(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.persistResults("Cup", "Map", List.of(first, entity)).join();

        ArgumentCaptor<ElytraPlayerEntity> captor = ArgumentCaptor.forClass(ElytraPlayerEntity.class);
        verify(playerRepo).updateElytraPlayer(captor.capture());
        assertThat(captor.getValue().getTotalWins()).isEqualTo(1); // unchanged
    }

    @Test
    void swallowsRepositoryFailures() {
        UUID playerId = UUID.randomUUID();
        Entity entity = playerEntity(playerId, "Alice", 10, 0);

        when(playerRepo.getElytraPlayerById(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("DB down")));

        CompletableFuture<Void> future = service.persistResults("Cup", "Map", List.of(entity));

        // The future completes normally — the race must never crash because persistence failed.
        assertThat(future).succeedsWithin(java.time.Duration.ofSeconds(1));
        assertThat(future.isCompletedExceptionally()).isFalse();
    }

    @Test
    void emptyRankedPlayersIsNoop() {
        CompletableFuture<Void> future = service.persistResults("Cup", "Map", List.of());
        assertThat(future).isCompleted();
        verify(playerRepo, never()).getElytraPlayerById(any());
        verify(resultRepo, never()).saveResult(any());
    }

    private static Entity playerEntity(UUID playerId, String username, int ringPoints, int positionBonus) {
        Player player = mock(Player.class);
        when(player.getUuid()).thenReturn(playerId);
        when(player.getUsername()).thenReturn(username);

        Entity entity = new Entity();
        entity.addComponent(new PlayerRefComponent(playerId, player));
        ScoreComponent score = new ScoreComponent();
        score.addRingPoints(ringPoints);
        score.setPositionBonus(positionBonus);
        entity.addComponent(score);
        return entity;
    }
}
