package Client2;

import com.google.common.util.concurrent.RateLimiter;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.SkiersApi;
import io.swagger.client.model.LiftRide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkThreadTest {

    @BeforeEach
    void setUp() {
        RecordProcessor.clearRecords();
        SkThread.resetCircuitBreakerForTest();
        SkClient2.counter = new Counter();
    }

    @Test
    void retriesWithExponentialBackoffAndTracksFailures() throws Exception {
        BlockingQueue<LiftRide> queue = new ArrayBlockingQueue<>(1);
        queue.add(new LiftRide());

        CountDownLatch latch = new CountDownLatch(1);
        ApiClient apiClient = new ApiClient();
        RateLimiter rateLimiter = RateLimiter.create(Double.MAX_VALUE);

        SkiersApi api = mock(SkiersApi.class);
        when(api.writeNewLiftRideWithHttpInfo(ArgumentMatchers.any(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                .thenThrow(new ApiException(500, "server error"));

        SkiersApiFactory factory = mock(SkiersApiFactory.class);
        when(factory.create(ArgumentMatchers.any(ApiClient.class))).thenReturn(api);

        RecordingSleeper sleeper = new RecordingSleeper();

        SkThread thread = new SkThread(queue, latch, apiClient, 1, rateLimiter, factory, sleeper);

        thread.run();

        assertThat(sleeper.getCalls()).containsExactly(1000L, 2000L, 4000L, 8000L, 10000L);
        assertThat(SkClient2.counter.getFailedPosts()).isEqualTo(5);
        assertThat(latch.getCount()).isZero();
    }

    @Test
    void backoffIsCappedAtTenSeconds() {
        assertThat(SkThread.calculateBackoffMillis(0)).isEqualTo(1000L);
        assertThat(SkThread.calculateBackoffMillis(4)).isEqualTo(10000L);
        assertThat(SkThread.calculateBackoffMillis(10)).isEqualTo(10000L);
    }

    private static class RecordingSleeper implements Sleeper {
        private final List<Long> calls = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            calls.add(millis);
        }

        List<Long> getCalls() {
            return calls;
        }
    }
}
