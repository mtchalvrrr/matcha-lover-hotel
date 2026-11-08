package com.hotel.system.room;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
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
        if(file==null || file.isEmpty() || file.getSize()>8L*1024*1024)
            throw new IllegalArgumentException("Choose a room photo up to 8 MB.");
        if(!TYPES.contains(file.getContentType()))
            throw new IllegalArgumentException("Room photos must be JPG, PNG, or WEBP images.");
        String original=file.getOriginalFilename()==null?"":file.getOriginalFilename();
        String ext=original.lastIndexOf('.')>=0?original.substring(original.lastIndexOf('.')).toLowerCase():"";
        if(!Set.of(".jpg",".jpeg",".png",".webp").contains(ext))
            throw new IllegalArgumentException("Unsupported image extension.");
        try {
            Files.createDirectories(root);
            String name=UUID.randomUUID()+ext;
            Path target=root.resolve(name).normalize();
            if(!target.startsWith(root)) throw new IllegalArgumentException("Invalid image path.");
            Files.copy(file.getInputStream(),target,StandardCopyOption.REPLACE_EXISTING);
            return name;
        } catch(IOException ex){ throw new IllegalStateException("Unable to save room photo.",ex); }
    }
    public void delete(String name) {
        if(name==null || name.contains("/") || name.contains("\\")) return;
        try { Files.deleteIfExists(root.resolve(name).normalize()); }
        catch(IOException ex){ throw new IllegalStateException("Unable to delete room photo.",ex); }
    }
    public Path root(){ return root; }
}