package Client2;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class RecordProcessor {

    protected static CopyOnWriteArrayList<Record> records = new CopyOnWriteArrayList<>();
    private String filePath;
    private long startTime;
    private long endTime;
    private static final Logger LOGGER = Logger.getLogger(RecordProcessor.class.getName());

    public RecordProcessor(String filePath, long startTime, long endTime) {
        this.filePath = filePath;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void calculateOutput() throws IOException {
        Collections.sort(records);

        try (FileWriter csvWriter = new FileWriter(filePath)) {
            csvWriter.append("startTime,requestType,latency,responseCode\n");

            if (records.isEmpty()) {
                LOGGER.warning("No records available to process; skipping metric calculations.");
                return;
            }

            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            double sum = 0;

            for (Record record : records) {
                sum += record.getLatency();
                max = Math.max(max, record.getLatency());
                min = Math.min(min, record.getLatency());
            }

            double median = percentile(records, 0.5);
            double p99 = percentile(records, 0.99);
            double mean = sum / records.size();
            double wallTimeInSeconds = (endTime - startTime) / 1000.0; // 总运行时间，单位为秒
            double throughput = records.size() / wallTimeInSeconds; // 吞吐量：请求数 / 总运行时间

            // 打印统计结果
            System.out.println(records.size());
            System.out.println("Mean response time: " + mean + " ms");
            System.out.println("Median response time: " + median + " ms");
            System.out.println("Throughput: " + throughput + " requests/sec");
            System.out.println("99th percentile response time: " + p99 + " ms");
            System.out.println("Min response time: " + min + " ms");
            System.out.println("Max response time: " + max + " ms");

            for (Record record : records) {
                csvWriter.append(record.toString());
            }
        }
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
}
