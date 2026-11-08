package com.hotel.system.registration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
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
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024)
            throw new IllegalArgumentException("Upload a valid ID document up to 5 MB.");
        if (!ALLOWED.contains(file.getContentType()))
            throw new IllegalArgumentException("ID document must be JPG, PNG, or PDF.");
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = original.lastIndexOf('.') >= 0 ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
        if (!Set.of(".jpg", ".jpeg", ".png", ".pdf").contains(extension))
            throw new IllegalArgumentException("Unsupported ID document extension.");
        try {
            Files.createDirectories(root);
            String filename = UUID.randomUUID() + extension;
            Path target = root.resolve(filename).normalize();
            if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid document path.");
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store the ID document.", ex);
        }
    }

    public Path resolve(String storedPath) {
        Path path = Paths.get(storedPath).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid document path.");
        return path;
    }
}