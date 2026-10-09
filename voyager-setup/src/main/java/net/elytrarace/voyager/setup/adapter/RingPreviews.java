package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.setup.mapsetup.RingOrientation;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The display previews of one open map: a thin block per ring, centred on the ring and turned to its normal, in the
 * map's world only. A preview is a view of the draft and is never written to a file.
 */
public final class RingPreviews {

    /** The thickness of a preview disc, in blocks. */
    static final double DISC_THICKNESS = 0.05;

    private final Instance instance;
    private final List<Entity> displays = new ArrayList<>();

    /**
     * @param instance the world the previews are shown in
     */
    public RingPreviews(Instance instance) {
        this.instance = instance;
    }

    /**
     * Shows exactly these rings, replacing every earlier preview; a count that differs from the rings is never left
     * behind.
     *
     * @param rings the rings of the saved draft
     */
    public void show(List<Ring> rings) {
        clear();
        for (Ring ring : rings) {
            displays.add(spawn(ring));
        }
    }

    /** Removes every preview this object shows. */
    public void clear() {
        for (Entity display : displays) {
            display.remove();
        }
        displays.clear();
    }

    private Entity spawn(Ring ring) {
        double radius = ring.radius();
        RingOrientation.Quaternion rotation = RingOrientation.fromNormal(ring.normal());
        Vec3 offset = RingOrientation.discOffset(rotation, radius, DISC_THICKNESS);

        Entity display = new Entity(EntityType.BLOCK_DISPLAY);
        BlockDisplayMeta meta = (BlockDisplayMeta) display.getEntityMeta();
        meta.setBlockState(Block.LIGHT_BLUE_STAINED_GLASS);
        meta.setScale(Vectors.toMinestom(new Vec3(2 * radius, 2 * radius, DISC_THICKNESS)));
        meta.setLeftRotation(new float[] {(float) rotation.x(), (float) rotation.y(), (float) rotation.z(),
                (float) rotation.w()});
        meta.setTranslation(Vectors.toMinestom(offset));
        display.setInstance(instance, Vectors.toMinestom(ring.center()).asPos());
        return display;
    }
}
