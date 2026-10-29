package net.elytrarace.voyager.api.permission;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The permission nodes Voyager gates its own actions with. The list is fixed: a node Voyager does not own is asked
 * about through {@link PermissionPolicy#allows(PermissionSubject, String)} instead.
 */
public enum PermissionNode {

    /** {@code /race reload}. */
    VOYAGER_COMMAND_RACE_RELOAD("voyager.command.race.reload"),
    /** {@code /race start}, registered in dev mode only. */
    VOYAGER_COMMAND_RACE_START("voyager.command.race.start"),
    /** {@code /race skip}, registered in dev mode only. */
    VOYAGER_COMMAND_RACE_SKIP("voyager.command.race.skip"),
    /** {@code /stop}. The console is always allowed. */
    VOYAGER_COMMAND_STOP("voyager.command.stop"),
    /** Every setup command and the wand. */
    VOYAGER_SETUP_USE("voyager.setup.use"),
    /** {@code /map import}, on top of {@link #VOYAGER_SETUP_USE}: an operator can grant building without importing. */
    VOYAGER_SETUP_IMPORT("voyager.setup.import"),
    /** {@code /map unlock}, the one command that releases a claim another builder holds. Gated by this node alone. */
    VOYAGER_SETUP_UNLOCK("voyager.setup.unlock");

    private static final Map<String, PermissionNode> BY_VALUE;

    static {
        Map<String, PermissionNode> byValue = new HashMap<>();
        for (PermissionNode node : values()) {
            byValue.put(node.value, node);
        }
        BY_VALUE = Collections.unmodifiableMap(byValue);
    }

    private final String value;

    PermissionNode(String value) {
        this.value = value;
    }

    /**
     * The node name as a permission backend sees it.
     *
     * @return the node name, for example {@code voyager.command.race.reload}
     */
    public String value() {
        return value;
    }

    /**
     * Finds the node with the given name.
     *
     * @param value the node name to look up
     * @return the node, or {@code null} when Voyager does not own a node with that name
     */
    public static @Nullable PermissionNode byValue(String value) {
        return BY_VALUE.get(value);
    }
}
