package net.elytrarace.voyager.platform.text;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Contract;

/**
 * Every sentence the setup server says to a builder, as a translation key with its arguments bound. The keys live in
 * {@code voyager_setup_en_US.properties}, shipped by voyager-setup and loaded with
 * {@link VoyagerTranslator#fromClasspath(String)}.
 */
public abstract class SetupMessages {

    /** The bundle the setup server loads. */
    public static final String BUNDLE_RESOURCE = "/voyager_setup_en_US.properties";

    private static final String ID_INVALID = "voyager.setup.id.invalid";
    private static final String MAP_CREATED = "voyager.setup.map.created";
    private static final String MAP_EXISTS = "voyager.setup.map.exists";
    private static final String MAP_OPENED = "voyager.setup.map.opened";
    private static final String MAP_NONE = "voyager.setup.map.none";
    private static final String REFUSED = "voyager.setup.refused";
    private static final String PLAYER_ONLY = "voyager.setup.player.only";
    private static final String WAND_GIVEN = "voyager.setup.wand.given";
    private static final String SPAWN_SAVED = "voyager.setup.spawn.saved";
    private static final String SPAWN_ELSEWHERE = "voyager.setup.spawn.elsewhere";
    private static final String SAVE_FAILED = "voyager.setup.save.failed";
    private static final String STATUS_SPAWN_SET = "voyager.setup.status.spawn.set";
    private static final String STATUS_SPAWN_UNSET = "voyager.setup.status.spawn.unset";
    private static final String STATUS_RINGS = "voyager.setup.status.rings";
    private static final String STATUS_PROBLEM_SPAWN = "voyager.setup.status.problem.spawn";
    private static final String STATUS_PROBLEM_RINGS = "voyager.setup.status.problem.rings";

    private SetupMessages() {
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component invalidId(String id) {
        return Component.translatable(ID_INVALID, Component.text(id));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component created(String id) {
        return Component.translatable(MAP_CREATED, Component.text(id));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component alreadyExists(String id) {
        return Component.translatable(MAP_EXISTS, Component.text(id));
    }

    @Contract(pure = true, value = "_, _ -> new")
    public static Component opened(String id, int ringCount) {
        return Component.translatable(MAP_OPENED, Component.text(id), Component.text(ringCount));
    }

    @Contract(pure = true)
    public static Component noMapOpen() {
        return Component.translatable(MAP_NONE);
    }

    /** A refusal that names what was refused, such as a file, in the words of the exception that refused it. */
    @Contract(pure = true, value = "_ -> new")
    public static Component refused(String detail) {
        return Component.translatable(REFUSED, Component.text(detail));
    }

    @Contract(pure = true)
    public static Component playerOnly() {
        return Component.translatable(PLAYER_ONLY);
    }

    @Contract(pure = true)
    public static Component wandGiven() {
        return Component.translatable(WAND_GIVEN);
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public static Component spawnSaved(double x, double y, double z) {
        return Component.translatable(SPAWN_SAVED, Component.text(x), Component.text(y), Component.text(z));
    }

    @Contract(pure = true)
    public static Component standInTheMap() {
        return Component.translatable(SPAWN_ELSEWHERE);
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component saveFailed(String id) {
        return Component.translatable(SAVE_FAILED, Component.text(id));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component statusSpawn(boolean set) {
        return Component.translatable(set ? STATUS_SPAWN_SET : STATUS_SPAWN_UNSET);
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component statusRings(int count) {
        return Component.translatable(STATUS_RINGS, Component.text(count));
    }

    @Contract(pure = true)
    public static Component statusProblemSpawn() {
        return Component.translatable(STATUS_PROBLEM_SPAWN);
    }

    @Contract(pure = true)
    public static Component statusProblemRings() {
        return Component.translatable(STATUS_PROBLEM_RINGS);
    }
}
