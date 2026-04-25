package net.elytrarace.server.game;

import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.cup.CupFlowServiceImpl;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GameSession}.
 *
 * <p>The session is constructed without a {@code ScoringStrategy}: scoring now lives
 * exclusively in ECS systems ({@code CompletionDetectionSystem}, etc.).
 */
class GameSessionTest {

    private GameSession session;

    @BeforeEach
    void setUp() {
        session = new GameSession(
                UUID.randomUUID(),
                new CupDefinition("Test Cup", GameMode.RACE, List.of()),
                new CupFlowServiceImpl()
        );
    }

    @Test
    @DisplayName("Added player appears in the player set")
    void addAndRemovePlayer() {
        // Given
        UUID player = UUID.randomUUID();

        // When
        session.addPlayer(player);

        // Then
        assertThat(session.getPlayers()).containsExactly(player);

        // When
        session.removePlayer(player);

        // Then
        assertThat(session.getPlayers()).isEmpty();
    }

    @Test
    @DisplayName("Removing a player also clears their stored velocity")
    void removePlayerClearsVelocity() {
        // Given
        UUID player = UUID.randomUUID();
        session.addPlayer(player);
        session.setVelocity(player, new Vec(1, 2, 3));

        // When
        session.removePlayer(player);

        // Then
        assertThat(session.getVelocity(player)).isEqualTo(Vec.ZERO);
    }

    @Test
    @DisplayName("Velocity defaults to Vec.ZERO for an unknown player")
    void velocityDefaultsToZero() {
        // Given
        UUID player = UUID.randomUUID();

        // When / Then
        assertThat(session.getVelocity(player)).isEqualTo(Vec.ZERO);
    }

    @Test
    @DisplayName("Velocities are stored and retrieved independently per player")
    void velocityIsStoredPerPlayer() {
        // Given
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        Vec vel1 = new Vec(1, 0, 0);
        Vec vel2 = new Vec(0, 1, 0);

        // When
        session.setVelocity(player1, vel1);
        session.setVelocity(player2, vel2);

        // Then
        assertThat(session.getVelocity(player1)).isEqualTo(vel1);
        assertThat(session.getVelocity(player2)).isEqualTo(vel2);
    }

    @Test
    @DisplayName("Ring passage is tracked per player and per ring index")
    void passedRingsAreTrackedPerPlayerAndRing() {
        // Given
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        assertThat(session.hasPassedRing(player1, 0)).isFalse();

        // When
        session.markRingPassed(player1, 0);

        // Then
        assertThat(session.hasPassedRing(player1, 0)).isTrue();
        assertThat(session.hasPassedRing(player1, 1)).isFalse();
        assertThat(session.hasPassedRing(player2, 0)).isFalse();
    }

    @Test
    @DisplayName("resetPassedRings clears all ring-passage state for all players")
    void resetPassedRingsClearsAll() {
        // Given
        UUID player = UUID.randomUUID();
        session.markRingPassed(player, 0);
        session.markRingPassed(player, 1);

        // When
        session.resetPassedRings();

        // Then
        assertThat(session.hasPassedRing(player, 0)).isFalse();
        assertThat(session.hasPassedRing(player, 1)).isFalse();
    }

    @Test
    @DisplayName("getPlayers() returns an unmodifiable view")
    void playersSetIsUnmodifiable() {
        // Given
        UUID player = UUID.randomUUID();
        session.addPlayer(player);

        // When / Then
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> session.getPlayers().add(UUID.randomUUID())
        );
    }
}
