package com.hotel.system.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationDocumentStorageTest {
    @TempDir Path temporaryDirectory;

    @Test
    void acceptsPdfWithValidSignatureAndStoresInsidePrivateDirectory() throws Exception {
        byte[] pdf = "%PDF-1.7 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        RegistrationDocumentStorage storage = new RegistrationDocumentStorage(temporaryDirectory.toString());
        Path saved = Path.of(storage.store(new MockMultipartFile("id", "identity.pdf", "application/pdf", pdf)));

        assertTrue(saved.startsWith(temporaryDirectory.toAbsolutePath().normalize()
                .resolve("private-registration-documents")));
        assertArrayEquals(pdf, Files.readAllBytes(saved));
        assertEquals(saved, storage.resolve(saved.toString()));
    }

    @Test
    void rejectsSpoofedPdfContent() {
        RegistrationDocumentStorage storage = new RegistrationDocumentStorage(temporaryDirectory.toString());
        MockMultipartFile spoofed = new MockMultipartFile("id", "identity.pdf", "application/pdf",
                "plain text, not a PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> storage.store(spoofed));
        assertFalse(Files.exists(temporaryDirectory.resolve("private-registration-documents")));
    }

    @Test
    void rejectsExtensionAndMimeTypeMismatch() {
        RegistrationDocumentStorage storage = new RegistrationDocumentStorage(temporaryDirectory.toString());
        byte[] pdf = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        assertThrows(IllegalArgumentException.class, () -> storage.store(
                new MockMultipartFile("id", "identity.png", "application/pdf", pdf)));
    }

    @Test
    void preventsResolvingFilesOutsidePrivateDocumentDirectory() {
        RegistrationDocumentStorage storage = new RegistrationDocumentStorage(temporaryDirectory.toString());

        assertThrows(IllegalArgumentException.class,
                () -> storage.resolve(temporaryDirectory.resolve("outside.txt").toString()));
    }
}
