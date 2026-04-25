package net.elytrarace.server.scoring;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.common.game.scoring.MedalBrackets;
import net.elytrarace.common.game.scoring.MedalTier;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.BracketConfigComponent;
import net.elytrarace.server.ecs.component.ElapsedTimeComponent;
import net.elytrarace.server.ecs.component.GameModeComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.ecs.system.CompletionDetectionSystem;
import net.elytrarace.server.physics.Ring;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CompletionDetectionSystem} — the ECS system that detects
 * map completion and stamps bracket points, medal tiers, and completion timestamps
 * onto a player's {@link ScoreComponent}.
 *
 * <p>A Minestom {@link Env} is required because {@link CompletionDetectionSystem} reads
 * {@code playerRef.getPlayer().getUsername()} for logging when a player finishes.
 */
@EnvTest
class ScoringServiceTest {

    private static final Duration REFERENCE = Duration.ofMinutes(3);
    private static final Ring SINGLE_RING = new Ring(new Vec(0, 60, 0), new Vec(0, 0, 1), 5.0, 10);

    // ── Fixtures ───────────────────────────────────────────────────────────────

    /**
     * Builds a game entity that holds the map, elapsed time, bracket config, and game mode.
     * The caller controls elapsed time and game mode to drive bracket classification.
     */
    private Entity buildGameEntity(MapDefinition map, long elapsedMs, GameMode mode) {
        var activeMap = new ActiveMapComponent();
        activeMap.setCurrentMap(map);

        return new Entity()
                .addComponent(activeMap)
                .addComponent(new ElapsedTimeComponent(elapsedMs))
                .addComponent(new BracketConfigComponent(MedalBrackets.DEFAULT, REFERENCE))
                .addComponent(new GameModeComponent(mode));
    }

    /**
     * Builds a player entity with all rings in the map already marked as passed,
     * so that on the very first {@code entityManager.update()} the system treats
     * the player as having just crossed the finish line.
     */
    private Entity buildFinishedPlayerEntity(
            net.minestom.server.entity.Player player, MapDefinition map) {
        var tracker = new RingTrackerComponent();
        for (int i = 0; i < map.rings().size(); i++) {
            tracker.markPassed(i);
        }
        return new Entity()
                .addComponent(new PlayerRefComponent(player.getUuid(), player))
                .addComponent(tracker)
                .addComponent(new ScoreComponent());
    }

    /**
     * Builds a player entity with NO rings marked as passed (the player has not finished).
     */
    private Entity buildUnfinishedPlayerEntity(net.minestom.server.entity.Player player) {
        return new Entity()
                .addComponent(new PlayerRefComponent(player.getUuid(), player))
                .addComponent(new RingTrackerComponent())
                .addComponent(new ScoreComponent());
    }

    /**
     * Creates a minimal one-ring map at a throwaway path.
     */
    private MapDefinition singleRingMap() {
        return new MapDefinition("TestMap", Path.of("/tmp/test"), List.of(SINGLE_RING), new Pos(0, 60, -10));
    }

    // ── Test groups ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Completion detection")
    class CompletionDetection {

        @Test
        @DisplayName("Player who passed all rings is marked as finished with the elapsed time")
        void shouldMarkPlayerFinishedWhenAllRingsPassed(Env env) {
            // Given
            var map = singleRingMap();
            long elapsedMs = REFERENCE.toMillis(); // 180 000 ms
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            em.addEntity(buildGameEntity(map, elapsedMs, GameMode.RACE));
            var playerEntity = buildFinishedPlayerEntity(player, map);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When
            em.update(1.0f / 20.0f);

            // Then
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.hasFinished()).isTrue();
            assertThat(score.getCompletionTimeMs()).isEqualTo(elapsedMs);
        }

