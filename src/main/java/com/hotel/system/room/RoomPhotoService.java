package com.hotel.system.room;

import com.hotel.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Service
@Transactional
public class RoomPhotoService {
    private final RoomRepository rooms;
    private final RoomPhotoRepository photos;
    private final RoomPhotoStorage storage;
    private final AuditService audit;
    public RoomPhotoService(RoomRepository rooms, RoomPhotoRepository photos, RoomPhotoStorage storage, AuditService audit) {
        this.rooms=rooms; this.photos=photos; this.storage=storage; this.audit=audit;
    }
    @Transactional(readOnly=true)
    public List<RoomPhoto> list(Long roomId){ rooms.findById(roomId).orElseThrow(()->new RoomNotFoundException("Room not found.")); return photos.findByRoomIdOrderByUploadedAtAsc(roomId); }
    public RoomPhoto upload(Long roomId, MultipartFile file, String actor) {
        Room room=rooms.findById(roomId).orElseThrow(()->new RoomNotFoundException("Room not found."));
        String filename=storage.store(file);
        boolean cover=photos.findByRoomIdOrderByUploadedAtAsc(roomId).isEmpty();
        RoomPhoto photo=photos.save(new RoomPhoto(roomId,filename,file.getOriginalFilename(),cover));
        if(cover) room.setCoverImagePath(photo.getPublicPath());
        audit.record(actor,"ROOM_PHOTO_UPLOADED","ROOM",roomId.toString(),"Uploaded room photo "+filename);
        return photo;
    }
    public void setCover(Long roomId, Long photoId, String actor) {
        Room room=rooms.findById(roomId).orElseThrow(()->new RoomNotFoundException("Room not found."));
        RoomPhoto selected=photos.findByIdAndRoomId(photoId,roomId).orElseThrow(()->new IllegalArgumentException("Photo not found."));
        for(RoomPhoto photo:photos.findByRoomIdOrderByUploadedAtAsc(roomId)) photo.setCover(photo.getId().equals(photoId));
        room.setCoverImagePath(selected.getPublicPath());
        audit.record(actor,"ROOM_COVER_CHANGED","ROOM",roomId.toString(),"Selected photo "+photoId+" as cover.");
    }
    public void delete(Long roomId, Long photoId, String actor) {
        Room room=rooms.findById(roomId).orElseThrow(()->new RoomNotFoundException("Room not found."));
        RoomPhoto photo=photos.findByIdAndRoomId(photoId,roomId).orElseThrow(()->new IllegalArgumentException("Photo not found."));
        boolean wasCover=photo.isCover();
        photos.delete(photo);
        storage.delete(photo.getFileName());
        if(wasCover) {
            List<RoomPhoto> remaining=photos.findByRoomIdOrderByUploadedAtAsc(roomId);
            if(remaining.isEmpty()) room.setCoverImagePath(null);
            else { remaining.get(0).setCover(true); room.setCoverImagePath(remaining.get(0).getPublicPath()); }
        }
        audit.record(actor,"ROOM_PHOTO_REMOVED","ROOM",roomId.toString(),"Removed photo "+photoId);
    }
}