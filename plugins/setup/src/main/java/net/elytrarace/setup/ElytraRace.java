package net.elytrarace.setup;

import net.elytrarace.api.conversation.ConversationFactory;
import net.elytrarace.setup.platform.BukkitConversationOwner;
import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.language.LanguageService;
import net.elytrarace.common.map.MapService;
import net.elytrarace.setup.command.CupCreateCommand;
import net.elytrarace.setup.command.CupListCommand;
import net.elytrarace.setup.command.CupRenameCommand;
import net.elytrarace.setup.command.EditingContextManager;
import net.elytrarace.setup.command.HelpCommand;
import net.elytrarace.setup.command.MapDeleteCommand;
import net.elytrarace.setup.command.GuideCommand;
import net.elytrarace.common.guide.GuidePointStore;
import net.elytrarace.setup.command.MapCreateCommand;
import net.elytrarace.setup.command.MapLoadCommand;
import net.elytrarace.setup.command.MapRenameCommand;
import net.elytrarace.setup.command.MapTeleportCommand;
import net.elytrarace.setup.command.CupDeleteCommand;
import net.elytrarace.setup.command.MapStatusCommand;
import net.elytrarace.setup.command.MapUnloadCommand;
import net.elytrarace.setup.command.PortalCancelCommand;
import net.elytrarace.setup.command.PortalRedoCommand;
import net.elytrarace.setup.command.PortalCommand;
import net.elytrarace.setup.command.PortalDeleteCommand;
import net.elytrarace.setup.command.PortalEditCommand;
import net.elytrarace.setup.command.PortalSaveCommand;
import net.elytrarace.setup.command.PortalPathCommand;
import net.elytrarace.setup.command.SplineConfigCommand;
import net.elytrarace.setup.command.PortalShowCommand;
import net.elytrarace.setup.command.PortalTestflyCommand;
import net.elytrarace.setup.command.PortalUndoCommand;
import net.elytrarace.setup.command.PortalsCommand;
import net.elytrarace.setup.guard.SetupCommandGuard;
import net.elytrarace.setup.gui.CupGuiListener;
import net.elytrarace.setup.gui.PortalManagerListener;
import net.elytrarace.setup.session.JsonSessionPersistence;
import net.elytrarace.setup.session.SetupSessionManager;
import net.elytrarace.setup.session.SetupSessionManagerImpl;
import net.elytrarace.setup.testfly.TestflyManager;
import net.elytrarace.setup.preview.ParticlePreviewManager;
import net.elytrarace.setup.undo.UndoManager;
import net.elytrarace.setup.conversation.portal.PortalPrompt;
import net.elytrarace.setup.listener.SetupListener;
import net.elytrarace.setup.model.SetupHolder;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.kyori.adventure.util.UTF8ResourceBundleControl;
import org.bukkit.NamespacedKey;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class ElytraRace extends JavaPlugin {

    public static final NamespacedKey SETUP_MODE = new NamespacedKey("elytrarace", "setup_mode");
    public static final NamespacedKey WORLD_SETUP = new NamespacedKey("elytrarace", "world_setup");
    public static final String SETUP_METADATA = "setup";

    private CupService cupService;
    private MapService mapService;
    private UndoManager undoManager;
    private EditingContextManager editingContextManager;
    private GuidePointStore guidePointStore;
    private ParticlePreviewManager previewManager;
    private TestflyManager testflyManager;
    private SetupSessionManagerImpl sessionManager;
    private JsonSessionPersistence sessionPersistence;
    private @NonNull PaperCommandManager<Source> commandManager;

    @Override
    public void onEnable() {
        try {
            Files.createDirectories(getDataPath());
        } catch (IOException e) {
            getLogger().warning("Unable to create plugin directory");
        }
        this.commandManager = PaperCommandManager
                .builder(PaperSimpleSenderMapper.simpleSenderMapper())
                .executionCoordinator(ExecutionCoordinator.asyncCoordinator())
                .buildOnEnable(this);
        LanguageService
                .create("elytrarace", Key.key("elytrarace", "language"), getDataFolder().toPath())
                .loadLanguage()
                .thenRun(() -> getLogger().info("Language has been loaded"));
        this.cupService = CupService.create(getDataPath());
        this.mapService = MapService.create(getDataPath());
        this.undoManager = new UndoManager();
        this.editingContextManager = new EditingContextManager();
        this.guidePointStore = new GuidePointStore(getDataPath());
        this.previewManager = new ParticlePreviewManager(this.mapService, this.guidePointStore);
        this.previewManager.start(this);
        this.testflyManager = new TestflyManager();
        this.testflyManager.start(this);
        this.sessionManager = new SetupSessionManagerImpl();
        this.sessionPersistence = new JsonSessionPersistence(getDataPath());
        CompletableFuture.runAsync(this::registerListeners);
        this.registerCommands();
        getLogger().info("ElytraRace has been enabled!");
    }

    @Override
    public void onDisable() {
        if (previewManager != null) {
            previewManager.stop();
        }
        if (testflyManager != null) {
            testflyManager.stop();
        }
        // Persist all active sessions so builders can resume after restart
        if (sessionPersistence != null && sessionManager != null) {
            sessionPersistence.saveAll(sessionManager);
            sessionPersistence.deleteExpired(Duration.ofDays(7));
        }
        getLogger().info("ElytraRace has been disabled!");
    }
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new SetupListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalManagerListener(this.mapService, this.undoManager), this);
        getServer().getPluginManager().registerEvents(new CupGuiListener(this.cupService, this.mapService, this), this);
    }

    private void registerCommands() {
        // Register the setup session guard as a postprocessor.
        // This rejects commands from players without an active session,
        // except for help/setup/cancel which are allowed without a session.
        this.commandManager.registerCommandPostProcessor(new SetupCommandGuard(this.sessionManager));

        // Help: /elytrarace help (also bare /elytrarace)
        HelpCommand.register(this.commandManager);
        // Register commands here
        this.commandManager.command(this.commandManager.commandBuilder("elytrarace")
                .literal("setup")
                .senderType(PlayerSource.class)
                .handler(context -> {
                    var player = context.sender().source();
                    player.sendActionBar(Component.translatable("setup.start"));
                    if (player.hasMetadata(SETUP_METADATA)) {
                        player.removeMetadata(SETUP_METADATA, this);
                    }
                    player.setMetadata(SETUP_METADATA, new FixedMetadataValue(this, new SetupHolder(player.getUniqueId())));
                    // Also create a SetupSession alongside the legacy SetupHolder
                    sessionManager.create(player.getUniqueId());
                })
        );
        this.commandManager.command(this.commandManager.commandBuilder("elytrarace")
                .literal("cancel")
                .senderType(PlayerSource.class)
                .handler(context -> {
                    var player = context.sender().source();
                    if (player.hasMetadata(SETUP_METADATA)) {
                        var metadata = player.getMetadata(SETUP_METADATA).getFirst();
                        Optional.ofNullable(metadata)
                                        .map(MetadataValue::value)
                                        .filter(SetupHolder.class::isInstance)
                                        .map(SetupHolder.class::cast)
                                        .ifPresent(setupHolder -> {
                                            setupHolder.getConversationTracker().abandonAllConversations();
                                        });
                        player.removeMetadata(SETUP_METADATA, this);
                        sessionManager.remove(player.getUniqueId());
                        player.sendActionBar(Component.translatable("setup.cancel"));
                    }
                })
        );
        // Cup GUI: /elytrarace cup (opens cup manager)
        CupListCommand.register(this.commandManager, this.cupService, this);
        // Cup create: /elytrarace cup create <name> <displayName>
        CupCreateCommand.register(this.commandManager, this.cupService);
        // Cup rename: /elytrarace cup rename <oldName> <newName> <newDisplayName>
        CupRenameCommand.register(this.commandManager, this.cupService);
        // Map create: /elytrarace map create <cup> <name> <displayName>
        MapCreateCommand.register(this.commandManager, this.mapService, this.cupService);
        // Map teleport: /elytrarace map tp <name>
        MapTeleportCommand.register(this.commandManager, this.mapService, this);
        // Map load: /elytrarace map load <worldFolder> (load Anvil world with VoidGen)
        MapLoadCommand.register(this.commandManager, this);
        // Map rename: /elytrarace map rename <oldName> <newName> <newDisplayName>
        MapRenameCommand.register(this.commandManager, this.mapService);
        // Map delete: /elytrarace map delete <name>
        MapDeleteCommand.register(this.commandManager, this.mapService, this.cupService);
        // Quick portal command: /elytrarace portal (auto-detects FAWE region, auto-indexes)
        PortalCommand.register(this.commandManager, this.mapService, this.undoManager, this.editingContextManager);
        // Portal delete: /elytrarace portal delete <index>
        PortalDeleteCommand.register(this.commandManager, this.mapService, this.undoManager);
        // Portal undo: /elytrarace portal undo
        PortalUndoCommand.register(this.commandManager, this.mapService, this.undoManager);
        // Portal show: /elytrarace portal show (toggle particle preview)
        PortalShowCommand.register(this.commandManager, this.previewManager, this);
        // Portal path: /elytrarace portal path (toggle spline ideal line)
        PortalPathCommand.register(this.commandManager, this.previewManager);
        // Spline config: /elytrarace spline <preset|spacing|size|color|info>
        SplineConfigCommand.register(this.commandManager, this.previewManager);
        // Portal edit: /elytrarace portal edit <index> (load FAWE region for editing)
        PortalEditCommand.register(this.commandManager, this.mapService, this.editingContextManager);
        // Portal save: /elytrarace portal save (save edited FAWE region)
        PortalSaveCommand.register(this.commandManager, this.mapService, this.editingContextManager, this.undoManager, this.previewManager);
        // Portal cancel: /elytrarace portal cancel (abort active edit)
        PortalCancelCommand.register(this.commandManager, this.editingContextManager);
        // Portal testfly: /elytrarace portal testfly [stop]
        PortalTestflyCommand.register(this.commandManager, this.mapService, this.testflyManager, this);
        // Map status: /elytrarace map status
        MapStatusCommand.register(this.commandManager, this.mapService, this.guidePointStore, this.previewManager);
        // Guide point commands: /elytrarace guide [delete|list]
        GuideCommand.register(this.commandManager, this.mapService, this.guidePointStore);
        // Portals GUI: /elytrarace portals
        PortalsCommand.register(this.commandManager, this.mapService, this);

        // Deprecated: /elytrarace portal create — redirect to /elytrarace portal
        this.commandManager.command(this.commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("create")
                .senderType(PlayerSource.class)
                .handler(context -> context.sender().source()
                        .sendMessage(Component.translatable("portal.create.deprecated")))
        );
        // Cup delete: /elytrarace cup delete <name>
        CupDeleteCommand.register(this.commandManager, this.cupService);
        // Map unload: /elytrarace map unload <name>
        MapUnloadCommand.register(this.commandManager, this.mapService, this);
        // Portal redo: /elytrarace portal redo
        PortalRedoCommand.register(this.commandManager, this.mapService, this.undoManager);
    }

    public CupService getCupService() {
        return this.cupService;
    }

    public MapService getMapService() {
        return mapService;
    }

    public UndoManager getUndoManager() {
        return undoManager;
    }

    public ParticlePreviewManager getPreviewManager() {
        return previewManager;
    }

    public EditingContextManager getEditingContextManager() {
        return editingContextManager;
    }

    public TestflyManager getTestflyManager() {
        return testflyManager;
    }

    public SetupSessionManager getSessionManager() {
        return sessionManager;
    }

    public JsonSessionPersistence getSessionPersistence() {
        return sessionPersistence;
    }
}
