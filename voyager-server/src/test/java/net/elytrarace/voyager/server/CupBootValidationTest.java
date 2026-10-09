package net.elytrarace.voyager.server;

import io.avaje.inject.BeanScope;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.elytrarace.voyager.server.inject.ServerBeans;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What boot does with the cups it does not play, and with the one it does.
 *
 * <p>The played cup is {@code test_cup}, which names the shipped map. Every other cup in a test is
 * unplayed and carries the problem the test is about. Every test writes its own data directory in its
 * own {@code @TempDir}, and the warning is read from a log capture that exists for that test only.
 */
@EnvTest
class CupBootValidationTest {

    @TempDir
    Path tempDir;

    @Test
    void bootWarnsOnceWithEveryUnplayableCup(Env env) throws IOException {
        Path data = dataDirectory();
        write(data.resolve("cups/a-broken.json"), "");
        write(data.resolve("cups/other.json"), cupNaming("other_cup", "no-such-map"));
        write(data.resolve("cups/test_cup.json"), PLAYED_CUP);

        try (LogCapture log = LogCapture.of(ServerBeans.class)) {
            assertThatCode(() -> VoyagerServer.openGraph(settings(data))).doesNotThrowAnyException();

            assertThat(log.warnings()).hasSize(1);
            assertThat(log.warnings().getFirst())
                    .contains("a-broken.json")
                    .contains("cup 'other_cup' plays 'no-such-map'");
        }
    }

    @Test
    void bootIsSilentWhenEveryCupIsConsistent(Env env) throws IOException {
        Path data = dataDirectory();
        write(data.resolve("cups/test_cup.json"), PLAYED_CUP);

        try (LogCapture log = LogCapture.of(ServerBeans.class)) {
            VoyagerServer.openGraph(settings(data));

            assertThat(log.warnings()).isEmpty();
        }
    }

    @Test
    void aBrokenUnselectedCupDoesNotRefuseBoot(Env env) throws IOException {
        Path data = dataDirectory();
        write(data.resolve("cups/a-broken.json"), "");
        write(data.resolve("cups/test_cup.json"), PLAYED_CUP);

        BeanScope graph = VoyagerServer.openGraph(settings(data));

        assertThat(graph.get(CupDefinition.class).name()).isEqualTo("test_cup");
    }

    @Test
    void aBrokenSelectedCupRefusesBootWithItsEntriesOnly(Env env) throws IOException {
        Path data = dataDirectory();
        write(data.resolve("cups/test_cup.json"), cupNaming("test_cup", "no-such-map-a", "no-such-map-b"));
        write(data.resolve("cups/other.json"), cupNaming("other_cup", "elsewhere-missing"));

        assertThatThrownBy(() -> VoyagerServer.openGraph(settings(data)))
                .satisfies(refusal -> assertThat(causeOf(refusal, UnresolvedCupMapException.class))
                        .hasMessage("2 cup entries do name a map no map definition provides: "
                                + "cup 'test_cup' plays 'no-such-map-a'; cup 'test_cup' plays 'no-such-map-b'")
                        .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("other_cup")));
    }

    @Test
    void onlyTheSelectedCupIsPlayed(Env env) throws IOException {
        // other.json sorts before test_cup.json, so a boot that took the first cup it read would play
        // other_cup, which names a map nothing provides.
        Path data = dataDirectory();
        write(data.resolve("cups/other.json"), cupNaming("other_cup", "no-such-map"));
        write(data.resolve("cups/test_cup.json"), PLAYED_CUP);

        BeanScope graph = VoyagerServer.openGraph(settings(data));

        CupDefinition played = graph.get(CupDefinition.class);
        assertThat(played.name()).isEqualTo("test_cup");
        assertThat(played.mapNames()).containsExactly("elytraraceblueandred");
    }

    /**
     * The data directory with the shipped map in it. Each test adds the cups it is about, so the map
     * the played cup names is always there.
     */
    private Path dataDirectory() throws IOException {
        Path data = tempDir.resolve("data");
        ShippedCatalogue.copyMapsInto(data);
        return data;
    }

    private ServerSettings settings(Path data) throws IOException {
        Path worlds = tempDir.resolve("worlds");
        Files.createDirectories(worlds);
        return new ServerSettings("127.0.0.1", 25572, data, worlds, Optional.of("test_cup"), false);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static <T extends Throwable> T causeOf(Throwable refusal, Class<T> type) {
        for (Throwable cause = refusal; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }

    private static String cupNaming(String name, String... mapNames) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < mapNames.length; i++) {
            names.append(i == 0 ? "" : ", ").append('"').append(mapNames[i]).append('"');
        }
        return "{\"name\": \"%s\", \"mode\": \"RACE\", \"mapNames\": [%s]}".formatted(name, names);
    }

    private static final String PLAYED_CUP = """
            {"name": "test_cup", "mode": "RACE", "mapNames": ["elytraraceblueandred"]}
            """;
}
