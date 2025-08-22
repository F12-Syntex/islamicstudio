package com.syntex.islamicstudio.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

public class VerseImageGenerator {

    private static Font quranFont;

    static {
        try (InputStream is = VerseImageGenerator.class.getResourceAsStream("/fonts/Al Qalam Quran.ttf")) {
            if (is != null) {
                quranFont = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(40f);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(quranFont);
            } else {
                System.err.println("⚠ Quran font not found in resources, using Serif fallback.");
                quranFont = new Font("Serif", Font.PLAIN, 40);
            }
        } catch (Exception e) {
            e.printStackTrace();
            quranFont = new Font("Serif", Font.PLAIN, 40);
        }
    }

    public static void generateVerseImage(String surah, int ayahNum,
                                          String arabic, String english, String footnote,
                                          String outputName) throws Exception {

        int width = 1000;
        int height = 1200; // taller canvas for long ayat
        int margin = 60;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // Background
        g.setColor(new Color(253, 252, 245));
        g.fillRect(0, 0, width, height);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int y = margin;

        // Title
        g.setColor(new Color(58, 44, 26));
        g.setFont(new Font("Serif", Font.BOLD, 32));
        g.drawString("Surah " + surah + " - Ayah " + ayahNum, margin, y);
        y += 70;

        // Arabic (right aligned, wrapped)
        g.setColor(Color.BLACK);
        g.setFont(quranFont.deriveFont(48f));
        y = drawWrappedTextRTL(g, arabic, margin, y, width - 2 * margin);

        y += 40; // spacing between Arabic and English

        // English translation
        g.setColor(new Color(51, 51, 51));
        g.setFont(new Font("Serif", Font.ITALIC, 24));
        y = drawWrappedTextLTR(g, english, margin, y, width - 2 * margin);

        y += 30;

        // Footnotes if any
        if (footnote != null && !footnote.isBlank()) {
            g.setFont(new Font("Serif", Font.PLAIN, 18));
            g.setColor(Color.GRAY);
            y = drawWrappedTextLTR(g, footnote, margin, y, width - 2 * margin);
        }

        g.dispose();

        File out = new File("output/" + outputName);
        out.getParentFile().mkdirs();
        ImageIO.write(image, "png", out);
        System.out.println("✓ Saved verse image: " + out.getAbsolutePath());
    }

    /** Left-to-right wrapped text (for English & footnotes) */
    private static int drawWrappedTextLTR(Graphics2D g, String text, int x, int y, int maxWidth) {
        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, g.getFont());
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());

        float wrapWidth = maxWidth;
        while (lbm.getPosition() < it.getEndIndex()) {
            var layout = lbm.nextLayout(wrapWidth);
            y += layout.getAscent();
            layout.draw(g, x, y);
            y += layout.getDescent() + layout.getLeading();
        }
        return y;
    }

    /** Right-to-left wrapped text (for Arabic) */
    private static int drawWrappedTextRTL(Graphics2D g, String text, int margin, int y, int maxWidth) {
        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, g.getFont());
        attrStr.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());

        float wrapWidth = maxWidth;
        while (lbm.getPosition() < it.getEndIndex()) {
            var layout = lbm.nextLayout(wrapWidth);
            y += layout.getAscent();
            float dx = margin + (wrapWidth - layout.getAdvance()); // right align inside box
            layout.draw(g, dx, y);
            y += layout.getDescent() + layout.getLeading();
        }
        return y;
    }
}