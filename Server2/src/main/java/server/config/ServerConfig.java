package server.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads RabbitMQ and Redis connection settings for the server components from an
 * {@code application.properties} file located on the classpath. Environment variables with the
 * upper snake case representation of the property key override file-based values.
 */
public final class ServerConfig {
    private static final Logger LOGGER = Logger.getLogger(ServerConfig.class.getName());
    private static final String PROPERTIES_FILE = "application.properties";
    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream inputStream = ServerConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (inputStream != null) {
                PROPERTIES.load(inputStream);
            } else {
                LOGGER.log(Level.WARNING,
                        () -> "Configuration file '" + PROPERTIES_FILE + "' was not found on the classpath.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to load server configuration from properties file", e);
        }
    }

    private ServerConfig() {
    }

    public static String getRabbitMqHost() {
        return getValue("rabbitmq.host", "RABBITMQ_HOST", "localhost");
    }

    public static int getRabbitMqPort() {
        String value = getValue("rabbitmq.port", "RABBITMQ_PORT", "5672");
        return Integer.parseInt(value);
    }

    public static String getRabbitMqUsername() {
        return getValue("rabbitmq.username", "RABBITMQ_USERNAME", "guest");
    }

    public static String getRabbitMqPassword() {
        return getValue("rabbitmq.password", "RABBITMQ_PASSWORD", "guest");
    }

    public static String getRedisUri() {
        return getValue("redis.uri", "REDIS_URI", "redis://localhost:6379");
    }

    public static String getQueueName() {
        return getValue("queue.name", "QUEUE_NAME", "SkierServletPostQueue");
    }

    private static String getValue(String propertyKey, String envKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return PROPERTIES.getProperty(propertyKey, Objects.requireNonNull(defaultValue));
    }
}
