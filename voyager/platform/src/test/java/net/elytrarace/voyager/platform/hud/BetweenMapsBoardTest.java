package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.standing.MapStanding;
import net.elytrarace.voyager.race.standing.MapStanding.Entry;
import net.elytrarace.voyager.race.standing.MapStanding.Outcome;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.coordinate.Pos;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The standings sidebar a racer reads while the next map is waited for: the order of its lines, its fifteen-line limit
 * and its removal. Each test builds its own board, racers and standing.
 */
@EnvTest
class BetweenMapsBoardTest {

    private static final Duration STEP = Duration.ofMillis(50);

    @Test
    void theSidebarListsTheFinishersRacersAndDnfsInOrderWithComputerRacersAmongThem(Env env) {
        UUID ada = new UUID(0, 1);
        UUID com = new UUID(0, 2);
        UUID ben = new UUID(0, 3);
        UUID cal = new UUID(0, 4);
        MapStanding standing = MapStanding.of(List.of(
                new Entry(cal, false, Outcome.DID_NOT_FINISH, null),
                new Entry(ben, false, Outcome.RACING, null),
                new Entry(com, true, Outcome.FINISHED, new RaceClock(1_240, STEP)),
                new Entry(ada, false, Outcome.FINISHED, new RaceClock(1_224, STEP))));

        List<BetweenMapsBoard.Line> lines = BetweenMapsBoard.lines(standing, namesOf());

        assertThat(lines).hasSize(4);
        assertThat(keyOf(lines.get(0).content())).isEqualTo("voyager.wait.sidebar.finisher");
        assertThat(keyOf(lines.get(1).content())).isEqualTo("voyager.wait.sidebar.finisher");
        assertThat(keyOf(lines.get(2).content())).isEqualTo("voyager.wait.sidebar.racing");
        assertThat(keyOf(lines.get(3).content())).isEqualTo("voyager.wait.sidebar.dnf");
        assertThat(leavesOf(lines.get(1).content()))
                .as("the computer racer is a finisher with its own name, its time of 1:02.0 and its gap to the leader of 0.8 s")
                .containsExactly("2", "COM 1", "1:02.0", "0.8");
    }

    @Test
    void aStandingOfTwentyRacersHoldsFifteenLinesAndEndsWithTheCountOfTheRest(Env env) {
        List<Entry> racers = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            racers.add(new Entry(new UUID(0, index + 1), false, Outcome.FINISHED, new RaceClock(1_200 + index, STEP)));
        }

        List<BetweenMapsBoard.Line> lines = BetweenMapsBoard.lines(MapStanding.of(racers), namesOf());

        assertThat(lines).as("at most fifteen lines, the last of them the count of racers not shown").hasSize(15);
        assertThat(keyOf(lines.get(14).content())).isEqualTo("voyager.wait.sidebar.more");
        assertThat(leavesOf(lines.get(14).content()))
                .as("fourteen racers are shown, so six of the twenty are not, and the last line says so")
                .containsExactly("6");
    }

    @Test
    void theSidebarIsShownToARacerAndRemovedWhenTheRacerLeaves(Env env) {
        Instance world = env.createFlatInstance();
        Player racer = env.createPlayer(world, new Pos(0, 60, 0));
        BetweenMapsBoard board = new BetweenMapsBoard();

        board.update(racer, BetweenMapsBoard.lines(MapStanding.of(List.of()), namesOf()));
        assertThat(board.sidebarOf(racer.getUuid()))
                .as("the sidebar is shown while the racer waits")
                .hasValueSatisfying(sidebar -> assertThat(sidebar.getViewers().contains(racer)).isTrue());

        board.hide(racer);
        assertThat(board.sidebarOf(racer.getUuid()))
                .as("and it is removed when the next map starts")
                .isEmpty();
    }

    /** Computer racer number 2 is shown as {@code COM 1}, the first seat; every other id is a human named by its number. */
    private static Function<UUID, Component> namesOf() {
        return id -> id.getLeastSignificantBits() == 2
                ? Component.text("COM 1")
                : Component.text("Racer " + id.getLeastSignificantBits());
    }

    private static String keyOf(Component component) {
        assertThat(component).isInstanceOf(TranslatableComponent.class);
        return ((TranslatableComponent) component).key();
    }

    /** The plain text of every text leaf in a line, in order, looking inside the translations it is built from. */
    private static List<String> leavesOf(Component component) {
        List<String> leaves = new ArrayList<>();
        if (component instanceof TranslatableComponent translatable) {
            for (var argument : translatable.arguments()) {
                leaves.addAll(leavesOf((Component) argument.value()));
            }
        } else if (component instanceof net.kyori.adventure.text.TextComponent text) {
            leaves.add(text.content());
        }
        return leaves;
    }
}
