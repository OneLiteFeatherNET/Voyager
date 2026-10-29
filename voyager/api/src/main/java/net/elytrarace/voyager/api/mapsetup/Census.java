package net.elytrarace.voyager.api.mapsetup;

/**
 * The chunk statuses of an imported copy. Every chunk record the copy holds is counted once, and an unreadable record
 * counts too, so the four counts add up to the records counted.
 *
 * @param full          chunks whose status is {@code full} or {@code minecraft:full}
 * @param partial       chunks with any other status: generation that did not finish
 * @param withoutStatus chunks that state no status
 * @param unreadable    records that could not be read or decoded
 */
public record Census(int full, int partial, int withoutStatus, int unreadable) {

    /** @return every chunk record counted, unreadable ones included */
    public int records() {
        return full + partial + withoutStatus + unreadable;
    }

    /**
     * The share of the records counted that are partial, rounded to the nearest whole percent.
     *
     * @return the percentage, 0 when no record was counted
     */
    public int partialPercent() {
        int records = records();
        return records == 0 ? 0 : (int) Math.round(100.0 * partial / records);
    }
}
