package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lobby HUD of a waiting racer: the action bar, and the experience bar that counts the start down. The HUD is shown only
 * while the racer stands in the lobby instance, and it is cleared when the racer leaves it. Each test builds its own
 * instances and racers.
 */
@EnvTest
class LobbyHudTest {

    @Test
    void oneRacerShortOfTwoShowsTheSingularMissingCount(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbyHud hud = new LobbyHud();

        hud.update(racer, lobby, Messages.lobbyActionBarWaiting(1), 0, 0.0f);

        assertThat(keyOf(hud.actionBarOf(racer.getUuid()).orElseThrow()))
                .as("one racer short uses the singular key, so the line reads 'Need 1 more player'")
                .isEqualTo("voyager.lobby.actionbar.waiting.one");
    }

    @Test
    void theExperienceLevelIsTheWholeSecondsLeftAndTheBarIsTheFractionLeft(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbyHud hud = new LobbyHud();

        hud.update(racer, lobby, Messages.lobbyActionBarCountdown(Duration.ofSeconds(10)), 10, 0.5f);

        assertThat(racer.getLevel()).as("ten seconds left reads as level 10").isEqualTo(10);
        assertThat(racer.getExp()).as("half of a twenty second lobby is half the bar").isEqualTo(0.5f);
    }

    @Test
    void aCancelledCountdownClearsTheLevelAndTheBar(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbyHud hud = new LobbyHud();
        hud.update(racer, lobby, Messages.lobbyActionBarCountdown(Duration.ofSeconds(11)), 11, 0.55f);

        hud.hide(racer);

        assertThat(racer.getLevel()).as("the level goes back to zero").isZero();
        assertThat(racer.getExp()).as("the bar is empty").isZero();
        assertThat(hud.actionBarOf(racer.getUuid())).isEmpty();
    }

    @Test
    void anUnchangedStateSendsNoUpdate(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbyHud hud = new LobbyHud();
        hud.update(racer, lobby, Messages.lobbyActionBarWaiting(1), 0, 0.0f);

        assertThat(hud.update(racer, lobby, Messages.lobbyActionBarWaiting(1), 0, 0.0f))
                .as("the same line and the same bar are not sent again")
                .isFalse();
    }

    @Test
    void theHudIsHiddenWhenTheRacerLeavesTheLobbyInstance(Env env) {
        Instance lobby = env.createFlatInstance();
        Instance firstMap = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbyHud hud = new LobbyHud();
        hud.update(racer, lobby, Messages.lobbyActionBarWaiting(1), 0, 0.0f);

        racer.setInstance(firstMap, new Pos(0, 60, 0)).join();
        hud.update(racer, lobby, Messages.lobbyActionBarWaiting(1), 0, 0.0f);

        assertThat(hud.actionBarOf(racer.getUuid())).isEmpty();
    }

    private static String keyOf(Component component) {
        return ((TranslatableComponent) component).key();
    }
}
