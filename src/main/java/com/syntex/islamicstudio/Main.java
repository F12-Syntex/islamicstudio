package com.syntex.islamicstudio;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import com.syntex.islamicstudio.cli.CliInterface;
import com.syntex.islamicstudio.cli.CommandLoader;
import com.syntex.islamicstudio.db.DatabaseManager;
import com.syntex.islamicstudio.db.SchemaInitializer;
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
            ImportManager.runImports(List.of(
                    new ImportManager.ImporterWithResource(new QuranUthmaniImporter(),
                            () -> getResourceStream("quran-uthmani.xml")),
                    new ImportManager.ImporterWithResource(new TranslationImporter("sahih-international"),
                            () -> getResourceStream("sahih-internationl.csv"))
            ));

            // 🔥 After imports, generate an image for a random ayah
            generateRandomAyahImage();

        } catch (Exception e) {
            System.err.println("⚠️ Failed to import resources: " + e.getMessage());
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
     * Fetch a random ayah from DB and generate an image in output/
     */
    private void generateRandomAyahImage() {
        try (Connection conn = DatabaseManager.getConnection(); Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT id, surah_id, ayah_number FROM ayah ORDER BY RANDOM() LIMIT 1");
            if (!rs.next()) {
                System.out.println("⚠️ No ayah found in database.");
                return;
            }

            int ayahId = rs.getInt("id");
            int surahId = rs.getInt("surah_id");
            int ayahNum = rs.getInt("ayah_number");

            // Surah name
            var psSurah = conn.prepareStatement("SELECT name_ar FROM surah WHERE id=?");
            psSurah.setInt(1, surahId);
            var rsSurah = psSurah.executeQuery();
            String surahName = rsSurah.next() ? rsSurah.getString("name_ar") : "?";

            // Arabic text
            var psText = conn.prepareStatement("SELECT text FROM ayah_text WHERE ayah_id=? AND source_id=1");
            psText.setInt(1, ayahId);
            var rsText = psText.executeQuery();
            String arabicText = rsText.next() ? rsText.getString("text") : "(no text)";

            // Translation
            var psTrans = conn.prepareStatement("SELECT translation FROM ayah_translation WHERE ayah_id=? AND source_id=1");
            psTrans.setInt(1, ayahId);
            var rsTrans = psTrans.executeQuery();
            String translation = rsTrans.next() ? rsTrans.getString("translation") : "(no translation)";

            // Footnotes (if any, just grab one for simplicity)
            var psFoot = conn.prepareStatement("SELECT marker, content FROM translation_footnote WHERE ayah_translation_id IN (SELECT id FROM ayah_translation WHERE ayah_id=? AND source_id=1)");
            psFoot.setInt(1, ayahId);
            var rsFoot = psFoot.executeQuery();
            StringBuilder footnotes = new StringBuilder();
            while (rsFoot.next()) {
                footnotes.append("[").append(rsFoot.getString("marker")).append("] ")
                        .append(rsFoot.getString("content")).append(" ");
            }

            // ✅ Generate image
            VerseImageGenerator.generateVerseImage(
                    surahName, ayahNum,
                    arabicText, translation, footnotes.toString(),
                    "ayah_" + surahId + "_" + ayahNum + ".png"
            );

        } catch (Exception e) {
            System.err.println("⚠️ Failed to generate random ayah image: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
