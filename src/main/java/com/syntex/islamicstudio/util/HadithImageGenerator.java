package com.syntex.islamicstudio.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.imageio.ImageIO;

public class HadithImageGenerator {

    public static void generateHadithImage(
            File background,
            String collection, String bookEn, String bookAr,
            String number, String grade, String narrator,
            String arabic, String english,
            String outputName) throws Exception {

        int width = 1080;
        int height = 1350;
        int margin = 90;

        BufferedImage bg = ImageIO.read(background);
        if (bg == null) throw new IllegalStateException("Could not load background image: " + background);

        Image scaled = bg.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.drawImage(scaled, 0, 0, null);

        // dark overlay for contrast
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, width, height);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int y = margin;

        // --- Title ---
        g.setFont(new Font("Serif", Font.BOLD, 42));
        y = drawWrappedTextLTR(g, collection, margin, y, width - 2 * margin, new Color(255, 215, 0));
        y += 30;

        // --- Book ---
        g.setFont(new Font("Serif", Font.ITALIC, 26));
        y = drawWrappedTextLTR(g, bookEn + " (" + bookAr + ")", margin, y, width - 2 * margin, Color.LIGHT_GRAY);
        y += 40;

        // --- Narrator ---
        g.setFont(new Font("SansSerif", Font.ITALIC, 24));
        y = drawWrappedTextLTR(g, "Narrated: " + narrator, margin, y, width - 2 * margin, Color.WHITE);
        y += 40;

        // --- Arabic (auto font-size scaling) ---
        int arabicFontSize = 40;
        while (textTooTall(arabic, width - 2 * margin, arabicFontSize, g, height - y - 250)) {
            arabicFontSize -= 2; // shrink until it fits
        }
        g.setFont(new Font("Serif", Font.PLAIN, arabicFontSize));
        y = drawWrappedTextRTL(g, arabic, margin, y, width - 2 * margin, Color.WHITE);
        y += 40;

        // --- English (auto font-size scaling) ---
        int engFontSize = 28;
        while (textTooTall(english, width - 2 * margin, engFontSize, g, height - y - 150)) {
            engFontSize -= 2;
        }
        g.setFont(new Font("Serif", Font.ITALIC, engFontSize));
        y = drawWrappedTextLTR(g, english, margin, y, width - 2 * margin, Color.WHITE);
        y += 30;

        // --- Footer ---
        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        drawTextShadowed(g, "Hadith #" + number + " • " + grade, margin, height - margin, new Color(255, 215, 0));

        g.dispose();

        File out = new File("output/" + outputName);
        out.getParentFile().mkdirs();
        ImageIO.write(canvas, "png", out);
        System.out.println("✅ Saved hadith image: " + out.getAbsolutePath());
    }

    // Helper: check if text is taller than allowed
    private static boolean textTooTall(String text, int maxWidth, int fontSize, Graphics2D g, int maxHeight) {
        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, new Font("Serif", Font.PLAIN, fontSize));
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());
        float wrapWidth = maxWidth;

        int height = 0;
        while (lbm.getPosition() < it.getEndIndex()) {
            var layout = lbm.nextLayout(wrapWidth);
            height += layout.getAscent() + layout.getDescent() + layout.getLeading();
        }
        return height > maxHeight;
    }

    private static int drawWrappedTextLTR(Graphics2D g, String text, int x, int y, int maxWidth, Color color) {
        if (text == null || text.isBlank()) return y;
        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, g.getFont());
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());

        g.setColor(color);
        while (lbm.getPosition() < it.getEndIndex()) {
            var layout = lbm.nextLayout(maxWidth);
            y += layout.getAscent();
            layout.draw(g, x, y);
            y += layout.getDescent() + layout.getLeading();
        }
        return y;
    }

    private static int drawWrappedTextRTL(Graphics2D g, String text, int margin, int y, int maxWidth, Color color) {
        if (text == null || text.isBlank()) return y;
        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, g.getFont());
        attrStr.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());

        g.setColor(color);
        while (lbm.getPosition() < it.getEndIndex()) {
            var layout = lbm.nextLayout(maxWidth);
            y += layout.getAscent();
            float dx = margin + (maxWidth - layout.getAdvance());
            layout.draw(g, dx, y);
            y += layout.getDescent() + layout.getLeading();
        }
        return y;
    }

    private static void drawTextShadowed(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(new Color(0, 0, 0, 220));
        g.drawString(text, x + 2, y + 2);
        g.setColor(color);
        g.drawString(text, x, y);
    }
}