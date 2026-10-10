package net.elytrarace.server.ecs.component;

import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.physics.Ring;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CupProgressComponentTest {

    private static final Ring RING = new Ring(Vec.ZERO, new Vec(0, 0, 1), 3.0, 10);

    private static MapDefinition map(String name) {
        return new MapDefinition(name, Path.of("/tmp/" + name), List.of(RING), new Pos(0, 60, 0));
    }

    @Test
    void initialStateIsFirstMap() {
        var cup = new CupDefinition("Test Cup", GameMode.RACE, List.of(map("map1"), map("map2")));
        var progress = new CupProgressComponent(cup);

        assertThat(progress.getCurrentMapIndex()).isZero();
        assertThat(progress.isComplete()).isFalse();
        assertThat(progress.getCurrentMap()).isNotNull();
        assertThat(progress.getCurrentMap().name()).isEqualTo("map1");
        assertThat(progress.totalMaps()).isEqualTo(2);
    }

    @Test
    void advanceMovesToNextMap() {
        var cup = new CupDefinition("Test Cup", GameMode.RACE, List.of(map("map1"), map("map2"), map("map3")));
        var progress = new CupProgressComponent(cup);

        progress.advance();

        assertThat(progress.getCurrentMapIndex()).isEqualTo(1);
        assertThat(progress.getCurrentMap().name()).isEqualTo("map2");
        assertThat(progress.isComplete()).isFalse();
    }

    @Test
    void advancePastLastMapMarksCupComplete() {
        var cup = new CupDefinition("Test Cup", GameMode.RACE, List.of(map("map1")));
        var progress = new CupProgressComponent(cup);

        progress.advance();

        assertThat(progress.isComplete()).isTrue();
        assertThat(progress.getCurrentMap()).isNull();
    }

    @Test
    void cupReferenceIsPreserved() {
        var cup = new CupDefinition("Grand Prix", GameMode.RACE, List.of(map("a"), map("b")));
        var progress = new CupProgressComponent(cup);

        assertThat(progress.getCup()).isSameAs(cup);
    }
}
