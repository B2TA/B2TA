package com.b2ta.api.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A submission's text, normalized once and split into paragraphs.
 *
 * <p>Normalization happens exactly once and this object is then the single source of
 * truth for prompting, evidence verification, and rendering. If the model reasons over
 * one form of the text and offsets are computed against another, every highlight lands
 * in the wrong place.
 *
 * <p>Offsets in {@link #text()} are absolute; the marking view needs them
 * paragraph-relative, which is what {@link #toParagraphOffset(int)} provides.
 */
public final class NormalizedDocument {

    /** Two or more newlines (with optional surrounding spaces) separate paragraphs. */
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n[ \\t]*\\n+");

    private final String text;
    private final List<Paragraph> paragraphs;

    private NormalizedDocument(String text, List<Paragraph> paragraphs) {
        this.text = text;
        this.paragraphs = List.copyOf(paragraphs);
    }

    /**
     * Normalizes raw extracted text and splits it into paragraphs.
     *
     * @param raw   text as extracted from the submission
     * @param title optional document title rendered as paragraph 0; may be null
     */
    public static NormalizedDocument of(String raw, String title) {
        String normalized = normalize(raw);

        List<Paragraph> result = new ArrayList<>();
        StringBuilder rebuilt = new StringBuilder();

        if (title != null && !title.isBlank()) {
            String cleanTitle = normalizeWhitespace(title);
            result.add(new Paragraph(0, null, cleanTitle, true, 0));
            rebuilt.append(cleanTitle);
        }

        // Split on blank lines, then re-emit with a known separator so the paragraph
        // start table matches the string the offsets are computed against.
        String[] blocks = PARAGRAPH_BREAK.split(normalized);
        int bodyIndex = 0;
        for (String block : blocks) {
            String paragraph = block.strip();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (!rebuilt.isEmpty()) {
                rebuilt.append("\n\n");
            }
            bodyIndex++;
            result.add(new Paragraph(
                    result.size(), "¶" + bodyIndex, paragraph, false, rebuilt.length()));
            rebuilt.append(paragraph);
        }

        return new NormalizedDocument(rebuilt.toString(), result);
    }

    /**
     * Applies the normalizations that make quote matching stable: ligatures and smart
     * punctuation folded to ASCII, soft hyphens removed, line endings unified.
     *
     * <p>Intra-paragraph whitespace is <em>not</em> collapsed here — the locator matches
     * whitespace-insensitively, so collapsing would only shift offsets without helping.
     */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("­", "")   // soft hyphen — invisible, breaks quote matching
                .replace("﻿", "")   // BOM
                .replace("ﬁ", "fi")
                .replace("ﬂ", "fl")
                .replace("ﬀ", "ff")
                .replace("ﬃ", "ffi")
                .replace("ﬄ", "ffl")
                .replace('‘', '\'')
                .replace('’', '\'')
                .replace('“', '"')
                .replace('”', '"')
                .replace('–', '-')
                .replace('—', '-')
                .replace(' ', ' ')  // non-breaking space
                .strip();
    }

    private static String normalizeWhitespace(String value) {
        return normalize(value).replaceAll("\\s+", " ").strip();
    }

    /** The normalized full text. All absolute offsets refer to this string. */
    public String text() {
        return text;
    }

    public List<Paragraph> paragraphs() {
        return paragraphs;
    }

    public boolean isEmpty() {
        return text.isBlank();
    }

    public int length() {
        return text.length();
    }

    /**
     * Converts an absolute offset in {@link #text()} to a paragraph index and an offset
     * within that paragraph.
     *
     * <p>The marking view stores highlight positions per paragraph, so a span located in
     * the full document has to be mapped back before it can be rendered.
     *
     * @return the position, or null when the offset falls outside every paragraph (i.e.
     * inside a separator)
     */
    public ParagraphOffset toParagraphOffset(int absoluteOffset) {
        if (absoluteOffset < 0 || absoluteOffset > text.length()) {
            return null;
        }
        // Paragraphs are ordered by start, so walk backwards to the first one that
        // begins at or before the offset.
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            Paragraph paragraph = paragraphs.get(i);
            if (absoluteOffset >= paragraph.absoluteStart()) {
                int within = absoluteOffset - paragraph.absoluteStart();
                if (within > paragraph.text().length()) {
                    // Offset landed in the separator after this paragraph.
                    return null;
                }
                return new ParagraphOffset(paragraph.idx(), within);
            }
        }
        return null;
    }

    /** One paragraph of the document. */
    public record Paragraph(
            int idx,
            String label,
            String text,
            boolean isTitle,
            int absoluteStart) {
    }

    /** A position expressed relative to a paragraph. */
    public record ParagraphOffset(int paragraphIdx, int offsetInParagraph) {
    }
}
