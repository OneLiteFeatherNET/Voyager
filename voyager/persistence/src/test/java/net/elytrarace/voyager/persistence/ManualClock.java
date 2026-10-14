package net.elytrarace.voyager.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock that moves only when a test says so. Each test builds its own instance. */
final class ManualClock extends Clock {

    private Instant now;

    ManualClock(Instant start) {
        this.now = start;
    }

    void advance(Duration by) {
        now = now.plus(by);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
