package com.syntex.islamicstudio.commands;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
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
        description = "Generate one or more Instagram-style images for a random authentic (Sahih) hadith (English-only) and print JSON per item"
)
@CommandCategory("Hadith")
public class HadithRandomImageCommand implements Runnable {

    private final OpenAIClient openAi = OpenAIOkHttpClient.fromEnv();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @CommandLine.Option(
            names = {"--collection"},
            description = "Restrict to a specific collection by exact name (e.g., 'Sahih al-Bukhari'). Default: any collection."
    )
    private String collectionFilter;

    @CommandLine.Option(
            names = {"-n", "--count"},
            description = "Number of images to generate (default: 1)"
    )
    private int count = 1;

    @Override
    public void run() {
        if (count < 1) {
            count = 1;
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            String baseSql = """
                SELECT h.narrator, h.english_text,
                       h.local_num, h.grade,
                       b.english_title AS book_en, b.arabic_title AS book_ar,
                       c.name AS coll, c.arabic_name AS coll_ar
                FROM hadith h
                JOIN hadith_book b ON h.book_id = b.id
                JOIN hadith_collection c ON b.collection_id = c.id
                WHERE LOWER(COALESCE(h.grade,'')) LIKE '%sahih%'
            """;

            StringBuilder sql = new StringBuilder(baseSql);
            List<Object> params = new ArrayList<>();

            if (collectionFilter != null && !collectionFilter.isBlank()) {
                sql.append(" AND c.name = ? ");
                params.add(collectionFilter);
            }

            sql.append(" ORDER BY RANDOM() LIMIT 1");

            for (int i = 0; i < count; i++) {
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int p = 0; p < params.size(); p++) {
                        ps.setObject(p + 1, params.get(p));
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            String scope = (collectionFilter == null || collectionFilter.isBlank())
                                    ? "from any collection"
                                    : "from collection '" + collectionFilter + "'";
                            System.out.println(Color.YELLOW.wrap("⚠️ No authentic (Sahih) hadith found " + scope + "."));

                            List<Map<String, Object>> collections = listAllCollections(conn);
                            Map<String, Object> out = new LinkedHashMap<>();
                            out.put("available_collections", collections);
                            System.out.println(gson.toJson(out));
                            continue;
                        }

                        String collection = rs.getString("coll");
                        String collectionAr = rs.getString("coll_ar");
                        String bookEn = rs.getString("book_en");
                        String bookAr = rs.getString("book_ar");
                        String num = rs.getString("local_num");
                        String grade = rs.getString("grade");
                        String narrator = rs.getString("narrator");
                        String english = rs.getString("english_text");

                        // Unique filename per iteration
                        String baseId = (num != null && !num.isBlank()) ? num : String.valueOf(System.currentTimeMillis());
                        String hadithReference = safeStr(bookEn) + "#" + safeStr(baseId) + "_" + i + ".png";

                        Map<String, Object> hadithJson = new LinkedHashMap<>();
                        hadithJson.put("collection", collection);
                        hadithJson.put("collection_ar", collectionAr);
                        hadithJson.put("book_en", bookEn);
                        hadithJson.put("book_ar", bookAr);
                        hadithJson.put("number", num);
                        hadithJson.put("grade", grade);
                        hadithJson.put("narrator", narrator);
                        hadithJson.put("english_text", english);
                        hadithJson.put("image_file", "output/" + hadithReference);
                        System.out.println(gson.toJson(hadithJson));

                        String raw = suggestBackgroundForHadith(english);
                        String keyword = sanitizeKeyword(raw);

                        File workDir = new File("output/hadith_bg");
                        if (!workDir.exists() && !workDir.mkdirs()) {
                            System.err.println("⚠️ Could not create directory: " + workDir.getAbsolutePath());
                        }

                        List<File> bg = PixabayImageDownloader.downloadImages(keyword, workDir, 1);
                        if (bg == null || bg.isEmpty() || bg.get(0) == null || !bg.get(0).exists()) {
                            String fallback = "calligraphy";
                            bg = PixabayImageDownloader.downloadImages(fallback, workDir, 1);
                            if (bg == null || bg.isEmpty()) {
                                System.err.println("⚠️ Failed to download background image for keywords: " + keyword + " and fallback.");
                                continue;
                            }
                        }

                        // Generate using the canonical method (now English-only by design)
                        HadithImageGenerator.generateHadithImage(
                                bg.get(0),
                                collection + " (" + safeStr(collectionAr) + ")",
                                safeStr(bookEn), safeStr(bookAr),
                                safeStr(num), safeStr(grade), safeStr(narrator),
                                safeStr(english),
                                hadithReference
                        );
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️ Failed to generate hadith images: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<Map<String, Object>> listAllCollections(Connection conn) {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = """
            SELECT id, name, arabic_name
            FROM hadith_collection
            ORDER BY name
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet crs = ps.executeQuery()) {
            while (crs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", crs.getObject("id"));
                row.put("name", crs.getString("name"));
                row.put("arabic_name", crs.getString("arabic_name"));
                out.add(row);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to list collections: " + e.getMessage());
        }
        return out;
    }

    private String suggestBackgroundForHadith(String englishText) {
        try {
            String prompt
                    = "You are selecting a background photo keyword for a tasteful hadith graphic.\n"
                    + "Return a single short keyword or a 2-word phrase that helps search Pixabay.\n\n"
                    + "Hadith (English):\n"
                    + englishText + "\n\n"
                    + "Requirements:\n"
                    + "- Output: ONE or TWO WORDS ONLY. No punctuation. No emojis.\n"
                    + "- The keyword MUST be suitable for a halal background: no humans, body parts, faces, silhouettes, crowds; no animals; no idols or religious symbols.\n"
                    + "- Prefer: natural landscapes, night sky, deserts, oceans, forests, abstract light, geometric textures, calligraphy textures.\n"
                    + "Return ONLY the keyword.";
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model("gpt-4o-mini")
                    .addUserMessage(prompt)
                    .temperature(0.5)
                    .build();

            ChatCompletion completion = openAi.chat().completions().create(params);
            String content = completion.choices().get(0).message().content().orElse("").trim();
            if (content.isEmpty()) {
                return "calligraphy";
            }
            return content;
        } catch (Exception e) {
            return "calligraphy";
        }
    }

    private String sanitizeKeyword(String kw) {
        String k = kw == null ? "" : kw.toLowerCase().trim();

        k = k.replaceAll("[^a-z\\s-]", " ").replaceAll("\\s+", " ").trim();
        if (k.isEmpty()) {
            return "calligraphy";
        }

        String[] parts = k.split("\\s+");
        if (parts.length > 2) {
            k = parts[0] + " " + parts[1];
        }

        String blocked = "(?i).*\\b("
                + "people|person|man|men|male|female|woman|women|girl|boy|face|body|hand|legs|knees|"
                + "animal|dog|cat|bird|horse|camel|fish|butterfly|"
                + "statue|idol|icon|cross|church|temple|synagogue|monk|nun|"
                + "celebrity|fashion|model|"
                + "alcohol|wine|beer|pork"
                + ")\\b.*";
        if (k.matches(blocked)) {
            return "calligraphy";
        }

        if (k.length() > 24) {
            return "calligraphy";
        }

        return k;
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }
}