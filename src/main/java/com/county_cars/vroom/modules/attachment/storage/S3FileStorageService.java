package com.county_cars.vroom.modules.attachment.storage;

import com.county_cars.vroom.common.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

/**
 * AWS S3 storage implementation.
 *
 * <p>The object key stored in the DB is {@code <category>/<storedFileName>}.
 * No signed URLs are generated — download is streamed through the backend.</p>
 *
 * <p>Activated by setting {@code attachment.storage.provider=s3}.
 * Instantiated by {@link com.county_cars.vroom.modules.attachment.config.StorageConfig},
 * which also builds the {@link S3Client} using the standard AWS credential chain.</p>
 */
@Slf4j
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final String bucketName;

    public S3FileStorageService(S3Client s3Client, String bucketName) {
        this.s3Client   = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public String store(MultipartFile file, String storedFileName, String category) {
        String objectKey = category.toLowerCase() + "/" + storedFileName;
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("File uploaded to S3: bucket={} key={}", bucketName, objectKey);
            return objectKey;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to upload file to S3: " + storedFileName, e);
        }
    }

    @Override
    public Resource load(String path) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build();
            var responseInputStream = s3Client.getObject(request);
            return new InputStreamResource(responseInputStream);
        } catch (Exception e) {
            log.error("Failed to load file from S3: key={} — {}", path, e.getMessage());
            throw new NotFoundException("File not found in S3: " + path);
        }
    }

    @Override
    public void delete(String path) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build();
            s3Client.deleteObject(request);
            log.info("File deleted from S3: bucket={} key={}", bucketName, path);
        } catch (Exception e) {
            log.error("Failed to delete file from S3: key={} — {}", path, e.getMessage());
        }
    }
}
