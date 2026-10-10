package net.elytrarace.voyager.server;

import net.elytrarace.voyager.platform.cup.TickPipeline;
import net.elytrarace.voyager.platform.cup.TickStep;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import io.avaje.inject.BeanScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the cup's per-tick order, which the waiting room relies on: the room runs after the pipeline, and the
 * pipeline must keep exactly its three steps in this order. A fourth step would move the room's gate into the
 * golden transcripts' tick.
 */
@EnvTest
class TickPipelineOrderTest {

    @TempDir
    Path tempDir;

    @Test
    void pipelineHasTheThreeStepsInTheirOrder(Env env) throws IOException {
        Path data = tempDir.resolve("data");
        Path worlds = tempDir.resolve("worlds");
        Files.createDirectories(worlds);
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        ServerSettings settings = new ServerSettings("127.0.0.1", 25571, data, worlds, Optional.of("alpha_cup"), false);

        try (BeanScope scope = VoyagerServer.openGraph(settings)) {
            List<String> names = scope.get(TickPipeline.class).steps().stream().map(TickStep::name).toList();

            assertThat(names).containsExactly("flight sample", "boost burn", "phase advance");
        }
    }
}
