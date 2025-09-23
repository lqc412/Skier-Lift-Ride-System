package Client2;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordProcessor {

    protected static CopyOnWriteArrayList<Record> records = new CopyOnWriteArrayList<>();
    private String filePath;
    private long startTime;
    private long endTime;

    public RecordProcessor(String filePath, long startTime, long endTime) {
        this.filePath = filePath;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void calculateOutput() throws IOException {
        Statistics statistics = calculateStatistics();

        // 打印统计结果
        System.out.println(statistics.getTotalRequests());
        System.out.println("Mean response time: " + statistics.getMean() + " ms");
        System.out.println("Median response time: " + statistics.getMedian() + " ms");
        System.out.println("Throughput: " + statistics.getThroughput() + " requests/sec");
        System.out.println("99th percentile response time: " + statistics.getP99() + " ms");
        System.out.println("Min response time: " + statistics.getMin() + " ms");
        System.out.println("Max response time: " + statistics.getMax() + " ms");

        // 写入 CSV 文件
        try (FileWriter csvWriter = new FileWriter(filePath)) {
            csvWriter.append("startTime,requestType,latency,responseCode\n");
            for (Record record : statistics.getSortedRecords()) {
                csvWriter.append(record.toString());
            }
        }
    }

    public Statistics calculateStatistics() {
        if (records.isEmpty()) {
            throw new IllegalStateException("No records available to calculate statistics.");
        }

        CopyOnWriteArrayList<Record> snapshot = new CopyOnWriteArrayList<>(records);
        Collections.sort(snapshot);

        double min = Double.MAX_VALUE;
        double max = 0;
        double sum = 0;

        double median = snapshot.get(snapshot.size() / 2).getLatency();
        double p99 = snapshot.get((int) (0.99 * snapshot.size())).getLatency();

        for (Record record : snapshot) {
            sum += record.getLatency();
            max = Math.max(max, record.getLatency());
            min = Math.min(min, record.getLatency());
        }

        double mean = sum / snapshot.size();
        double wallTimeInSeconds = (endTime - startTime) / 1000.0;
        double throughput = snapshot.size() / wallTimeInSeconds;

        return new Statistics(snapshot, mean, median, throughput, p99, min, max);
    }

    public static void clearRecords() {
        records.clear();
    }

    public static class Statistics {
        private final CopyOnWriteArrayList<Record> sortedRecords;
        private final double mean;
        private final double median;
        private final double throughput;
        private final double p99;
        private final double min;
        private final double max;

        private Statistics(CopyOnWriteArrayList<Record> sortedRecords, double mean, double median, double throughput,
                           double p99, double min, double max) {
            this.sortedRecords = sortedRecords;
            this.mean = mean;
            this.median = median;
            this.throughput = throughput;
            this.p99 = p99;
            this.min = min;
            this.max = max;
        }

        public CopyOnWriteArrayList<Record> getSortedRecords() {
            return new CopyOnWriteArrayList<>(sortedRecords);
        }

        public int getTotalRequests() {
            return sortedRecords.size();
        }

        public double getMean() {
            return mean;
        }

        public double getMedian() {
            return median;
        }

        public double getThroughput() {
            return throughput;
        }

        public double getP99() {
            return p99;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }
    }
}
