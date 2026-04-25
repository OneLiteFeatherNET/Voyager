package net.elytrarace.server.ecs.system;

import net.elytrarace.api.database.repository.MapRecordRepository;
import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.common.game.scoring.MedalBrackets;
import net.elytrarace.common.game.scoring.MedalTier;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.BracketConfigComponent;
import net.elytrarace.server.ecs.component.CupProgressComponent;
import net.elytrarace.server.ecs.component.ElapsedTimeComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.GameModeComponent;
import net.elytrarace.server.ecs.component.MapRecordComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Detects when a player has completed the active map (passed every ring) and
 * stamps their {@link ScoreComponent} with the completion time, medal tier, and
 * bracket bonus.
 * <p>
 * Scoring is record-relative:
 * <ul>
 *   <li>The first player to ever finish a (cup, map) combination becomes the record holder
 *       and receives DIAMOND regardless of their absolute time.</li>
 *   <li>Any subsequent finisher faster than the current in-session record also receives
 *       DIAMOND and becomes the new in-session record holder.</li>
 *   <li>All other finishers are classified relative to the current record via
 *       {@link MedalBrackets#classify(Duration, Duration)}.</li>
 * </ul>
 * The in-session record is stored in {@link MapRecordComponent} on the game entity.
 * When {@link MapRecordRepository} is available, each finish triggers an async UPSERT
 * (only persisted if the time is faster than the existing DB record).
 * <p>
 * The system is idempotent: it short-circuits once {@link ScoreComponent#hasFinished()}
 * is true.
 */
public class CompletionDetectionSystem implements net.elytrarace.common.ecs.System {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompletionDetectionSystem.class);

    private static final int POINTS_DIAMOND = 60;
    private static final int POINTS_GOLD    = 45;
    private static final int POINTS_SILVER  = 30;
    private static final int POINTS_BRONZE  = 15;
    private static final int POINTS_FINISH  = 5;
    private static final int POINTS_DNF     = 0;

    private final EntityManager entityManager;
    private final @Nullable MapRecordRepository mapRecordRepository;

    public CompletionDetectionSystem(EntityManager entityManager) {
        this(entityManager, null);
    }

    public CompletionDetectionSystem(EntityManager entityManager,
                                     @Nullable MapRecordRepository mapRecordRepository) {
        this.entityManager = entityManager;
        this.mapRecordRepository = mapRecordRepository;
    }

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(PlayerRefComponent.class, RingTrackerComponent.class, ScoreComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        if (entity.hasComponent(ElytraFlightComponent.class)
                && !entity.getComponent(ElytraFlightComponent.class).isFlying()) {
            return;
        }

        var playerRef = entity.getComponent(PlayerRefComponent.class);
        var tracker   = entity.getComponent(RingTrackerComponent.class);
        var score     = entity.getComponent(ScoreComponent.class);

        ActiveMapComponent activeMap = findGameComponent(ActiveMapComponent.class);
        if (activeMap == null) return;
        MapDefinition map = activeMap.getCurrentMap();
        if (map == null) return;
        int totalRings = map.rings().size();
        if (totalRings == 0) return;

        if (tracker.passedCount() < totalRings) return;
        if (score.hasFinished()) return;

        long elapsedMs = readElapsedMs();
        MedalBrackets brackets = readBrackets();

        // Classify relative to current record (or award DIAMOND to first finisher).
        Entity gameEntity = findGameEntityWithActiveMap();
        MapRecordComponent currentRecord = gameEntity != null
                ? gameEntity.getComponent(MapRecordComponent.class) : null;

        MedalTier tier;
        if (currentRecord == null || elapsedMs <= currentRecord.recordTimeMs()) {
            // First finisher or new record — always DIAMOND
            tier = MedalTier.DIAMOND;
            if (gameEntity != null) {
                gameEntity.addComponent(new MapRecordComponent(elapsedMs));
            }
        } else {
            tier = brackets.classify(
                    Duration.ofMillis(elapsedMs),
                    Duration.ofMillis(currentRecord.recordTimeMs()));
        }

        score.setCompletionTimeMs(elapsedMs);
        score.setMedalTier(tier);

        GameMode mode = readGameMode();
        int bracketPts = 0;
        if (mode == GameMode.RACE) {
            bracketPts = bracketPointsFor(tier);
            score.setPositionBonus(bracketPts);
        }

        LOGGER.info("Player {} completed the map in {} ms — {} (bracket points: {})",
                playerRef.getPlayer().getUsername(), elapsedMs, tier, bracketPts);

        persistRecord(playerRef.getPlayerId(), elapsedMs);
    }

    private void persistRecord(UUID holderId, long elapsedMs) {
        if (mapRecordRepository == null) return;
        String cupName = readCupName();
        String mapName = readMapName();
        if (cupName == null || mapName == null) return;
        mapRecordRepository.saveOrUpdateRecord(cupName, mapName, holderId, elapsedMs)
                .exceptionally(ex -> {
                    LOGGER.warn("Failed to persist map record for ({}, {}): {}", cupName, mapName, ex.getMessage());
                    return null;
                });
    }

    private long readElapsedMs() {
        ElapsedTimeComponent elapsed = findGameComponent(ElapsedTimeComponent.class);
        if (elapsed == null) {
            LOGGER.warn("No ElapsedTimeComponent on game entity — defaulting elapsed time to 0 ms");
            return 0L;
        }
        return elapsed.elapsedMs();
    }

    private MedalBrackets readBrackets() {
        BracketConfigComponent config = findGameComponent(BracketConfigComponent.class);
        return config != null ? config.brackets() : MedalBrackets.DEFAULT;
    }

    private GameMode readGameMode() {
        GameModeComponent modeComp = findGameComponent(GameModeComponent.class);
        if (modeComp == null) {
            LOGGER.warn("No GameModeComponent on game entity — defaulting to {}", GameMode.RACE);
            return GameMode.RACE;
        }
        return modeComp.mode();
    }

    private @Nullable String readCupName() {
        CupProgressComponent prog = findGameComponent(CupProgressComponent.class);
        return prog != null ? prog.getCup().name() : null;
    }

    private @Nullable String readMapName() {
        ActiveMapComponent activeMap = findGameComponent(ActiveMapComponent.class);
        if (activeMap == null || activeMap.getCurrentMap() == null) return null;
        return activeMap.getCurrentMap().name();
    }

    private <T extends Component> @Nullable T findGameComponent(Class<T> componentClass) {
        for (Entity e : entityManager.getEntities()) {
            if (e.hasComponent(componentClass)) {
                return e.getComponent(componentClass);
            }
        }
        return null;
    }

    private @Nullable Entity findGameEntityWithActiveMap() {
        for (Entity e : entityManager.getEntities()) {
            if (e.hasComponent(ActiveMapComponent.class)) {
                return e;
            }
        }
        return null;
    }

    private static int bracketPointsFor(MedalTier tier) {
        return switch (tier) {
            case DIAMOND -> POINTS_DIAMOND;
            case GOLD    -> POINTS_GOLD;
            case SILVER  -> POINTS_SILVER;
            case BRONZE  -> POINTS_BRONZE;
            case FINISH  -> POINTS_FINISH;
            case DNF     -> POINTS_DNF;
        };
    }
}
