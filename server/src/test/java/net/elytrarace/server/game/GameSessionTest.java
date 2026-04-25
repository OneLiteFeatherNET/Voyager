package net.elytrarace.server.game;

import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.cup.CupFlowServiceImpl;
import net.elytrarace.server.scoring.ScoringServiceImpl;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GameSessionTest {

    private GameSession session;

    @BeforeEach
    void setUp() {
        session = new GameSession(
                UUID.randomUUID(),
                new CupDefinition("Test Cup", GameMode.RACE, List.of()),
                new CupFlowServiceImpl(),
                new ScoringServiceImpl()
        );
    }

    @Test
    void addAndRemovePlayer() {
        UUID player = UUID.randomUUID();

        session.addPlayer(player);

        assertThat(session.getPlayers()).containsExactly(player);

        session.removePlayer(player);

        assertThat(session.getPlayers()).isEmpty();
    }

    @Test
    void removePlayerClearsVelocity() {
        UUID player = UUID.randomUUID();
        session.addPlayer(player);
        session.setVelocity(player, new Vec(1, 2, 3));

        session.removePlayer(player);

        assertThat(session.getVelocity(player)).isEqualTo(Vec.ZERO);
    }

    @Test
    void velocityDefaultsToZero() {
        UUID player = UUID.randomUUID();

        assertThat(session.getVelocity(player)).isEqualTo(Vec.ZERO);
    }

    @Test
    void velocityIsStoredPerPlayer() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        Vec vel1 = new Vec(1, 0, 0);
        Vec vel2 = new Vec(0, 1, 0);

        session.setVelocity(player1, vel1);
        session.setVelocity(player2, vel2);

        assertThat(session.getVelocity(player1)).isEqualTo(vel1);
        assertThat(session.getVelocity(player2)).isEqualTo(vel2);
    }

    @Test
    void passedRingsAreTrackedPerPlayerAndRing() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        assertThat(session.hasPassedRing(player1, 0)).isFalse();

        session.markRingPassed(player1, 0);

        assertThat(session.hasPassedRing(player1, 0)).isTrue();
        assertThat(session.hasPassedRing(player1, 1)).isFalse();
        assertThat(session.hasPassedRing(player2, 0)).isFalse();
    }

    @Test
    void resetPassedRingsClearsAll() {
        UUID player = UUID.randomUUID();
        session.markRingPassed(player, 0);
        session.markRingPassed(player, 1);

        session.resetPassedRings();

        assertThat(session.hasPassedRing(player, 0)).isFalse();
        assertThat(session.hasPassedRing(player, 1)).isFalse();
    }

    @Test
    void playersSetIsUnmodifiable() {
        UUID player = UUID.randomUUID();
        session.addPlayer(player);

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> session.getPlayers().add(UUID.randomUUID())
        );
    }
}
