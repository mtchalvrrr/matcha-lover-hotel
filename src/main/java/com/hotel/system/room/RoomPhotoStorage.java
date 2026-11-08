package com.hotel.system.room;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class RoomPhotoStorage {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final Path root;

    public RoomPhotoStorage(@Value("${hotel.storage.location:./data/uploads}") String location) {
        root = Paths.get(location).toAbsolutePath().normalize().resolve("room-photos");
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 8L * 1024 * 1024) {
            throw new IllegalArgumentException("Choose a room photo up to 8 MB.");
        }
        String contentType = file.getContentType();
        if (!TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Room photos must be JPG, PNG, or WEBP images.");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = original.lastIndexOf('.') >= 0
                ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
        if (!Set.of(".jpg", ".jpeg", ".png", ".webp").contains(extension)) {
            throw new IllegalArgumentException("Unsupported image extension.");
        }

        try {
            byte[] bytes = file.getBytes();
            if (!matchesSignature(bytes, contentType) || !extensionMatches(extension, contentType)) {
                throw new IllegalArgumentException("The uploaded file content does not match a supported image type.");
            }
            Files.createDirectories(root);
            String name = UUID.randomUUID() + extension;
            Path target = root.resolve(name).normalize();
            if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid image path.");
            Files.write(target, bytes);
            return name;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save room photo.", ex);
        }
    }

    private boolean extensionMatches(String extension, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> extension.equals(".jpg") || extension.equals(".jpeg");
            case "image/png" -> extension.equals(".png");
            case "image/webp" -> extension.equals(".webp");
            default -> false;
        };
    }

    private boolean matchesSignature(byte[] bytes, String contentType) {
        if (bytes == null) return false;
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                    && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    public void delete(String name) {
        if (name == null || name.contains("/") || name.contains("\\")) return;
        try {
            Files.deleteIfExists(root.resolve(name).normalize());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to delete room photo.", ex);
        }
    }

    public Path root() { return root; }
}
