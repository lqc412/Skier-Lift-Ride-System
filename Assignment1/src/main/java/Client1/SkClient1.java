package Client1;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Client1 class is responsible for simulating a multi-threaded client
 * that performs various phases of HTTP requests to a skier server.
 * The phases simulate the load of skier lift rides at a ski resort.
 */
public class SkClient1 {

    private static final double PHASE1_COMPLETION_THRESHOLD = 0.2;
    protected static Counter counter = new Counter();

    /**
     * The main method is the entry point of the program. It starts by executing
     * two main phases of multi-threaded requests with configurable thread counts
     * and request sizes. Each phase runs a set number of threads that send requests
     * to the server, and the results are calculated once all phases are complete.
     *
     * @param args command line arguments
     * @throws InterruptedException when the thread is interrupted while waiting
     * @throws IOException          when an I/O error occurs
     */
    public static void main(String[] args) throws InterruptedException, IOException {
        // Phase I
        long phase1Start = System.currentTimeMillis();
        int numP1Threads = 32;
        int numP1Requests = 1000;
        CountDownLatch phase1Latch = new CountDownLatch(numP1Threads);
        CountDownLatch phase1TriggerLatch = createPhase2TriggerLatch(numP1Threads);
        startPhase("Phase1", numP1Threads, numP1Requests, phase1Latch, phase1TriggerLatch);

        phase1TriggerLatch.await();

        // Phase II
        int numP2Threads = 112;
        int numP2Requests = 1500;
        CountDownLatch phase2Latch = new CountDownLatch(numP2Threads);
        startPhase("Phase2", numP2Threads, numP2Requests, phase2Latch, null);

        awaitPhaseCompletion("Phase1", phase1Latch, numP1Threads * numP1Requests);
        long phase1End = System.currentTimeMillis();

        long phase1Duration = phase1End - phase1Start;
        int phase1Requests = numP1Threads * numP1Requests;
        double phase1Throughput = (double) phase1Requests / (phase1Duration / 1000.0);

        System.out.println("\nPhase 1 Result:");
        System.out.println("-".repeat(30));
        System.out.println("Phase 1 Duration: " + phase1Duration + " ms");
        System.out.println("Phase 1 Throughput: " + phase1Throughput + " requests/sec");

        awaitPhaseCompletion("Phase2", phase2Latch, numP2Threads * numP2Requests);
        long end = System.currentTimeMillis();

        long wallTime = end - phase1Start;
        int success = counter.getSuccessfulPosts();
        int failed = counter.getFailedPosts();
        long throughput = 1000 * (success + failed) / wallTime;

        System.out.println("\nClient Result:");
        System.out.println("-".repeat(30));
        System.out.println("Number of successful requests: " + success);
        System.out.println("Number of failed requests: " + failed);
        System.out.println("Total wall time: " + wallTime + " ms");
        System.out.println("Total Throughput (requests/sec): " + throughput);
        System.out.println("Phase duration: " + (end - phase1Start) + " ms");
    }

    protected static CountDownLatch createPhase2TriggerLatch(int totalThreads) {
        return new CountDownLatch(calculatePhase2TriggerCount(totalThreads));
    }

    protected static int calculatePhase2TriggerCount(int totalThreads) {
        int triggerCount = (int) Math.ceil(totalThreads * PHASE1_COMPLETION_THRESHOLD);
        return Math.max(1, triggerCount);
    }

    /**
     * Spins up a phase with the provided number of threads and request count.
     * Each thread receives both the completion latch for its phase and an optional
     * trigger latch used to coordinate the start of the next phase.
     *
     * @param phaseName         the logging name of the phase
     * @param numberOfThreads   number of worker threads to start
     * @param numOfRequests     number of requests assigned to each thread
     * @param completionLatch   latch decremented when each thread completes its work
     * @param phaseTriggerLatch optional latch used to signal when the phase has reached the trigger threshold
     */
    private static void startPhase(String phaseName, int numberOfThreads, int numOfRequests,
                                   CountDownLatch completionLatch, CountDownLatch phaseTriggerLatch) {
        System.out.println(phaseName + " is starting...");
        for (int i = 0; i < numberOfThreads; i++) {
            SkThread skierThread = new SkThread(numOfRequests, completionLatch, phaseTriggerLatch);
            skierThread.start();
        }
    }

    /**
     * Blocks until the given phase completes and logs its completion message.
     *
     * @param phaseName     the logging name of the phase
     * @param latch         latch tracking remaining running threads for the phase
     * @param totalRequests total number of requests issued during the phase
     * @throws InterruptedException if waiting for completion is interrupted
     */
    private static void awaitPhaseCompletion(String phaseName, CountDownLatch latch, int totalRequests) throws InterruptedException {
        latch.await();
        System.out.println(phaseName + " completed " + totalRequests + " requests");
    }
}
