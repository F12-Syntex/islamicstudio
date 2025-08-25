package com.syntex.islamicstudio.db.importer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Imports one hadith collection JSON (hadith/<resourceName>.json) into the
 * unified hadith tables. For fresh-DB workflow: clearData is a no-op; importers
 * assume an empty DB.
 */
public class HadithImporter implements Importer {

    private final String resourceName;

    public HadithImporter(String resourceName) {
        this.resourceName = resourceName;
    }

    @Override
    public String getName() {
        return "hadith-" + resourceName;
    }

    @Override
    public void clearData(Connection conn) throws Exception {
        // Determine collection name from JSON file and wipe only its books/hadith
        String jsonPath = "hadith/" + resourceName + ".json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(jsonPath)) {
            if (in == null) {
                System.err.println("⚠️ could not open " + jsonPath + " to determine collection name; skipping clear.");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
            String collName = root.get("name").getAsString();

            // Lookup collection id (may or may not exist yet)
            Integer collId = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM hadith_collection WHERE name=?")) {
                ps.setString(1, collName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    collId = rs.getInt(1);
                }
            }

            if (collId == null) {
                return; // nothing to clear
            }
            // Delete hadith belonging to that collection via books
            try (PreparedStatement delHadith = conn.prepareStatement(
                    "DELETE FROM hadith WHERE book_id IN (SELECT id FROM hadith_book WHERE collection_id=?)")) {
                delHadith.setInt(1, collId);
                delHadith.executeUpdate();
            }
            // Delete the books
            try (PreparedStatement delBooks = conn.prepareStatement(
                    "DELETE FROM hadith_book WHERE collection_id=?")) {
                delBooks.setInt(1, collId);
                delBooks.executeUpdate();
            }
            // Keep the collection row; import will INSERT OR IGNORE to ensure it exists.
        }
    }

    @Override
    public void importData(Connection conn, InputStream inputStream) throws Exception {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(inputStream)).getAsJsonObject();

        // 1) Insert or fetch collection
        try (PreparedStatement insColl = conn.prepareStatement("""
            INSERT OR IGNORE INTO hadith_collection(name, arabic_name, short_desc, num_books, num_hadiths)
            VALUES (?, ?, ?, ?, ?)
        """)) {
            insColl.setString(1, root.get("name").getAsString());
            insColl.setString(2, root.get("arabic_name").getAsString());
            insColl.setString(3, root.get("short_desc").getAsString());
            insColl.setInt(4, parseIntSafe(root.get("num_books").getAsString()));
            insColl.setInt(5, parseIntSafe(root.get("num_hadiths").getAsString()));
            insColl.executeUpdate();
        }

        int collId;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM hadith_collection WHERE name=?")) {
            ps.setString(1, root.get("name").getAsString());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new IllegalStateException("Failed to retrieve hadith_collection id for " + root.get("name").getAsString());
            }
            collId = rs.getInt(1);
        }

        // Prepare statements
        PreparedStatement insBook = conn.prepareStatement("""
            INSERT OR IGNORE INTO hadith_book(collection_id, num, english_title, arabic_title)
            VALUES (?, ?, ?, ?)
        """);

        PreparedStatement insHadith = conn.prepareStatement("""
            INSERT OR IGNORE INTO hadith(book_id, title, narrator, english_text, arabic_text, local_num, grade, uuid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """);

        JsonArray books = root.getAsJsonArray("all_books");
        for (var b : books) {
            JsonObject book = b.getAsJsonObject();

            int bookNum = parseIntSafe(book.get("num").getAsString());
            String bookEn = book.get("english_title").getAsString();
            String bookAr = book.get("arabic_title").getAsString();

            // 2) Insert or fetch book
            insBook.setInt(1, collId);
            insBook.setInt(2, bookNum);
            insBook.setString(3, bookEn);
            insBook.setString(4, bookAr);
            insBook.executeUpdate();

            int bookId;
            try (PreparedStatement findBook = conn.prepareStatement(
                    "SELECT id FROM hadith_book WHERE collection_id=? AND num=?")) {
                findBook.setInt(1, collId);
                findBook.setInt(2, bookNum);
                ResultSet rs = findBook.executeQuery();
                if (!rs.next()) {
                    throw new IllegalStateException("Failed to retrieve hadith_book id for book #" + bookNum + " in coll_id=" + collId);
                }
                bookId = rs.getInt(1);
            }

            // 3) Insert hadith rows
            JsonArray hadiths = book.getAsJsonArray("hadith_list");
            for (var h : hadiths) {
                JsonObject hadith = h.getAsJsonObject();
                String title = hadith.get("title").getAsString();
                String narrator = hadith.get("narrator").getAsString();
                String englishText = safeString(hadith, "english_text");
                String arabicText = safeString(hadith, "arabic_text");
                String localNum = safeString(hadith, "local_num");
                String grade = safeString(hadith, "grade");
                String uuid = safeString(hadith, "uuid");

                insHadith.setInt(1, bookId);
                insHadith.setString(2, title);
                insHadith.setString(3, narrator);
                insHadith.setString(4, englishText);
                insHadith.setString(5, arabicText);
                insHadith.setString(6, localNum);
                insHadith.setString(7, grade);
                insHadith.setString(8, uuid);
                insHadith.executeUpdate();
            }
        }
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String safeString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

}
