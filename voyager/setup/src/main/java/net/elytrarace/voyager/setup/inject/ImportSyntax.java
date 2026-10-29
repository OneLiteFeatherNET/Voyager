package net.elytrarace.voyager.setup.inject;

import net.elytrarace.voyager.platform.permission.PlayerSubjects;
import net.elytrarace.voyager.setup.adapter.ImportCommand;
import net.elytrarace.voyager.setup.adapter.SetupCommands;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;

/**
 * Attaches the {@code import} syntax to the {@code /map} command. The syntax is Minestom's and so lives in the composition
 * root, where Minestom is allowed; the import itself is {@link ImportCommand}, which names no Minestom type.
 */
final class ImportSyntax {

    private ImportSyntax() {
    }

    /**
     * Adds {@code /map import <folder> <id>} to the map command.
     *
     * @param map           the map command the syntax joins
     * @param importCommand the import the syntax runs
     */
    static void attach(SetupCommands map, ImportCommand importCommand) {
        ArgumentWord folder = ArgumentType.Word("folder");
        ArgumentWord id = ArgumentType.Word("id");
        map.addSyntax((sender, context) -> importCommand.run(
                        PlayerSubjects.subjectOf(sender),
                        context.get(folder),
                        context.get(id),
                        line -> sender.sendMessage(line)),
                ArgumentType.Literal("import"), folder, id);
    }
}
