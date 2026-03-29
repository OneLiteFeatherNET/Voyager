package net.elytrarace.server.ecs.component;

import net.elytrarace.server.physics.RingType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RingEffectComponentTest {

    @Test
    void initiallyEmpty() {
        var component = new RingEffectComponent();

        assertThat(component.pendingCount()).isZero();
        assertThat(component.pollEffect()).isNull();
    }

    @Test
    void addEffectIncreasesCount() {
        var component = new RingEffectComponent();

        component.addEffect(RingType.BOOST, 1);

        assertThat(component.pendingCount()).isEqualTo(1);
    }

    @Test
    void pollEffectReturnsAndRemoves() {
        var component = new RingEffectComponent();
        component.addEffect(RingType.BOOST, 5);

        var effect = component.pollEffect();

        assertThat(effect).isNotNull();
        assertThat(effect.type()).isEqualTo(RingType.BOOST);
        assertThat(effect.ticksRemaining()).isEqualTo(5);
        assertThat(component.pendingCount()).isZero();
    }

    @Test
    void effectsAreProcessedInFifoOrder() {
        var component = new RingEffectComponent();
        component.addEffect(RingType.BOOST, 1);
        component.addEffect(RingType.SLOW, 2);
        component.addEffect(RingType.CHECKPOINT, 3);

        assertThat(component.pollEffect().type()).isEqualTo(RingType.BOOST);
        assertThat(component.pollEffect().type()).isEqualTo(RingType.SLOW);
        assertThat(component.pollEffect().type()).isEqualTo(RingType.CHECKPOINT);
        assertThat(component.pollEffect()).isNull();
    }

    @Test
    void peekDoesNotRemoveEffect() {
        var component = new RingEffectComponent();
        component.addEffect(RingType.BONUS, 1);

        var peeked = component.peekEffect();

        assertThat(peeked).isNotNull();
        assertThat(peeked.type()).isEqualTo(RingType.BONUS);
        assertThat(component.pendingCount()).isEqualTo(1);
    }

    @Test
    void multipleEffectsOfSameTypeCanBeQueued() {
        var component = new RingEffectComponent();
        component.addEffect(RingType.BOOST, 1);
        component.addEffect(RingType.BOOST, 1);

        assertThat(component.pendingCount()).isEqualTo(2);
    }
}
