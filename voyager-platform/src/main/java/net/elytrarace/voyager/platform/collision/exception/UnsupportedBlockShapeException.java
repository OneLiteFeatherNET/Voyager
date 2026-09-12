package net.elytrarace.voyager.platform.collision.exception;

/**
 * Thrown when a Minestom block's collision shape is not the implementation type the platform layer
 * decomposes into boxes.
 *
 * <p>Minestom's {@code Shape} interface exposes only overall bounds and intersection predicates —
 * the list of boxes a shape is made of lives on the {@code ShapeImpl} record alone, so reading it
 * needs a cast to an implementation type. Every block's runtime shape is a {@code ShapeImpl} in
 * Minestom {@code 2026.08.28-26.2}, verified by walking the whole block registry, but that is a
 * property of the version rather than a guarantee of the API. This exception is what a Minestom
 * upgrade that changes it looks like: one named failure at one call site, rather than a
 * {@code ClassCastException} from an anonymous cast.
 */
public final class UnsupportedBlockShapeException extends RuntimeException {

    public UnsupportedBlockShapeException(String blockKey, String shapeType) {
        super(("the collision shape of %s is a %s, which the platform layer cannot decompose into "
                + "boxes — only Minestom's ShapeImpl exposes its bounding boxes")
                .formatted(blockKey, shapeType));
    }
}
