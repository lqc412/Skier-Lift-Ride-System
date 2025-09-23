import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MultiThreadConsumer {
    private static final String QUEUE_NAME = "SkierServletPostQueue";
    private static final int NUM_THREADS = 200; // Adjust the number of threads based on server performance
    private static final Logger LOGGER = Logger.getLogger(MultiThreadConsumer.class.getName());
    private static final List<Channel> channels = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        // Configuration parameters can be read from config files or environment variables
        String rabbitmqHost = "54.188.239.188";
        int rabbitmqPort = 5672;
        String rabbitmqUsername = "lqc412";
        String rabbitmqPassword = "lqc412";

        // Redis URI with authentication information
        String redisURI = "redis://lqc412:Password@35.165.107.222:6379";

        Gson gson = new Gson();
        ConnectionFactory factory = new ConnectionFactory();

        // Configure RabbitMQ connection
        factory.setHost(rabbitmqHost);
        factory.setPort(rabbitmqPort);
        factory.setUsername(rabbitmqUsername);
        factory.setPassword(rabbitmqPassword);
        LOGGER.info("Attempting to connect to RabbitMQ...");
        Connection connection = factory.newConnection();
        LOGGER.info("RabbitMQ connection successful");

        // Create Jedis connection pool
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        // Configure connection pool parameters as needed

        // Parse Redis URI
        URI redisUri = null;
        try {
            redisUri = new URI(redisURI);
        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "Redis URI format error", e);
            return;
        }

        // Create JedisPool and declare as final
        final JedisPool jedisPool = new JedisPool(poolConfig, redisUri);
        LOGGER.info("Redis connection pool created successfully");

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);

        for (int i = 0; i < NUM_THREADS; i++) {
            pool.execute(() -> {
                try {
                    Channel channel = connection.createChannel();
                    channels.add(channel);
                    channel.queueDeclare(QUEUE_NAME, false, false, false, null);
                    channel.basicQos(100); // Limit the number of unacknowledged messages per consumer

                    DeliverCallback deliverCallback = createDeliverCallback(channel, jedisPool, gson);

                    channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {
                    });
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Exception occurred in consumer thread", e);
                }
            });
        }

        // Gracefully shut down threads and resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down consumer threads...");
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
                // Close all channels
                for (Channel channel : channels) {
                    try {
                        channel.close();
                    } catch (IOException | TimeoutException e) {
                        LOGGER.log(Level.SEVERE, "Exception occurred while closing channel", e);
                    }
                }
                connection.close();
                jedisPool.close();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception occurred during shutdown", e);
            }
        }));
    }

    static DeliverCallback createDeliverCallback(Channel channel, JedisPool jedisPool, Gson gson) {
        return (consumerTag, delivery) -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                JsonObject jsonObject = gson.fromJson(message, JsonObject.class);

                // Retrieve skierID, day, liftID from the message
                Integer skierID = jsonObject.get("skierID").getAsInt();
                String day = jsonObject.get("day").getAsString();
                int liftID = jsonObject.get("liftID").getAsInt();
                int vertical = liftID * 10;

                // Design of keys in Redis
                String skierDaysKey = "skier:" + skierID + ":days";
                String skierVerticalKey = "skier:" + skierID + ":day:" + day + ":vertical";
                String skierLiftsKey = "skier:" + skierID + ":day:" + day + ":lifts";
                String resortVisitorsKey = "resort:" + jsonObject.get("resortID").getAsString() + ":day:" + day + ":visitors";

                // Use Pipeline to improve Redis operation performance
                Pipeline pipeline = jedis.pipelined();
                pipeline.sadd(skierDaysKey, day); // Record the days the skier has skied
                pipeline.incrBy(skierVerticalKey, vertical); // Update the total vertical for each day
                pipeline.rpush(skierLiftsKey, String.valueOf(liftID)); // Record the lifts the skier has taken each day
                pipeline.sadd(resortVisitorsKey, String.valueOf(skierID)); // Record skiers who visited a resort
                pipeline.sync();

                // Acknowledge that the message has been processed
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception occurred while processing message", e);
                // Reject the message without requeueing
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            }
        };
    }
}
