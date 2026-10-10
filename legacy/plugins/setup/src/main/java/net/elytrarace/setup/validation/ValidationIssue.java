package net.elytrarace.setup.validation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public record ValidationIssue(Severity severity, Component message) {

    public Component formatted() {
        NamedTextColor color = switch (severity) {
            case INFO -> NamedTextColor.AQUA;
            case WARNING -> NamedTextColor.YELLOW;
            case ERROR -> NamedTextColor.RED;
        };
        return Component.translatable("validation.issue.prefix", Component.text(severity.name())).color(color).append(message);
    }
}
