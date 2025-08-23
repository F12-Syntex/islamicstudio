package com.syntex.islamicstudio.db.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;

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
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM hadith");
            stmt.executeUpdate("DELETE FROM hadith_book");
            stmt.executeUpdate("DELETE FROM hadith_collection");
        }
    }

    @Override
    public void importData(Connection conn, InputStream inputStream) throws Exception {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(inputStream)).getAsJsonObject();

        PreparedStatement insColl = conn.prepareStatement("""
            INSERT INTO hadith_collection(name, arabic_name, short_desc, num_books, num_hadiths)
            VALUES (?, ?, ?, ?, ?)
        """, Statement.RETURN_GENERATED_KEYS);

        insColl.setString(1, root.get("name").getAsString());
        insColl.setString(2, root.get("arabic_name").getAsString());
        insColl.setString(3, root.get("short_desc").getAsString());
        insColl.setInt(4, root.get("num_books").getAsInt());
        insColl.setInt(5, root.get("num_hadiths").getAsInt());
        insColl.executeUpdate();

        ResultSet rsColl = insColl.getGeneratedKeys();
        rsColl.next();
        int collId = rsColl.getInt(1);

        JsonArray books = root.getAsJsonArray("all_books");
        PreparedStatement insBook = conn.prepareStatement("""
            INSERT INTO hadith_book(collection_id, num, english_title, arabic_title)
            VALUES (?, ?, ?, ?)
        """, Statement.RETURN_GENERATED_KEYS);

        PreparedStatement insHadith = conn.prepareStatement("""
            INSERT INTO hadith(book_id, title, narrator, english_text, arabic_text, local_num, grade, uuid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """);

        for (var b : books) {
            JsonObject book = b.getAsJsonObject();
            insBook.setInt(1, collId);
            insBook.setInt(2, book.get("num").getAsInt());
            insBook.setString(3, book.get("english_title").getAsString());
            insBook.setString(4, book.get("arabic_title").getAsString());
            insBook.executeUpdate();

            ResultSet rsBook = insBook.getGeneratedKeys();
            rsBook.next();
            int bookId = rsBook.getInt(1);

            JsonArray hadiths = book.getAsJsonArray("hadith_list");
            for (var h : hadiths) {
                JsonObject hadith = h.getAsJsonObject();
                insHadith.setInt(1, bookId);
                insHadith.setString(2, hadith.get("title").getAsString());
                insHadith.setString(3, hadith.get("narrator").getAsString());
                insHadith.setString(4, hadith.get("english_text").getAsString());
                insHadith.setString(5, hadith.get("arabic_text").getAsString());
                insHadith.setString(6, hadith.get("local_num").getAsString());
                insHadith.setString(7, hadith.get("grade").getAsString());
                insHadith.setString(8, hadith.get("uuid").getAsString());
                insHadith.executeUpdate();
            }
        }
    }
}