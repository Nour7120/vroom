package com.county_cars.vroom.modules.attachment.storage;

import com.county_cars.vroom.common.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem storage implementation.
 *
 * <p>Files are saved to {@code <uploadDir>/<category>/<storedFileName>}.
 * Only the relative path ({@code <category>/<storedFileName>}) is stored in the DB —
 * the physical root is never exposed.</p>
 *
 * <p>Activated by setting {@code attachment.storage.provider=local} (default).
 * Instantiated by {@link com.county_cars.vroom.modules.attachment.config.StorageConfig}.</p>
 */
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    private final Path rootPath;

    public LocalFileStorageService(String uploadDir) {
        this.rootPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
            log.info("Local upload directory initialised: {}", rootPath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + rootPath, e);
        }
    }

    @Override
    public String store(MultipartFile file, String storedFileName, String category) {
        Path categoryDir = rootPath.resolve(category.toLowerCase());
        try {
            Files.createDirectories(categoryDir);
            Path target = categoryDir.resolve(storedFileName).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String relativePath = category.toLowerCase() + "/" + storedFileName;
            log.info("File stored locally: {}", relativePath);
            return relativePath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file: " + storedFileName, e);
        }
    }

    @Override
    public Resource load(String path) {
        try {
            Path filePath = rootPath.resolve(path).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new NotFoundException("File not found or not readable: " + path);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new NotFoundException("Could not resolve file path: " + path);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Path filePath = rootPath.resolve(path).normalize();
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("File deleted: {}", path);
            } else {
                log.warn("File not found for deletion (already gone?): {}", path);
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {} — {}", path, e.getMessage());
        }
    }
}
