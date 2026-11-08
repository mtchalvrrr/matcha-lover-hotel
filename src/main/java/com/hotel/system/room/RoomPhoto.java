package com.hotel.system.room;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "room_photos", indexes = @Index(name = "idx_room_photos_room", columnList = "room_id"))
public class RoomPhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "room_id", nullable = false)
    private Long roomId;
    @Column(name = "file_name", nullable = false, unique = true, length = 100)
    private String fileName;
    @Column(name = "original_file_name", length = 255)
    private String originalFileName;
    @Column(name = "is_cover", nullable = false)
    private boolean cover;
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    protected RoomPhoto() {}
    public RoomPhoto(Long roomId, String fileName, String originalFileName, boolean cover) {
        this.roomId=roomId; this.fileName=fileName; this.originalFileName=originalFileName; this.cover=cover;
    }
    public Long getId(){return id;}
    public Long getRoomId(){return roomId;}
    public String getFileName(){return fileName;}
    public String getOriginalFileName(){return originalFileName;}
    public boolean isCover(){return cover;}
    public Instant getUploadedAt(){return uploadedAt;}
    public String getPublicPath(){return "/room-images/" + fileName;}
    public void setCover(boolean cover){this.cover=cover;}
}