package com.hotel.system.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseBackupServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createsReadableSqliteBackupOutsideTheLiveDatabaseFile() throws Exception {
        Path database = tempDir.resolve("hotel.db");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + database);
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE backup_probe (value TEXT NOT NULL)");
            statement.execute("INSERT INTO backup_probe(value) VALUES ('backup-ok')");
        }

        DatabaseBackupService service = new DatabaseBackupService(dataSource,
                tempDir.resolve("backups").toString(),
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneId.of("Asia/Manila")));

        Path backup = service.createBackup();

        assertTrue(Files.exists(backup));
        assertNotEquals(database.toAbsolutePath(), backup.toAbsolutePath());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + backup);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT value FROM backup_probe")) {
            assertTrue(result.next());
            assertEquals("backup-ok", result.getString(1));
        }
    }

    @Test
    void retainsOnlyTheFourteenNewestBackups() throws Exception {
        Path database = tempDir.resolve("hotel-retention.db");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + database);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE retention_probe (value INTEGER)");
        }
        Path backupDirectory = tempDir.resolve("retained");
        DatabaseBackupService service = new DatabaseBackupService(dataSource,
                backupDirectory.toString(),
                Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneId.of("Asia/Manila")));

        for (int i = 0; i < 16; i++) service.createBackup();

        try (Stream<Path> files = Files.list(backupDirectory)) {
            assertEquals(14, files.filter(path -> path.getFileName().toString().endsWith(".sqlite")).count());
        }
    }
}
