package Client1;

import config.AppConfig;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.ApiResponse;
import io.swagger.client.api.SkiersApi;
import io.swagger.client.model.LiftRide;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * SkThread represents a thread that sends a set number of POST requests
 * to the server to simulate skier lift rides. The thread handles retries
 * and tracks the success or failure of each request.
 */
public class SkThread extends Thread {

    private static final int RETRY_LIMIT = 5;
    private final int requestCount;
    private final CountDownLatch latch;

    /**
     * Constructs a SkThread instance with the specified number of requests
     * and a CountDownLatch to track the thread's completion.
     *
     * @param requestCount the number of POST requests to send
     * @param latch        the latch to count down when the thread completes
     */
    public SkThread(int requestCount, CountDownLatch latch) {
        this.requestCount = requestCount;
        this.latch = latch;
    }

    /**
     * Executes the thread's logic by sending POST requests to simulate lift rides.
     * Each request is retried up to the retry limit in case of failure.
     */
    @Override
    public void run() {
        SkiersApi apiInstance = new SkiersApi();
        ApiClient client = apiInstance.getApiClient();
        String serverUrl = AppConfig.getClient1BaseUrl();
        client.setBasePath(serverUrl);
        Random random = new Random();

        for (int i = 0; i < requestCount; i++) {
            LiftRide ride = new LiftRide().time(random.nextInt(361)).liftID(random.nextInt(41));
            SkEvent skierEvent = new SkEvent();

            for (int retry = 0; retry < RETRY_LIMIT; retry++) {
                try {
                    long startTime = System.currentTimeMillis();
                    ApiResponse<Void> response = apiInstance.writeNewLiftRideWithHttpInfo(
                            ride,
                            skierEvent.getResortID(),
                            skierEvent.getSeasonID(),
                            skierEvent.getDayID(),
                            skierEvent.getSkierID()
                    );
                    SkClient1.counter.incrementSuccessfulPost(1);
                    long endTime = System.currentTimeMillis();
                    break;
                } catch (ApiException e) {
                    SkClient1.counter.incrementFailedPost(1);
                    System.err.println("Exception when calling SkierApi#writeNewLiftRide, attempt " + (retry + 1) + " failed");
                    e.printStackTrace();
                }
            }

            // Phase I completion triggers Phase II
//            if (i % 1000 == 0 && i != 0) {
//                System.out.println("Thread " + Thread.currentThread().getId() + i);
//            }
        }

        try {
            SkClient1.latchToPhase2.countDown();
            latch.countDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
