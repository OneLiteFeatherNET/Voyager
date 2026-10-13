package net.elytrarace.voyager.cloudnet.bridge;

import net.elytrarace.voyager.api.handoff.exception.HandoffUnavailableException;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The CloudNet handoff, driven through its fallback seam. The seam stands in for CloudNet's player executor, so no CloudNet
 * type is on this test's class path: the handoff's own logic is what these tests read.
 */
class CloudNetPlayerHandoffTest {

    private static final UUID RACER = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    @Test
    void aHandoffCallsTheConnectorWithThePlayersId() {
        List<UUID> asked = new ArrayList<>();
        CloudNetPlayerHandoff handoff = new CloudNetPlayerHandoff(asked::add);

        handoff.toHub(RACER);

        assertThat(asked).containsExactly(RACER);
    }

    @Test
    void aHandoffAsksTheFallbackOnceAndNothingElse() {
        List<UUID> asked = new ArrayList<>();
        CloudNetPlayerHandoff handoff = new CloudNetPlayerHandoff(asked::add);

        handoff.toHub(RACER);

        assertThat(asked).as("the handoff makes one CloudNet call and does not kick the player").hasSize(1);
    }

    @Test
    void aConnectorFailureBecomesAHandoffUnavailableException() {
        IllegalStateException failure = new IllegalStateException("no fallback is configured");
        CloudNetPlayerHandoff handoff = new CloudNetPlayerHandoff(playerId -> {
            throw failure;
        });

        assertThatThrownBy(() -> handoff.toHub(RACER))
                .isInstanceOf(HandoffUnavailableException.class)
                .hasMessageContaining(RACER.toString())
                .hasCause(failure);
    }
}
