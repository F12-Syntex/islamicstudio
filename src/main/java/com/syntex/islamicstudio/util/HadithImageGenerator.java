package com.syntex.islamicstudio.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.SecureRandom;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.imageio.ImageIO;

public class HadithImageGenerator {

    /**
     * Renders a portrait (4:5) hadith card without Arabic: title, book line, narrator, English body, footer chips.
     */
    public static void generateHadithImage(
            File background,
            String collection, String bookEn, String bookAr,
            String number, String grade, String narrator,
            String english,
            String outputName) throws Exception {

        // Normalize
        collection = normalizeInline(collection);
        bookEn = normalizeInline(bookEn);
        bookAr = normalizeInline(bookAr);
        number = normalizeInline(number);
        grade = normalizeInline(grade);
        narrator = normalizeInline(narrator);
        english = normalizeInline(english);

        // Canvas (4:5 for Instagram portrait)
        int exportWidth = 1080;
        int exportHeight = 1350;
        BufferedImage canvas = new BufferedImage(exportWidth, exportHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();

        // High quality
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Theme
        Color bgDark = new Color(14, 14, 18);
        Color cardDark = new Color(24, 24, 30);
        Color cardBorder = new Color(255, 255, 255, 28);
        Color accent = new Color(255, 215, 0);
        Color accentDeep = new Color(210, 170, 0);
        Color softText = new Color(235, 235, 240);
        Color mutedText = new Color(180, 182, 190);

        // Background richness
        g.setColor(bgDark);
        g.fillRect(0, 0, exportWidth, exportHeight);
        paintBackgroundGradient(g, exportWidth, exportHeight, new Color(22, 22, 28), new Color(10, 10, 14));
        paintVignette(g, exportWidth, exportHeight, 120);
        paintNoise(g, exportWidth, exportHeight, 9, 0.03f);

        // Card
        int outerPadding = 56;
        int cardX = outerPadding;
        int cardY = outerPadding;
        int cardW = exportWidth - 2 * outerPadding;
        int cardH = exportHeight - 2 * outerPadding;

        float cardRadius = 40f;
        Shape cardShape = new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, cardRadius, cardRadius);

        paintGlow(g, (int) (cardX + cardW * 0.5), cardY + 100, 380, new Color(255, 215, 0, 22));
        paintGlow(g, (int) (cardX + cardW * 0.5), cardY + cardH - 100, 420, new Color(0, 0, 0, 90));

        g.setColor(cardDark);
        g.fill(cardShape);
        g.setColor(cardBorder);
        g.setStroke(new BasicStroke(2f));
        g.draw(cardShape);
        drawInnerGradient(g, cardShape, new Color(255, 255, 255, 16), new Color(0, 0, 0, 0));

        // Layout bounds
        int innerPadding = 44;
        int contentX = cardX + innerPadding;
        int contentY = cardY + innerPadding;
        int contentW = cardW - innerPadding * 2;
        int contentBottom = cardY + cardH - innerPadding; // safe bottom for content (above chips)
        int y = contentY;

        // Top image area (cover, rounded)
        BufferedImage bg = ImageIO.read(background);
        if (bg == null) throw new IllegalStateException("Could not load background image: " + background);

        int imageAreaHeight = (int) Math.round(cardH * 0.42); // slightly shorter to give text more space
        int imageRadius = 28;
        int boxW = contentW;
        int boxH = imageAreaHeight;

        double srcW = bg.getWidth();
        double srcH = bg.getHeight();
        double scale = Math.max((double) boxW / srcW, (double) boxH / srcH);
        int drawW = (int) Math.ceil(srcW * scale);
        int drawH = (int) Math.ceil(srcH * scale);
        int dx = contentX + (boxW - drawW) / 2;
        int dy = y + (boxH - drawH) / 2;

        Shape imageClip = new RoundRectangle2D.Float(contentX, y, boxW, boxH, imageRadius, imageRadius);
        g.setClip(imageClip);
        g.drawImage(bg.getScaledInstance(drawW, drawH, Image.SCALE_SMOOTH), dx, dy, null);

        // Enrich overlays
        GradientPaint duo = new GradientPaint(contentX, y, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 32),
                contentX, y + boxH, new Color(0, 0, 0, 90));
        g.setPaint(duo);
        g.fillRect(contentX, y, boxW, boxH);

