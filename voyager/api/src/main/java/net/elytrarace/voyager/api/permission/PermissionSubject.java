package net.elytrarace.voyager.api.permission;

import java.util.Objects;
import java.util.UUID;

/**
 * Who asks a permission question: the console, or a player with the operator level the server reports for them.
 * The two variants are closed, so a switch over a subject needs no default branch.
 */
public sealed interface PermissionSubject {

    /** The highest operator level Minecraft defines. */
    int MAX_OPERATOR_LEVEL = 4;

    /** The server console. It is allowed every node. */
    record Console() implements PermissionSubject {
    }

    /**
     * A connected player.
     *
     * @param id            the player's UUID, the key a permission backend stores the player under
     * @param operatorLevel the operator level from 0 to {@link #MAX_OPERATOR_LEVEL}
     */
    record Player(UUID id, int operatorLevel) implements PermissionSubject {

        public Player {
            Objects.requireNonNull(id, "id must not be null");
            if (operatorLevel < 0 || operatorLevel > MAX_OPERATOR_LEVEL) {
                throw new IllegalArgumentException(
                        "operator level must be 0 to %d, was %d".formatted(MAX_OPERATOR_LEVEL, operatorLevel));
            }
        }
    }
}
