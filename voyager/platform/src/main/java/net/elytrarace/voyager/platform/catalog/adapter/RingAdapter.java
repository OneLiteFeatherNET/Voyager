package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingShape;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.exception.InvalidRingException;

import org.jetbrains.annotations.ApiStatus;

/**
 * Reads one ring: its centre, normal, radius, score and type, and its index, which the file may leave out.
 *
 * <p>A ring is a circle, or an oval when it states the pair {@code radiusMinor} and {@code majorAxis}. An oval needs
 * schema version 3: a file of an older version that states either field is refused here, rather than being read as a
 * circle of its semi-major radius by a server that does not know the field. The pair must come together.
 *
 * <p>A ring's index is its zero-based position in the {@code rings} array, so it is derived from that
 * position rather than repeated in every file. A file that does state an index must state the position,
 * and a contradiction is refused here, with the position named, rather than corrected silently — the
 * refusal is the only way a hand-edited file tells its author that two numbers disagree.
 *
 * <p>The normal is read as stored rather than re-derived — that is decision D-E3-5, and the
 * conversion that produced these files is where the derivation happened, once, with the flight path
 * in hand to settle the sign. {@link Ring} still checks that what arrives is unit length, so a file
 * edited by hand into a normal of length 1.4 is refused here rather than quietly scoring rings at an
 * angle. A radius or a point count outside its bounds is refused the same way, with the file and the position named.
 *
 * <p>An unrecognised type is refused rather than defaulted to {@code STANDARD}. The old loader
 * defaulted, which turns a misspelt {@code BOOST} into a ring that scores but does not boost — a
 * course that plays wrong while every file in it looks right.
 */
@ApiStatus.Internal
final class RingAdapter {

    /** The schema version that first states an oval ring. */
    static final int OVAL_SCHEMA_VERSION = 3;

    private static final String RADIUS_MINOR = "radiusMinor";
    private static final String MAJOR_AXIS = "majorAxis";

    private RingAdapter() {
    }

    /**
     * Reads the ring at {@code position} in its file's {@code rings} array.
     *
     * @param element  the ring object
     * @param position the zero-based position of the ring in the array, which is its index
     * @param version  the file's schema version, as {@link SchemaVersion#read} returned it
     * @param file     the file's description for messages, such as {@code "map 'sky-drift'"}
     * @return the ring, indexed by {@code position}
     * @throws JsonParseException   if the ring is malformed, declares an index other than its position, or states
     *                              an oval that its file's version or its own pair does not allow
     * @throws InvalidRingException if a value is outside what a ring may hold, named with the file and the position
     */
    static Ring read(JsonElement element, int position, int version, String file) {
        JsonObject json = JsonFields.object(element, "ring at position %s".formatted(position));
        String what = "ring %s".formatted(position);

        if (JsonFields.present(json, "index")) {
            int declared = JsonFields.integer(json, "index", what);
            if (declared != position) {
                throw new JsonParseException(("ring at position %s declares index %s; an index must equal "
                        + "its position in the rings array, or be left out").formatted(position, declared));
            }
        }

        String typeName = JsonFields.string(json, "type", what);
        Vec3 center = Vec3Adapter.read(JsonFields.required(json, "center", what));
        Vec3 normal = Vec3Adapter.read(JsonFields.required(json, "normal", what));
        double radius = JsonFields.number(json, "radius", what);
        int points = JsonFields.integer(json, "points", what);
        RingType type = RingType.byName(typeName).orElseThrow(() -> new JsonParseException(
                "%s has the unrecognised type '%s'".formatted(what, typeName)));
        try {
            RingShape shape = shape(json, radius, normal, file, position, version, what);
            return new Ring(position, center, normal, shape, points, type);
        } catch (InvalidRingException exception) {
            throw InvalidRingException.atPosition(file, position, exception);
        }
    }

    /**
     * The shape a ring states: a circle when it states no oval field, otherwise an oval. An oval is refused unless its
     * file is of version 3 and it states both fields. Equal radii make the circle of that radius, after the axis is
     * checked as stated.
     */
    private static RingShape shape(JsonObject json, double radius, Vec3 normal, String file, int position,
            int version, String what) {
        boolean statesMinor = JsonFields.present(json, RADIUS_MINOR);
        boolean statesAxis = JsonFields.present(json, MAJOR_AXIS);
        if (!statesMinor && !statesAxis) {
            return new RingShape.Circle(radius);
        }
        String field = statesMinor ? RADIUS_MINOR : MAJOR_AXIS;
        if (version < OVAL_SCHEMA_VERSION) {
            throw new JsonParseException(("%s, ring at position %s: states '%s', which needs schemaVersion %s, "
                    + "and this file is schemaVersion %s").formatted(file, position, field, OVAL_SCHEMA_VERSION,
                            version));
        }
        if (statesMinor != statesAxis) {
            String missing = statesMinor ? MAJOR_AXIS : RADIUS_MINOR;
            throw new JsonParseException(("%s, ring at position %s: states '%s' but not '%s'; an oval states both")
                    .formatted(file, position, field, missing));
        }
        double radiusMinor = JsonFields.number(json, RADIUS_MINOR, what);
        Vec3 majorAxis = Vec3Adapter.read(JsonFields.required(json, MAJOR_AXIS, what));
        return RingShape.of(radius, radiusMinor, majorAxis, normal);
    }
}
