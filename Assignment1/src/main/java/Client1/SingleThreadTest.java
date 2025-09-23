package Client1;

import java.util.concurrent.CountDownLatch;

/**
 * SingleThreadTest class for running a single-threaded test
 * to evaluate the latency and throughput when sending 10000 requests.
 */
public class SingleThreadTest {

    public static void main(String[] args) throws InterruptedException {
        int numRequests = 10000;
        int numTests = 1;
        double totalThroughput = 0;
        long totalExecutionTimeSum = 0;

        for (int test = 1; test <= numTests; test++) {
            System.out.println("Running test #" + test);

            CountDownLatch completionLatch = new CountDownLatch(1);
            CountDownLatch triggerLatch = new CountDownLatch(1);
            SkThread skierThread = new SkThread(numRequests, completionLatch, triggerLatch);

            long startTime = System.currentTimeMillis();

            skierThread.start();
            completionLatch.await();
            triggerLatch.await();

            long endTime = System.currentTimeMillis();
            long totalExecutionTime = endTime - startTime;

            int success = SkClient1.counter.getSuccessfulPosts();
            int failed = SkClient1.counter.getFailedPosts();

            System.out.println("Test #" + test + " Results:");
            System.out.println("Total Requests: " + numRequests);
            System.out.println("Successful Requests: " + success);
            System.out.println("Failed Requests: " + failed);
            System.out.println("Total Execution Time: " + totalExecutionTime + " ms");

            double throughput = numRequests / (totalExecutionTime / 1000.0);
            System.out.println("Estimated Throughput for test #" + test + ": " + throughput + " requests/sec");

            totalThroughput += throughput;
            totalExecutionTimeSum += totalExecutionTime;

            SkClient1.counter = new Counter();
        }

        double averageThroughput = totalThroughput / numTests;
        long averageExecutionTime = totalExecutionTimeSum / numTests;

        System.out.println("\nAverage Results after " + numTests + " tests:");
        System.out.println("Average Execution Time: " + averageExecutionTime + " ms");
        System.out.println("Average Throughput: " + averageThroughput + " requests/sec");

        double phase1Throughput = averageThroughput * 32;

        System.out.println("\nPredicted Throughput:");
        System.out.println("Phase 1 (32 threads): " + phase1Throughput + " requests/sec");
    }
}
