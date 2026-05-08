package com.county_cars.vroom.modules.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Root configuration for the Notification module.
 *
 * <p>Registers {@link NotificationProperties} with the Spring context so that
 * {@code @ConfigurationProperties} binding is active without needing to annotate
 * the main application class.</p>
 */

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
@EnableTransactionManagement
public class NotificationConfig {
    // Intentionally empty — acts as a module registration hook.
    // Workers are registered as @Component beans and picked up by component scan.
}

