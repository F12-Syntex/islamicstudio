package com.syntex.islamicstudio.db.importer;

import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;

import com.syntex.islamicstudio.db.DatabaseManager;

public class ImportManager {

    public static void runImports(List<ImporterWithResource> importers) {
        try (Connection conn = DatabaseManager.getConnection()) {
            createRegistryTable(conn);

            for (ImporterWithResource wrapper : importers) {
                Importer importer = wrapper.importer();
                String name = importer.getName();

                // Compute checksum
                String checksum;
                try (InputStream in = wrapper.streamSupplier().get()) {
                    checksum = computeChecksum(in);
                }

                // Check registry
                PreparedStatement psCheck = conn.prepareStatement(
                    "SELECT checksum FROM import_registry WHERE importer=?"
                );
                psCheck.setString(1, name);
                ResultSet rs = psCheck.executeQuery();

                boolean needsImport = true;

                if (rs.next()) {
                    String existing = rs.getString("checksum");
                    if (existing.equals(checksum)) {
                        System.out.println("✅ " + name + " already up-to-date. Skipping...");
                        needsImport = false;
                    } else {
                        System.out.println("⚠️ " + name + " changed. Re-importing...");
                        importer.clearData(conn);
                    }
                } else {
                    System.out.println("ℹ️ First import for " + name + ". Proceeding...");
                    importer.clearData(conn);
                }

                if (needsImport) {
                    try (InputStream in = wrapper.streamSupplier().get()) {
                        importer.importData(conn, in);
                    }

                    PreparedStatement psUp = conn.prepareStatement("""
                        INSERT INTO import_registry(importer, file_path, checksum, imported_at)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT(importer) DO UPDATE SET checksum=excluded.checksum,
                                                              imported_at=CURRENT_TIMESTAMP
                    """);
                    psUp.setString(1, name);
                    psUp.setString(2, "classpath"); // no real file path
                    psUp.setString(3, checksum);
                    psUp.executeUpdate();

                    System.out.println("✅ " + name + " imported and registered.");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("❌ Import process failed: " + e.getMessage(), e);
        }
    }

    private static void createRegistryTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS import_registry (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    importer TEXT NOT NULL UNIQUE,
                    file_path TEXT,
                    checksum TEXT NOT NULL,
                    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    private static String computeChecksum(InputStream in) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) {
            md.update(buf, 0, r);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /**
     * Helper record to bind importer with a resource stream supplier.
     */
    public record ImporterWithResource(Importer importer, java.util.function.Supplier<InputStream> streamSupplier) {}
}