package net.elytrarace.voyager.setup.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.permission.LevelPermissionPolicy;
import net.elytrarace.voyager.platform.permission.luckperms.LuckPermsBootstrap;
import net.elytrarace.voyager.platform.permission.luckperms.LuckPermsPolicy;
import net.elytrarace.voyager.platform.permission.luckperms.NetLuckPermsGateway;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.setup.adapter.BuilderSessions;
import net.elytrarace.voyager.setup.adapter.SetupCommands;
import net.elytrarace.voyager.setup.adapter.TerrainGuard;
import net.elytrarace.voyager.setup.adapter.WandListener;
import net.elytrarace.voyager.setup.config.SetupSettings;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The beans of the setup server. Constructors are called here, not annotated: no class under {@code setup.adapter} or
 * {@code setup.mapsetup} carries a DI annotation, so a constructor change is a compile error in this file.
 */
@Factory
public final class SetupBeans {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetupBeans.class);

    /**
     * The one permission policy the setup commands and the wand ask. LuckPerms answers when its loader is on the class
     * path, and the level-based fallback answers when it is not (ADR-0024).
     */
    @Bean
    PermissionPolicy permissionPolicy() {
        if (LuckPermsBootstrap.isPresent()) {
            return new LuckPermsPolicy(new NetLuckPermsGateway());
        }
        LOGGER.warn("LuckPerms is not on the class path: setup commands and the wand fall back to operator level 4 for "
                + "players, and the console is allowed everything. Put the LuckPerms loader on the class path to grant nodes.");
        return new LevelPermissionPolicy();
    }

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
            PermissionPolicy policy, @External SetupSettings settings) {
        return new SetupCommands(store, sessions, settings.worldsPath(), instances, policy);
    }

    @Bean
    WandListener wandListener(BuilderSessions sessions, PermissionPolicy policy) {
        return new WandListener(sessions, policy);
    }

    @Bean
    TerrainGuard terrainGuard(BuilderSessions sessions) {
        return new TerrainGuard(sessions);
    }
}
