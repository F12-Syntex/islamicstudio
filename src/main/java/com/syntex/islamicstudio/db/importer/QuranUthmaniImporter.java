package com.syntex.islamicstudio.db.importer;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class QuranUthmaniImporter implements Importer {

    @Override
    public String getName() { return "quran-uthmani"; }

    @Override
    public void clearData(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM ayah_text");
            stmt.executeUpdate("DELETE FROM ayah");
            stmt.executeUpdate("DELETE FROM surah");
            stmt.executeUpdate("DELETE FROM text_source");
        }
    }

    @Override
    public void importData(Connection conn, InputStream inputStream) throws Exception {
        PreparedStatement surahStmt = conn.prepareStatement(
            "INSERT INTO surah(id, name_ar, ayah_count) VALUES (?, ?, ?)"
        );
        PreparedStatement ayahStmt = conn.prepareStatement(
            "INSERT INTO ayah(surah_id, ayah_number) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS
        );
        PreparedStatement textStmt = conn.prepareStatement(
            "INSERT INTO ayah_text(ayah_id, source_id, text, bismillah) VALUES (?, ?, ?, ?)"
        );

        Statement s = conn.createStatement();
        s.executeUpdate("INSERT OR IGNORE INTO text_source(id, name, description) VALUES (1, 'Uthmani', 'Uthmani Script')");

        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = db.parse(inputStream);
        NodeList suras = doc.getElementsByTagName("sura");

        for (int i = 0; i < suras.getLength(); i++) {
            Element sura = (Element) suras.item(i);
            int surahId = Integer.parseInt(sura.getAttribute("index"));
            String name = sura.getAttribute("name");
            NodeList ayas = sura.getElementsByTagName("aya");

            surahStmt.setInt(1, surahId);
            surahStmt.setString(2, name);
            surahStmt.setInt(3, ayas.getLength());
            surahStmt.executeUpdate();

            for (int j = 0; j < ayas.getLength(); j++) {
                Element aya = (Element) ayas.item(j);
                int ayahNumber = Integer.parseInt(aya.getAttribute("index"));
                String text = aya.getAttribute("text");
                String bismillah = aya.hasAttribute("bismillah") ? aya.getAttribute("bismillah") : null;

                ayahStmt.setInt(1, surahId);
                ayahStmt.setInt(2, ayahNumber);
                ayahStmt.executeUpdate();

                ResultSet rs = ayahStmt.getGeneratedKeys();
                int ayahId = rs.next() ? rs.getInt(1) : getAyahId(conn, surahId, ayahNumber);

                textStmt.setInt(1, ayahId);
                textStmt.setInt(2, 1);
                textStmt.setString(3, text);
                textStmt.setString(4, bismillah);
                textStmt.executeUpdate();
            }
        }
    }

    private int getAyahId(Connection conn, int surahId, int ayahNum) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT id FROM ayah WHERE surah_id=? AND ayah_number=?");
        ps.setInt(1, surahId);
        ps.setInt(2, ayahNum);
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) throw new IllegalStateException("Ayah not found: " + surahId + ":" + ayahNum);
        return rs.getInt(1);
    }
}