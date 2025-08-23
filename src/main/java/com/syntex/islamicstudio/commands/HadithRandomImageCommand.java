package com.syntex.islamicstudio.commands;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.syntex.islamicstudio.cli.Color;
import com.syntex.islamicstudio.cli.CommandCategory;
import com.syntex.islamicstudio.db.DatabaseManager;
import com.syntex.islamicstudio.media.PixabayImageDownloader;
import com.syntex.islamicstudio.util.HadithImageGenerator;

import picocli.CommandLine;

@CommandLine.Command(
        name = "hadith-random-image",
        description = "Generate an Instagram-style image for a random hadith and print JSON"
)
@CommandCategory("Hadith")
public class HadithRandomImageCommand implements Runnable {

    private final OpenAIClient openAi = OpenAIOkHttpClient.fromEnv();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void run() {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("""
                SELECT h.narrator, h.english_text, h.arabic_text,
                       h.local_num, h.grade,
                       b.english_title as book_en, b.arabic_title as book_ar,
                       c.name as coll, c.arabic_name as coll_ar
                FROM hadith h
                JOIN hadith_book b ON h.book_id=b.id
                JOIN hadith_collection c ON b.collection_id=c.id
                ORDER BY RANDOM() LIMIT 1
            """);

            if (!rs.next()) {
                System.out.println(Color.RED.wrap("⚠ No hadith found in database."));
                return;
            }

            // Collect hadith fields
            String collection = rs.getString("coll");
            String collectionAr = rs.getString("coll_ar");
            String bookEn = rs.getString("book_en");
            String bookAr = rs.getString("book_ar");
            String num = rs.getString("local_num");
            String grade = rs.getString("grade");
            String narrator = rs.getString("narrator");
            String arabic = rs.getString("arabic_text");
            String english = rs.getString("english_text");

            // --- 1) JSON output ---
            Map<String, Object> hadithJson = new LinkedHashMap<>();
            hadithJson.put("collection", collection);
            hadithJson.put("collection_ar", collectionAr);
            hadithJson.put("book_en", bookEn);
            hadithJson.put("book_ar", bookAr);
            hadithJson.put("number", num);
            hadithJson.put("grade", grade);
            hadithJson.put("narrator", narrator);
            hadithJson.put("arabic_text", arabic);
            hadithJson.put("english_text", english);

            String fileName = "hadith_" + num + ".png";
            hadithJson.put("image_file", "output/" + fileName);

            System.out.println(gson.toJson(hadithJson));

            // --- 2) Generate Image ---
            String keyword = suggestBackgroundForHadith(english);

            File workDir = new File("output/hadith_bg");
            workDir.mkdirs();
            List<File> bg = PixabayImageDownloader.downloadImages(keyword, workDir, 1);

            HadithImageGenerator.generateHadithImage(
                    bg.get(0),
                    collection + " (" + collectionAr + ")",
                    bookEn, bookAr,
                    num, grade, narrator,
                    arabic, english,
                    fileName
            );

        } catch (Exception e) {
            System.err.println("⚠ Failed to generate hadith image: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String suggestBackgroundForHadith(String englishText) {
        try {
            String prompt = "Suggest a short, visual keyword for a Pixabay photo background that matches the theme of this hadith:\n\n"
                    + englishText + "\n\n"
                    + "Guidelines:\n"
                    + "- Respond with ONE or TWO words only.\n"
                    + "- No people, no animals. NOTHING HARAAM. NO WOMEN.\n"
                    + "- Prefer natural landscapes, flowers, sky, oceans, light, calligraphy textures.";

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model("gpt-4o-mini")
                    .addUserMessage(prompt)
                    .maxTokens(20)
                    .temperature(0.5)
                    .build();

            ChatCompletion completion = openAi.chat().completions().create(params);
            return completion.choices().get(0).message().content().orElse("abstract background");
        } catch (Exception e) {
            return "abstract background";
        }
    }
}