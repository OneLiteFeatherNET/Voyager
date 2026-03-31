package net.elytrarace.setup.validation;

import net.elytrarace.common.map.model.MapDTO;
import net.kyori.adventure.text.Component;
import java.util.ArrayList;
import java.util.List;

public class MapCompletenessValidator implements SetupValidator {

    @Override
    public List<ValidationIssue> validate(MapDTO map) {
        var issues = new ArrayList<ValidationIssue>();

        if (map.portals().isEmpty()) {
            issues.add(new ValidationIssue(Severity.ERROR,
                    Component.translatable("validation.error.no_portals")));
            return issues;
        }

        if (map.portals().size() == 1) {
            issues.add(new ValidationIssue(Severity.WARNING,
                    Component.translatable("validation.warn.single_portal")));
        }

        for (var portal : map.portals()) {
            boolean hasCenter = portal.locations().stream().anyMatch(l -> l.center());
            if (!hasCenter) {
                issues.add(new ValidationIssue(Severity.WARNING,
                        Component.translatable("validation.warn.portal_no_center")
                                .arguments(Component.text(portal.index()))));
            }
        }

        return issues;
    }
}
