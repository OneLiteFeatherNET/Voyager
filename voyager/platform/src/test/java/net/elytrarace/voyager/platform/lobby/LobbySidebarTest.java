package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.text.Messages;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
    private static final LobbyStatus WAITING = new LobbyStatus(3, 16, null);

    @Test
    void theSidebarShowsWhileTheRoomWaits(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbySidebar sidebar = new LobbySidebar();

        sidebar.update(racer, lobby, BOARD, WAITING, true);

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
        sidebar.update(racer, lobby, BOARD, WAITING, true);

        racer.setInstance(firstMap, new Pos(0, 60, 0)).join();
        sidebar.update(racer, lobby, BOARD, WAITING, true);

        assertThat(sidebar.sidebarOf(racer.getUuid())).isEmpty();
    }

    @Test
    void noSidebarIsShownWhileACupRaces(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbySidebar sidebar = new LobbySidebar();

        sidebar.update(racer, lobby, BOARD, WAITING, false);

        assertThat(sidebar.sidebarOf(racer.getUuid())).isEmpty();
    }

    @Test
    void aSidebarOfFifteenMapsHasExactlyFifteenLines(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbySidebar sidebar = new LobbySidebar();
        LobbyBoard fifteen = new LobbyBoard("Long Tour", names(15));

        sidebar.update(racer, lobby, fifteen, new LobbyStatus(3, 16, Duration.ofSeconds(12)), true);

        assertThat(sidebar.sidebarOf(racer.getUuid()).orElseThrow().getLines())
                .as("the client shows at most fifteen lines, so the sidebar holds exactly fifteen")
                .hasSize(15);
    }

    @Test
    void everySidebarLineUsesTheBlankNumberFormat() {
        assertThat(LobbySidebar.NUMBER_FORMAT)
                .as("the blank format hides the score column, so no map line reads as a rank")
                .isEqualTo(Sidebar.NumberFormat.blank());
    }

    @Test
    void theLinesAreRacersThenTheMapsThenStatusAndFooter() {
        List<LobbySidebar.Line> lines = LobbySidebar.lines(BOARD, WAITING);

        assertThat(lines.stream().map(LobbySidebar.Line::id).toList())
                .containsExactly("racers", "blank-top", "map-0", "map-1", "map-2", "blank-bottom", "status", "footer");
    }

    @Test
    void theStatusLineReadsWaitingWithoutACountdown() {
        LobbySidebar.Line status = line("status", LobbySidebar.lines(BOARD, WAITING));

        assertThat(status.content()).isEqualTo(Messages.lobbySidebarStatusWaiting());
    }

    @Test
    void theStatusLineReadsTheSecondsToTheStartDuringACountdown() {
        LobbyStatus counting = new LobbyStatus(3, 16, Duration.ofSeconds(12));

        LobbySidebar.Line status = line("status", LobbySidebar.lines(BOARD, counting));

        assertThat(status.content()).isEqualTo(Messages.lobbySidebarStatusCountdown(Duration.ofSeconds(12)));
    }

    @Test
    void theRacersLineCountsOnlineRacersAgainstTheMaximum() {
        LobbySidebar.Line racers = line("racers", LobbySidebar.lines(BOARD, WAITING));

        assertThat(racers.content()).isEqualTo(Messages.lobbySidebarRacers(3, 16));
    }

    private static LobbySidebar.Line line(String id, List<LobbySidebar.Line> lines) {
        return lines.stream().filter(line -> line.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<String> names(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> "map-" + index).toList();
    }
}
