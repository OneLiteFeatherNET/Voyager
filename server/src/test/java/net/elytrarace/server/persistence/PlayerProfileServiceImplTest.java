package net.elytrarace.server.persistence;

import net.elytrarace.api.database.entity.ElytraPlayerEntity;
import net.elytrarace.api.database.repository.ElytraPlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlayerProfileServiceImpl} covering the two join
 * scenarios — first-time join (insert) and re-join (update) — plus rename
 * handling and DB failure resilience.
 */
class PlayerProfileServiceImplTest {

    private ElytraPlayerRepository repository;
    private PlayerProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(ElytraPlayerRepository.class);
        service = new PlayerProfileServiceImpl(repository);
    }

    @Test
    void firstJoinCreatesAndPersistsNewProfile() {
        UUID playerId = UUID.randomUUID();
        when(repository.getElytraPlayerById(playerId))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(repository.saveElytraPlayer(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        ElytraPlayerEntity result = service.onPlayerJoin(playerId, "Alice").join();

        assertThat(result).isNotNull();
        assertThat(result.getPlayerId()).isEqualTo(playerId);
        assertThat(result.getLastKnownName()).isEqualTo("Alice");
        assertThat(result.getLastPlayed()).isNotNull();
        assertThat(result.getTotalGamesPlayed()).isZero();
        assertThat(result.getTotalWins()).isZero();
        assertThat(result.getTotalRingsPassed()).isZero();

        verify(repository).saveElytraPlayer(result);
        verify(repository, never()).updateElytraPlayer(any());
    }

    @Test
    void returningPlayerTriggersUpdateAndRefreshesTimestamp() {
        UUID playerId = UUID.randomUUID();
        ElytraPlayerEntity existing = new ElytraPlayerEntity(playerId);
        existing.setLastKnownName("Alice");
        existing.setTotalGamesPlayed(5);
        existing.setLastPlayed(null);

        when(repository.getElytraPlayerById(playerId))
                .thenReturn(CompletableFuture.completedFuture(existing));
        when(repository.updateElytraPlayer(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        ElytraPlayerEntity result = service.onPlayerJoin(playerId, "Alice").join();

        assertThat(result).isSameAs(existing);
        assertThat(result.getLastPlayed()).isNotNull();
        assertThat(result.getTotalGamesPlayed()).isEqualTo(5); // preserved
        verify(repository).updateElytraPlayer(existing);
        verify(repository, never()).saveElytraPlayer(any());
    }

    @Test
    void renameUpdatesLastKnownName() {
        UUID playerId = UUID.randomUUID();
        ElytraPlayerEntity existing = new ElytraPlayerEntity(playerId);
        existing.setLastKnownName("OldName");

        when(repository.getElytraPlayerById(playerId))
                .thenReturn(CompletableFuture.completedFuture(existing));
        when(repository.updateElytraPlayer(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        ElytraPlayerEntity result = service.onPlayerJoin(playerId, "NewName").join();

        assertThat(result.getLastKnownName()).isEqualTo("NewName");
        ArgumentCaptor<ElytraPlayerEntity> captor = ArgumentCaptor.forClass(ElytraPlayerEntity.class);
        verify(repository).updateElytraPlayer(captor.capture());
        assertThat(captor.getValue().getLastKnownName()).isEqualTo("NewName");
    }

    @Test
    void databaseFailureResolvesToNullWithoutThrowing() {
        UUID playerId = UUID.randomUUID();
        when(repository.getElytraPlayerById(playerId))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("connection refused")));

        CompletableFuture<ElytraPlayerEntity> future = service.onPlayerJoin(playerId, "Alice");

        assertThat(future).succeedsWithin(Duration.ofSeconds(1));
        assertThat(future.join()).isNull();
        verify(repository, never()).saveElytraPlayer(any());
        verify(repository, never()).updateElytraPlayer(any());
    }
}
