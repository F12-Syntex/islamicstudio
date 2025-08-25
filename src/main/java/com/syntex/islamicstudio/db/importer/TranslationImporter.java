package com.syntex.islamicstudio.db.importer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

public class TranslationImporter implements Importer {

    private final String name;

    public TranslationImporter(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void clearData(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM translation_footnote");
            stmt.executeUpdate("DELETE FROM ayah_translation");
            stmt.executeUpdate("DELETE FROM translation_source");
        }
    }

    @Override
    public void importData(Connection conn, InputStream inputStream) throws Exception {
        // Insert source record if not exists
        Statement s = conn.createStatement();
        s.executeUpdate("""
            INSERT OR IGNORE INTO translation_source(id, language, name, author)
            VALUES (1, 'en', 'Sahih International', 'Sahih International')
        """);

        PreparedStatement findAyah = conn.prepareStatement(
                "SELECT id FROM ayah WHERE surah_id=? AND ayah_number=?");

        PreparedStatement insTr = conn.prepareStatement(
                "INSERT INTO ayah_translation(ayah_id, source_id, translation) VALUES (?, 1, ?)",
                Statement.RETURN_GENERATED_KEYS
        );

        PreparedStatement insFoot = conn.prepareStatement(
                "INSERT INTO translation_footnote(ayah_translation_id, marker, content) VALUES (?, ?, ?)"
        );

        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String[] parts;
            int lineNum = 0;

            // Skip metadata lines until header
            while ((parts = reader.readNext()) != null) {
                lineNum++;
                if (parts.length > 0 && parts[0] != null && parts[0].trim().equalsIgnoreCase("id")) {
                    break;
                }
            }

            if (parts == null || parts.length < 4) {
                throw new IllegalStateException("Invalid CSV header row");
            }

            while ((parts = reader.readNext()) != null) {
                lineNum++;
                if (parts.length < 4) {
                    continue;
                }

                int surah = Integer.parseInt(parts[1].trim());
                int aya = Integer.parseInt(parts[2].trim());
                String translation = parts[3].trim();
                String footnotes = (parts.length > 4) ? parts[4].trim() : null;

                findAyah.setInt(1, surah);
                findAyah.setInt(2, aya);
                ResultSet rs = findAyah.executeQuery();
                if (!rs.next()) {
                    throw new IllegalStateException("Ayah not found for Surah " + surah + " Aya " + aya);
                }
                int ayahId = rs.getInt(1);

                insTr.setInt(1, ayahId);
                insTr.setString(2, translation);
                insTr.executeUpdate();

                int trId;
                ResultSet trKeys = insTr.getGeneratedKeys();
                if (trKeys.next()) {
                    trId = trKeys.getInt(1);
                } else {
                    PreparedStatement f = conn.prepareStatement(
                            "SELECT id FROM ayah_translation WHERE ayah_id=? AND source_id=1");
                    f.setInt(1, ayahId);
                    ResultSet fr = f.executeQuery();
                    if (!fr.next()) {
                        throw new IllegalStateException("Failed to retrieve translation ID for ayah " + ayahId);
                    }
                    trId = fr.getInt(1);
                }

                if (footnotes != null && !footnotes.isBlank()) {
                    String[] split = footnotes.split("\\[(\\d+)\\]");
                    int idx = 1;
                    for (String fn : split) {
                        if (fn.trim().isEmpty()) {
                            continue;
                        }
                        insFoot.setInt(1, trId);
                        insFoot.setString(2, String.valueOf(idx));
                        insFoot.setString(3, fn.trim());
                        insFoot.executeUpdate();
                        idx++;
                    }
                }
            }
        } catch (CsvValidationException e) {
            throw new RuntimeException("Invalid CSV format: " + e.getMessage(), e);
        }
    }
}
