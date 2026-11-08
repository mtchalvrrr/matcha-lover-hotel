package com.hotel.system.backup;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class DatabaseBackupService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final DataSource dataSource;
    private final Path backupDirectory;
    private final Clock clock;

    public DatabaseBackupService(DataSource dataSource,
            @Value("${hotel.backup.location:./data/backups}") String backupLocation,
            Clock clock) {
        this.dataSource = dataSource;
        this.backupDirectory = Path.of(backupLocation).toAbsolutePath().normalize();
        this.clock = clock;
    }

    public Path createBackup() {
        try {
            Files.createDirectories(backupDirectory);
            String fileName = "hotel-db-" + LocalDateTime.now(clock).format(FILE_TIME)
                    + "-" + UUID.randomUUID().toString().substring(0, 8) + ".sqlite";
            Path target = backupDirectory.resolve(fileName).normalize();
            if (!target.startsWith(backupDirectory)) {
                throw new IllegalStateException("Invalid backup destination.");
            }
            try (Connection connection = dataSource.getConnection()) {
                String jdbcUrl = connection.getMetaData().getURL();
                if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
                    throw new IllegalStateException("Automated backup currently supports SQLite databases only.");
                }
                String escapedPath = target.toString().replace("'", "''");
                try (Statement statement = connection.createStatement()) {
                    statement.execute("VACUUM INTO '" + escapedPath + "'");
                }
            }
            pruneOldBackups();
            return target;
        } catch (Exception ex) {
            throw new IllegalStateException("The database backup could not be created.", ex);
        }
    }

    @Scheduled(cron = "${hotel.backup.cron:0 0 2 * * *}", zone = "${hotel.time-zone:Asia/Manila}")
    public void createScheduledBackup() {
        try {
            Path backup = createBackup();
            log.info("Scheduled SQLite backup created: {}", backup.getFileName());
        } catch (RuntimeException ex) {
            log.error("Scheduled SQLite backup failed.", ex);
        }
    }

    private void pruneOldBackups() throws Exception {
        try (Stream<Path> files = Files.list(backupDirectory)) {
            for (Path oldBackup : files
                    .filter(path -> path.getFileName().toString().startsWith("hotel-db-")
                            && path.getFileName().toString().endsWith(".sqlite"))
                    .sorted(Comparator.comparingLong(this::lastModified).reversed())
                    .skip(14)
                    .toList()) {
                Files.deleteIfExists(oldBackup);
            }
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ex) {
            return 0L;
        }
    }
}
