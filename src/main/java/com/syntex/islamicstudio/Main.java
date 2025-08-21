package com.syntex.islamicstudio;

import java.io.InputStream;
import java.util.List;

import com.syntex.islamicstudio.cli.CliInterface;
import com.syntex.islamicstudio.cli.CommandLoader;
import com.syntex.islamicstudio.db.SchemaInitializer;
import com.syntex.islamicstudio.db.importer.ImportManager;
import com.syntex.islamicstudio.db.importer.QuranUthmaniImporter;
import com.syntex.islamicstudio.db.importer.TranslationImporter;

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

            printRandomAyah();

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
        if (in == null) throw new IllegalStateException("Resource not found: " + resourceName);
        return in;
    }

    private void printRandomAyah() {
        try (var conn = com.syntex.islamicstudio.db.DatabaseManager.getConnection();
             var stmt = conn.createStatement()) {

            var rs = stmt.executeQuery("SELECT id, surah_id, ayah_number FROM ayah ORDER BY RANDOM() LIMIT 1");
            if (!rs.next()) {
                System.out.println("No ayah found in database.");
                return;
            }

            int ayahId = rs.getInt("id");
            int surahId = rs.getInt("surah_id");
            int ayahNum = rs.getInt("ayah_number");

            var surahStmt = conn.prepareStatement("SELECT name_ar FROM surah WHERE id=?");
            surahStmt.setInt(1, surahId);
            var surahRs = surahStmt.executeQuery();
            String surahName = surahRs.next() ? surahRs.getString("name_ar") : "?";

            var textStmt = conn.prepareStatement("SELECT text FROM ayah_text WHERE ayah_id=? AND source_id=1");
            textStmt.setInt(1, ayahId);
            var textRs = textStmt.executeQuery();
            String arabicText = textRs.next() ? textRs.getString("text") : "(no text)";

            var trStmt = conn.prepareStatement("SELECT translation FROM ayah_translation WHERE ayah_id=? AND source_id=1");
            trStmt.setInt(1, ayahId);
            var trRs = trStmt.executeQuery();
            String translation = trRs.next() ? trRs.getString("translation") : "(no translation)";

            System.out.println("📖 Random Ayah:");
            System.out.println("Surah " + surahId + " (" + surahName + ") - Ayah " + ayahNum);
            System.out.println("Arabic: " + arabicText);
            System.out.println("English: " + translation);

        } catch (Exception e) {
            System.err.println("⚠️ Failed to fetch random ayah: " + e.getMessage());
            e.printStackTrace();
        }
    }
}