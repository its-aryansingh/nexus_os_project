package com.nexus.os.agents.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Document chunking. Three strategies — pick whichever fits the source.
 * See docs/SYSTEM_DESIGN.md §4.1.
 *
 * <p>Tokens are estimated as {@code chars / 4} — fine for chunk sizing
 * (off-by-20% on heavily non-Latin text is acceptable; embedding models
 * cap by token count themselves and we leave headroom).
 */
@Component
public class ChunkingStrategy {

    /** Token estimate scale. */
    private static final int CHARS_PER_TOKEN = 4;

    /**
     * Fixed-window chunking with overlap. The workhorse for prose +
     * mixed content. Sliding window so context isn't lost at boundaries.
     */
    public List<Chunk> fixedWindow(String text, int targetTokens, int overlapTokens) {
        if (text == null || text.isBlank()) return List.of();
        final int windowChars = targetTokens * CHARS_PER_TOKEN;
        final int overlapChars = overlapTokens * CHARS_PER_TOKEN;
        final int step = Math.max(1, windowChars - overlapChars);

        final var chunks = new ArrayList<Chunk>();
        int idx = 0;
        int pos = 0;
        while (pos < text.length()) {
            final int end = Math.min(text.length(), pos + windowChars);
            chunks.add(new Chunk(idx, text.substring(pos, end), pos, end));
            if (end == text.length()) break;
            pos += step;
            idx++;
        }
        return chunks;
    }

    /**
     * Sentence-aware variant. Never splits mid-sentence — fitting for
     * narrative content where breaking a sentence loses meaning.
     * Falls back to fixed-window if a single sentence is bigger than
     * the target window.
     */
    public List<Chunk> sentenceAware(String text, int targetTokens) {
        if (text == null || text.isBlank()) return List.of();
        final int windowChars = targetTokens * CHARS_PER_TOKEN;
        final var sentences = text.split("(?<=[.!?])\\s+");
        final var chunks = new ArrayList<Chunk>();
        final var buf = new StringBuilder();
        int idx = 0;
        int startPos = 0;
        int curPos = 0;
        for (final var s : sentences) {
            if (buf.length() + s.length() + 1 > windowChars && buf.length() > 0) {
                chunks.add(new Chunk(idx++, buf.toString().strip(), startPos, curPos));
                buf.setLength(0);
                startPos = curPos;
            }
            buf.append(s).append(' ');
            curPos += s.length() + 1;
        }
        if (!buf.isEmpty()) {
            chunks.add(new Chunk(idx, buf.toString().strip(), startPos, curPos));
        }
        return chunks;
    }

    /**
     * Code-aware chunking: split on blank-line boundaries (paragraph
     * separators), which roughly map to function / class boundaries in
     * most languages.
     */
    public List<Chunk> semantic(String text, int targetTokens) {
        if (text == null || text.isBlank()) return List.of();
        final int windowChars = targetTokens * CHARS_PER_TOKEN;
        final var blocks = text.split("\\n{2,}");
        final var chunks = new ArrayList<Chunk>();
        final var buf = new StringBuilder();
        int idx = 0;
        int startPos = 0;
        int curPos = 0;
        for (final var b : blocks) {
            if (buf.length() + b.length() + 2 > windowChars && buf.length() > 0) {
                chunks.add(new Chunk(idx++, buf.toString(), startPos, curPos));
                buf.setLength(0);
                startPos = curPos;
            }
            buf.append(b).append("\n\n");
            curPos += b.length() + 2;
        }
        if (!buf.isEmpty()) {
            chunks.add(new Chunk(idx, buf.toString(), startPos, curPos));
        }
        return chunks;
    }

    /** One chunk = ({@code index}, text, original-document {@code startChar..endChar}). */
    public record Chunk(int index, String text, int startChar, int endChar) {
        public int approximateTokens() {
            return Math.max(1, text.length() / CHARS_PER_TOKEN);
        }
    }
}
