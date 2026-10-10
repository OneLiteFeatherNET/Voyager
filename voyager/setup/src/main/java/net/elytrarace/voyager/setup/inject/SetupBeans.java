package net.elytrarace.voyager.setup.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.setup.adapter.BuilderSessions;
import net.elytrarace.voyager.setup.adapter.SetupCommands;
import net.elytrarace.voyager.setup.adapter.TerrainGuard;
import net.elytrarace.voyager.setup.adapter.WandListener;
import net.elytrarace.voyager.setup.config.SetupSettings;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceManager;

/**
 * The beans of the setup server. Constructors are called here, not annotated: no class under {@code setup.adapter} or
 * {@code setup.mapsetup} carries a DI annotation, so a constructor change is a compile error in this file.
 */
@Factory
public final class SetupBeans {

    @Bean
    DraftStore draftStore(@External SetupSettings settings) {
        return new JsonDraftStore(settings.dataPath(), settings.worldsPath());
    }

    /** Minestom's instance manager. {@code MinecraftServer.init()} has to have run before the graph is built. */
    @Bean
    InstanceManager instanceManager() {
        return MinecraftServer.getInstanceManager();
    }

    @Bean
    MapInstances mapInstances(InstanceManager instances, @External SetupSettings settings) {
        return new MapInstances(instances, settings.worldsPath());
    }

    @Bean
    BuilderSessions builderSessions() {
        return new BuilderSessions();
    }

    @Bean
    SetupCommands setupCommands(DraftStore store, BuilderSessions sessions, MapInstances instances,
            @External SetupSettings settings) {
        return new SetupCommands(store, sessions, settings.worldsPath(), instances);
    }

    @Bean
    WandListener wandListener(BuilderSessions sessions) {
        return new WandListener(sessions);
    }

    @Bean
    TerrainGuard terrainGuard(BuilderSessions sessions) {
        return new TerrainGuard(sessions);
    }
}
