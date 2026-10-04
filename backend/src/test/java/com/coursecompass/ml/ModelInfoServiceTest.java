package com.coursecompass.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ModelInfoServiceTest {

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-03T12:00:00Z");

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

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void refreshesAfterTheTtlSoARetrainedModelShowsUp() throws Exception {
        MlClient ml = mock(MlClient.class);
        when(ml.modelInfo())
                .thenReturn(json.readTree("{\"model_version\": \"tree_v1\"}"))
                .thenReturn(json.readTree("{\"model_version\": \"tree_v2\"}"));
        MutableClock clock = new MutableClock();
        ModelInfoService service = new ModelInfoService(ml, Duration.ofMinutes(5), clock);

        assertThat(service.modelInfo().get("model_version").asText()).isEqualTo("tree_v1");
        clock.now = clock.now.plus(Duration.ofMinutes(4));
        assertThat(service.modelInfo().get("model_version").asText()).isEqualTo("tree_v1");
        verify(ml, times(1)).modelInfo();

        clock.now = clock.now.plus(Duration.ofMinutes(2));
        assertThat(service.modelInfo().get("model_version").asText()).isEqualTo("tree_v2");
    }

    @Test
    void servesLastGoodCopyWhenMlServiceIsDown() throws Exception {
        MlClient ml = mock(MlClient.class);
        when(ml.modelInfo())
                .thenReturn(json.readTree("{\"model_version\": \"tree_v1\"}"))
                .thenThrow(new MlServiceException(HttpStatus.SERVICE_UNAVAILABLE, "down"));
        MutableClock clock = new MutableClock();
        ModelInfoService service = new ModelInfoService(ml, Duration.ofMinutes(5), clock);

        service.modelInfo();
        clock.now = clock.now.plus(Duration.ofMinutes(10));
        assertThat(service.modelInfo().get("model_version").asText()).isEqualTo("tree_v1");
    }

    @Test
    void errorsWhenMlServiceIsDownAndNothingIsCached() {
        MlClient ml = mock(MlClient.class);
        when(ml.modelInfo()).thenThrow(new MlServiceException(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        assertThatThrownBy(() -> new ModelInfoService(ml, Duration.ofMinutes(5), new MutableClock()).modelInfo())
                .isInstanceOf(MlServiceException.class);
    }
}
