package net.elytrarace.setup.validation;

import net.elytrarace.common.map.model.MapDTO;
import java.util.ArrayList;
import java.util.List;

public class CompositeValidator implements SetupValidator {

    private final List<SetupValidator> validators;

    public CompositeValidator(SetupValidator... validators) {
        this.validators = List.of(validators);
    }

    @Override
    public List<ValidationIssue> validate(MapDTO map) {
        var issues = new ArrayList<ValidationIssue>();
        for (var v : validators) {
            issues.addAll(v.validate(map));
        }
        return issues;
    }
}
