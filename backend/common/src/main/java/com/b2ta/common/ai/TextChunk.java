package com.b2ta.common.ai;

/**
 * A window of submission text sent to Bedrock as one invocation.
 *
 * @param index       position of this chunk in the sequence, from 0
 * @param globalStart character offset of {@link #text} within the full extracted submission text
 * @param text        the chunk content, exactly {@code fullText.substring(globalStart, globalEnd())}
 */
public record TextChunk(int index, int globalStart, String text) {

    public TextChunk {
        if (index < 0) {
            throw new IllegalArgumentException("Chunk index must be non-negative");
        }
        if (globalStart < 0) {
            throw new IllegalArgumentException("Chunk global start must be non-negative");
        }
        if (text == null) {
            throw new IllegalArgumentException("Chunk text must not be null");
        }
    }

    /** Exclusive end offset of this chunk in the global offset space. */
    public int globalEnd() {
        return globalStart + text.length();
    }

    public int length() {
        return text.length();
    }

    /**
     * Translates a chunk-local offset to the global offset space.
     *
     * <p>Bedrock is asked to return offsets relative to the chunk, so this addition is the only
     * transformation applied. Keeping it a single operation is what makes the remapping property
     * (design Property 7) hold: {@code fullText.substring(toGlobal(a), toGlobal(b))} is
     * {@code text.substring(a, b)} for any {@code 0 <= a <= b <= length()}.
     */
    public int toGlobal(int localOffset) {
        return globalStart + localOffset;
    }
}
