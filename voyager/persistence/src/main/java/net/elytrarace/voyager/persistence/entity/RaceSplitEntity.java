package net.elytrarace.voyager.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** The game tick at which a racer passed one ring. The key is the racer's result and the ring's index. Table {@code race_split}. */
@Entity
@Table(name = "race_split")
@IdClass(RaceSplitEntity.Key.class)
public class RaceSplitEntity {

    @Id
    @Column(name = "race_result_id", nullable = false)
    private long raceResultId;

    @Id
    @Column(name = "ring_index", nullable = false)
    private int ringIndex;

    @Column(name = "game_tick", nullable = false)
    private int gameTick;

    protected RaceSplitEntity() {
    }

    /** The composite key of {@link RaceSplitEntity}; Hibernate needs its equality and serialisation. */
    public static final class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private long raceResultId;
        private int ringIndex;

        public Key() {
        }

        public Key(long raceResultId, int ringIndex) {
            this.raceResultId = raceResultId;
            this.ringIndex = ringIndex;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && raceResultId == key.raceResultId
                    && ringIndex == key.ringIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(raceResultId, ringIndex);
        }
    }
}
