package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.permission.PermissionNode;
import net.kyori.adventure.text.Component;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.exception.DraftAlreadyExistsException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftChangedException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftLocationConflictException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftNotFoundException;
import net.elytrarace.voyager.api.mapsetup.exception.InvalidDraftException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException;
import net.elytrarace.voyager.api.mapsetup.exception.InvalidMapIdException;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.permission.PlayerSubjects;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.text.SetupMessages;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.WorldFolders;
import net.elytrarace.voyager.platform.world.exception.UnknownWorldException;
import net.elytrarace.voyager.platform.world.exception.WorldAlreadyExistsException;
import net.elytrarace.voyager.setup.mapsetup.DraftEditor;
import net.elytrarace.voyager.setup.mapsetup.EditClaims;
import net.elytrarace.voyager.setup.mapsetup.MapStatus;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * {@code /map} — the builder's commands: {@code new}, {@code open}, {@code close}, {@code spawn} and {@code status}, and
 * the administrator's {@code unlock}.
 *
 * <p>Each command is a thin Minestom wrapper: the decisions are in {@link DraftEditor}, {@link MapStatus} and the store,
 * and the refusals are reported in {@link SetupMessages}. A command that changes the map saves it before it replies.
 */
public final class SetupCommands extends Command {

    private static final Pos START = new Pos(0, 64, 0);

    private final DraftStore store;
    private final BuilderSessions sessions;
    private final Path worlds;
    private final MapInstances instances;
    private final PermissionPolicy policy;
    private final SetupLobby lobby;

    /**
     * @param store     the draft store the commands save through
     * @param sessions  the open map of each builder, and the claims of the maps
     * @param worlds    the directory the world folders sit in
     * @param instances the loader of the map worlds
     * @param policy    answers whether a sender holds a node; every subcommand asks it for its own node
     * @param lobby     where a builder goes when the builder has no map open any more
     */
    public SetupCommands(DraftStore store, BuilderSessions sessions, Path worlds, MapInstances instances,
            PermissionPolicy policy, SetupLobby lobby) {
        super("map");
        this.store = store;
        this.sessions = sessions;
        this.worlds = worlds;
        this.instances = instances;
        this.policy = policy;
        this.lobby = lobby;

        ArgumentWord newId = ArgumentType.Word("id");
        addSyntax((sender, context) -> gated(sender, PermissionNode.VOYAGER_SETUP_USE,
                () -> create(sender, context.get(newId))), ArgumentType.Literal("new"), newId);
        ArgumentWord openId = ArgumentType.Word("id");
        addSyntax((sender, context) -> gated(sender, PermissionNode.VOYAGER_SETUP_USE,
                () -> open(sender, context.get(openId))), ArgumentType.Literal("open"), openId);
        addSyntax((sender, context) -> gated(sender, PermissionNode.VOYAGER_SETUP_USE, () -> close(sender)),
                ArgumentType.Literal("close"));
        addSyntax((sender, context) -> gated(sender, PermissionNode.VOYAGER_SETUP_USE, () -> spawn(sender)),
                ArgumentType.Literal("spawn"));
        addSyntax((sender, context) -> gated(sender, PermissionNode.VOYAGER_SETUP_USE, () -> status(sender)),
                ArgumentType.Literal("status"));
        ArgumentWord unlockId = ArgumentType.Word("id");
        addSyntax((sender, context) -> gated(sender, PermissionNode.VOYAGER_SETUP_UNLOCK,
                () -> unlock(sender, context.get(unlockId))), ArgumentType.Literal("unlock"), unlockId);
    }

    /**
     * Runs a subcommand for a sender who holds the node it needs; tells every other sender so, and changes nothing. The
     * check is made per subcommand, before any draft, world or claim is touched.
     */
    private void gated(CommandSender sender, PermissionNode node, Runnable subcommand) {
        if (policy.allows(PlayerSubjects.subjectOf(sender), node)) {
            subcommand.run();
        } else {
            sender.sendMessage(Messages.commandDenied());
        }
    }

    private void create(CommandSender sender, String raw) {
        if (!(sender instanceof Player builder)) {
            sender.sendMessage(SetupMessages.playerOnly());
            return;
        }
        MapId id;
        try {
            id = new MapId(raw);
        } catch (InvalidMapIdException exception) {
            builder.sendMessage(SetupMessages.invalidId(raw));
            return;
        }
        try {
            store.create(DraftEditor.skeleton(id));
        } catch (DraftAlreadyExistsException | WorldAlreadyExistsException exception) {
            builder.sendMessage(SetupMessages.alreadyExists(id.value()));
            return;
        } catch (UncheckedIOException exception) {
            builder.sendMessage(SetupMessages.refused(exception.getMessage()));
            return;
        }
        MapDraft draft = store.load(id);
        if (enter(builder, draft)) {
            builder.sendMessage(SetupMessages.created(id.value()));
        }
    }

