package com.syntex.islamicstudio;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.syntex.islamicstudio.cli.CliInterface;
import com.syntex.islamicstudio.cli.CommandLoader;
import com.syntex.islamicstudio.db.DatabaseManager;
import com.syntex.islamicstudio.db.SchemaInitializer;
import com.syntex.islamicstudio.db.importer.HadithImporter;
import com.syntex.islamicstudio.db.importer.ImportManager;
import com.syntex.islamicstudio.db.importer.QuranUthmaniImporter;
import com.syntex.islamicstudio.db.importer.TranslationImporter;
import com.syntex.islamicstudio.util.VerseImageGenerator;

import picocli.CommandLine;

@CommandLine.Command(
        name = "islamicstudio",
        description = "Islamic Studio CLI application"
)
public class Main implements Runnable {

    @Override
    public void run() {
        SchemaInitializer.init();

        try {
            List<ImportManager.ImporterWithResource> importers = new ArrayList<>();

            // Qur’an + translation
            importers.add(new ImportManager.ImporterWithResource(
                    new QuranUthmaniImporter(),
                    () -> getResourceStream("quran-uthmani.xml")));
            importers.add(new ImportManager.ImporterWithResource(
                    new TranslationImporter("sahih-international"),
                    () -> getResourceStream("sahih-internationl.csv")));

            // Hadith (scan hadith/ folder)
            importers.addAll(scanHadithImports());

            ImportManager.runImports(importers);

        } catch (Exception e) {
            System.err.println("⚠ Failed to import resources: " + e.getMessage());
            e.printStackTrace();
        }

        Config config = new Config();
        CommandLine cmd = buildCommandLine();
        CliInterface cli = new CliInterface(config, cmd);
        cli.start();
    }

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        CommandLine cmd = buildCommandLine();
        if (args.length > 0) {
            int exitCode = cmd.execute(args);
            System.exit(exitCode);
        } else {
            new Main().run();
        }
    }

    private static CommandLine buildCommandLine() {
        CommandLine root = new CommandLine(new Main());
        CommandLoader.registerCommands(root, "com.syntex.islamicstudio.commands");
        return root;
    }

    private static InputStream getResourceStream(String resourceName) {
        InputStream in = Main.class.getClassLoader().getResourceAsStream(resourceName);
        if (in == null) {
            throw new IllegalStateException("Resource not found: " + resourceName);
        }
        return in;
    }

    /**
     * Scan resources/hadith/ for all JSON files and create importers.
     */
    private static List<ImportManager.ImporterWithResource> scanHadithImports() throws Exception {
        List<ImportManager.ImporterWithResource> list = new ArrayList<>();

        Enumeration<URL> urls = Main.class.getClassLoader().getResources("hadith");
        if (!urls.hasMoreElements()) {
            System.err.println("⚠ hadith/ folder not found in resources.");
            return list;
        }

        URL hadithUrl = urls.nextElement();
        if (hadithUrl.getProtocol().equals("file")) {
            // running from IDE / exploded classes
            File dir = new File(hadithUrl.toURI());
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    String fileName = "hadith/" + f.getName();
                    String baseName = f.getName().replaceFirst("\\.json$", "");
                    list.add(new ImportManager.ImporterWithResource(
                            new HadithImporter(baseName),
                            () -> getResourceStream(fileName)));
                }
            }
        } else if (hadithUrl.getProtocol().equals("jar")) {
            // running inside a packaged JAR
            String path = hadithUrl.getPath();
            String jarPath = path.substring(5, path.indexOf("!"));
            try (JarFile jar = new JarFile(jarPath)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("hadith/") && entry.getName().endsWith(".json")) {
                        String baseName = new File(entry.getName()).getName()
                                .replaceFirst("\\.json$", "");
                        list.add(new ImportManager.ImporterWithResource(
                                new HadithImporter(baseName),
                                () -> getResourceStream(entry.getName())));
                    }
                }
            }
        }

        return list;
    }

    /**
     * Example: Fetch a random ayah from DB and generate an image in output/
     */
    private void generateRandomAyahImage() {
        try (Connection conn = DatabaseManager.getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, surah_id, ayah_number FROM ayah ORDER BY RANDOM() LIMIT 1");
            if (!rs.next()) return;

            int ayahId = rs.getInt("id");
            int surahId = rs.getInt("surah_id");
            int ayahNum = rs.getInt("ayah_number");

            var psSurah = conn.prepareStatement("SELECT name_ar FROM surah WHERE id=?");
            psSurah.setInt(1, surahId);
            var rsSurah = psSurah.executeQuery();
            String surahName = rsSurah.next() ? rsSurah.getString("name_ar") : "?";

            var psText = conn.prepareStatement("SELECT text FROM ayah_text WHERE ayah_id=? AND source_id=1");
            psText.setInt(1, ayahId);
            var rsText = psText.executeQuery();
            String arabicText = rsText.next() ? rsText.getString("text") : "(no text)";

            var psTrans = conn.prepareStatement("SELECT translation FROM ayah_translation WHERE ayah_id=? AND source_id=1");
            psTrans.setInt(1, ayahId);
            var rsTrans = psTrans.executeQuery();
            String translation = rsTrans.next() ? rsTrans.getString("translation") : "(no translation)";

            StringBuilder footnotes = new StringBuilder();
            var psFoot = conn.prepareStatement("SELECT marker, content FROM translation_footnote WHERE ayah_translation_id IN (SELECT id FROM ayah_translation WHERE ayah_id=? AND source_id=1)");
            psFoot.setInt(1, ayahId);
            var rsFoot = psFoot.executeQuery();
            while (rsFoot.next()) {
                footnotes.append("[").append(rsFoot.getString("marker")).append("] ")
                        .append(rsFoot.getString("content")).append(" ");
            }

            VerseImageGenerator.generateVerseImage(
                    surahName, ayahNum,
                    arabicText, translation, footnotes.toString(),
                    "ayah_" + surahId + "_" + ayahNum + ".png"
            );

        } catch (Exception e) {
            System.err.println("⚠ Failed to generate random ayah image: " + e.getMessage());
            e.printStackTrace();
        }
    }
}