package Client2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordProcessorTest {

    @AfterEach
    void tearDown() {
        RecordProcessor.records.clear();
    }

    @Test
    void calculateOutputWithNoRecords() throws IOException {
        RecordProcessor.records.clear();
        Path tempFile = Files.createTempFile("record-processor-test-empty", ".csv");
        RecordProcessor processor = new RecordProcessor(tempFile.toString(), 0, 1000);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream captureStream = new PrintStream(outputStream);
        System.setOut(captureStream);
        try {
            processor.calculateOutput();
        } finally {
            System.setOut(originalOut);
            captureStream.close();
        }

        String consoleOutput = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(consoleOutput.isEmpty(), "Expected no metrics to be printed when there are no records.");

        List<String> lines = Files.readAllLines(tempFile, StandardCharsets.UTF_8);
        assertEquals(List.of("startTime,requestType,latency,responseCode"), lines);

        Files.deleteIfExists(tempFile);
    }

    @Test
    void calculateOutputWithSingleRecord() throws IOException {
        Path tempFile = Files.createTempFile("record-processor-test-single", ".csv");
        RecordProcessor.records.clear();
        RecordProcessor.records.add(new Record(0, "POST", 200, 201));
        RecordProcessor processor = new RecordProcessor(tempFile.toString(), 0, 1000);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream captureStream = new PrintStream(outputStream);
        System.setOut(captureStream);
        try {
            processor.calculateOutput();
        } finally {
            System.setOut(originalOut);
            captureStream.close();
        }

        String[] lines = outputStream.toString(StandardCharsets.UTF_8).trim().split("\\R");
        assertArrayEquals(new String[]{
                "1",
                "Mean response time: 200.0 ms",
                "Median response time: 200.0 ms",
                "Throughput: 1.0 requests/sec",
                "99th percentile response time: 200.0 ms",
                "Min response time: 200.0 ms",
                "Max response time: 200.0 ms"
        }, lines);

        String expectedCsv = "startTime,requestType,latency,responseCode\n" +
                "0,POST,200,201\n";
        String actualCsv = Files.readString(tempFile, StandardCharsets.UTF_8);
        assertEquals(expectedCsv, actualCsv);

        Files.deleteIfExists(tempFile);
    }

    @Test
    void calculateOutputWithMultipleRecords() throws IOException {
        Path tempFile = Files.createTempFile("record-processor-test-multiple", ".csv");
        RecordProcessor.records.clear();
        RecordProcessor.records.add(new Record(0, "POST", 40, 200));
        RecordProcessor.records.add(new Record(0, "POST", 10, 200));
        RecordProcessor.records.add(new Record(0, "POST", 50, 200));
        RecordProcessor.records.add(new Record(0, "POST", 20, 200));
        RecordProcessor.records.add(new Record(0, "POST", 30, 200));
        RecordProcessor processor = new RecordProcessor(tempFile.toString(), 0, 5000);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream captureStream = new PrintStream(outputStream);
        System.setOut(captureStream);
        try {
            processor.calculateOutput();
        } finally {
            System.setOut(originalOut);
            captureStream.close();
        }

        String[] lines = outputStream.toString(StandardCharsets.UTF_8).trim().split("\\R");
        assertArrayEquals(new String[]{
                "5",
                "Mean response time: 30.0 ms",
                "Median response time: 30.0 ms",
                "Throughput: 1.0 requests/sec",
                "99th percentile response time: 49.6 ms",
                "Min response time: 10.0 ms",
                "Max response time: 50.0 ms"
        }, lines);

        String expectedCsv = "startTime,requestType,latency,responseCode\n" +
                "0,POST,10,200\n" +
                "0,POST,20,200\n" +
                "0,POST,30,200\n" +
                "0,POST,40,200\n" +
                "0,POST,50,200\n";
        String actualCsv = Files.readString(tempFile, StandardCharsets.UTF_8);
        assertEquals(expectedCsv, actualCsv);

        Files.deleteIfExists(tempFile);
    }
}
