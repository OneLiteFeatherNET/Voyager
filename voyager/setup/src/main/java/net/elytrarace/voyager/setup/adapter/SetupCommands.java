package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.permission.PermissionNode;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.exception.DraftAlreadyExistsException;
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
 * {@code /map} — the builder's commands: {@code new}, {@code open}, {@code spawn} and {@code status}.
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

    /**
     * @param store     the draft store the commands save through
     * @param sessions  the open map of each builder
     * @param worlds    the directory the world folders sit in
     * @param instances the loader of the map worlds
     * @param policy    answers whether a sender holds {@code voyager.setup.use}; every subcommand asks it
     */
    public SetupCommands(DraftStore store, BuilderSessions sessions, Path worlds, MapInstances instances,
            PermissionPolicy policy) {
        super("map");
        this.store = store;
        this.sessions = sessions;
        this.worlds = worlds;
        this.instances = instances;
        this.policy = policy;

        ArgumentWord newId = ArgumentType.Word("id");
        addSyntax((sender, context) -> gated(sender, () -> create(sender, context.get(newId))),
                ArgumentType.Literal("new"), newId);
        ArgumentWord openId = ArgumentType.Word("id");
        addSyntax((sender, context) -> gated(sender, () -> open(sender, context.get(openId))),
                ArgumentType.Literal("open"), openId);
        addSyntax((sender, context) -> gated(sender, () -> spawn(sender)), ArgumentType.Literal("spawn"));
        addSyntax((sender, context) -> gated(sender, () -> status(sender)), ArgumentType.Literal("status"));
    }

    /**
     * Runs a subcommand for a sender who holds {@code voyager.setup.use}; tells every other sender so, and changes
     * nothing. The check is made per subcommand, so a refused builder never reaches a draft or a world.
     */
    private void gated(CommandSender sender, Runnable subcommand) {
        if (policy.allows(PlayerSubjects.subjectOf(sender), PermissionNode.VOYAGER_SETUP_USE)) {
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
        enter(builder, draft);
        builder.sendMessage(SetupMessages.created(id.value()));
    }

    private void open(CommandSender sender, String raw) {
        if (!(sender instanceof Player builder)) {
            sender.sendMessage(SetupMessages.playerOnly());
            return;
        }
        MapDraft draft;
        try {
            draft = store.load(new MapId(raw));
        } catch (InvalidMapIdException | DraftNotFoundException | DraftLocationConflictException
                 | InvalidDraftException exception) {
            builder.sendMessage(SetupMessages.refused(exception.getMessage()));
            return;
        }
        enter(builder, draft);
        builder.sendMessage(SetupMessages.opened(draft.id().value(), draft.rings().size()));
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

    /** Opens the draft's world for the builder, moves the builder into it and gives the wand. */
    private void enter(Player builder, MapDraft draft) {
        Instance world;
        try {
            world = instances.forWorld(draft.world());
        } catch (UnknownWorldException exception) {
            builder.sendMessage(SetupMessages.refused(exception.getMessage()));
            return;
        }
        sessions.open(builder.getUuid(), new MapSession(store, draft, world));
        builder.setGameMode(GameMode.CREATIVE);
        Pos at = draft.spawn() == null ? START : Vectors.toMinestom(draft.spawn()).asPos();
        builder.setInstance(world, at);
        if (Wand.giveIfMissing(builder)) {
            builder.sendMessage(SetupMessages.wandGiven());
        }
    }
}