        GradientPaint glaze = new GradientPaint(contentX, y, new Color(255, 255, 255, 26),
                contentX, y + (int) (boxH * 0.38), new Color(255, 255, 255, 0));
        g.setPaint(glaze);
        g.fillRect(contentX, y, boxW, (int) (boxH * 0.38));

        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 34));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Float(contentX + 0.75f, y + 0.75f, boxW - 1.5f, boxH - 1.5f, imageRadius, imageRadius));

        y += boxH;
        y += 28;

        // Title dynamic sizing (bigger baseline since there is no Arabic)
        int titleMaxLines = 2;
        int titleMaxHeight = approxLineHeight(g, new Font("Serif", Font.BOLD, 56)) * titleMaxLines + 6;
        Font titleFont = fitTextHeight(collection, new Font("Serif", Font.BOLD, 56), new Font("Serif", Font.BOLD, 28),
                contentW, titleMaxHeight, g, false);
        g.setFont(titleFont);

        int titleHeight = measureWrappedHeight(collection, g.getFont(), contentW, g, false);
        int titleTop = y;
        y = drawWrappedTextLTR(g, collection, contentX, y, contentW, softText);

        // Accent bar just below the last title line
        int barY = Math.max(titleTop + 6, titleTop + titleHeight - 10);
        drawAccentBar(g, contentX, barY, Math.min(200, contentW / 3), accent, accentDeep);
        y += 12;

        // Book line fit-to-width
        String bookLine = bookEn + " (" + bookAr + ")";
        Font bookFont = fitTextWidth(bookLine, new Font("Serif", Font.ITALIC, 30), new Font("Serif", Font.ITALIC, 18),
                contentW, g);
        g.setFont(bookFont);
        y = drawWrappedTextLTR(g, bookLine, contentX, y, contentW, mutedText);
        y += 22;

        // Separator
        drawSeparator(g, contentX, y, contentW, new Color(255, 255, 255, 28));
        y += 24;

        // Narrator (a bit larger)
        String narratorLine = "Narrated by: " + nullSafe(narrator);
        Font narratorFont = fitTextWidth(narratorLine, new Font("SansSerif", Font.BOLD, 30), new Font("SansSerif", Font.BOLD, 18),
                contentW, g);
        g.setFont(narratorFont);
        y = drawWrappedTextLTR(g, narratorLine, contentX, y, contentW, softText);
        y += 18;

        // English paragraph: generous space and conservative downscaling
        int safeGapAboveChips = 20;
        int chipBlockHeight = measureChipBlockHeight(g, 22, 10);
        int maxTextBottom = contentBottom - chipBlockHeight - safeGapAboveChips;

        int availableForEnglish = Math.max(240, maxTextBottom - y);
        int engFontSize = fitParagraphToHeight(english, 32, 18, contentW, availableForEnglish, g);
        g.setFont(new Font("Serif", Font.ITALIC, engFontSize));
        y = drawWrappedTextLTR(g, english, contentX, y, contentW, new Color(230, 230, 236));

        // Clamp to safe area
        y = Math.min(y, maxTextBottom);

        // Footer separator
        int sepY = Math.min(maxTextBottom - 10, y + 14);
        if (sepY > contentY + 100) {
            drawSeparator(g, contentX, sepY, contentW, new Color(255, 255, 255, 24));
        }

        // Footer chips
        int chipY = contentBottom - 8;
        String chip1 = "Hadith #" + nullSafe(number);
        String chip2 = nullSafe(grade);

        int usedChipFont = 22;
        int minChipFont = 18;
        int chipPaddingX = 14;
        int chipPaddingY = 10;
        int chipRadius = 16;

        while (true) {
            int[] widths = chipWidths(g, chip1, chip2, usedChipFont, chipPaddingX);
            int needed = widths[0] + widths[1] + 12;
            if (needed <= contentW || usedChipFont <= minChipFont) break;
            usedChipFont -= 2;
        }

        Font chipF = new Font("SansSerif", Font.BOLD, usedChipFont);
        g.setFont(chipF);
        int[] widths = chipWidths(g, chip1, chip2, usedChipFont, chipPaddingX);
        int chipH = g.getFontMetrics().getAscent() + g.getFontMetrics().getDescent() + chipPaddingY * 2;

        int chip1X = contentX;
        int chip2X = chip1X + widths[0] + 12;

        drawChip(g, chip1X, chipY - chipH, widths[0], chipH, chipRadius,
                new Color(255, 215, 0, 36), new Color(255, 215, 0, 80),
                accent, softText);
        drawStringCenteredVertically(g, chip1, chip1X + chipPaddingX, chipY - chipH, chipH, softText);

        drawChip(g, chip2X, chipY - chipH, widths[1], chipH, chipRadius,
                new Color(255, 255, 255, 16), new Color(255, 255, 255, 40),
                new Color(255, 255, 255, 60), softText);
        drawStringCenteredVertically(g, chip2, chip2X + chipPaddingX, chipY - chipH, chipH, softText);

        g.dispose();

        // Export
        File out = new File("output/" + outputName);
        out.getParentFile().mkdirs();
        ImageIO.write(toOpaque(canvas, bgDark), "png", out);
        System.out.println("✅ Saved hadith image: " + out.getAbsolutePath());
    }

    // ========= MEASUREMENT & FITTING =========

    private static int measureWrappedHeight(String text, Font font, int maxWidth, Graphics2D g, boolean rtl) {
        if (text == null || text.isBlank()) return 0;
        AttributedString a = new AttributedString(text);
        a.addAttribute(TextAttribute.FONT, font);
        if (rtl) a.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        AttributedCharacterIterator it = a.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());
        int h = 0;
        while (lbm.getPosition() < it.getEndIndex()) {
            TextLayout layout = lbm.nextLayout(maxWidth);
            h += layout.getAscent() + layout.getDescent() + layout.getLeading();
        }
        return h;
    }

    private static int approxLineHeight(Graphics2D g, Font f) {
        var fm = g.getFontMetrics(f);
        return fm.getAscent() + fm.getDescent() + fm.getLeading();
    }

    private static Font fitTextWidth(String text, Font start, Font min, int maxWidth, Graphics2D g) {
        if (text == null || text.isBlank()) return start;
        int size = start.getSize();
        int minSize = Math.max(min.getSize(), 8);
        for (; size >= minSize; size--) {
            Font f = start.deriveFont((float) size);
            if (!wrapExceedsWidth(text, f, maxWidth, g)) return f;
        }
        return min.deriveFont((float) minSize);
    }

    private static Font fitTextHeight(String text, Font start, Font min, int maxWidth, int maxHeight, Graphics2D g, boolean rtl) {
        int size = start.getSize();
        int minSize = Math.max(min.getSize(), 8);
        for (; size >= minSize; size--) {
            Font f = start.deriveFont((float) size);
            int h = measureWrappedHeight(text, f, maxWidth, g, rtl);
            if (h <= maxHeight) return f;
        }
        return min.deriveFont((float) minSize).deriveFont(Font.PLAIN, (float) (minSize * 0.8));
    }

    private static int fitParagraphToHeight(String text, int startSize, int minSize, int maxWidth, int maxHeight, Graphics2D g) {
        int s = startSize;
        for (; s >= minSize; s -= 2) {
            if (!textTooTall(text, maxWidth, s, g, maxHeight)) break;
        }
        return Math.max(s, minSize);
    }

    private static boolean wrapExceedsWidth(String text, Font font, int maxWidth, Graphics2D g) {
        AttributedString a = new AttributedString(text);
        a.addAttribute(TextAttribute.FONT, font);
        AttributedCharacterIterator it = a.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());
        while (lbm.getPosition() < it.getEndIndex()) {
            TextLayout layout = lbm.nextLayout(maxWidth);
            if (layout.getAdvance() > maxWidth + 0.5f) return true;
        }
        return false;
    }

    private static boolean textTooTall(String text, int maxWidth, int fontSize, Graphics2D g, int maxHeight) {
        if (text == null) text = "";
        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, new Font("Serif", Font.PLAIN, fontSize));
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());
        int height = 0;
        while (lbm.getPosition() < it.getEndIndex()) {
            TextLayout layout = lbm.nextLayout(maxWidth);
            height += layout.getAscent() + layout.getDescent() + layout.getLeading();
            if (height > maxHeight) return true;
        }
        return false;
    }

    private static int measureChipBlockHeight(Graphics2D g, int chipFontSize, int chipPaddingY) {
        Font f = new Font("SansSerif", Font.BOLD, chipFontSize);
        var fm = g.getFontMetrics(f);
        return fm.getAscent() + fm.getDescent() + chipPaddingY * 2 + 8;
    }

    private static int[] chipWidths(Graphics2D g, String c1, String c2, int chipFontSize, int paddingX) {
        Font f = new Font("SansSerif", Font.BOLD, chipFontSize);
        var fm = g.getFontMetrics(f);
        int w1 = fm.stringWidth(c1) + paddingX * 2;
        int w2 = fm.stringWidth(c2) + paddingX * 2;
        return new int[]{w1, w2};
    }

    // ========= DRAWING =========

    private static String normalizeInline(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ');
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private static String nullSafe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    private static void paintBackgroundGradient(Graphics2D g, int w, int h, Color top, Color bottom) {
        GradientPaint gp = new GradientPaint(0, 0, top, 0, h, bottom);
        g.setPaint(gp);
        g.fillRect(0, 0, w, h);
    }

    private static void paintVignette(Graphics2D g, int w, int h, int darkness) {
        float cx = w / 2f, cy = h / 2f;
        float radius = Math.max(w, h);
        float[] dist = {0f, 1f};
        Color[] colors = {new Color(0, 0, 0, 0), new Color(0, 0, 0, Math.min(200, darkness))};
        RadialGradientPaint rg = new RadialGradientPaint(cx, cy, radius, dist, colors, CycleMethod.NO_CYCLE);
        g.setPaint(rg);
        g.fillRect(0, 0, w, h);
    }

    private static void paintGlow(Graphics2D g, int cx, int cy, int radius, Color color) {
        float[] dist = {0f, 1f};
        Color[] colors = {color, new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)};
        RadialGradientPaint rg = new RadialGradientPaint(cx, cy, radius, dist, colors, CycleMethod.NO_CYCLE);
        g.setPaint(rg);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    private static void paintNoise(Graphics2D g, int w, int h, int intensity, float opacity) {
        SecureRandom rng = new SecureRandom();
        int count = (int) (w * h * opacity * 0.02);
        g.setColor(new Color(255, 255, 255, intensity));
        for (int i = 0; i < count; i++) g.fillRect(rng.nextInt(w), rng.nextInt(h), 1, 1);
        g.setColor(new Color(0, 0, 0, intensity));
        for (int i = 0; i < count; i++) g.fillRect(rng.nextInt(w), rng.nextInt(h), 1, 1);
    }

    private static void drawInnerGradient(Graphics2D g, Shape shape, Color inner, Color outer) {
        var b = shape.getBounds2D();
        GradientPaint gp = new GradientPaint((float) b.getCenterX(), (float) b.getY(), inner,
                (float) b.getCenterX(), (float) (b.getMaxY()), outer);
        var oldClip = g.getClip();
        g.setClip(shape);
        g.setPaint(gp);
        g.fill(shape);
        g.setClip(oldClip);
    }

    private static void drawSeparator(Graphics2D g, int x, int y, int width, Color color) {
        g.setColor(color);
        g.fillRoundRect(x, y, width, 2, 2, 2);
    }

    private static void drawAccentBar(Graphics2D g, int x, int y, int width, Color c1, Color c2) {
        GradientPaint gp = new GradientPaint(x, y, c1, x + width, y, c2);
        g.setPaint(gp);
        g.fillRoundRect(x, y, width, 6, 6, 6);
    }

    private static void drawChip(Graphics2D g, int x, int y, int w, int h, int r, Color fill1, Color fill2, Color stroke, Color textColor) {
        Shape chip = new RoundRectangle2D.Float(x, y, w, h, r, r);
        GradientPaint gp = new GradientPaint(x, y, fill1, x, y + h, fill2);
        g.setPaint(gp);
        g.fill(chip);
        g.setColor(stroke);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(chip);
        GradientPaint inner = new GradientPaint(x, y, new Color(255, 255, 255, 18), x, y + h, new Color(255, 255, 255, 0));
        var old = g.getClip();
        g.setClip(chip);
        g.setPaint(inner);
        g.fillRect(x, y, w, h / 2);
        g.setClip(old);
    }

    private static void drawStringCenteredVertically(Graphics2D g, String text, int x, int boxY, int boxH, Color color) {
        var fm = g.getFontMetrics();
        int baseline = boxY + (boxH - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(new Color(0, 0, 0, 140));
        g.drawString(text, x + 1, baseline + 1);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    private static BufferedImage toOpaque(BufferedImage src, Color bg) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setColor(bg);
        g2.fillRect(0, 0, out.getWidth(), out.getHeight());
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return out;
    }

    // ========= TEXT DRAWING =========

    private static int drawWrappedTextLTR(Graphics2D g, String text, int x, int y, int maxWidth, Color color) {
        if (text == null || text.isBlank()) return y;
        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, g.getFont());
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());

        g.setColor(color);
        while (lbm.getPosition() < it.getEndIndex()) {
            TextLayout layout = lbm.nextLayout(maxWidth);
            y += layout.getAscent();
            g.setColor(new Color(0, 0, 0, 140));
            layout.draw(g, x + 1, y + 1);
            g.setColor(color);
            layout.draw(g, x, y);
            y += layout.getDescent() + layout.getLeading();
        }
        return y;
    }
}