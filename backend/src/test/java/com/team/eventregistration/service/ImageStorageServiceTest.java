package com.team.eventregistration.service;

import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.FileStorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.FileSystemUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageStorageServiceTest {

    private ImageStorageService service;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("test-thumbnails");
        service = new ImageStorageService(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        FileSystemUtils.deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("Stores valid image file")
    void shouldStore() throws IOException {
        String content = "dummy-image-bytes";
        String name = service.store(new ByteArrayInputStream(content.getBytes()),
                "avatar.png", "image/png", content.length());

        assertTrue(name.endsWith(".png"));
        assertTrue(Files.exists(tempDir.resolve(name)));
    }

    @Test
    @DisplayName("Rejects unsupported MIME type")
    void shouldRejectBadMime() {
        assertThrows(BusinessRuleException.class, () ->
                service.store(new ByteArrayInputStream("x".getBytes()), "t.txt", "text/plain", 1));
    }

    @Test
    @DisplayName("Rejects oversized file")
    void shouldRejectOversized() {
        assertThrows(BusinessRuleException.class, () ->
                service.store(new ByteArrayInputStream("x".getBytes()), "l.jpg", "image/jpeg", 6L * 1024 * 1024));
    }

    @Test
    @DisplayName("Deletes existing file")
    void shouldDelete() throws IOException {
        Path f = tempDir.resolve("old.jpg");
        Files.writeString(f, "data");
        service.delete("old.jpg");
        assertFalse(Files.exists(f));
    }

    @Test
    @DisplayName("No error on deleting non-existent file")
    void shouldNotThrowOnMissing() {
        assertDoesNotThrow(() -> service.delete("missing.jpg"));
    }
}
