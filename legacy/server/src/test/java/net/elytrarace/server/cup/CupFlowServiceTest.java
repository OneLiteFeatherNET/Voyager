package net.elytrarace.server.cup;

import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.server.physics.Ring;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CupFlowServiceTest {

    private CupFlowService service;
    private CupDefinition cup;

    @BeforeEach
    void setUp() {
        service = new CupFlowServiceImpl();

        var ring = new Ring(new Vec(0, 50, 0), new Vec(0, 0, 1), 5.0, 10);
        var spawn = new Pos(0, 64, 0);

        var map1 = new MapDefinition("Map 1", Path.of("world1"), List.of(ring), spawn);
        var map2 = new MapDefinition("Map 2", Path.of("world2"), List.of(ring), spawn);
        var map3 = new MapDefinition("Map 3", Path.of("world3"), List.of(ring), spawn);

        cup = new CupDefinition("Test Cup", GameMode.RACE, List.of(map1, map2, map3));
    }

    @Test
    void firstMapIsInitiallyActive() {
        service.startCup(cup);

        assertThat(service.getCurrentMap()).isPresent();
        assertThat(service.getCurrentMap().get().name()).isEqualTo("Map 1");
        assertThat(service.getCurrentMapIndex()).isZero();
    }

    @Test
    void advanceToNextMapSwitchesCorrectly() {
        service.startCup(cup);

        service.advanceToNextMap();

        assertThat(service.getCurrentMapIndex()).isEqualTo(1);
        assertThat(service.getCurrentMap().get().name()).isEqualTo("Map 2");
    }

    @Test
    void isCupCompleteAtEnd() {
        service.startCup(cup);

        service.advanceToNextMap();
        service.advanceToNextMap();

        assertThat(service.isCupComplete()).isTrue();
    }

    @Test
    void hasNextMapIsFalseAtLastMap() {
        service.startCup(cup);

        service.advanceToNextMap();
        service.advanceToNextMap();

        assertThat(service.hasNextMap()).isFalse();
    }

    @Test
    void getCurrentMapIndexIsCorrectThroughProgression() {
        service.startCup(cup);

        assertThat(service.getCurrentMapIndex()).isZero();

        service.advanceToNextMap();
        assertThat(service.getCurrentMapIndex()).isEqualTo(1);

        service.advanceToNextMap();
        assertThat(service.getCurrentMapIndex()).isEqualTo(2);
    }

    @Test
    void getTotalMapsReturnsCorrectCount() {
        service.startCup(cup);

        assertThat(service.getTotalMaps()).isEqualTo(3);
    }

    @Test
    void advanceBeyondLastMapThrows() {
        service.startCup(cup);

        service.advanceToNextMap();
        service.advanceToNextMap();

        assertThatThrownBy(() -> service.advanceToNextMap())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startCupWithEmptyMapsThrows() {
        var emptyCup = new CupDefinition("Empty", GameMode.RACE, List.of());

        assertThatThrownBy(() -> service.startCup(emptyCup))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCurrentMapReturnsEmptyBeforeStart() {
        assertThat(service.getCurrentMap()).isEmpty();
    }

    @Test
    void hasNextMapReturnsTrueWhenNotAtEnd() {
        service.startCup(cup);

        assertThat(service.hasNextMap()).isTrue();

        service.advanceToNextMap();
        assertThat(service.hasNextMap()).isTrue();
    }
}
