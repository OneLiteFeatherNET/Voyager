package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.platform.text.Palette;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.projectile.FireworkRocketMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.FireworkList;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The firework verification of the pinned Minestom. A rocket entity can carry an explosion in its metadata, so the bursts are
 * rockets and not the particle fallback. The rocket is set off and removed in the same tick, so only its metadata is checked.
 */
@EnvTest
class FireworksTest {

    @Test
    void aRocketCarriesAnExplosionOfTheGivenColour() {
        Entity rocket = Fireworks.rocketFor(Palette.GOLD);

        ItemStack info = ((FireworkRocketMeta) rocket.getEntityMeta()).getFireworkInfo();
        FireworkList fireworks = info.get(DataComponents.FIREWORKS);
        assertThat(fireworks.explosions()).hasSize(1);
        FireworkExplosion explosion = fireworks.explosions().getFirst();
        assertThat(explosion.shape()).isEqualTo(FireworkExplosion.Shape.LARGE_BALL);
        assertThat(explosion.colors()).containsExactly(Palette.GOLD);
    }

    @Test
    void aBurstLeavesNoRocketBehindOnceItHasDetonated(Env env) {
        Instance lobby = env.createFlatInstance();

        Fireworks.burst(lobby, new Pos(0.5, 42.0, 0.5), Palette.SILVER);

        assertThat(lobby.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.FIREWORK_ROCKET)
                .toList()).isEmpty();
    }
}
