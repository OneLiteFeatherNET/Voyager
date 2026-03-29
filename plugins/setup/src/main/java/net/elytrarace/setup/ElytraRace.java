package net.elytrarace.setup;

import net.elytrarace.api.conversation.ConversationFactory;
import net.elytrarace.setup.platform.BukkitConversationOwner;
import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.language.LanguageService;
import net.elytrarace.common.map.MapService;
import net.elytrarace.setup.command.CupCreateCommand;
import net.elytrarace.setup.command.CupListCommand;
import net.elytrarace.setup.command.EditingContextManager;
import net.elytrarace.setup.command.MapCreateCommand;
import net.elytrarace.setup.command.MapLoadCommand;
import net.elytrarace.setup.command.MapTeleportCommand;
import net.elytrarace.setup.command.PortalCommand;
import net.elytrarace.setup.command.PortalDeleteCommand;
import net.elytrarace.setup.command.PortalEditCommand;
import net.elytrarace.setup.command.PortalSaveCommand;
import net.elytrarace.setup.command.PortalShowCommand;
import net.elytrarace.setup.command.PortalTestflyCommand;
import net.elytrarace.setup.command.PortalUndoCommand;
import net.elytrarace.setup.command.PortalsCommand;
import net.elytrarace.setup.gui.CupGuiListener;
import net.elytrarace.setup.gui.PortalManagerListener;
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
    private ParticlePreviewManager previewManager;
    private TestflyManager testflyManager;
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
        this.previewManager = new ParticlePreviewManager(this.mapService);
        this.previewManager.start(this);
        this.testflyManager = new TestflyManager();
        this.testflyManager.start(this);
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
        getLogger().info("ElytraRace has been disabled!");
    }
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new SetupListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalManagerListener(this.mapService, this.undoManager), this);
        getServer().getPluginManager().registerEvents(new CupGuiListener(this.cupService, this.mapService, this), this);
    }

    private void registerCommands() {
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
                        player.sendActionBar(Component.translatable("setup.cancel"));
                    }
                })
        );
        // Cup GUI: /elytrarace cup (opens cup manager)
        CupListCommand.register(this.commandManager, this.cupService, this);
        // Cup create: /elytrarace cup create <name> <displayName>
        CupCreateCommand.register(this.commandManager, this.cupService);
        // Map create: /elytrarace map create <cup> <name> <displayName>
        MapCreateCommand.register(this.commandManager, this.mapService, this.cupService);
        // Map teleport: /elytrarace map tp <name>
        MapTeleportCommand.register(this.commandManager, this.mapService);
        // Map load: /elytrarace map load <worldFolder> (load Anvil world with VoidGen)
        MapLoadCommand.register(this.commandManager, this);
        // Quick portal command: /elytrarace portal (auto-detects FAWE region, auto-indexes)
        PortalCommand.register(this.commandManager, this.mapService, this.undoManager);
        // Portal delete: /elytrarace portal delete <index>
        PortalDeleteCommand.register(this.commandManager, this.mapService, this.undoManager);
        // Portal undo: /elytrarace portal undo
        PortalUndoCommand.register(this.commandManager, this.mapService, this.undoManager);
        // Portal show: /elytrarace portal show (toggle particle preview)
        PortalShowCommand.register(this.commandManager, this.previewManager);
        // Portal edit: /elytrarace portal edit <index> (load FAWE region for editing)
        PortalEditCommand.register(this.commandManager, this.mapService, this.editingContextManager);
        // Portal save: /elytrarace portal save (save edited FAWE region)
        PortalSaveCommand.register(this.commandManager, this.mapService, this.editingContextManager, this.undoManager);
        // Portal testfly: /elytrarace portal testfly [stop]
        PortalTestflyCommand.register(this.commandManager, this.mapService, this.testflyManager);
        // Portals GUI: /elytrarace portals
        PortalsCommand.register(this.commandManager, this.mapService, this);

        // Legacy conversation-based portal creation: /elytrarace portal create
        this.commandManager.command(this.commandManager.commandBuilder("elytrarace")
                .literal("portal")
                .literal("create")
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
                                    new ConversationFactory(new BukkitConversationOwner(this))
                                            .withFirstPrompt(new PortalPrompt())
                                            .withPrefix(context1 -> Component.translatable("plugin.prefix"))
                                            .buildConversation(setupHolder)
                                            .begin();
                                });

                    }
                })
        );
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
}
