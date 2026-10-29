package net.elytrarace.voyager.setup.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.mapsetup.WorldImporter;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.config.ConfigLoader;
import net.elytrarace.voyager.platform.mapsetup.FolderWorldImporter;
import net.elytrarace.voyager.platform.permission.LevelPermissionPolicy;
import net.elytrarace.voyager.platform.permission.luckperms.LuckPermsBootstrap;
import net.elytrarace.voyager.platform.permission.luckperms.LuckPermsPolicy;
import net.elytrarace.voyager.platform.permission.luckperms.NetLuckPermsGateway;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.setup.adapter.BuilderSessions;
import net.elytrarace.voyager.setup.adapter.ImportCommand;
import net.elytrarace.voyager.setup.adapter.SetupCommands;
import net.elytrarace.voyager.setup.adapter.TerrainGuard;
import net.elytrarace.voyager.setup.adapter.WandListener;
import net.elytrarace.voyager.setup.config.ImportSettings;
import net.elytrarace.voyager.setup.config.SetupSettings;
import net.elytrarace.voyager.setup.mapsetup.ImportGuard;
import net.elytrarace.voyager.setup.mapsetup.ImportGuardedDraftStore;
import net.elytrarace.voyager.setup.mapsetup.EditClaims;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

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

    /** The one registry of edit claims; in memory only, so every start begins with no claim (ADR-0044). */
    @Bean
    EditClaims editClaims() {
        return new EditClaims();
    }

    @Bean
    BuilderSessions builderSessions(EditClaims claims) {
        return new BuilderSessions(claims);
    }

    @Bean
    SetupCommands setupCommands(DraftStore store, BuilderSessions sessions, MapInstances instances,
            PermissionPolicy policy, ImportGuard guard, ImportCommand importCommand, @External SetupSettings settings) {
        SetupCommands commands = new SetupCommands(new ImportGuardedDraftStore(store, guard), sessions,
                settings.worldsPath(), instances, policy);
        ImportSyntax.attach(commands, importCommand);
        return commands;
    }

    /** The import folder and the limits, read once through the configuration loader. */
    @Bean
    ImportSettings importSettings() {
        ConfigLoader loader = ConfigLoader.fromJvm();
        return ImportSettings.from(loader::get);
    }

    /** The claims of the imports that are running; a second import of the same id is refused while one holds it. */
    @Bean
    ImportGuard importGuard() {
        return new ImportGuard();
    }

    @Bean
    WorldImporter worldImporter(@External SetupSettings settings, ImportSettings importSettings) {
        return new FolderWorldImporter(importSettings.root(), settings.worldsPath(), importSettings.maxBytes(),
                importSettings.maxRegions());
    }

    /** The copy and the save run on a thread of their own, and the reply returns to the server tick. */
    @Bean
    ImportCommand importCommand(DraftStore store, WorldImporter importer, ImportGuard guard, PermissionPolicy policy) {
        Executor background = task -> Thread.ofPlatform().name("voyager-import").daemon(true).start(task);
        Executor mainThread = task -> MinecraftServer.getSchedulerManager().scheduleNextTick(task);
        return new ImportCommand(store, importer, guard, policy, background, mainThread);
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
