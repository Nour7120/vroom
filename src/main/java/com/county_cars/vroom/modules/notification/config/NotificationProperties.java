package com.county_cars.vroom.modules.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;


@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private int batchSize = 100;
    private int workerPoolSize = 2;
    private long jobProcessorIntervalMs = 10_000L;
    private long retryProcessorIntervalMs = 30_000L;
    private int maxAttempts = 3;
    private List<Long> backoffDelaysMs = List.of(60_000L, 300_000L, 900_000L);
    private boolean pushEnabled = true;
    private boolean retryEnabled = true;
    @NestedConfigurationProperty
    private FcmProperties fcm = new FcmProperties();

    @Getter
    @Setter
    public static class FcmProperties {
        private boolean mockEnabled = false;
        private String credentialsPath = "";
        private int connectTimeoutMs = 10_000;
        private int readTimeoutMs = 30_000;
    }
}

