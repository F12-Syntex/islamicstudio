package com.syntex.islamicstudio.media.quran;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.syntex.islamicstudio.env.EnvKey;
import com.syntex.islamicstudio.env.EnvManager;

public class PixabayDownloader {

    public static File downloadBackgroundVideo(String query, File workDir) throws Exception {
        String encoded = query.trim().replace(" ", "+").replaceAll("[^a-zA-Z0-9+]", "");
        String apiUrl = "https://pixabay.com/api/videos/?key=" + EnvManager.getInstance().get(EnvKey.PIXABAY_API_KEY) + "&q=" + encoded;

        System.out.println("📥 Searching for Pixabay video: " + apiUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestProperty("User-Agent", "IslamicStudio/1.0");
        conn.connect();

        String json = new String(conn.getInputStream().readAllBytes());
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray hits = root.getAsJsonArray("hits");

        if (hits == null || hits.size() == 0) {
            throw new IllegalStateException("No Pixabay results for: " + query);
        }

        JsonObject first = hits.get(0).getAsJsonObject();
        JsonObject videos = first.getAsJsonObject("videos");
        String url = videos.getAsJsonObject("medium").get("url").getAsString();

        System.out.println("📥 Downloading Pixabay video: " + url);

        File out = new File(workDir, "background.mp4");
        try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream()); FileOutputStream fos = new FileOutputStream(out)) {
            in.transferTo(fos);
        }

        return out;
    }
}
