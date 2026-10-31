package guru.nicks.commons.jpa.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.bind.Binder;

import java.util.function.Consumer;

@UtilityClass
public class JpaConfigUtils {

    public static final String PRIMARY_DATASOURCE_CONFIG_PREFIX = "spring.datasource";
    public static final String SHARED_DATASOURCE_CONFIG_PREFIX = PRIMARY_DATASOURCE_CONFIG_PREFIX + ".shared";

    /**
     * Creates HikariCP datasource based on properties under the given prefix.
     *
     * @param prefix properties prefix (see e.g. {@link #PRIMARY_DATASOURCE_CONFIG_PREFIX} and
     *               {@link #SHARED_DATASOURCE_CONFIG_PREFIX})
     * @param binder property binder
     * @return datasource
     */
    public static HikariDataSource createHikariDataSource(String prefix, Binder binder) {
        var config = new HikariConfig();

        // URL, username, password
        bindProperty(binder, prefix + ".url", String.class, config::setJdbcUrl);
        bindProperty(binder, prefix + ".username", String.class, config::setUsername);
        bindProperty(binder, prefix + ".password", String.class, config::setPassword);
        bindProperty(binder, prefix + ".driver-class-name", String.class, config::setDriverClassName);

        // HikariCP-specific properties
        String hikariPrefix = prefix + ".hikari";
        bindProperty(binder, hikariPrefix + ".pool-name", String.class, config::setPoolName);
        bindProperty(binder, hikariPrefix + ".minimum-idle", Integer.class, config::setMinimumIdle);
        bindProperty(binder, hikariPrefix + ".maximum-pool-size", Integer.class, config::setMaximumPoolSize);
        bindProperty(binder, hikariPrefix + ".connection-timeout", Long.class, config::setConnectionTimeout);
        bindProperty(binder, hikariPrefix + ".max-lifetime", Long.class, config::setMaxLifetime);
        bindProperty(binder, hikariPrefix + ".idle-timeout", Long.class, config::setIdleTimeout);
        bindProperty(binder, hikariPrefix + ".connection-test-query", String.class, config::setConnectionTestQuery);

        return new HikariDataSource(config);
    }

    /**
     * Helper method to bind property from environment and apply consumer if value is present.
     *
     * @param binder        property binder
     * @param name          property name
     * @param propertyClass property class
     * @param valueConsumer consumer to apply if property value is present
     * @param <T>           property type
     */
    public static <T> void bindProperty(Binder binder, String name, Class<T> propertyClass, Consumer<T> valueConsumer) {
        T value = binder
                .bind(name, propertyClass)
                .orElse(null);

        if (value != null) {
            valueConsumer.accept(value);
        }
    }

}
