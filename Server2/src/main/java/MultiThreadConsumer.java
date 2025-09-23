import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import config.AppConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MultiThreadConsumer implements AutoCloseable {
    private static final int DEFAULT_NUM_THREADS = 200; // Adjust the number of threads based on server performance
    private static final Logger LOGGER = Logger.getLogger(MultiThreadConsumer.class.getName());

    private final Gson gson = new Gson();
    private final String queueName;
    private final Connection connection;
    private final JedisPool jedisPool;
    private final ExecutorService executorService;
    private final List<Channel> channels = new CopyOnWriteArrayList<>();
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final boolean closeJedisPoolOnShutdown;
    private final int numThreads;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private MultiThreadConsumer(Connection connection, JedisPool jedisPool, String queueName, int numThreads, boolean closeJedisPoolOnShutdown) {
        this.connection = connection;
        this.jedisPool = jedisPool;
        this.queueName = queueName;
        this.numThreads = numThreads;
        this.executorService = Executors.newFixedThreadPool(numThreads);
        this.closeJedisPoolOnShutdown = closeJedisPoolOnShutdown;
    }

    public static void main(String[] args) throws Exception {
        MultiThreadConsumer consumer = startDefault();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                consumer.close();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception occurred during shutdown", e);
            }
        }));

        try {
            consumer.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            consumer.close();
        }
    }

    public static MultiThreadConsumer startDefault() throws Exception {
        ConnectionFactory factory = buildDefaultConnectionFactory();
        JedisPool jedisPool = buildDefaultJedisPool();
        return start(factory, jedisPool, AppConfig.getQueueName(), DEFAULT_NUM_THREADS, true);
    }

    public static MultiThreadConsumer start(ConnectionFactory factory, JedisPool jedisPool, String queueName, int numThreads) throws Exception {
        return start(factory, jedisPool, queueName, numThreads, false);
    }

    private static MultiThreadConsumer start(ConnectionFactory factory, JedisPool jedisPool, String queueName, int numThreads, boolean closeJedisPoolOnShutdown) throws Exception {
        Objects.requireNonNull(factory, "ConnectionFactory must not be null");
        Objects.requireNonNull(jedisPool, "JedisPool must not be null");
        Objects.requireNonNull(queueName, "Queue name must not be null");

        LOGGER.info("Attempting to connect to RabbitMQ...");
        Connection connection = factory.newConnection();
        LOGGER.info("RabbitMQ connection successful");

        MultiThreadConsumer consumer = new MultiThreadConsumer(connection, jedisPool, queueName, numThreads, closeJedisPoolOnShutdown);
        consumer.startConsumers();
        return consumer;
    }

    private static ConnectionFactory buildDefaultConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(AppConfig.getRabbitHost());
        factory.setPort(AppConfig.getRabbitPort());
        factory.setUsername(AppConfig.getRabbitUsername());
        factory.setPassword(AppConfig.getRabbitPassword());
        if (AppConfig.isRabbitUseSsl()) {
            try {
                factory.useSslProtocol();
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new IllegalStateException("Failed to enable SSL for RabbitMQ connection", e);
            }
        }
        return factory;
    }

    private static JedisPool buildDefaultJedisPool() throws URISyntaxException {
        String redisURI = AppConfig.getRedisUri();
        URI uri = new URI(redisURI);
        return new JedisPool(new JedisPoolConfig(), uri);
    }

    private void startConsumers() {
        for (int i = 0; i < numThreads; i++) {
            executorService.execute(() -> {
                try {
                    Channel channel = connection.createChannel();
                    channels.add(channel);
                    channel.queueDeclare(queueName, false, false, false, null);
                    channel.basicQos(100); // Limit the number of unacknowledged messages per consumer

                    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                        try (Jedis jedis = jedisPool.getResource()) {
                            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                            JsonObject jsonObject = gson.fromJson(message, JsonObject.class);

                            // Retrieve skierID, day, liftID from the message
                            Integer skierID = jsonObject.get("skierID").getAsInt();
                            String day = jsonObject.get("day").getAsString();
                            String season = jsonObject.get("seasonID").getAsString();
                            int liftID = jsonObject.get("liftID").getAsInt();
                            int vertical = liftID * 10;

                            // Design of keys in Redis
                            String skierDaysKey = "skier:" + skierID + ":days";
                            String skierSeasonDayMember = season + "|" + day;
                            String skierVerticalKey = "skier:" + skierID + ":season:" + season + ":day:" + day + ":vertical";
                            String skierLiftsKey = "skier:" + skierID + ":season:" + season + ":day:" + day + ":lifts";
                            String resortVisitorsKey = "resort:" + jsonObject.get("resortID").getAsString() + ":season:" + season + ":day:" + day + ":visitors";

                            // Use Pipeline to improve Redis operation performance
                            Pipeline pipeline = jedis.pipelined();
                            pipeline.sadd(skierDaysKey, skierSeasonDayMember); // Record the days the skier has skied
                            pipeline.incrBy(skierVerticalKey, vertical); // Update the total vertical for each day
                            pipeline.rpush(skierLiftsKey, String.valueOf(liftID)); // Record the lifts the skier has taken each day
                            pipeline.sadd(resortVisitorsKey, String.valueOf(skierID)); // Record skiers who visited a resort
                            pipeline.sync();

                            // Acknowledge that the message has been processed
                            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Exception occurred while processing message", e);
                            try {
                                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                            } catch (IOException ioException) {
                                LOGGER.log(Level.SEVERE, "Failed to nack delivery", ioException);
                            }
                        }
                    };

                    channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {
                    });
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Exception occurred in consumer thread", e);
                }
            });
        }
    }

    public void awaitTermination() throws InterruptedException {
        shutdownLatch.await();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warning("Consumer threads did not terminate within the timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (Channel channel : channels) {
            try {
                channel.close();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception occurred while closing channel", e);
            }
        }

        try {
            connection.close();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception occurred while closing connection", e);
        }

        if (closeJedisPoolOnShutdown) {
            jedisPool.close();
        }

        shutdownLatch.countDown();
    }
}
