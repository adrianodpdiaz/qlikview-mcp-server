package com.qlikview.mcp.guard;

import com.qlikview.mcp.config.QlikViewProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputLimiterTest {

    @Test
    void limitLeavesTextUnchangedWhenUnderCap() {
        OutputLimiter limiter = limiterWith(1000, 1000);

        OutputLimiter.Result result = limiter.limit("short");

        assertThat(result.text()).isEqualTo("short");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void limitTruncatesTextWhenOverCap() {
        OutputLimiter limiter = limiterWith(5, 1000);

        OutputLimiter.Result result = limiter.limit("this text is too long");

        assertThat(result.text()).isEqualTo("this ");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void limitListLeavesListUnchangedWhenUnderCap() {
        OutputLimiter limiter = limiterWith(1000, 5);

        OutputLimiter.ListResult<String> result = limiter.limitList(List.of("a", "b"));

        assertThat(result.items()).containsExactly("a", "b");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void limitListTruncatesListWhenOverCap() {
        OutputLimiter limiter = limiterWith(1000, 2);

        OutputLimiter.ListResult<String> result = limiter.limitList(List.of("a", "b", "c", "d"));

        assertThat(result.items()).containsExactly("a", "b");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void limitListAtExactCapIsNotTruncated() {
        OutputLimiter limiter = limiterWith(1000, 3);

        OutputLimiter.ListResult<String> result = limiter.limitList(List.of("a", "b", "c"));

        assertThat(result.items()).containsExactly("a", "b", "c");
        assertThat(result.truncated()).isFalse();
    }

    private OutputLimiter limiterWith(int maxChars, int maxItems) {
        QlikViewProperties properties = new QlikViewProperties(
            List.of(),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(maxChars, maxItems));
        return new OutputLimiter(properties);
    }
}
