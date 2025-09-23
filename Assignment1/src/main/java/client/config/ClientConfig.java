package client.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads configuration values for the Assignment1 clients from an {@code application.properties} file
 * located on the classpath. Environment variables may override property file values by using the
 * upper snake case representation of the property key (e.g. {@code CLIENT_API_BASE_URL} for
 * {@code client.api.baseUrl}).
 */
public final class ClientConfig {
    private static final Logger LOGGER = Logger.getLogger(ClientConfig.class.getName());
    private static final String PROPERTIES_FILE = "application.properties";
    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream inputStream = ClientConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (inputStream != null) {
                PROPERTIES.load(inputStream);
            } else {
                LOGGER.warning(() -> "Configuration file '" + PROPERTIES_FILE + "' was not found on the classpath.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to load client configuration from properties file", e);
        }
    }

    private ClientConfig() {
    }

    /**
     * Returns the configured Skier API base URL. The value is read from the {@code client.api.baseUrl}
     * property or from the {@code CLIENT_API_BASE_URL} environment variable when provided.
     */
    public static String getApiBaseUrl() {
        return getValue("client.api.baseUrl", "CLIENT_API_BASE_URL", "http://localhost:8080/Server2_war");
    }

    private static String getValue(String propertyKey, String envKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return PROPERTIES.getProperty(propertyKey, Objects.requireNonNull(defaultValue));
    }
}
