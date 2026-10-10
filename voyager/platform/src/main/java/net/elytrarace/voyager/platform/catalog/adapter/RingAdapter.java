package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;

import org.jetbrains.annotations.ApiStatus;

/**
 * Reads one ring: its centre, normal, radius, score and type, and its index, which the file may leave out.
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
 * angle.
 *
 * <p>An unrecognised type is refused rather than defaulted to {@code STANDARD}. The old loader
 * defaulted, which turns a misspelt {@code BOOST} into a ring that scores but does not boost — a
 * course that plays wrong while every file in it looks right.
 */
@ApiStatus.Internal
final class RingAdapter {

    private RingAdapter() {
    }

    /**
     * Reads the ring at {@code position} in its map's {@code rings} array.
     *
     * @param element  the ring object
     * @param position the zero-based position of the ring in the array, which is its index
     * @return the ring, indexed by {@code position}
     * @throws JsonParseException if the ring is malformed, or declares an index other than its position
     */
    static Ring read(JsonElement element, int position) {
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
        return new Ring(
                position,
                Vec3Adapter.read(JsonFields.required(json, "center", what)),
                Vec3Adapter.read(JsonFields.required(json, "normal", what)),
                JsonFields.number(json, "radius", what),
                JsonFields.integer(json, "points", what),
                RingType.byName(typeName).orElseThrow(() -> new JsonParseException(
                        "%s has the unrecognised type '%s'".formatted(what, typeName))));
    }
}
