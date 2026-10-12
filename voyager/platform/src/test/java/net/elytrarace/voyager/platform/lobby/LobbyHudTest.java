package net.elytrarace.voyager.platform.lobby;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lobby's boss bar: shown while a racer stands in the lobby instance, updated only when its text or its progress
 * changes, and hidden when the racer leaves the lobby. Each test builds its own instances, so none depends on another.
 *
 * <p>{@link LobbyHud#update} answers whether it changed what the racer sees. That answer is the seam these tests read,
 * because a boss bar leaves no trace a test can read back: Adventure addresses it to a viewer and Minestom turns it into
 * a packet.
 */
@EnvTest
class LobbyHudTest {

    private static final Component WAITING = Component.text("Waiting for racers: 1 of 2 online");
    private static final Component COUNTDOWN = Component.text("Cup starts in 10 s");

    @Test
    void theBossBarShowsOnlyInTheLobbyInstance(Env env) {
        Instance lobby = env.createFlatInstance();
        Instance firstMap = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbyHud hud = new LobbyHud();

        hud.update(racer, lobby, WAITING, 1.0f);
        assertThat(hud.barOf(racer.getUuid())).isPresent();

        racer.setInstance(firstMap, new Pos(0, 60, 0)).join();
        hud.update(racer, lobby, WAITING, 1.0f);
        assertThat(hud.barOf(racer.getUuid())).isEmpty();
    }

    @Test
    void anUnchangedTitleAndProgressSendNoUpdate(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbyHud hud = new LobbyHud();
        hud.update(racer, lobby, WAITING, 1.0f);

        assertThat(hud.update(racer, lobby, WAITING, 1.0f)).isFalse();
    }

    @Test
    void aChangedProgressSendsAnUpdate(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbyHud hud = new LobbyHud();
        hud.update(racer, lobby, COUNTDOWN, 1.0f);

        assertThat(hud.update(racer, lobby, COUNTDOWN, 0.5f)).isTrue();
    }

    @Test
    void theBarIsHiddenWhenTheRacerLeavesTheLobby(Env env) {
        Instance lobby = env.createFlatInstance();
        Instance firstMap = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbyHud hud = new LobbyHud();
        hud.update(racer, lobby, COUNTDOWN, 1.0f);

        racer.setInstance(firstMap, new Pos(0, 60, 0)).join();
        hud.update(racer, lobby, COUNTDOWN, 1.0f);

        assertThat(hud.barOf(racer.getUuid())).isEmpty();
    }
}
