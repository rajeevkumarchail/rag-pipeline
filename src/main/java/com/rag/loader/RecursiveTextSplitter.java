package com.rag.loader;

import com.rag.model.LoaderConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Recursive character-level text splitter.
 *
 * Algorithm (mirrors LangChain's RecursiveCharacterTextSplitter internals):
 *
 *  Phase 1 — splitRecursive:
 *    Try separators in semantic order: paragraph → line → sentence → word → character.
 *    The first separator that actually splits the text is used.
 *    Any resulting piece that is still > chunkSize is recursively split with the
 *    remaining (finer-grained) separators.
 *    Result: a flat list of "atomic" pieces, each ≤ chunkSize.
 *
 *  Phase 2 — mergeWithOverlap:
 *    Walk the atomic pieces left-to-right, accumulating them in a sliding buffer.
 *    When the buffer would exceed chunkSize, emit it as a chunk, then trim pieces
 *    from the *front* of the buffer until the remaining content ≤ chunkOverlap.
 *    Those retained pieces become the overlap at the start of the next chunk.
 *    This is O(n) in the number of splits.
 *
 * Why Pattern.quote(sep)?
 *   ". " contains a regex metachar. We want literal splitting, not regex.
 *
 * Why split(sep, -1)?
 *   Java's split() silently drops trailing empty strings without the limit=-1 guard.
 *   "a\n\n" would become ["a"] instead of ["a", "", ""].
 */
public class RecursiveTextSplitter {

    private static final List<String> SEPARATORS = List.of("\n\n", "\n", ". ", " ", "");

    private final LoaderConfig config;

    public RecursiveTextSplitter(LoaderConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public LoaderConfig getConfig() {
        return config;
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> atomicPieces = splitRecursive(text, SEPARATORS);
        return mergeWithOverlap(atomicPieces);
    }

    // --- Phase 1 -------------------------------------------------------

    private List<String> splitRecursive(String text, List<String> separators) {
        if (text.length() <= config.chunkSize()) {
            return List.of(text);
        }

        for (int i = 0; i < separators.size(); i++) {
            String sep = separators.get(i);

            if (sep.isEmpty()) {
                return hardSplit(text);
            }

            String[] parts = text.split(Pattern.quote(sep), -1);
            if (parts.length <= 1) continue; // separator not present, try next

            List<String> finer = separators.subList(i + 1, separators.size());
            List<String> result = new ArrayList<>();

            for (String part : parts) {
                if (part.isBlank()) continue;
                if (part.length() > config.chunkSize()) {
                    result.addAll(splitRecursive(part, finer));
                } else {
                    result.add(part);
                }
            }

            return result;
        }

        // unreachable: the empty-string separator always terminates the loop
        return hardSplit(text);
    }

    /** Last-resort: slice at exactly chunkSize with no semantic awareness. */
    private List<String> hardSplit(String text) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            result.add(text.substring(i, Math.min(i + config.chunkSize(), text.length())));
            i += config.chunkSize();
        }
        return result;
    }

    // --- Phase 2 -------------------------------------------------------

    /**
     * Reassemble atomic pieces into final chunks with overlap.
     *
     * Two-tier overlap strategy:
     *
     *  Tier 1 (piece-level): trim pieces from the front of the buffer until the
     *  remaining content is ≤ chunkOverlap.  Works well when pieces are finer-
     *  grained than chunkOverlap (e.g. individual words).
     *
     *  Tier 2 (character-level fallback): when tier-1 trims the buffer to empty
     *  — which happens when individual pieces are coarser than chunkOverlap, e.g.
     *  full sentences — re-seed the buffer with an exact character slice from the
     *  tail of the just-emitted chunk.  This guarantees ≥ min(chunkOverlap,
     *  chunk.length()) characters of overlap regardless of piece granularity.
     *
     * Trade-off: a re-seeded chunk can reach chunkSize + chunkOverlap characters.
     * chunkSize is therefore a soft target, not a hard cap.  This matches
     * LangChain's RecursiveCharacterTextSplitter behaviour.
     */
    private List<String> mergeWithOverlap(List<String> pieces) {
        List<String> chunks = new ArrayList<>();
        Deque<String> buffer = new ArrayDeque<>();
        int bufferLen = 0;

        for (String piece : pieces) {
            if (bufferLen + piece.length() > config.chunkSize() && !buffer.isEmpty()) {
                // Emit current buffer
                String chunk = String.join("", buffer);
                if (chunk.strip().length() >= config.minChunkSize()) {
                    chunks.add(chunk);
                }

                // Tier 1: trim pieces from front until remaining ≤ chunkOverlap
                while (!buffer.isEmpty() && bufferLen > config.chunkOverlap()) {
                    bufferLen -= buffer.pollFirst().length();
                }

                // Tier 2: if trimming emptied the buffer (pieces were coarser than
                // chunkOverlap), re-seed with a character-level tail from the emitted chunk.
                if (buffer.isEmpty() && config.chunkOverlap() > 0) {
                    String seed = chunk.substring(
                            Math.max(0, chunk.length() - config.chunkOverlap()));
                    if (!seed.isBlank()) {
                        buffer.addFirst(seed);
                        bufferLen = seed.length();
                    }
                }
            }
            buffer.addLast(piece);
            bufferLen += piece.length();
        }

        // Flush any remaining content
        if (!buffer.isEmpty()) {
            String last = String.join("", buffer);
            if (last.strip().length() >= config.minChunkSize()) {
                chunks.add(last);
            }
        }

        return chunks;
    }
}
