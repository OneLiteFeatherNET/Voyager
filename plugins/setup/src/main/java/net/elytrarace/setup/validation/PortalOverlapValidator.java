package net.elytrarace.setup.validation;

import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.MapDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.kyori.adventure.text.Component;
import java.util.ArrayList;
import java.util.List;

public class PortalOverlapValidator implements SetupValidator {

    private static final double MIN_DISTANCE = 5.0;

    @Override
    public List<ValidationIssue> validate(MapDTO map) {
        var issues = new ArrayList<ValidationIssue>();
        var portals = map.portals().stream().toList();

        for (int i = 0; i < portals.size(); i++) {
            for (int j = i + 1; j < portals.size(); j++) {
                var a = portals.get(i);
                var b = portals.get(j);
                var centerA = center(a);
                var centerB = center(b);
                if (centerA == null || centerB == null) continue;

                double dx = centerA.x() - centerB.x();
                double dy = centerA.y() - centerB.y();
                double dz = centerA.z() - centerB.z();
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) < MIN_DISTANCE) {
                    issues.add(new ValidationIssue(Severity.WARNING,
                            Component.translatable("validation.warn.portal_overlap")
                                    .arguments(Component.text(a.index()), Component.text(b.index()))));
                }
            }
        }
        return issues;
    }

    private LocationDTO center(PortalDTO portal) {
        return portal.locations().stream().filter(LocationDTO::center).findFirst().orElse(null);
    }
}
