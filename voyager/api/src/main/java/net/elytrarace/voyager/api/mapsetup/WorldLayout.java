package net.elytrarace.voyager.api.mapsetup;

/** The two folder layouts an importable world may use for its overworld region files. */
public enum WorldLayout {

    /** Region files in {@code region/} at the world root. */
    REGION_AT_ROOT("region/"),
    /** Region files in {@code dimensions/minecraft/overworld/region/}. */
    DIMENSION_OVERWORLD("dimensions/minecraft/overworld/region/");

    private final String folder;

    WorldLayout(String folder) {
        this.folder = folder;
    }

    /** @return the folder of the layout, relative to the world folder, with a trailing slash */
    public String folder() {
        return folder;
    }
}
