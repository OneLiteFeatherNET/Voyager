package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.persistence.SqlStateClassifier.Failure;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlStateClassifierTest {

    @Test
    void classTwentyTwoIsADataError() {
        assertThat(SqlStateClassifier.classify("22001"))
                .as("string data right truncation is a data error")
                .isEqualTo(Failure.DATA);
    }

    @Test
    void classTwentyThreeIsADataError() {
        assertThat(SqlStateClassifier.classify("23505"))
                .as("unique violation is a data error")
                .isEqualTo(Failure.DATA);
    }

    @Test
    void classEightIsAConnectionError() {
        assertThat(SqlStateClassifier.classify("08006"))
                .as("connection failure is retried")
                .isEqualTo(Failure.RETRY);
    }

    @Test
    void anUnknownStateIsRetried() {
        assertThat(SqlStateClassifier.classify("ZZ999"))
                .as("a state the classifier does not know is retried")
                .isEqualTo(Failure.RETRY);
    }

    @Test
    void aMissingStateIsRetried() {
        assertThat(SqlStateClassifier.classify(null))
                .as("no state means the connection failed or the cause is unknown")
                .isEqualTo(Failure.RETRY);
    }
}
