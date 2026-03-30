package net.elytrarace.setup.wizard;

public enum WizardStep {
    ENTER_SETUP("wizard.step.enter_setup", "wizard.hint.enter_setup"),
    CREATE_CUP("wizard.step.create_cup", "wizard.hint.create_cup"),
    LOAD_WORLD("wizard.step.load_world", "wizard.hint.load_world"),
    CREATE_MAP("wizard.step.create_map", "wizard.hint.create_map"),
    PLACE_PORTALS("wizard.step.place_portals", "wizard.hint.place_portals"),
    ADD_GUIDE_POINTS("wizard.step.add_guide_points", "wizard.hint.add_guide_points"),
    PREVIEW_SPLINE("wizard.step.preview_spline", "wizard.hint.preview_spline"),
    TEST_FLY("wizard.step.test_fly", "wizard.hint.test_fly");

    private final String titleKey;
    private final String hintKey;

    WizardStep(String titleKey, String hintKey) {
        this.titleKey = titleKey;
        this.hintKey = hintKey;
    }

    public String titleKey() { return titleKey; }
    public String hintKey() { return hintKey; }

    public float progress() {
        return (float) (ordinal() + 1) / values().length;
    }
}
