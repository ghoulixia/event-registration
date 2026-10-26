package com.team.eventregistration.service;

import com.team.eventregistration.exception.FileStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

@Service
public class ImageStorageService {

    private final Path uploadDir;

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    public ImageStorageService(
            @Value("${app.upload.dir:uploads/thumbnails}") String uploadDirPath) {
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new FileStorageException("Could not create upload directory: " + this.uploadDir, e);
        }
    }

    public String store(InputStream inputStream, String originalName,
                        String contentType, long fileSize) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid file format. Only JPG, PNG, and WebP are allowed.");
        }
        if (fileSize <= 0 || fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File must contain between 1 byte and 5MB.");
        }

        String extension = switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String storedFileName = UUID.randomUUID() + extension;

        Path targetPath = uploadDir.resolve(storedFileName).normalize();
        try {
            if (!targetPath.startsWith(uploadDir)) {
                throw new FileStorageException("Invalid file path.");
            }
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return storedFileName;
        } catch (IOException e) {
            try { Files.deleteIfExists(targetPath); }
            catch (IOException cleanupFailure) { e.addSuppressed(cleanupFailure); }
            throw new FileStorageException("Error saving file: " + originalName, e);
        }
    }

    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) return;
        try {
            Path filePath = uploadDir.resolve(fileName).normalize();
            if (filePath.startsWith(uploadDir)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException ignored) {
        }
    }

}
