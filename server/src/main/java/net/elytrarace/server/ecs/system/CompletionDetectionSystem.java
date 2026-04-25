package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.common.game.scoring.MedalBrackets;
import net.elytrarace.common.game.scoring.MedalTier;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.BracketConfigComponent;
import net.elytrarace.server.ecs.component.ElapsedTimeComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.GameModeComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;

/**
 * Detects when a player has completed the active map (passed every ring) and
 * stamps their {@link ScoreComponent} with the completion time, medal tier, and
 * RACE-mode bracket bonus.
 * <p>
 * The system is idempotent: it short-circuits once {@link ScoreComponent#hasFinished()}
 * is true, so running it multiple ticks per player has no observable effect.
 * It also degrades gracefully if optional game-entity components
 * ({@link ElapsedTimeComponent}, {@link BracketConfigComponent},
 * {@link GameModeComponent}) are missing, falling back to safe defaults and
 * logging at WARN level.
 */
public class CompletionDetectionSystem implements net.elytrarace.common.ecs.System {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompletionDetectionSystem.class);

    private static final int POINTS_DIAMOND = 60;
    private static final int POINTS_GOLD = 45;
    private static final int POINTS_SILVER = 30;
    private static final int POINTS_BRONZE = 15;
    private static final int POINTS_FINISH = 5;
    private static final int POINTS_DNF = 0;

    private static final Duration FALLBACK_REFERENCE = Duration.ofMinutes(3);

    private final EntityManager entityManager;

    public CompletionDetectionSystem(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(PlayerRefComponent.class, RingTrackerComponent.class, ScoreComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        // If the entity carries flight info, require active flight to count completion.
        // Players still on the ground or pre-launch should not finish a race.
        if (entity.hasComponent(ElytraFlightComponent.class)) {
            ElytraFlightComponent flight = entity.getComponent(ElytraFlightComponent.class);
            if (!flight.isFlying()) {
                return;
            }
        }

        var playerRef = entity.getComponent(PlayerRefComponent.class);
        var tracker = entity.getComponent(RingTrackerComponent.class);
        var score = entity.getComponent(ScoreComponent.class);

        ActiveMapComponent activeMap = findGameComponent(ActiveMapComponent.class);
        if (activeMap == null) {
            return;
        }
        MapDefinition map = activeMap.getCurrentMap();
        if (map == null) {
            return;
        }
        int totalRings = map.rings().size();
        if (totalRings == 0) {
            return;
        }

        if (tracker.passedCount() < totalRings) {
            return;
        }
        if (score.hasFinished()) {
            return;
        }

        // Player just finished this tick.
        long elapsedMs = readElapsedMs();
        BracketConfigComponent config = findGameComponent(BracketConfigComponent.class);
        MedalBrackets brackets;
        Duration reference;
        if (config == null) {
            LOGGER.warn("No BracketConfigComponent on game entity — falling back to MedalBrackets.DEFAULT and {}",
                    FALLBACK_REFERENCE);
            brackets = MedalBrackets.DEFAULT;
            reference = FALLBACK_REFERENCE;
        } else {
            brackets = config.brackets();
            reference = config.reference();
        }

        GameMode mode = readGameMode();

        score.setCompletionTimeMs(elapsedMs);
        MedalTier tier = brackets.classify(Duration.ofMillis(elapsedMs), reference);
        score.setMedalTier(tier);

        int bracketPts = 0;
        if (mode == GameMode.RACE) {
            bracketPts = bracketPointsFor(tier);
            score.setPositionBonus(bracketPts);
        }

        LOGGER.info("Player {} completed the map in {} ms — {} (bracket points: {})",
                playerRef.getPlayer().getUsername(), elapsedMs, tier, bracketPts);
    }

    private long readElapsedMs() {
        ElapsedTimeComponent elapsed = findGameComponent(ElapsedTimeComponent.class);
        if (elapsed == null) {
            LOGGER.warn("No ElapsedTimeComponent on game entity — defaulting elapsed time to 0 ms");
            return 0L;
        }
        return elapsed.elapsedMs();
    }

    private GameMode readGameMode() {
        GameModeComponent modeComp = findGameComponent(GameModeComponent.class);
        if (modeComp == null) {
            LOGGER.warn("No GameModeComponent on game entity — defaulting to {}", GameMode.RACE);
            return GameMode.RACE;
        }
        return modeComp.mode();
    }

    private <T extends Component> @Nullable T findGameComponent(Class<T> componentClass) {
        for (Entity gameEntity : entityManager.getEntities()) {
            if (gameEntity.hasComponent(componentClass)) {
                return gameEntity.getComponent(componentClass);
            }
        }
        return null;
    }

    private static int bracketPointsFor(MedalTier tier) {
        return switch (tier) {
            case DIAMOND -> POINTS_DIAMOND;
            case GOLD -> POINTS_GOLD;
            case SILVER -> POINTS_SILVER;
            case BRONZE -> POINTS_BRONZE;
            case FINISH -> POINTS_FINISH;
            case DNF -> POINTS_DNF;
        };
    }
}
