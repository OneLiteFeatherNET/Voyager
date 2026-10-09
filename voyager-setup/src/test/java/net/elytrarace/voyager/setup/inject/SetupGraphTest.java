package net.elytrarace.voyager.setup.inject;

import io.avaje.inject.BeanScope;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.setup.SetupServer;
import net.elytrarace.voyager.setup.config.SetupSettings;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The object graph of the setup server, built per test over a temporary data directory. */
@EnvTest
class SetupGraphTest {

    @TempDir
    Path root;

    @Test
    void theGraphResolvesTheDraftStoreOverTheConfiguredDirectories(Env env) throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        SetupSettings settings = new SetupSettings("127.0.0.1", 25566, data, worlds);

        try (BeanScope graph = SetupServer.openGraph(settings)) {
            assertThat(graph.get(DraftStore.class)).isInstanceOf(JsonDraftStore.class);
        }
    }
}
