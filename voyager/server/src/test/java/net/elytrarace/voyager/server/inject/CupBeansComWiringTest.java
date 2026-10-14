package net.elytrarace.voyager.server.inject;

import net.elytrarace.voyager.platform.com.ComField;
import net.elytrarace.voyager.platform.cup.CompetitorField;
import net.elytrarace.voyager.platform.cup.NoCompetitors;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.minestom.server.entity.Player;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The composition root's choice of competitor field: no computer racers binds the no-op field, and a count above zero binds
 * the computer racer field. Each test builds its own settings, its own temp folders and its own Minestom environment, and
 * reads no system property or wall clock.
 */
@EnvTest
class CupBeansComWiringTest {

    private static final Instant FIXED = Instant.parse("2026-10-14T00:00:00Z");

    @TempDir
    Path root;

    @Test
    void aCountOfZeroBindsNoCompetitors(Env env) throws IOException {
        assertThat(competitorsFor(env, "0")).isSameAs(NoCompetitors.INSTANCE);
    }

    @Test
    void aCountAboveZeroBindsComField(Env env) throws IOException {
        assertThat(competitorsFor(env, "3")).isInstanceOf(ComField.class);
    }

    private CompetitorField competitorsFor(Env env, String count) throws IOException {
        ServerSettings settings = settingsWithCount(count);
        MapInstances instances = new MapInstances(env.process().instance(), root.resolve("worlds"));
        Supplier<Collection<Player>> players = List::of;
        return new CupBeans().competitorField(settings, players, instances, Clock.fixed(FIXED, ZoneOffset.UTC));
    }

    private ServerSettings settingsWithCount(String count) throws IOException {
        Path data = Files.createDirectories(root.resolve("catalog"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        Map<String, String> properties = new HashMap<>();
        properties.put(ServerSettings.DATA_PATH_PROPERTY, data.toString());
        properties.put(ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString());
        properties.put(ServerSettings.COM_COUNT_PROPERTY, count);
        return ServerSettings.fromProperties(new String[0], properties::get);
    }
}
