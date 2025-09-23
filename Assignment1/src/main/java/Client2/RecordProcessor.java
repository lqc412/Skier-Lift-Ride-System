package Client2;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class RecordProcessor {

    protected static CopyOnWriteArrayList<Record> records = new CopyOnWriteArrayList<>();
    private final Path csvPath;
    private final Path summaryPath;
    private final long startTime;
    private final long endTime;
    private final int successfulPosts;
    private final int failedPosts;
    private static final Logger LOGGER = Logger.getLogger(RecordProcessor.class.getName());

    public RecordProcessor(String filePath, long startTime, long endTime, int successfulPosts, int failedPosts, String summaryFilePath) {
        this.csvPath = Paths.get(filePath);
        this.summaryPath = summaryFilePath == null || summaryFilePath.isEmpty()
                ? Paths.get("reports", "summary.json")
                : Paths.get(summaryFilePath);
        this.startTime = startTime;
        this.endTime = endTime;
        this.successfulPosts = successfulPosts;
        this.failedPosts = failedPosts;
    }

    public void calculateOutput() throws IOException {
        Collections.sort(records);

        SummaryMetrics metrics = computeMetrics();
        writeCsv();
        writeSummary(metrics);
        logMetrics(metrics);
    }

    private SummaryMetrics computeMetrics() {
        int totalRequests = successfulPosts + failedPosts;
        double wallTimeInSeconds = Math.max(0.0, (endTime - startTime) / 1000.0);
        double throughput = wallTimeInSeconds > 0 ? successfulPosts / wallTimeInSeconds : 0.0;

        if (records.isEmpty()) {
            LOGGER.warning("No records available to process; latency metrics are unavailable.");
            return new SummaryMetrics(totalRequests, successfulPosts, failedPosts, throughput,
                    null, null, null, null, null);
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;

        for (Record record : records) {
            double latency = record.getLatency();
            sum += latency;
            max = Math.max(max, latency);
            min = Math.min(min, latency);
        }

        double median = percentile(records, 0.5);
        double p99 = percentile(records, 0.99);
        double mean = sum / records.size();

        return new SummaryMetrics(totalRequests, successfulPosts, failedPosts, throughput,
                mean, median, p99, min, max);
    }

    private void writeCsv() throws IOException {
        Path csvParent = csvPath.toAbsolutePath().getParent();
        if (csvParent != null) {
            Files.createDirectories(csvParent);
        }

        try (BufferedWriter csvWriter = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            csvWriter.append("startTime,requestType,latency,responseCode\n");
            for (Record record : records) {
                csvWriter.append(record.toString());
            }
        }
    }

    private void writeSummary(SummaryMetrics metrics) throws IOException {
        Path absoluteSummaryPath = summaryPath.toAbsolutePath();
        Path parent = absoluteSummaryPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String json = buildSummaryJson(metrics);
        Files.writeString(absoluteSummaryPath, json, StandardCharsets.UTF_8);
    }

    private void logMetrics(SummaryMetrics metrics) {
        System.out.println("Total requests sent: " + metrics.totalRequests());
        System.out.println("Successful requests: " + metrics.successfulRequests());
        System.out.println("Failed requests: " + metrics.failedRequests());
        System.out.println("Throughput: " + formatNumber(metrics.throughput()) + " requests/sec");

        if (metrics.meanLatency() == null) {
            System.out.println("No latency metrics available because no successful requests were recorded.");
        } else {
            System.out.println("Mean response time: " + formatNumber(metrics.meanLatency()) + " ms");
            System.out.println("Median response time: " + formatNumber(metrics.medianLatency()) + " ms");
            System.out.println("99th percentile response time: " + formatNumber(metrics.p99Latency()) + " ms");
            System.out.println("Min response time: " + formatNumber(metrics.minLatency()) + " ms");
            System.out.println("Max response time: " + formatNumber(metrics.maxLatency()) + " ms");
        }

        System.out.println("CSV results written to: " + csvPath.toAbsolutePath());
        System.out.println("Summary written to: " + summaryPath.toAbsolutePath());
    }

    private static double percentile(CopyOnWriteArrayList<Record> sortedRecords, double percentile) {
        if (sortedRecords.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute percentile for an empty record set.");
        }

        double boundedPercentile = Math.max(0.0, Math.min(1.0, percentile));
        int size = sortedRecords.size();
        double index = boundedPercentile * (size - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        double lowerValue = sortedRecords.get(lowerIndex).getLatency();
        double upperValue = sortedRecords.get(upperIndex).getLatency();

        if (lowerIndex == upperIndex) {
            return lowerValue;
        }

        double fraction = index - lowerIndex;
        return lowerValue + (upperValue - lowerValue) * fraction;
    }

    private String buildSummaryJson(SummaryMetrics metrics) {
        String throughput = formatNumber(metrics.throughput());
        String mean = formatOptionalNumber(metrics.meanLatency());
        String median = formatOptionalNumber(metrics.medianLatency());
        String p99 = formatOptionalNumber(metrics.p99Latency());
        String min = formatOptionalNumber(metrics.minLatency());
        String max = formatOptionalNumber(metrics.maxLatency());

        return "{\n" +
                "  \"totalRequests\": " + metrics.totalRequests() + ",\n" +
                "  \"successfulRequests\": " + metrics.successfulRequests() + ",\n" +
                "  \"failedRequests\": " + metrics.failedRequests() + ",\n" +
                "  \"throughput\": " + throughput + ",\n" +
                "  \"latency\": {\n" +
                "    \"mean\": " + mean + ",\n" +
                "    \"median\": " + median + ",\n" +
                "    \"p99\": " + p99 + ",\n" +
                "    \"min\": " + min + ",\n" +
                "    \"max\": " + max + "\n" +
                "  }\n" +
                "}\n";
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String formatOptionalNumber(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "null";
        }
        return formatNumber(value);
    }

    private record SummaryMetrics(int totalRequests,
                                  int successfulRequests,
                                  int failedRequests,
                                  double throughput,
                                  Double meanLatency,
                                  Double medianLatency,
                                  Double p99Latency,
                                  Double minLatency,
                                  Double maxLatency) {
    }
}
