package com.hotel.system.registration;

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
public class RegistrationDocumentStorage {
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "application/pdf");
    private final Path root;

    public RegistrationDocumentStorage(@Value("${hotel.storage.location:./data/uploads}") String location) {
        this.root = Paths.get(location).toAbsolutePath().normalize().resolve("private-registration-documents");
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException("Upload a valid ID document up to 5 MB.");
        }
        String contentType = file.getContentType();
        if (!ALLOWED.contains(contentType)) {
            throw new IllegalArgumentException("ID document must be JPG, PNG, or PDF.");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = original.lastIndexOf('.') >= 0
                ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
        if (!Set.of(".jpg", ".jpeg", ".png", ".pdf").contains(extension)) {
            throw new IllegalArgumentException("Unsupported ID document extension.");
        }

        try {
            byte[] bytes = file.getBytes();
            if (!matchesSignature(bytes, contentType) || !extensionMatches(extension, contentType)) {
                throw new IllegalArgumentException("The uploaded file content does not match a supported ID document type.");
            }
            Files.createDirectories(root);
            String filename = UUID.randomUUID() + extension;
            Path target = root.resolve(filename).normalize();
            if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid document path.");
            Files.write(target, bytes);
            return target.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store the ID document.", ex);
        }
    }

    private boolean extensionMatches(String extension, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> extension.equals(".jpg") || extension.equals(".jpeg");
            case "image/png" -> extension.equals(".png");
            case "application/pdf" -> extension.equals(".pdf");
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
            case "application/pdf" -> bytes.length >= 5
                    && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                    && bytes[3] == 'F' && bytes[4] == '-';
            default -> false;
        };
    }


    public void delete(String storedPath) {
        Path path = resolve(storedPath);
        if (!path.getFileName().toString().matches("[A-Za-z0-9-]+\\.(jpg|jpeg|png|pdf)")) {
            throw new IllegalArgumentException("Invalid private document filename.");
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to remove the private ID document.", ex);
        }
    }

    public Path resolve(String storedPath) {
        Path path = Paths.get(storedPath).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid document path.");
        return path;
    }
}
