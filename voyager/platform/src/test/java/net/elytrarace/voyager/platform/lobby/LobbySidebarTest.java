package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lobby sidebar: one per racer, shown while the room waits in the place racers wait, removed when the racer leaves that
 * place for a map, and never shown while a cup races. Each test builds its own instances and racers.
 */
@EnvTest
class LobbySidebarTest {

    private static final LobbyBoard BOARD = LobbyBoard.of(
            new CupDefinition("Grand Tour", List.of("alpha", "bravo", "charlie"), GameMode.RACE),
            new CatalogSnapshot(Map.of(), Map.of()));

    @Test
    void theSidebarShowsWhileTheRoomWaits(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbySidebar sidebar = new LobbySidebar();

        sidebar.update(racer, lobby, BOARD, true);

        Sidebar shown = sidebar.sidebarOf(racer.getUuid()).orElseThrow();
        assertThat(shown.getViewers().contains(racer))
                .as("the racer is a viewer of the sidebar while the room waits")
                .isTrue();
    }

    @Test
    void theSidebarIsRemovedWhenARacerEntersAMap(Env env) {
        Instance lobby = env.createFlatInstance();
        Instance firstMap = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbySidebar sidebar = new LobbySidebar();
        sidebar.update(racer, lobby, BOARD, true);

        racer.setInstance(firstMap, new Pos(0, 60, 0)).join();
        sidebar.update(racer, lobby, BOARD, true);

        assertThat(sidebar.sidebarOf(racer.getUuid())).isEmpty();
    }

    @Test
    void noSidebarIsShownWhileACupRaces(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbySidebar sidebar = new LobbySidebar();

        sidebar.update(racer, lobby, BOARD, false);

        assertThat(sidebar.sidebarOf(racer.getUuid())).isEmpty();
    }
}
