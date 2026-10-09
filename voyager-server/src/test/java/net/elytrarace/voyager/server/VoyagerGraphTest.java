package net.elytrarace.voyager.server;

import io.avaje.inject.BeanScope;

import net.elytrarace.voyager.api.race.CupCatalog;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapCatalog;
import net.elytrarace.voyager.platform.catalog.JsonCupCatalog;
import net.elytrarace.voyager.platform.catalog.JsonMapCatalog;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.elytrarace.voyager.server.game.CupSession;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceManager;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The object graph the composition root builds, resolved from a {@link BeanScope} over the committed
 * catalogue.
 *
 * <p>Each test opens its own scope over its own copy of the data in its own {@code @TempDir}, so the
 * graph of one test cannot be the graph another test left behind. The scope is closed by
 * try-with-resources, and every Minestom-backed test runs in a fresh {@link EnvTest} process.
 */
@EnvTest
class VoyagerGraphTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesEveryBeanTheCompositionRootProvides(Env env) throws IOException {
        try (BeanScope scope = VoyagerServer.openGraph(settings(Optional.of("test_cup")))) {
            List<Type> provided = List.of(
                    InstanceManager.class,
                    JsonMapCatalog.class,
                    JsonCupCatalog.class,
                    MapCatalog.class,
                    CupCatalog.class,
                    CupDefinition.class,
                    MapInstances.class,
                    RaceRuns.class,
                    MapTransition.class,
                    FlightTracker.class,
                    RaceTimings.class,
                    onlinePlayersType(),
                    CupSession.class);

            for (Type type : provided) {
                Object bean = scope.get(type);
                assertThat(bean).as("bean %s", type.getTypeName()).isNotNull();
            }
        }
    }

    @Test
    void resolvesTheSettingsItWasGiven(Env env) throws IOException {
        ServerSettings settings = settings(Optional.of("test_cup"));

        try (BeanScope scope = VoyagerServer.openGraph(settings)) {
            assertThat(scope.get(ServerSettings.class)).isSameAs(settings);
        }
    }

    @Test
    void returnsTheSameInstanceOfEachServiceOnEveryLookup(Env env) throws IOException {
        try (BeanScope scope = VoyagerServer.openGraph(settings(Optional.of("test_cup")))) {
            assertThat(scope.get(CupSession.class)).isSameAs(scope.get(CupSession.class));
            assertThat(scope.get(CupDefinition.class)).isSameAs(scope.get(CupDefinition.class));
            assertThat(scope.get(MapInstances.class)).isSameAs(scope.get(MapInstances.class));
            assertThat(scope.get(RaceRuns.class)).isSameAs(scope.get(RaceRuns.class));
            assertThat(scope.get(FlightTracker.class)).isSameAs(scope.get(FlightTracker.class));
            assertThat(scope.get(InstanceManager.class)).isSameAs(scope.get(InstanceManager.class));
        }
    }

    @Test
    void servesEachCatalogueInterfaceWithTheSameInstanceAsItsConcreteBean(Env env) throws IOException {
        try (BeanScope scope = VoyagerServer.openGraph(settings(Optional.of("test_cup")))) {
            Object maps = scope.get(JsonMapCatalog.class);
            Object cups = scope.get(JsonCupCatalog.class);

            assertThat(scope.get(MapCatalog.class)).isSameAs(maps);
            assertThat(scope.get(CupCatalog.class)).isSameAs(cups);
        }
    }

    @Test
    void buildsTheSessionForTheCupTheSettingsName(Env env) throws IOException {
        try (BeanScope scope = VoyagerServer.openGraph(settings(Optional.of("test_cup")))) {
            assertThat(scope.get(CupSession.class).cup().name()).isEqualTo("test_cup");
        }
    }

    @Test
    void readsTheOnlinePlayersAgainOnEveryCall(Env env) throws IOException {
        Instance instance = env.createFlatInstance();

        try (BeanScope scope = VoyagerServer.openGraph(settings(Optional.of("test_cup")))) {
            Supplier<Collection<Player>> players = scope.get(onlinePlayersType());
            assertThat(players.get()).isEmpty();

            Player racer = env.createConnection().connect(instance, new Pos(0, 42, 0));

            assertThat(players.get()).containsExactly(racer);
        }
    }

    /** The one provided type that is generic: its bean key is the full parameterized type. */
    private static Type onlinePlayersType() {
        return new TypeLiteral<Supplier<Collection<Player>>>() {
        }.type();
    }

    private ServerSettings settings(Optional<String> cupName) throws IOException {
        Path data = tempDir.resolve("data");
        Path worlds = tempDir.resolve("worlds");
        Files.createDirectories(worlds);
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        return new ServerSettings("127.0.0.1", 25570, data, worlds, cupName, false);
    }

    /** Captures a generic type argument at compile time, the way {@code TypeToken} does. */
    private abstract static class TypeLiteral<T> {
        final Type type() {
            return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        }
    }
}