    /**
     * The order of an open is fixed, and any refusal stops it before the next step: a builder who already holds the map
     * is told it is open and nothing reloads; otherwise the draft is loaded, the world found, the claim taken, and only
     * then the session installed.
     */
    private void open(CommandSender sender, String raw) {
        if (!(sender instanceof Player builder)) {
            sender.sendMessage(SetupMessages.playerOnly());
            return;
        }
        MapId id;
        try {
            id = new MapId(raw);
        } catch (InvalidMapIdException exception) {
            builder.sendMessage(SetupMessages.refused(exception.getMessage()));
            return;
        }
        Optional<MapSession> held = sessions.find(builder.getUuid()).filter(session -> session.id().equals(id));
        if (held.isPresent()) {
            builder.sendMessage(SetupMessages.opened(id.value(), held.get().draft().rings().size()));
            return;
        }
        MapDraft draft;
        try {
            draft = store.load(id);
        } catch (DraftNotFoundException | DraftLocationConflictException | InvalidDraftException exception) {
            builder.sendMessage(SetupMessages.refused(exception.getMessage()));
            return;
        }
        if (enter(builder, draft)) {
            builder.sendMessage(SetupMessages.opened(draft.id().value(), draft.rings().size()));
        }
    }

    /** Gives the builder's map back: its previews are removed, its claim is released, and the builder has no map open. */
    private void close(CommandSender sender) {
        if (!(sender instanceof Player builder)) {
            sender.sendMessage(SetupMessages.playerOnly());
            return;
        }
        Optional<MapSession> session = sessions.find(builder.getUuid());
        if (session.isEmpty()) {
            builder.sendMessage(SetupMessages.noMapOpen());
            return;
        }
        MapId id = session.get().id();
        sessions.forget(builder.getUuid());
        leave(builder);
        builder.sendMessage(SetupMessages.mapClosed(id.value()));
    }

    /**
     * Moves a builder who no longer holds a map into the lobby and takes the setup wand away: the lobby is where a builder
     * without a map waits, and the wand is useless there.
     */
    private void leave(Player builder) {
        builder.setInstance(lobby.instance(), lobby.spawn());
        Wand.takeFrom(builder);
    }

    /**
     * Releases the claim of a map whoever holds it, ends the holder's session on it, and names the holder. It does not
     * open the map for the sender.
     */
    private void unlock(CommandSender sender, String raw) {
        MapId id;
        try {
            id = new MapId(raw);
        } catch (InvalidMapIdException exception) {
            sender.sendMessage(SetupMessages.refused(exception.getMessage()));
            return;
        }
        Optional<EditClaims.Holder> released = sessions.unlock(id, this::leave);
        sender.sendMessage(released.isPresent()
                ? SetupMessages.mapUnlocked(id.value(), released.get().name())
                : SetupMessages.mapUnheld(id.value()));
    }

    private void spawn(CommandSender sender) {
        if (!(sender instanceof Player builder)) {
            sender.sendMessage(SetupMessages.playerOnly());
            return;
        }
        Optional<MapSession> session = sessions.find(builder.getUuid());
        if (session.isEmpty()) {
            builder.sendMessage(SetupMessages.noMapOpen());
            return;
        }
        if (builder.getInstance() != session.get().instance()) {
            builder.sendMessage(SetupMessages.standInTheMap());
            return;
        }
        Vec3 feet = Vectors.toDomain(builder.getPosition());
        try {
            session.get().commit(DraftEditor.withSpawn(session.get().draft(), feet));
            builder.sendMessage(SetupMessages.spawnSaved(feet.x(), feet.y(), feet.z()));
        } catch (DraftChangedException exception) {
            builder.sendMessage(SetupMessages.saveChanged(session.get().id().value()));
        } catch (DraftWriteFailedException exception) {
            builder.sendMessage(SetupMessages.saveFailed(session.get().id().value()));
        }
    }

    private void status(CommandSender sender) {
        if (!(sender instanceof Player builder)) {
            sender.sendMessage(SetupMessages.playerOnly());
            return;
        }
        Optional<MapSession> session = sessions.find(builder.getUuid());
        if (session.isEmpty()) {
            builder.sendMessage(SetupMessages.noMapOpen());
            return;
        }
        MapStatus status = MapStatus.of(session.get().draft());
        builder.sendMessage(SetupMessages.statusSpawn(status.spawnSet()));
        builder.sendMessage(SetupMessages.statusRings(status.ringCount()));
        for (MapStatus.Problem problem : status.blockingProblems()) {
            builder.sendMessage(problem == MapStatus.Problem.NO_SPAWN
                    ? SetupMessages.statusProblemSpawn()
                    : SetupMessages.statusProblemRings());
        }
    }

    /**
     * Finds the draft's world, claims the map for the builder, installs the session, moves the builder into the world and
     * gives the wand. Returns false, after telling the builder why, when the world cannot be found or another builder
     * holds the map; nothing is claimed or changed then.
     */
    private boolean enter(Player builder, MapDraft draft) {
        Instance world;
        try {
            world = instances.forWorld(draft.world());
        } catch (UnknownWorldException exception) {
            builder.sendMessage(SetupMessages.refused(exception.getMessage()));
            return false;
        }
        Optional<EditClaims.Holder> holder = sessions.claim(builder.getUuid(), builder.getUsername(), draft.id());
        if (holder.isPresent()) {
            builder.sendMessage(SetupMessages.claimHeld(draft.id().value(), holder.get().name()));
            return false;
        }
        sessions.open(builder.getUuid(), new MapSession(builder, store, draft, world));
        builder.setGameMode(GameMode.CREATIVE);
        Pos at = draft.spawn() == null ? START : Vectors.toMinestom(draft.spawn()).asPos();
        builder.setInstance(world, at);
        if (Wand.giveIfMissing(builder)) {
            for (Component line : SetupMessages.wandInstructions()) {
                builder.sendMessage(line);
            }
        }
        return true;
    }
}
