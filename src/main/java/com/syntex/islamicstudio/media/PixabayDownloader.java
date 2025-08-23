package com.syntex.islamicstudio.media;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.syntex.islamicstudio.env.EnvKey;
import com.syntex.islamicstudio.env.EnvManager;

public class PixabayDownloader {

    public static List<File> downloadBackgroundVideos(String query, File workDir, int count) throws Exception {
        String encoded = query.trim().replace(" ", "+").replaceAll("[^a-zA-Z0-9+]", "");
        String apiUrl = "https://pixabay.com/api/videos/?key="
                + EnvManager.getInstance().get(EnvKey.PIXABAY_API_KEY)
                + "&q=" + encoded;

        System.out.println("🔎 Searching Pixabay videos: " + apiUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestProperty("User-Agent", "IslamicStudio/1.0");
        conn.connect();

        String json = new String(conn.getInputStream().readAllBytes());
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray hits = root.getAsJsonArray("hits");

        if (hits == null || hits.size() == 0) {
            throw new IllegalStateException("No Pixabay results for: " + query);
        }

        List<File> result = new ArrayList<>();
        for (int i = 0; i < Math.min(count, hits.size()); i++) {
            JsonObject first = hits.get(i).getAsJsonObject();
            JsonObject videos = first.getAsJsonObject("videos");
            String url = videos.getAsJsonObject("medium").get("url").getAsString();

            File out = new File(workDir, "bg" + (i + 1) + ".mp4");
            try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream()); FileOutputStream fos = new FileOutputStream(out)) {
                in.transferTo(fos);
            }
            System.out.println("✅ Downloaded " + out.getName());
            result.add(out);
        }

        return result;
    }
}
