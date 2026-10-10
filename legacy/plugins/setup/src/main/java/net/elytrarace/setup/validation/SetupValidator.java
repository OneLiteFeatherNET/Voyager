package net.elytrarace.setup.validation;

import net.elytrarace.common.map.model.MapDTO;
import java.util.List;

public interface SetupValidator {
    List<ValidationIssue> validate(MapDTO map);
}