        @Test
        @DisplayName("Player who has not passed all rings is not marked as finished")
        void shouldNotMarkPlayerFinishedWhenRingsRemaining(Env env) {
            // Given
            var map = singleRingMap();
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            em.addEntity(buildGameEntity(map, REFERENCE.toMillis(), GameMode.RACE));
            var playerEntity = buildUnfinishedPlayerEntity(player);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When
            em.update(1.0f / 20.0f);

            // Then
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.hasFinished()).isFalse();
        }
    }

    @Nested
    @DisplayName("Bracket points — RACE mode")
    class BracketPointsRaceMode {

        @Test
        @DisplayName("Elapsed time at reference boundary → DIAMOND tier with 60 bracket points")
        void shouldAwardDiamondTierAndSixtyPoints(Env env) {
            // Given  — exactly at reference = 1.00× → DIAMOND
            long elapsedMs = REFERENCE.toMillis();
            var map = singleRingMap();
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            em.addEntity(buildGameEntity(map, elapsedMs, GameMode.RACE));
            var playerEntity = buildFinishedPlayerEntity(player, map);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When
            em.update(1.0f / 20.0f);

            // Then
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.getMedalTier()).isEqualTo(MedalTier.DIAMOND);
            assertThat(score.getPositionBonus()).isEqualTo(60);
        }

        @Test
        @DisplayName("Elapsed time at 1.05× reference → GOLD tier with 45 bracket points")
        void shouldAwardGoldTierAndFortyFivePoints(Env env) {
            // Given  — 105% of reference (MedalBrackets.DEFAULT: gold threshold = 1.10×)
            long elapsedMs = (long) (REFERENCE.toMillis() * 1.05);
            var map = singleRingMap();
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            em.addEntity(buildGameEntity(map, elapsedMs, GameMode.RACE));
            var playerEntity = buildFinishedPlayerEntity(player, map);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When
            em.update(1.0f / 20.0f);

            // Then
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.getMedalTier()).isEqualTo(MedalTier.GOLD);
            assertThat(score.getPositionBonus()).isEqualTo(45);
        }

        @Test
        @DisplayName("Elapsed time at 1.15× reference → SILVER tier with 30 bracket points")
        void shouldAwardSilverTierAndThirtyPoints(Env env) {
            // Given  — 115% of reference (MedalBrackets.DEFAULT: silver threshold = 1.25×)
            long elapsedMs = (long) (REFERENCE.toMillis() * 1.15);
            var map = singleRingMap();
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            em.addEntity(buildGameEntity(map, elapsedMs, GameMode.RACE));
            var playerEntity = buildFinishedPlayerEntity(player, map);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When
            em.update(1.0f / 20.0f);

            // Then
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.getMedalTier()).isEqualTo(MedalTier.SILVER);
            assertThat(score.getPositionBonus()).isEqualTo(30);
        }

        @Test
        @DisplayName("Elapsed time at 1.35× reference → BRONZE tier with 15 bracket points")
        void shouldAwardBronzeTierAndFifteenPoints(Env env) {
            // Given  — 135% of reference (MedalBrackets.DEFAULT: bronze threshold = 1.50×)
            long elapsedMs = (long) (REFERENCE.toMillis() * 1.35);
            var map = singleRingMap();
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            em.addEntity(buildGameEntity(map, elapsedMs, GameMode.RACE));
            var playerEntity = buildFinishedPlayerEntity(player, map);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When
            em.update(1.0f / 20.0f);

            // Then
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.getMedalTier()).isEqualTo(MedalTier.BRONZE);
            assertThat(score.getPositionBonus()).isEqualTo(15);
        }

        @Test
        @DisplayName("Elapsed time at 2× reference → FINISH tier with 5 bracket points")
        void shouldAwardFinishTierAndFivePoints(Env env) {
            // Given  — 200% of reference, past all medal thresholds → FINISH
            long elapsedMs = REFERENCE.toMillis() * 2;
            var map = singleRingMap();
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            em.addEntity(buildGameEntity(map, elapsedMs, GameMode.RACE));
            var playerEntity = buildFinishedPlayerEntity(player, map);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When
            em.update(1.0f / 20.0f);

            // Then
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.getMedalTier()).isEqualTo(MedalTier.FINISH);
            assertThat(score.getPositionBonus()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Bracket points — PRACTICE mode")
    class BracketPointsPracticeMode {

        @Test
        @DisplayName("PRACTICE mode sets medal tier but awards zero bracket points")
        void shouldSetMedalTierButAwardZeroBracketPointsInPracticeMode(Env env) {
            // Given  — DIAMOND time, but practice mode → medal is classified, no bracket pts
            long elapsedMs = REFERENCE.toMillis();
            var map = singleRingMap();
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            em.addEntity(buildGameEntity(map, elapsedMs, GameMode.PRACTICE));
            var playerEntity = buildFinishedPlayerEntity(player, map);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When
            em.update(1.0f / 20.0f);

            // Then
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.getMedalTier()).isEqualTo(MedalTier.DIAMOND);
            assertThat(score.getPositionBonus()).isZero();
        }
    }

    @Nested
    @DisplayName("DNF handling")
    class DnfHandling {

        @Test
        @DisplayName("Player who has not finished receives no bracket points and no medal tier")
        void shouldLeaveUnfinishedPlayerWithNoBracketPointsOrMedalTier(Env env) {
            // Given  — player has not crossed any ring
            var map = singleRingMap();
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            em.addEntity(buildGameEntity(map, REFERENCE.toMillis(), GameMode.RACE));
            var playerEntity = buildUnfinishedPlayerEntity(player);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When
            em.update(1.0f / 20.0f);

            // Then
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.getMedalTier()).isNull();
            assertThat(score.getPositionBonus()).isZero();
            assertThat(score.hasFinished()).isFalse();
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("Calling the system twice on the same finished player does not double-count bracket points")
        void shouldNotDoubleCountBracketPointsOnSecondTick(Env env) {
            // Given
            long elapsedMs = REFERENCE.toMillis();
            var map = singleRingMap();
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            em.addEntity(buildGameEntity(map, elapsedMs, GameMode.RACE));
            var playerEntity = buildFinishedPlayerEntity(player, map);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When  — two ticks
            em.update(1.0f / 20.0f);
            em.update(1.0f / 20.0f);

            // Then  — still 60 points, not 120
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.getPositionBonus()).isEqualTo(60);
            assertThat(score.getMedalTier()).isEqualTo(MedalTier.DIAMOND);
        }

        @Test
        @DisplayName("Completion time is stamped exactly once even across multiple ticks")
        void shouldStampCompletionTimeOnlyOnce(Env env) {
            // Given
            long elapsedMs = REFERENCE.toMillis();
            var map = singleRingMap();
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0, 60, -10));

            var em = new EntityManager();
            var gameEntity = buildGameEntity(map, elapsedMs, GameMode.RACE);
            em.addEntity(gameEntity);
            var playerEntity = buildFinishedPlayerEntity(player, map);
            em.addEntity(playerEntity);
            em.addSystem(new CompletionDetectionSystem(em));

            // When  — first tick stamps the time; second tick uses a different elapsed value
            em.update(1.0f / 20.0f);
            // Replace game entity with a different elapsed time to confirm the second tick is skipped
            em.removeEntity(gameEntity);
            em.addEntity(buildGameEntity(map, elapsedMs * 2, GameMode.RACE));
            em.update(1.0f / 20.0f);

            // Then  — original stamp is preserved
            var score = playerEntity.getComponent(ScoreComponent.class);
            assertThat(score.getCompletionTimeMs()).isEqualTo(elapsedMs);
        }
    }

    @Nested
    @DisplayName("MedalBrackets — classify() boundary values")
    class MedalBracketsBoundaries {

        @Test
        @DisplayName("Elapsed time exactly at reference → DIAMOND (1.00× boundary)")
        void shouldReturnDiamondAtExactReference() {
            // Given / When
            MedalTier tier = MedalBrackets.DEFAULT.classify(REFERENCE, REFERENCE);
            // Then
            assertThat(tier).isEqualTo(MedalTier.DIAMOND);
        }

        @Test
        @DisplayName("Elapsed time just above gold threshold → SILVER")
        void shouldReturnSilverJustAboveGoldThreshold() {
            // Given  — gold = 1.10×; 1.11× is just above gold → SILVER
            Duration elapsed = Duration.ofMillis((long) (REFERENCE.toMillis() * 1.11));
            // When
            MedalTier tier = MedalBrackets.DEFAULT.classify(elapsed, REFERENCE);
            // Then
            assertThat(tier).isEqualTo(MedalTier.SILVER);
        }

        @Test
        @DisplayName("Elapsed time far above bronze threshold → FINISH")
        void shouldReturnFinishFarAboveBronzeThreshold() {
            // Given  — bronze = 1.50×; 3.00× is far above → FINISH
            Duration elapsed = Duration.ofMillis(REFERENCE.toMillis() * 3);
            // When
            MedalTier tier = MedalBrackets.DEFAULT.classify(elapsed, REFERENCE);
            // Then
            assertThat(tier).isEqualTo(MedalTier.FINISH);
        }

        @Test
        @DisplayName("Required system components contain PlayerRefComponent, RingTrackerComponent, ScoreComponent")
        void shouldDeclareRequiredComponents() {
            // Given
            var em = new EntityManager();
            var system = new CompletionDetectionSystem(em);
            // When
            var required = system.getRequiredComponents();
            // Then
            assertThat(required).containsExactlyInAnyOrder(
                    PlayerRefComponent.class,
                    RingTrackerComponent.class,
                    ScoreComponent.class
            );
        }
    }
}
