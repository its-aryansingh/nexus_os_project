package com.nexus.os.agents.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingStrategyTest {

    private final ChunkingStrategy chunker = new ChunkingStrategy();

    @Test
    void fixedWindow_emptyInput_returnsEmpty() {
        assertTrue(chunker.fixedWindow("", 100, 10).isEmpty());
        assertTrue(chunker.fixedWindow(null, 100, 10).isEmpty());
    }

    @Test
    void fixedWindow_shortInput_singleChunk() {
        final var chunks = chunker.fixedWindow("hello world", 100, 10);
        assertEquals(1, chunks.size());
        assertEquals("hello world", chunks.get(0).text());
        assertEquals(0, chunks.get(0).startChar());
    }

    @Test
    void fixedWindow_overlapPreservesContext() {
        final var text = "x".repeat(800);   // ~200 tokens
        final var chunks = chunker.fixedWindow(text, 50, 10);   // 50 tokens / 200 chars window, 10 token / 40 char overlap
        assertTrue(chunks.size() >= 4, "expected at least 4 chunks, got " + chunks.size());
        // Adjacent chunks should overlap by ~40 chars
        for (int i = 1; i < chunks.size(); i++) {
            assertTrue(chunks.get(i).startChar() < chunks.get(i - 1).endChar(),
                    "chunk " + i + " does not overlap previous");
        }
    }

    @Test
    void sentenceAware_doesNotSplitMidSentence() {
        final var text = "First short. Second short. Third short.";
        final var chunks = chunker.sentenceAware(text, 5);
        for (final var c : chunks) {
            final var t = c.text().strip();
            if (t.isEmpty()) continue;
            assertTrue(t.endsWith(".") || t.endsWith("!") || t.endsWith("?"),
                    "chunk should end on a sentence terminator: \"" + t + "\"");
        }
    }

    @Test
    void semantic_splitsOnDoubleNewline() {
        final var text = "block one\n\nblock two\n\nblock three";
        final var chunks = chunker.semantic(text, 2);   // very small window
        assertTrue(chunks.size() >= 2);
    }

    @Test
    void chunkApproximateTokens_roughlyCharsOver4() {
        final var c = new ChunkingStrategy.Chunk(0, "x".repeat(40), 0, 40);
        assertEquals(10, c.approximateTokens());
    }
}
