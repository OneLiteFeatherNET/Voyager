package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one text the lobby displays are built from: the cup's name, and its maps in play order with the sidebar's limit. The
 * board is a value, so each test builds its own.
 */
class LobbyBoardTest {

    private static final CatalogSnapshot NO_MAPS = new CatalogSnapshot(Map.of(), Map.of());

    @Test
    void theBoardTitlesTheCupByItsDisplayName() {
        LobbyBoard board = LobbyBoard.of(new CupDefinition("alpha_cup", List.of("alpha"), GameMode.RACE, "Alpha Cup"),
                NO_MAPS);

        assertThat(PlainTextComponentSerializer.plainText().serialize(board.title())).isEqualTo("Alpha Cup");
    }

    @Test
    void theBoardListsEachMapByItsDisplayNameWhenTheSnapshotHoldsIt() {
        CatalogSnapshot snapshot = new CatalogSnapshot(
                Map.of("elytraraceblueandred", namedMap("elytraraceblueandred", "Blue & Red")), Map.of());

        LobbyBoard board = LobbyBoard.of(
                new CupDefinition("alpha_cup", List.of("elytraraceblueandred"), GameMode.RACE, "Alpha Cup"), snapshot);

        assertThat(plain(board.lines())).containsExactly("1. Blue & Red");
    }

    @Test
    void aMapTheSnapshotDoesNotHoldIsListedByItsId() {
        LobbyBoard board = LobbyBoard.of(
                new CupDefinition("alpha_cup", List.of("elytraraceblueandred"), GameMode.RACE, "Alpha Cup"), NO_MAPS);

        assertThat(plain(board.lines())).containsExactly("1. elytraraceblueandred");
    }

    private static MapDefinition namedMap(String id, String displayName) {
        return new MapDefinition(id, id + "-world", Vec3.ZERO,
                List.of(new Ring(0, Vec3.ZERO, new Vec3(0, 1, 0), 3, 10, RingType.STANDARD)),
                Duration.ofSeconds(60), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0),
                RingEffectTuning.DEFAULT, displayName);
    }

    @Test
    void theBoardListsTheMapsInPlayOrder() {
        LobbyBoard board = LobbyBoard.of(cup("Grand Tour", "alpha", "bravo", "charlie"), NO_MAPS);

        assertThat(plain(board.lines())).containsExactly("1. alpha", "2. bravo", "3. charlie");
    }

    @Test
    void theBoardNamesTheCup() {
        LobbyBoard board = LobbyBoard.of(cup("Grand Tour", "alpha"), NO_MAPS);

        assertThat(board.cupName()).isEqualTo("Grand Tour");
        assertThat(PlainTextComponentSerializer.plainText().serialize(board.title())).isEqualTo("Grand Tour");
    }

    @Test
    void aCupOfFifteenMapsShowsTwelveAndAPlusThreeMoreLine() {
        LobbyBoard board = LobbyBoard.of(cup("Long Tour", names(15)), NO_MAPS);

        List<Component> lines = board.lines();

        assertThat(lines).hasSize(LobbyBoard.MAX_MAP_LINES + 1);
        assertThat(plain(lines.subList(0, LobbyBoard.MAX_MAP_LINES)).getLast()).isEqualTo("12. map-12");
        TranslatableComponent more = (TranslatableComponent) lines.getLast();
        assertThat(more.key()).isEqualTo("voyager.lobby.board.more");
        assertThat(more.arguments()).hasSize(1);
        assertThat(PlainTextComponentSerializer.plainText().serialize(more.arguments().getFirst().asComponent()))
                .isEqualTo("3");
    }

    @Test
    void aCupOfFiveMapsShowsNoMoreLine() {
        LobbyBoard board = LobbyBoard.of(cup("Short Tour", names(5)), NO_MAPS);

        assertThat(board.lines()).hasSize(5);
        assertThat(board.lines())
                .noneMatch(line -> line instanceof TranslatableComponent translatable
                        && translatable.key().equals("voyager.lobby.board.more"));
    }

    private static CupDefinition cup(String name, String... maps) {
        return new CupDefinition(name, List.of(maps), GameMode.RACE);
    }

    private static String[] names(int count) {
        List<String> names = new ArrayList<>();
        for (int map = 1; map <= count; map++) {
            names.add("map-" + map);
        }
        return names.toArray(String[]::new);
    }

    private static List<String> plain(List<Component> lines) {
        return lines.stream().map(line -> PlainTextComponentSerializer.plainText().serialize(line)).toList();
    }
}
