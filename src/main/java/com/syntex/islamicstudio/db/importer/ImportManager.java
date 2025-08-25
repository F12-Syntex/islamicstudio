package com.syntex.islamicstudio.db.importer;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import com.syntex.islamicstudio.db.DatabaseManager;

public class ImportManager {

    public static void runImports(List<ImporterWithResource> importers) {
        try (Connection conn = DatabaseManager.getConnection()) {
            createStatusTable(conn);

            for (ImporterWithResource wrapper : importers) {
                Importer importer = wrapper.importer();
                String name = importer.getName();

                if (isCompleted(conn, name)) {
                    System.out.println("✓ Import already completed for " + name + " — skipping.");
                    continue;
                }

                System.out.println("⏳ Importing " + name + " ...");
                conn.setAutoCommit(false);
                boolean ok = false;
                try {
                    // Clear any partial data for this resource
                    importer.clearData(conn);

                    // Import
                    try (InputStream in = wrapper.streamSupplier().get()) {
                        importer.importData(conn, in);
                    }

                    markCompleted(conn, name);
                    conn.commit();
                    ok = true;
                    System.out.println("✅ Imported: " + name);
                } catch (Exception e) {
                    System.err.println("❌ Import failed for " + name + ": " + e.getMessage());
                    try { conn.rollback(); } catch (Exception ignored) {}
                    // Leave status as not completed so it can be retried next run.
                    throw e;
                } finally {
                    try { conn.setAutoCommit(true); } catch (Exception ignored) {}
                    if (!ok) {
                        System.err.println("⚠️ Rolled back " + name + " (no partial data kept).");
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("🚨 Import process failed: " + e.getMessage(), e);
        }
    }

    private static void createStatusTable(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS import_status (
                    importer TEXT PRIMARY KEY,
                    completed INTEGER NOT NULL DEFAULT 0,
                    completed_at TEXT
                )
            """);
        }
    }

    private static boolean isCompleted(Connection conn, String importer) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT completed FROM import_status WHERE importer=?")) {
            ps.setString(1, importer);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("completed") == 1;
                return false;
            }
        }
    }

    private static void markCompleted(Connection conn, String importer) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO import_status(importer, completed, completed_at)
            VALUES (?, 1, ?)
            ON CONFLICT(importer) DO UPDATE SET completed=1, completed_at=excluded.completed_at
        """)) {
            ps.setString(1, importer);
            ps.setString(2, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    public record ImporterWithResource(Importer importer, java.util.function.Supplier<InputStream> streamSupplier) {}
}