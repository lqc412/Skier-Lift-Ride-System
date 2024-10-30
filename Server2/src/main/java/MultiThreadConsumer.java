import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MultiThreadConsumer {
    private static final String QUEUE_NAME = "SkierServletPostQueue";
    private static final int NUM_THREADS = 200;
    private static final Logger LOGGER = Logger.getLogger(MultiThreadConsumer.class.getName());

    public static void main(String[] args) throws Exception {
        Gson gson = new Gson();
        ConnectionFactory factory = new ConnectionFactory();
        ConcurrentMap<Integer, List<JsonObject>> map = new ConcurrentHashMap<>();

        // Configure RabbitMQ connection
        //factory.setHost("localhost");
        factory.setHost("54.188.239.188");
        factory.setPort(5672);
        factory.setUsername("lqc412");
        factory.setPassword("lqc412");
        System.out.println("Trying to connect to RabbitMQ...");
        Connection connection = factory.newConnection();
        System.out.println("Connection successful");

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        for (int i = 0; i < NUM_THREADS; i++) {
            pool.execute(() -> {
                try {
                    Channel channel = connection.createChannel();
                    channel.queueDeclare(QUEUE_NAME, false, false, false, null);
                    channel.basicQos(100); // Limit the number of unacknowledged messages per consumer
                    //Was 50 for 1
                    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                        String message = new String(delivery.getBody(), "UTF-8");
                        JsonObject jsonObject = gson.fromJson(message, JsonObject.class);

                        Integer key = jsonObject.get("skierID").getAsInt();
                        map.computeIfAbsent(key, k -> new ArrayList<>()).add(jsonObject);

                        // Acknowledge the message
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    };

                    channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {
                    });
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Exception in consumer thread", e);
                }
            });
        }

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down consumer threads...");
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
                connection.close();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception during shutdown", e);
            }
        }));
    }
}
