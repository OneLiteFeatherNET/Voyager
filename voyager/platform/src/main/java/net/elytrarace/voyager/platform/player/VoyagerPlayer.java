package net.elytrarace.voyager.platform.player;

import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.platform.permission.PlayerSubjects;
import net.kyori.adventure.permission.PermissionChecker;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.util.TriState;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;

/**
 * A Minestom player that answers Adventure's {@link PermissionChecker#POINTER} through the {@link PermissionPolicy} port.
 *
 * <p>Minestom has no permission system and installs no pointer, so a player without this class answers every
 * permission question with nothing. CloudNet's bridge extension reads this pointer for its maintenance bypass and its
 * task checks (ADR-0026), which is why every player of the game and setup servers is created as this class, through
 * {@link VoyagerPlayerProvider}. The pointer holds no state of its own: each question is put to the policy with the
 * player's identity as it is at that moment.
 */
public final class VoyagerPlayer extends Player {

    private final Pointers pointers;

    VoyagerPlayer(PlayerConnection connection, GameProfile gameProfile, PermissionPolicy policy) {
        super(connection, gameProfile);
        PermissionChecker checker = node -> policy.allows(PlayerSubjects.of(this), node) ? TriState.TRUE : TriState.FALSE;
        this.pointers = super.pointers().toBuilder()
                .withStatic(PermissionChecker.POINTER, checker)
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The pointers of this player, which include the permission pointer.
     */
    @Override
    public Pointers pointers() {
        return this.pointers;
    }
}
