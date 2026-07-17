package ru.eltc.deliverymonitor.domain.timeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IssueKeyExtractor} — pure regex, no Spring / DB.
 */
class IssueKeyExtractorTest {

    private final IssueKeyExtractor extractor = new IssueKeyExtractor();

    @ParameterizedTest
    @CsvSource({
            "feature/MPTPSUPP-1234, MPTPSUPP-1234",
            "MPTPSUPP-1: fix banner, MPTPSUPP-1",
            "feature/MPTPSUPP-90001_demo-banner, MPTPSUPP-90001",
            "ABC-9, ABC-9",
            "prefix MPTPSUPP-42 suffix, MPTPSUPP-42",
            "MPTPSUPP-1 and MPTPSUPP-2, MPTPSUPP-1"
    })
    void extractsFirstIssueKey(String text, String expected) {
        assertThat(extractor.extract(text)).isEqualTo(Optional.of(expected));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "feature/orphan-no-jira-key", "no-key-here", "mptp-123", "ABC_"})
    void returnsEmptyWhenNoKey(String text) {
        assertThat(extractor.extract(text)).isEmpty();
    }

    @Test
    void doesNotMatchLowercaseProjectPrefix() {
        assertThat(extractor.extract("feature/mptpsupp-123")).isEmpty();
    }
}
