package net.elytrarace.voyager.server;

import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The composition root refuses a graph it cannot build, while the graph is being built.
 *
 * <p>{@code VoyagerServer.main} only reaches the port-binding line after {@code openGraph} returns, so
 * a refusal here is a refusal to listen. Each test writes its own data directory in its own
 * {@code @TempDir}.
 */
@EnvTest
class VoyagerStartupTest {

    @TempDir
    Path tempDir;

    @Test
    void refusesToBuildTheGraphWhenTheCupNamesAMapTheCatalogueDoesNotHold(Env env) throws IOException {
        Path data = tempDir.resolve("data");
        Path worlds = tempDir.resolve("worlds");
        Files.createDirectories(worlds);
        ShippedCatalogue.copyMapsInto(data);
        Files.createDirectories(data.resolve("cups"));
        Files.writeString(data.resolve("cups").resolve("broken_cup.json"), """
                {
                  "name": "broken_cup",
                  "mode": "RACE",
                  "mapNames": [ "no-such-map" ]
                }
                """);
        ServerSettings settings = new ServerSettings("127.0.0.1", 25571, data, worlds,
                Optional.of("broken_cup"), false);

        assertThatThrownBy(() -> VoyagerServer.openGraph(settings))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(UnresolvedCupMapException.class)
                .hasMessageContaining("no-such-map");
    }
}
