package com.b2ta.common.ai;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5.6 — design Property 7: Chunk Offset Remapping Correctness.
 *
 * <p>The property that matters is that remapping never changes which text a pair of offsets refers to:
 * for any chunk and any local range inside it, indexing the original text with the remapped offsets
 * must yield exactly the substring the local offsets select from the chunk. A violation here would
 * highlight the wrong sentence in the document with no error anywhere.
 */
@Tag("pbt")
class TextChunkerPropertyTest {

    private final TextChunker chunker = new TextChunker();

    /** Prose-like text long enough to force chunking, with sentence and paragraph breaks. */
    @Provide
    Arbitrary<String> proseText() {
        Arbitrary<String> sentence = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(60)
                .map(body -> body + ". ");
        return sentence.list().ofMinSize(1).ofMaxSize(400)
                .map(sentences -> String.join("", sentences));
    }

    @Property(tries = 200)
    void remappedOffsetsSelectTheSameSubstring(@ForAll("proseText") String text,
                                               @ForAll @IntRange(min = 0, max = 999) int seedA,
                                               @ForAll @IntRange(min = 0, max = 999) int seedB) {
        List<TextChunk> chunks = chunker.chunk(text);
        if (chunks.isEmpty()) {
            return;
        }
        for (TextChunk chunk : chunks) {
            if (chunk.length() == 0) {
                continue;
            }
            int a = seedA % chunk.length();
            int b = seedB % chunk.length();
            int localStart = Math.min(a, b);
            int localEnd = Math.max(a, b);

            String fromChunk = chunk.text().substring(localStart, localEnd);
            String fromGlobal = text.substring(chunk.toGlobal(localStart), chunk.toGlobal(localEnd));

            assertThat(fromGlobal).isEqualTo(fromChunk);
        }
    }

    @Property(tries = 200)
    void everyChunkIsExactlyTheTextAtItsGlobalRange(@ForAll("proseText") String text) {
        for (TextChunk chunk : chunker.chunk(text)) {
            assertThat(chunk.globalEnd()).isLessThanOrEqualTo(text.length());
            assertThat(text.substring(chunk.globalStart(), chunk.globalEnd()))
                    .isEqualTo(chunk.text());
        }
    }

    @Property(tries = 200)
    void chunksCoverEveryCharacterInAscendingOrder(@ForAll("proseText") String text) {
        List<TextChunk> chunks = chunker.chunk(text);
        if (text.isEmpty()) {
            assertThat(chunks).isEmpty();
            return;
        }

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).globalStart()).isZero();
        assertThat(chunks.get(chunks.size() - 1).globalEnd()).isEqualTo(text.length());

        for (int i = 1; i < chunks.size(); i++) {
            TextChunk previous = chunks.get(i - 1);
            TextChunk current = chunks.get(i);
            // Strictly increasing starts guarantee termination; a start at or before the previous
            // chunk's start would mean the chunker made no progress.
            assertThat(current.globalStart()).isGreaterThan(previous.globalStart());
            // No gap: the next chunk begins at or before the previous one ends, so no character of
            // the submission escapes analysis.
            assertThat(current.globalStart()).isLessThanOrEqualTo(previous.globalEnd());
        }
    }

    @Property(tries = 200)
    void noChunkExceedsTheTargetSize(@ForAll("proseText") String text) {
        for (TextChunk chunk : chunker.chunk(text)) {
            assertThat(chunk.length()).isLessThanOrEqualTo(TextChunker.TARGET_CHUNK_SIZE);
        }
    }

    @Test
    void shortTextProducesASingleChunk() {
        List<TextChunk> chunks = chunker.chunk("One short paragraph of text.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).globalStart()).isZero();
    }

    @Test
    void emptyTextProducesNoChunks() {
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void consecutiveChunksOverlap() {
        String text = "x".repeat(TextChunker.TARGET_CHUNK_SIZE * 3);
        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int i = 1; i < chunks.size(); i++) {
            int overlap = chunks.get(i - 1).globalEnd() - chunks.get(i).globalStart();
            // No sentence boundary exists in this text, so the cut lands on the target and the
            // overlap is exactly the configured value.
            assertThat(overlap).isEqualTo(TextChunker.OVERLAP);
        }
    }

    @Test
    void boundaryPrefersASentenceEndWithinTheSearchWindow() {
        // A full stop 50 characters before the target end is inside the 200-character window, so the
        // cut should land just after it rather than mid-sentence at exactly 4000.
        int sentenceEnd = TextChunker.TARGET_CHUNK_SIZE - 50;
        String text = "a".repeat(sentenceEnd - 1) + ". " + "b".repeat(TextChunker.TARGET_CHUNK_SIZE);

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks.get(0).globalEnd()).isEqualTo(sentenceEnd);
        assertThat(chunks.get(0).text()).endsWith(".");
    }
}
