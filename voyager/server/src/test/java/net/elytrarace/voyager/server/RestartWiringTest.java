package net.elytrarace.voyager.server;

import io.avaje.inject.BeanScope;

import net.elytrarace.voyager.platform.handoff.CupRestart;
import net.elytrarace.voyager.platform.lobby.WaitingRoom;
import net.elytrarace.voyager.platform.rotation.RotationPlan;
import net.elytrarace.voyager.race.rotation.CupRotationStore;
import net.elytrarace.voyager.server.config.PersistenceSettings;
import net.elytrarace.voyager.server.config.ServerSettings;

import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The restart the composition root passes into the object graph. Each test opens its own graph over its own copy of the
 * shipped catalogue in its own temporary directory, and closes it again.
 */
@EnvTest
class RestartWiringTest {

    @TempDir
    Path tempDir;

    /** The graph holds the restart it was given, and the waiting room is built in the same graph that holds it. */
    @Test
    void theGraphHoldsTheRestartItWasGiven(Env env) throws IOException {
        CupRestart restart = CupRestart.after(CupRotationStore.NONE, "the test state");

        try (BeanScope scope = VoyagerServer.openGraph(settings(), PersistenceSettings.off(),
                RotationPlan.single(Optional.of("alpha_cup")), CupRotationStore.NONE, restart)) {
            assertThat(scope.get(CupRestart.class)).isSameAs(restart);
            assertThat(scope.get(WaitingRoom.class)).isNotNull();
        }
    }

    /** Without the setting the graph holds the disabled restart, so a server that does not opt in is built as before. */
    @Test
    void aGraphBuiltWithoutTheSettingHoldsTheDisabledRestart(Env env) throws IOException {
        try (BeanScope scope = VoyagerServer.openGraph(settings())) {
            assertThat(scope.get(CupRestart.class).enabled()).isFalse();
        }
    }

    private ServerSettings settings() throws IOException {
        Path data = tempDir.resolve("data");
        Path worlds = tempDir.resolve("worlds");
        Files.createDirectories(worlds);
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        return new ServerSettings("127.0.0.1", 25571, data, worlds, Optional.of("alpha_cup"), false,
                ServerSettings.PRODUCTION_MINIMUM_RACERS);
    }
}
