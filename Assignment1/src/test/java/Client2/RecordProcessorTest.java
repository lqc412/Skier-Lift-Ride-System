package Client2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordProcessorTest {

    @BeforeEach
    void setUp() {
        RecordProcessor.clearRecords();
    }

    @Test
    void calculatesStatisticsForSortedSnapshot() {
        RecordProcessor.records.add(new Record(0, "POST", 40, 200));
        RecordProcessor.records.add(new Record(0, "POST", 10, 200));
        RecordProcessor.records.add(new Record(0, "POST", 30, 200));
        RecordProcessor.records.add(new Record(0, "POST", 20, 200));

        RecordProcessor processor = new RecordProcessor("ignored.csv", 0, 2000);
        RecordProcessor.Statistics statistics = processor.calculateStatistics();

        assertThat(statistics.getTotalRequests()).isEqualTo(4);
        assertThat(statistics.getMean()).isEqualTo(25.0);
        assertThat(statistics.getMedian()).isEqualTo(30.0);
        assertThat(statistics.getP99()).isEqualTo(40.0);
        assertThat(statistics.getMin()).isEqualTo(10.0);
        assertThat(statistics.getMax()).isEqualTo(40.0);
        assertThat(statistics.getThroughput()).isEqualTo(2.0);

        assertThat(statistics.getSortedRecords())
                .extracting(Record::getLatency)
                .containsExactly(10L, 20L, 30L, 40L);
    }

    @Test
    void calculateOutputWritesCsvInSortedOrder() throws IOException {
        RecordProcessor.records.add(new Record(1, "POST", 15, 201));
        RecordProcessor.records.add(new Record(2, "POST", 5, 201));

        Path tempFile = Files.createTempFile("record-processor", ".csv");

        RecordProcessor processor = new RecordProcessor(tempFile.toString(), 0, 1000);
        processor.calculateOutput();

        List<String> lines = Files.readAllLines(tempFile);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("startTime,requestType,latency,responseCode");
        assertThat(lines.get(1)).contains(",POST,5,");
        assertThat(lines.get(2)).contains(",POST,15,");

        Files.deleteIfExists(tempFile);
    }
}
