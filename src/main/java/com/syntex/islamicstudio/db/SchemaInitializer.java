package com.syntex.islamicstudio.db;

import java.sql.Connection;
import java.sql.Statement;

public class SchemaInitializer {
    public static void init() {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS surah (
                id INTEGER PRIMARY KEY,
                name_ar TEXT NOT NULL,
                name_en TEXT,
                ayah_count INT
            );""");

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS ayah (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                surah_id INT NOT NULL,
                ayah_number INT NOT NULL,
                UNIQUE(surah_id, ayah_number),
                FOREIGN KEY (surah_id) REFERENCES surah(id)
            );""");

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS text_source (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT
            );""");

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS ayah_text (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ayah_id INT NOT NULL,
                source_id INT NOT NULL,
                text TEXT NOT NULL,
                bismillah TEXT,
                FOREIGN KEY (ayah_id) REFERENCES ayah(id),
                FOREIGN KEY (source_id) REFERENCES text_source(id),
                UNIQUE(ayah_id, source_id)
            );""");

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS translation_source (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                language TEXT NOT NULL,
                name TEXT NOT NULL,
                author TEXT,
                notes TEXT
            );""");

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS ayah_translation (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ayah_id INT NOT NULL,
                source_id INT NOT NULL,
                translation TEXT NOT NULL,
                FOREIGN KEY (ayah_id) REFERENCES ayah(id),
                FOREIGN KEY (source_id) REFERENCES translation_source(id),
                UNIQUE(ayah_id, source_id)
            );""");

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS translation_footnote (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ayah_translation_id INT NOT NULL,
                marker TEXT,
                content TEXT NOT NULL,
                FOREIGN KEY (ayah_translation_id) REFERENCES ayah_translation(id)
            );""");

            System.out.println("✅ Schema initialized.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}