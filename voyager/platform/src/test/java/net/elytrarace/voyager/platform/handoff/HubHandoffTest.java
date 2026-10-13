package net.elytrarace.voyager.platform.handoff;

import net.elytrarace.voyager.api.handoff.PlayerHandoff;
import net.elytrarace.voyager.api.handoff.exception.HandoffUnavailableException;
import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hub move for each racer: through the installed handoff when there is one, and a disconnect with the translated message
 * when there is none or the handoff refuses. The handoff, the disconnect and the warning are injected, so no test touches the
 * process-wide slot in {@code PlayerHandoffs}, and each test builds its own racers.
 */
@EnvTest
class HubHandoffTest {

    @Test
    void anInstalledHandoffReceivesEveryRacer(Env env) {
        Instance instance = env.createFlatInstance();
        Player first = env.createPlayer(instance, new Pos(0, 60, 0));
        Player second = env.createPlayer(instance, new Pos(0, 60, 0));
        List<UUID> sent = new ArrayList<>();
        List<Player> kicked = new ArrayList<>();
        HubHandoff hub = new HubHandoff(() -> Optional.of(sent::add), (racer, reason) -> kicked.add(racer), warnings());

        hub.send(List.of(first, second));

        assertThat(sent).containsExactly(first.getUuid(), second.getUuid());
        assertThat(kicked).isEmpty();
    }

    @Test
    void withoutAnInstalledHandoffEachRacerIsDisconnectedWithTheMessage(Env env) {
        Instance instance = env.createFlatInstance();
        Player racer = env.createPlayer(instance, new Pos(0, 60, 0));
        List<Component> reasons = new ArrayList<>();
        List<Player> kicked = new ArrayList<>();
        HubHandoff hub = new HubHandoff(Optional::empty, (player, reason) -> {
            kicked.add(player);
            reasons.add(reason);
        }, warnings());

        hub.send(List.of(racer));

        assertThat(kicked).containsExactly(racer);
        assertThat(reasons).containsExactly(Messages.handoffDisconnect());
    }

    @Test
    void aRefusedHandoffDisconnectsWithTheSameMessageAndWarnsNamingTheRacer(Env env) {
        Instance instance = env.createFlatInstance();
        Player racer = env.createPlayer(instance, new Pos(0, 60, 0));
        PlayerHandoff refusing = playerId -> {
            throw HandoffUnavailableException.refused(playerId, null);
        };
        List<Component> reasons = new ArrayList<>();
        List<String> logged = new ArrayList<>();
        HubHandoff hub = new HubHandoff(() -> Optional.of(refusing), (player, reason) -> reasons.add(reason),
                logged::add);

        hub.send(List.of(racer));

        assertThat(reasons).containsExactly(Messages.handoffDisconnect());
        assertThat(logged)
                .as("a refusal is logged once, and the line names the racer")
                .singleElement()
                .asString()
                .contains(racer.getUsername());
    }

    private static HubHandoff.Warnings warnings() {
        return message -> {
            throw new AssertionError("no warning was expected, but got: " + message);
        };
    }
}
