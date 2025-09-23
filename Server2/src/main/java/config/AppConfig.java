package config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized configuration helper for clients and services.
 * Values are resolved in the following order of precedence:
 * <ol>
 *     <li>Environment variables using an upper snake case representation of the key</li>
 *     <li>A properties file (defaults to {@code config/app.properties})</li>
 *     <li>Hard coded sensible defaults</li>
 * </ol>
 */
public final class AppConfig {
    private static final Logger LOGGER = Logger.getLogger(AppConfig.class.getName());
    private static final String DEFAULT_PROPERTIES_PATH = "config/app.properties";
    private static final String CONFIG_FILE_ENV = "APP_CONFIG_FILE";
    private static final Properties PROPERTIES = new Properties();

    static {
        loadProperties();
    }

    private AppConfig() {
    }

    private static void loadProperties() {
        String explicitPath = System.getenv(CONFIG_FILE_ENV);
        if (explicitPath != null && !explicitPath.isEmpty()) {
            loadFromPath(explicitPath);
        }

        if (PROPERTIES.isEmpty()) {
            loadFromPath(DEFAULT_PROPERTIES_PATH);
        }
    }

    private static void loadFromPath(String pathString) {
        Path path = Paths.get(pathString);
        if (!Files.exists(path)) {
            LOGGER.log(Level.FINE, "Configuration file {0} not found, skipping", pathString);
            return;
        }

        try (InputStream in = Files.newInputStream(path)) {
            PROPERTIES.load(in);
            LOGGER.log(Level.CONFIG, "Loaded configuration from {0}", pathString);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to load configuration from " + pathString, e);
        }
    }

    private static String toEnvKey(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    public static String getString(String key, String defaultValue) {
        String envKey = toEnvKey(key);
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return PROPERTIES.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.WARNING, "Invalid integer for key {0}: {1}", new Object[]{key, value});
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.WARNING, "Invalid long for key {0}: {1}", new Object[]{key, value});
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.WARNING, "Invalid double for key {0}: {1}", new Object[]{key, value});
            return defaultValue;
        }
    }

    public static String getClient1BaseUrl() {
        return getString("client1.baseUrl", "http://localhost:8080/Server_war_exploded");
    }

    public static String getClient2BaseUrl() {
        return getString("client2.baseUrl", "http://localhost:8080/Server2_war");
    }

    public static double getClient2RateLimit() {
        return getDouble("client2.rateLimit", 5000.0);
    }

    public static int getClient2FailureThreshold() {
        return getInt("client2.failureThreshold", 100);
    }

    public static long getClient2CircuitBreakerTimeoutMs() {
        return getLong("client2.circuitBreakerTimeoutMs", 10_000L);
    }

    public static String getRabbitHost() {
        return getString("rabbitmq.host", "localhost");
    }

    public static int getRabbitPort() {
        return getInt("rabbitmq.port", 5672);
    }

    public static String getRabbitUsername() {
        return getString("rabbitmq.username", "guest");
    }

    public static String getRabbitPassword() {
        return getString("rabbitmq.password", "guest");
    }

    public static boolean isRabbitUseSsl() {
        return Boolean.parseBoolean(getString("rabbitmq.use-ssl", "false"));
    }

    public static String getRedisUri() {
        return getString("redis.uri", "redis://localhost:6379");
    }

    public static String getQueueName() {
        return getString("queue.name", "SkierServletPostQueue");
    }
}
