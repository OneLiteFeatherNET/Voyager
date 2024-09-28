package net.elytrarace.setup;

import net.elytrarace.api.conversation.ConversationFactory;
import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.utils.PluginTranslationRegistry;
import net.elytrarace.setup.conversation.cup.CupPrompt;
import net.elytrarace.setup.conversation.map.MapPrompt;
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
        this.registerLanguage();
        CompletableFuture.runAsync(this::registerListeners);
        this.registerCommands();
        this.cupService = CupService.create(this);
        this.mapService = MapService.create(this);
        getLogger().info("ElytraRace has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ElytraRace has been disabled!");
    }

    private void registerLanguage() {
        final TranslationRegistry translationRegistry = new PluginTranslationRegistry(TranslationRegistry.create(Key.key("elytrarace", "translations")));
        translationRegistry.defaultLocale(Locale.US);
        Path langFolder = getDataFolder().toPath().resolve("lang");
        var languages = new HashSet<String>();
        languages.add("en-US");
        if (Files.exists(langFolder)) {
            try (var urlClassLoader = new URLClassLoader(new URL[]{langFolder.toUri().toURL()})) {
                languages.stream().map(Locale::forLanguageTag).forEach(r -> {
                    var bundle = ResourceBundle.getBundle("bettergopaint", r, urlClassLoader, UTF8ResourceBundleControl.get());
                    translationRegistry.registerAll(r, bundle, false);
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            languages.stream().map(Locale::forLanguageTag).forEach(r -> {
                var bundle = ResourceBundle.getBundle("elytrarace", r, UTF8ResourceBundleControl.get());
                translationRegistry.registerAll(r, bundle, false);
            });
        }
        GlobalTranslator.translator().addSource(translationRegistry);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new SetupListener(this), this);
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
        this.commandManager.command(this.commandManager.commandBuilder("elytrarace")
                .literal("cup")
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
                                    new ConversationFactory(this)
                                            .withFirstPrompt(new CupPrompt())
                                            .withPrefix(context1 -> Component.translatable("plugin.prefix"))
                                            .buildConversation(setupHolder)
                                            .begin();
                                });

                    }
                })
        );
        this.commandManager.command(this.commandManager.commandBuilder("elytrarace")
                .literal("map")
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
                                    new ConversationFactory(this)
                                            .withFirstPrompt(new MapPrompt())
                                            .withPrefix(context1 -> Component.translatable("plugin.prefix"))
                                            .buildConversation(setupHolder)
                                            .begin();
                                });

                    }
                })
        );
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
                                    new ConversationFactory(this)
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
}
