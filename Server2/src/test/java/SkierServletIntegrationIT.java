import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import config.AppConfig;
import org.awaitility.Awaitility;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class SkierServletIntegrationIT {

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.11-management"));

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private JedisPool verificationPool;
    private JedisPool consumerPool;
    private ConnectionFactory connectionFactory;
    private MultiThreadConsumer consumer;
    private Server server;
    private String baseUrl;
    private String queueName;

    @BeforeAll
    void setUpSuite() throws Exception {
        queueName = "integration-" + UUID.randomUUID();

        Map<String, String> overrides = new HashMap<>();
        overrides.put("rabbitmq.host", RABBIT.getHost());
        overrides.put("rabbitmq.port", Integer.toString(RABBIT.getAmqpPort()));
        overrides.put("rabbitmq.username", RABBIT.getAdminUsername());
        overrides.put("rabbitmq.password", RABBIT.getAdminPassword());
        overrides.put("redis.uri", "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort());
        overrides.put("queue.name", queueName);
        TestConfigUtil.overrideAppConfig(overrides);

        connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(AppConfig.getRabbitHost());
        connectionFactory.setPort(AppConfig.getRabbitPort());
        connectionFactory.setUsername(AppConfig.getRabbitUsername());
        connectionFactory.setPassword(AppConfig.getRabbitPassword());

        consumerPool = new JedisPool(new JedisPoolConfig(), REDIS.getHost(), REDIS.getFirstMappedPort());
        consumer = MultiThreadConsumer.start(connectionFactory, consumerPool, queueName, 4);

        verificationPool = new JedisPool(new JedisPoolConfig(), REDIS.getHost(), REDIS.getFirstMappedPort());

        server = startServer();
    }

    @AfterAll
    void tearDownSuite() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (consumer != null) {
            consumer.close();
            consumer.awaitTermination();
        }
        if (consumerPool != null) {
            consumerPool.close();
        }
        if (verificationPool != null) {
            verificationPool.close();
        }
    }

    @BeforeEach
    void resetState() throws Exception {
        try (Jedis jedis = verificationPool.getResource()) {
            jedis.flushAll();
        }

        try (Connection connection = connectionFactory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(queueName, false, false, false, null);
            channel.queuePurge(queueName);
        }
    }

    @Test
    void shouldReportHealthWithoutTouchingDependencies() throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(buildUri("/skiers/health"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        org.junit.jupiter.api.Assertions.assertEquals(200, response.statusCode());
        JsonObject body = gson.fromJson(response.body(), JsonObject.class);
        org.junit.jupiter.api.Assertions.assertEquals("UP", body.get("status").getAsString());
    }

    @Test
    void shouldPublishLiftRideAndReturnAggregates() throws IOException, InterruptedException {
        int resortId = 7;
        String seasonId = "2024";
        String dayId = "11";
        int skierId = 101;
        int liftId = 5;
        int expectedVertical = liftId * 10;

        HttpRequest post = HttpRequest.newBuilder()
                .uri(buildUri("/skiers/" + resortId + "/seasons/" + seasonId + "/days/" + dayId + "/skiers/" + skierId))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"time\":15,\"liftID\":" + liftId + "}"))
                .build();

        HttpResponse<String> postResponse = httpClient.send(post, HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(201, postResponse.statusCode());

        String verticalKey = "skier:" + skierId + ":season:" + seasonId + ":day:" + dayId + ":vertical";
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            try (Jedis jedis = verificationPool.getResource()) {
                String value = jedis.get(verticalKey);
                return value != null && Integer.parseInt(value) == expectedVertical;
            }
        });

        try (Jedis jedis = verificationPool.getResource()) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    jedis.sismember("skier:" + skierId + ":days", seasonId + "|" + dayId));
            List<String> lifts = jedis.lrange("skier:" + skierId + ":season:" + seasonId + ":day:" + dayId + ":lifts", 0, -1);
            org.junit.jupiter.api.Assertions.assertEquals(List.of(Integer.toString(liftId)), lifts);
            org.junit.jupiter.api.Assertions.assertTrue(
                    jedis.sismember("resort:" + resortId + ":season:" + seasonId + ":day:" + dayId + ":visitors",
                            Integer.toString(skierId)));
        }

        HttpResponse<String> dailyResponse = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(buildUri("/skiers/" + resortId + "/seasons/" + seasonId + "/days/" + dayId + "/skiers/" + skierId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(200, dailyResponse.statusCode());
        assertDailyVertical(dailyResponse.body(), seasonId, expectedVertical);

        HttpResponse<String> totalResponse = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(buildUri("/skiers/" + skierId + "/vertical"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(200, totalResponse.statusCode());
        assertTotalVertical(totalResponse.body(), Map.of(seasonId, expectedVertical));
    }

    @Test
    void shouldHandleLegacyDataAndMissingSkier() throws IOException, InterruptedException {
        int skierId = 777;

        try (Jedis jedis = verificationPool.getResource()) {
            jedis.sadd("skier:" + skierId + ":days", "2020|10", "5");
            jedis.set("skier:" + skierId + ":season:2020:day:10:vertical", "120");
            jedis.set("skier:" + skierId + ":day:5:vertical", "90");
            jedis.sadd("resort:55:day:5:visitors", Integer.toString(skierId));
        }

        HttpResponse<String> totalResponse = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(buildUri("/skiers/" + skierId + "/vertical"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(200, totalResponse.statusCode());
        assertTotalVertical(totalResponse.body(), Map.of("2020", 120, "TOTAL", 90));

        HttpResponse<String> legacyDaily = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(buildUri("/skiers/55/seasons/2021/days/5/skiers/" + skierId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(200, legacyDaily.statusCode());
        assertDailyVertical(legacyDaily.body(), "2021", 90);

        HttpResponse<String> missingDaily = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(buildUri("/skiers/55/seasons/2021/days/5/skiers/" + (skierId + 1)))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(200, missingDaily.statusCode());
        assertDailyVertical(missingDaily.body(), "2021", 0);

        HttpResponse<String> missingTotal = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(buildUri("/skiers/" + (skierId + 2) + "/vertical"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(200, missingTotal.statusCode());
        assertTotalVertical(missingTotal.body(), Map.of("TOTAL", 0));
    }

    private Server startServer() throws Exception {
        Server jettyServer = new Server(0);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        ServletHolder holder = new ServletHolder(new SkierServlet());
        holder.setInitOrder(1);
        context.addServlet(holder, "/skiers/*");

        jettyServer.start();
        int port = ((ServerConnector) jettyServer.getConnectors()[0]).getLocalPort();
        baseUrl = "http://localhost:" + port;
        return jettyServer;
    }

    private URI buildUri(String path) {
        return URI.create(baseUrl + path);
    }

    private void assertDailyVertical(String body, String expectedSeason, int expectedVertical) {
        JsonObject json = gson.fromJson(body, JsonObject.class);
        JsonArray array = json.getAsJsonArray("skiervertical");
        org.junit.jupiter.api.Assertions.assertEquals(1, array.size());
        JsonObject element = array.get(0).getAsJsonObject();
        org.junit.jupiter.api.Assertions.assertEquals(expectedSeason, element.get("seasonID").getAsString());
        org.junit.jupiter.api.Assertions.assertEquals(expectedVertical, element.get("totalVert").getAsInt());
    }

    private void assertTotalVertical(String body, Map<String, Integer> expectedTotals) {
        JsonObject json = gson.fromJson(body, JsonObject.class);
        JsonArray array = json.getAsJsonArray("skiervertical");
        org.junit.jupiter.api.Assertions.assertEquals(expectedTotals.size(), array.size());

        for (int i = 0; i < array.size(); i++) {
            JsonObject element = array.get(i).getAsJsonObject();
            String season = element.get("seasonID").getAsString();
            int total = element.get("totalVert").getAsInt();
            org.junit.jupiter.api.Assertions.assertEquals(expectedTotals.get(season).intValue(), total,
                    "Unexpected total for season " + season);
        }
    }
}
