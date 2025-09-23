import config.AppConfig;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;

final class TestConfigUtil {
    private TestConfigUtil() {
    }

    static void overrideAppConfig(Map<String, String> overrides) {
        try {
            Field propertiesField = AppConfig.class.getDeclaredField("PROPERTIES");
            propertiesField.setAccessible(true);
            Properties properties = (Properties) propertiesField.get(null);
            properties.clear();
            overrides.forEach(properties::setProperty);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to override AppConfig properties", e);
        }
    }
}
