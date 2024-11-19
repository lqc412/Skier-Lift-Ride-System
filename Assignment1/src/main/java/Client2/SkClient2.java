package Client2;

import io.swagger.client.ApiClient;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.ConnectionPool;
import com.google.common.util.concurrent.RateLimiter;
import io.swagger.client.model.LiftRide;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SkClient2 {
    protected static Counter counter = new Counter();
    private static final int INITIAL_THREADS = 32;
    private static final int INITIAL_REQUESTS_PER_THREAD = 1000;
    private static final int TOTAL_REQUESTS = 200_000;
    private static final int PHASE2_THREADS = 168; // 固定 Phase 2 的线程数量
    private static final Logger LOGGER = Logger.getLogger(SkClient2.class.getName());

    public static void main(String[] args) throws InterruptedException, IOException {
        // Record the start time
        long startTime = System.currentTimeMillis();

        // Thread-safe queue to store LiftRide events
        BlockingQueue<LiftRide> rideQueue = new LinkedBlockingQueue<>();

        // Start generating 200,000 LiftRide events
        System.out.println("Generating 200,000 lift ride events...");
        LiftRideEventGenerator generator = new LiftRideEventGenerator(rideQueue, TOTAL_REQUESTS);
        generator.start();
        generator.join(); // Wait for event generation to complete

        // Shared ApiClient instance
        OkHttpClient httpClient = new OkHttpClient();
        httpClient.setConnectionPool(new ConnectionPool(500, 5, TimeUnit.MINUTES));
        ApiClient sharedClient = new ApiClient();
        sharedClient.setHttpClient(httpClient);
        sharedClient.setBasePath("http://35.90.159.5:8080/Server2_war");

        // 全局限流器，初始速率为每秒 5000 个请求
        RateLimiter globalRateLimiter = RateLimiter.create(5000);

        // Create ExecutorService to manage threads
        ExecutorService executorService = Executors.newCachedThreadPool();
        AtomicInteger remainingRequests = new AtomicInteger(TOTAL_REQUESTS);

        // Start Phase 1: Create 32 threads, each sending 1000 requests
        CountDownLatch phase1Latch = new CountDownLatch(INITIAL_THREADS);
        System.out.println("\nStarting Phase 1 with " + INITIAL_THREADS + " threads...");

        for (int i = 0; i < INITIAL_THREADS; i++) {
            executorService.submit(new SkThread(rideQueue, phase1Latch, sharedClient, INITIAL_REQUESTS_PER_THREAD, globalRateLimiter));
        }

        phase1Latch.await(); // Wait for all Phase 1 threads to complete
        remainingRequests.addAndGet(-(INITIAL_THREADS * INITIAL_REQUESTS_PER_THREAD));

        // Start Phase 2: Create 168 threads to send the remaining requests
        System.out.println("\nStarting Phase 2 with " + PHASE2_THREADS + " threads...");
        int requestsPerPhase2Thread = (remainingRequests.get() + PHASE2_THREADS - 1) / PHASE2_THREADS; // 平均分配剩余请求
        CountDownLatch phase2Latch = new CountDownLatch(PHASE2_THREADS);

        for (int i = 0; i < PHASE2_THREADS; i++) {
            int requestsToSend = Math.min(requestsPerPhase2Thread, remainingRequests.get());
            executorService.submit(new SkThread(rideQueue, phase2Latch, sharedClient, requestsToSend, globalRateLimiter));
            remainingRequests.addAndGet(-requestsToSend);
        }

        phase2Latch.await(); // Wait for all Phase 2 threads to complete
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.MINUTES);

        // Record the end time
        long endTime = System.currentTimeMillis();
        long wallTime = endTime - startTime;

        // Output results
        System.out.println("\nAll requests have been completed.");
        System.out.println("Number of successful requests: " + counter.getSuccessfulPosts());
        System.out.println("Number of failed requests: " + counter.getFailedPosts());

        // Call RecordProcessor to calculate and output the results
        String outputFilePath = "./output.csv";
        RecordProcessor recordProcessor = new RecordProcessor(outputFilePath, startTime, endTime);
        recordProcessor.calculateOutput();
    }
}
