package com.hotel.system.room;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RoomPhotoStorageTest {
    @TempDir Path temporaryDirectory;

    @Test
    void acceptsMatchingPngSignatureAndStoresGeneratedFilename() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
        RoomPhotoStorage storage = new RoomPhotoStorage(temporaryDirectory.toString());
        String name = storage.store(new MockMultipartFile("photo", "room.png", "image/png", png));

        assertTrue(name.endsWith(".png"));
        assertArrayEquals(png, Files.readAllBytes(storage.root().resolve(name)));
    }

    @Test
    void rejectsTextFileRenamedAsPngEvenWhenMimeTypeClaimsImage() throws Exception {
        RoomPhotoStorage storage = new RoomPhotoStorage(temporaryDirectory.toString());
        MockMultipartFile spoofed = new MockMultipartFile("photo", "room.png", "image/png",
                "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> storage.store(spoofed));
        assertFalse(Files.exists(storage.root()));
    }

    @Test
    void deleteCannotEscapeTheRoomPhotoDirectory() throws Exception {
        RoomPhotoStorage storage = new RoomPhotoStorage(temporaryDirectory.toString());
        Files.createDirectories(storage.root());
        Path sentinel = temporaryDirectory.resolve("keep-me.txt");
        Files.writeString(sentinel, "important");
        
        storage.delete("..");

        assertTrue(Files.exists(sentinel));
    }

    @Test
    void rejectsExtensionAndMimeTypeMismatch() {
        RoomPhotoStorage storage = new RoomPhotoStorage(temporaryDirectory.toString());
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        assertThrows(IllegalArgumentException.class, () -> storage.store(
                new MockMultipartFile("photo", "room.jpg", "image/png", png)));
    }
}
