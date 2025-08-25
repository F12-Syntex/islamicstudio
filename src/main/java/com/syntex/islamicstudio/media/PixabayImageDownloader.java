package com.syntex.islamicstudio.media;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.syntex.islamicstudio.env.EnvKey;
import com.syntex.islamicstudio.env.EnvManager;

public class PixabayImageDownloader {

    // Deny terms (any match fails)
    private static final Set<String> DENY = setOf(
            "people","person","man","men","male","female","woman","women","girl","boy",
            "face","body","hand","arm","leg","legs","knee","knees","silhouette","portrait",
            "selfie","couple","group","crowd",
            "animal","dog","cat","bird","horse","camel","fish","butterfly","insect",
            "statue","idol","sculpture",
            "cross","church","temple","synagogue","monk","nun","saint","cathedral","chapel",
            "fashion","model","runway",
            "alcohol","wine","beer","pork","bacon","ham"
    );

    // Allow hints (boost confidence, but not strictly required)
    private static final Set<String> ALLOW_HINT = setOf(
            "landscape","nature","mountain","desert","dune","sky","clouds","night","stars","milky way",
            "ocean","sea","waves","coast","beach","waterfall","river","lake",
            "forest","trees","leaves","mist","fog",
            "flower","flowers","macro","bloom","petal",
            "light","sunlight","sun rays","sunbeams","bokeh","abstract","texture","pattern","geometric","gradient",
            "calligraphy","arabic","islamic","mosaic","tile","ornament","architecture"
    );

    private static Set<String> setOf(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    /**
     * Filter Pixabay hits using the 'tags' field against our halal rules.
     * Returns the index of the first acceptable hit, or -1 if none pass.
     */
    private static int pickAcceptableHit(JsonArray hits) {
        for (int i = 0; i < hits.size(); i++) {
            JsonObject img = hits.get(i).getAsJsonObject();
            String tags = img.has("tags") && !img.get("tags").isJsonNull()
                    ? img.get("tags").getAsString()
                    : "";
            if (tagsPass(tags)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean tagsPass(String tagsCsv) {
        // Pixabay tags are comma-separated
        String[] raw = tagsCsv.toLowerCase(Locale.ROOT).split("\\s*,\\s*");
        Set<String> tags = new HashSet<>(Arrays.asList(raw));

        // Denylist check: if any tag contains a denied term, fail
        for (String t : tags) {
            for (String d : DENY) {
                if (t.contains(d)) return false;
            }
        }

        // Optional: soft allow check (not required, but good heuristic)
        // If there are no allow hints at all, we still accept, but prefer those with hints.
        // This method just filters; the ordering preference is handled by Pixabay itself (popular first).
        return true;
    }

    /**
     * Download exactly one acceptable image that passes tag filter.
     * If none pass, returns empty list.
     */
    public static List<File> downloadImages(String query, File workDir, int count) throws Exception {
        if (count <= 0) count = 1;
        String encoded = query.trim().replace(" ", "+");
        String apiKey = EnvManager.getInstance().get(EnvKey.PIXABAY_API_KEY);
        String apiUrl = "https://pixabay.com/api/?key=" + apiKey
                + "&q=" + encoded
                + "&image_type=photo&orientation=vertical&safesearch=true&per_page=50";

        System.out.println("🔎 Searching Pixabay images: " + apiUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestProperty("User-Agent", "IslamicStudio/1.0");
        conn.connect();

        String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray hits = root.getAsJsonArray("hits");

        if (hits == null || hits.size() == 0) {
            throw new IllegalStateException("No Pixabay image results for: " + query);
        }

        List<File> result = new ArrayList<>();
        int idx = pickAcceptableHit(hits);
        if (idx < 0) {
            System.out.println("⚠️ No acceptable images by tags for '" + query + "'.");
            return result; // empty; caller can decide fallback
        }

        for (int i = idx; i < hits.size() && result.size() < count; i++) {
            JsonObject img = hits.get(i).getAsJsonObject();
            if (!tagsPass(img.has("tags") ? img.get("tags").getAsString() : "")) continue;

            String url = bestImageUrl(img);
            if (url == null) continue;

            File out = new File(workDir, "bg" + (result.size() + 1) + ".jpg");
            try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
                 FileOutputStream fos = new FileOutputStream(out)) {
                in.transferTo(fos);
            }
            System.out.println("✅ Downloaded acceptable image: " + out.getName() + " (tags: " + img.get("tags").getAsString() + ")");
            result.add(out);
        }

        return result;
    }

    private static String bestImageUrl(JsonObject img) {
        // Prefer largeImageURL; fallback to webformatURL if missing
        if (img.has("largeImageURL") && !img.get("largeImageURL").isJsonNull()) {
            return img.get("largeImageURL").getAsString();
        }
        if (img.has("webformatURL") && !img.get("webformatURL").isJsonNull()) {
            return img.get("webformatURL").getAsString();
        }
        // some responses include 'images' object variants; handle generically if needed
        if (img.has("images") && img.get("images").isJsonObject()) {
            JsonObject images = img.getAsJsonObject("images");
            for (String key : new String[]{"large", "medium", "small"}) {
                JsonElement el = images.get(key);
                if (el != null && el.isJsonObject()) {
                    JsonElement url = el.getAsJsonObject().get("url");
                    if (url != null && !url.isJsonNull()) return url.getAsString();
                }
            }
        }
        return null;
    }
}