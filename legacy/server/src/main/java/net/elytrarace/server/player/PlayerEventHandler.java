package net.elytrarace.server.player;

import net.elytrarace.api.database.service.DatabaseService;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.ecs.component.FireworkBoostComponent;
import net.elytrarace.server.ecs.component.PlayerProfileComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.persistence.PlayerProfileService;
import net.elytrarace.server.persistence.PlayerProfileServiceImpl;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Registers Minestom player lifecycle events and delegates to {@link PlayerService}.
 * <p>
 * Uses an {@link EventNode} scoped to {@code "player-events"} that is attached
 * to the global event handler. This keeps player-related event logic isolated
 * and easy to remove or replace.
 * <p>
 * Firework boost logic lives entirely in the ECS via {@link FireworkBoostComponent}
 * and {@code FireworkBoostSystem}. This handler only signals intent by setting the
 * component flag — all cooldown tracking and impulse application happen in the tick loop.
 */
public final class PlayerEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerEventHandler.class);
    private static final Pos DEFAULT_SPAWN = new Pos(0, 2, 0);

    private final PlayerService playerService;
    private final InstanceContainer lobbyInstance;
    private final @Nullable PlayerProfileService profileService;
    private final EventNode<Event> eventNode;
    private @Nullable EntityManager entityManager;

    /**
     * Creates a new event handler without database integration. Useful for tests
     * or minimal servers that do not need player persistence.
     */
    public PlayerEventHandler(PlayerService playerService, InstanceContainer lobbyInstance) {
        this(playerService, lobbyInstance, (PlayerProfileService) null);
    }

    /**
     * Creates a new event handler and derives the profile service from the given
     * {@link DatabaseService}. If the database service has no player repository
     * available, profile persistence is silently disabled.
     */
    public PlayerEventHandler(PlayerService playerService,
                              InstanceContainer lobbyInstance,
                              @Nullable DatabaseService databaseService) {
        this(playerService, lobbyInstance, buildProfileService(databaseService));
    }

    /**
     * Creates a new event handler with an explicit profile service. Primarily used
     * by tests that want to inject a mock profile service.
     */
    public PlayerEventHandler(PlayerService playerService,
                              InstanceContainer lobbyInstance,
                              @Nullable PlayerProfileService profileService) {
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.lobbyInstance = Objects.requireNonNull(lobbyInstance, "lobbyInstance must not be null");
        this.profileService = profileService;
        this.eventNode = EventNode.all("player-events");
    }

    private static @Nullable PlayerProfileService buildProfileService(@Nullable DatabaseService databaseService) {
        if (databaseService == null) {
            return null;
        }
        return databaseService.getElytraPlayerRepository()
                .map(repo -> (PlayerProfileService) new PlayerProfileServiceImpl(repo))
                .orElse(null);
    }

    /**
     * Sets the ECS entity manager used to look up player entities on firework use.
     * Must be called before {@link #register()} or firework boost signals will be dropped.
     *
     * @param entityManager the ECS entity manager
     */
    public void setEntityManager(@Nullable EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Registers all player-related event listeners and attaches the event node
     * to the global event handler.
     */
    public void register() {
        eventNode.addListener(AsyncPlayerConfigurationEvent.class, this::onConfiguration);
        eventNode.addListener(PlayerDisconnectEvent.class, this::onDisconnect);
        eventNode.addListener(PlayerSpawnEvent.class, this::onSpawn);
        eventNode.addListener(PlayerUseItemEvent.class, this::onUseItem);

        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        LOGGER.info("Player event handlers registered");
    }

    /**
     * Removes the event node from the global event handler, effectively
     * unregistering all player-related listeners.
     */
    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
        LOGGER.info("Player event handlers unregistered");
    }

    private void onConfiguration(AsyncPlayerConfigurationEvent event) {
        event.setSpawningInstance(lobbyInstance);
        Player player = event.getPlayer();
        player.setRespawnPoint(DEFAULT_SPAWN);
        playerService.onPlayerJoin(player);
        loadOrCreateProfileAsync(player);
    }

    /**
     * Kicks off an async upsert of the player's profile row. On completion the
     * result (which may be {@code null} if the DB was unreachable) is cached on
     * {@link PlayerService} and attached to any existing ECS entity for this
     * player via {@link PlayerProfileComponent}.
     */
    private void loadOrCreateProfileAsync(Player player) {
        if (profileService == null) {
            return;
        }
        profileService.onPlayerJoin(player.getUuid(), player.getUsername())
                .whenComplete((profile, ex) -> {
                    if (ex != null) {
                        LOGGER.warn("Profile upsert failed for {} ({}) — continuing without profile",
                                player.getUsername(), player.getUuid(), ex);
                        return;
                    }
                    playerService.cacheProfile(player.getUuid(), profile);
                    attachProfileToEntity(player.getUuid(), profile);
                });
    }

    private void attachProfileToEntity(java.util.UUID playerId,
                                       @Nullable net.elytrarace.api.database.entity.ElytraPlayerEntity profile) {
        EntityManager em = this.entityManager;
        if (em == null || profile == null) {
            return;
        }
        for (Entity entity : em.getEntities()) {
            if (!entity.hasComponent(PlayerRefComponent.class)) {
                continue;
            }
            if (!entity.getComponent(PlayerRefComponent.class).getPlayerId().equals(playerId)) {
                continue;
            }
            var existing = entity.getComponent(PlayerProfileComponent.class);
            if (existing != null) {
                existing.setProfile(profile);
            } else {
                entity.addComponent(new PlayerProfileComponent(profile));
            }
            return;
        }
    }

    private void onDisconnect(PlayerDisconnectEvent event) {
        playerService.onPlayerLeave(event.getPlayer());
    }

    private void onSpawn(PlayerSpawnEvent event) {
        if (event.isFirstSpawn()) {
            event.getPlayer().setGameMode(GameMode.ADVENTURE);
        }
    }

    /**
     * Signals a boost request to the ECS when the player uses a firework rocket.
     * <p>
     * The request is stored atomically on {@link FireworkBoostComponent} and consumed
     * by {@code FireworkBoostSystem} on the next tick. Cooldown enforcement and impulse
     * application are fully handled inside the ECS.
     */
    private void onUseItem(PlayerUseItemEvent event) {
        if (event.getItemStack().material() != Material.FIREWORK_ROCKET) {
            return;
        }
        if (entityManager == null) {
            return;
        }
        Player player = event.getPlayer();
        for (Entity entity : entityManager.getEntities()) {
            if (!entity.hasComponent(PlayerRefComponent.class)) {
                continue;
            }
            if (!entity.getComponent(PlayerRefComponent.class).getPlayerId().equals(player.getUuid())) {
                continue;
            }
            var boostComp = entity.getComponent(FireworkBoostComponent.class);
            if (boostComp != null) {
                boostComp.requestBoost();
            }
            break;
        }
    }

    /**
     * Returns the event node managed by this handler (mainly useful for testing).
     *
     * @return the event node
     */
    public EventNode<Event> getEventNode() {
        return eventNode;
    }
}
