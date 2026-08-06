package com.b2ta.common.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted submission text into overlapping windows for the Match_Engine
 * (Requirement 6.5).
 *
 * <p>Three constraints shape the algorithm:
 *
 * <ul>
 *   <li><b>Target size 4000 characters.</b> Keeps each invocation well inside the model's context
 *       and keeps per-chunk latency predictable.
 *   <li><b>Prefer a sentence boundary within 200 characters of the target.</b> Cutting mid-sentence
 *       costs match quality at both seams: the model sees a truncated thought and tends either to
 *       skip the passage or to return a range that stops short of the real evidence.
 *   <li><b>400-character overlap.</b> A passage that straddles a boundary is fully contained in at
 *       least one chunk as long as it is no longer than the overlap. Passages are capped at 1500
 *       characters, so a long passage can still be split; deduplication then keeps the better of the
 *       two partial matches rather than presenting both.
 * </ul>
 *
 * <p>Chunk offsets are always expressed in the offset space of the text passed in, so a caller that
 * hands over the full extracted text gets back offsets it can use directly.
 */
@Component
public class TextChunker {

    /** Target chunk length in characters. */
    public static final int TARGET_CHUNK_SIZE = 4000;

    /** How far back from the target end a sentence boundary is looked for. */
    public static final int BOUNDARY_SEARCH_WINDOW = 200;

    /** Characters of the previous chunk repeated at the start of the next. */
    public static final int OVERLAP = 400;

    /**
     * Chunks the given text.
     *
     * @param text extracted submission text; may be empty
     * @return one chunk when the text fits in {@link #TARGET_CHUNK_SIZE}, otherwise overlapping
     *         chunks in ascending offset order, together covering every character of {@code text}
     */
    public List<TextChunk> chunk(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (text.length() <= TARGET_CHUNK_SIZE) {
            return List.of(new TextChunk(0, 0, text));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;

        while (start < text.length()) {
            int targetEnd = Math.min(start + TARGET_CHUNK_SIZE, text.length());
            int end = targetEnd == text.length()
                    ? targetEnd
                    : findBoundary(text, start, targetEnd);

            chunks.add(new TextChunk(index++, start, text.substring(start, end)));

            if (end >= text.length()) {
                break;
            }
            // Step back by the overlap, but always advance at least one character so a pathological
            // boundary choice cannot loop forever.
            int nextStart = end - OVERLAP;
            start = Math.max(nextStart, start + 1);
        }
        return List.copyOf(chunks);
    }

    /**
     * Finds the preferred cut point at or before {@code targetEnd}.
     *
     * <p>Searches backwards through the window for a sentence terminator followed by whitespace,
     * then for a paragraph break, then gives up and cuts at {@code targetEnd}. Never returns a
     * position at or before {@code start}, which would produce an empty chunk.
     */
    private int findBoundary(String text, int start, int targetEnd) {
        int floor = Math.max(start + 1, targetEnd - BOUNDARY_SEARCH_WINDOW);

        for (int i = targetEnd - 1; i >= floor; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?')
                    && (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1)))) {
                // Cut just after the terminator so the sentence stays whole in this chunk.
                return i + 1;
            }
        }
        for (int i = targetEnd - 1; i >= floor; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        return targetEnd;
    }
}
