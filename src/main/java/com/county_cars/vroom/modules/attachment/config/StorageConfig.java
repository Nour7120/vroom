package com.county_cars.vroom.modules.attachment.config;

import com.county_cars.vroom.modules.attachment.storage.FileStorageService;
import com.county_cars.vroom.modules.attachment.storage.LocalFileStorageService;
import com.county_cars.vroom.modules.attachment.storage.S3FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Registers the active {@link FileStorageService} implementation based on
 * {@code attachment.storage.provider} in {@code application.properties}.
 *
 * <pre>
 * attachment.storage.provider=local   → LocalFileStorageService
 * attachment.storage.provider=s3      → S3FileStorageService
 * </pre>
 *
 * <p>No Spring profile is needed. Switching storage backend is a single
 * property change with no code or profile activation required.</p>
 */
@Slf4j
@Configuration
public class StorageConfig {

    // ── Local ─────────────────────────────────────────────────────────────────────

    /**
     * Registers {@link LocalFileStorageService} when
     * {@code attachment.storage.provider=local} (the default).
     */
    @Bean
    @ConditionalOnProperty(
            name    = "attachment.storage.provider",
            havingValue = "local",
            matchIfMissing = true          // default when property is absent
    )
    public FileStorageService localFileStorageService(
            @Value("${attachment.local.upload-dir:uploads}") String uploadDir) {
        log.info("Storage provider: LOCAL (uploadDir={})", uploadDir);
        return new LocalFileStorageService(uploadDir);
    }

    // ── S3 ────────────────────────────────────────────────────────────────────────

    /**
     * Registers {@link S3FileStorageService} when
     * {@code attachment.storage.provider=s3}.
     *
     * <p>The {@link S3Client} uses the standard AWS credential chain
     * ({@code AWS_ACCESS_KEY_ID} / {@code AWS_SECRET_ACCESS_KEY} env vars,
     * {@code ~/.aws/credentials}, or an IAM instance role).</p>
     */
    @Bean
    @ConditionalOnProperty(
            name        = "attachment.storage.provider",
            havingValue = "s3"
    )
    public FileStorageService s3FileStorageService(
            @Value("${attachment.s3.bucket-name}") String bucketName,
            @Value("${attachment.s3.region:eu-west-1}") String region) {
        log.info("Storage provider: S3 (bucket={}, region={})", bucketName, region);
        S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        return new S3FileStorageService(s3Client, bucketName);
    }
}

