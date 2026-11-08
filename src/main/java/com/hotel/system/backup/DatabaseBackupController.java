package com.hotel.system.backup;

import com.hotel.system.audit.AuditService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import java.nio.file.Path;

@Controller
public class DatabaseBackupController {
    private final DatabaseBackupService backups;
    private final AuditService audit;

    public DatabaseBackupController(DatabaseBackupService backups, AuditService audit) {
        this.backups = backups;
        this.audit = audit;
    }

    @PostMapping("/super-admin/backup")
    public ResponseEntity<Resource> downloadBackup(Authentication authentication) {
        Path backup = backups.createBackup();
        audit.record(authentication.getName(), "DATABASE_BACKUP_CREATED", "DATABASE", "sqlite",
                "A manual database backup was created and downloaded.");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + backup.getFileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .body(new FileSystemResource(backup));
    }
}
