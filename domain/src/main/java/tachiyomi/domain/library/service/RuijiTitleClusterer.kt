package tachiyomi.domain.library.service

import java.util.Locale

// MIKO -->
/**
 * **Ruiji** (類似, "similar") — pure string-similarity clustering behind
 * `LibraryGroupType.RUIJI_TITLES`. Knows nothing about manga or Android: callers normalize the
 * titles they care about, dedupe them, and get back a single-linkage clustering they can map
 * entries onto. Kept free of `Context` so the whole algorithm is unit-testable (the repo has no
 * precedent for faking `Context` in JVM tests).
 */
object RuijiTitleClusterer {

    /**
     * Lowercases (root locale) and keeps only Unicode letters and digits, concatenated.
     *
     * Deliberately NOT the `TITLE_DUPLICATES` normalization (`[^a-z0-9]`): that one erases every
     * CJK/Cyrillic character, which would collapse all non-Latin titles into one empty key.
     */
    fun normalizeTitle(title: String): String {
        return buildString(title.length) {
            for (c in title.lowercase(Locale.ROOT)) {
                if (c.isLetterOrDigit()) append(c)
            }
        }
    }

    /**
     * Sørensen–Dice similarity over character-bigram multisets of two already-normalized titles,
     * in `[0.0, 1.0]`. Empty strings never resemble anything (not even each other) — that is what
     * quietly turns symbol-only titles into singletons for the caller. Non-empty identical strings
     * are `1.0`; a string shorter than 2 chars has no bigrams and only matches itself.
     */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.length < 2 || b.length < 2) return 0.0

        val bigramsA = bigramsOf(a)
        val bigramsB = bigramsOf(b)
        return diceCoefficient(bigramsA, bigramsB)
    }

    /**
     * Single-linkage clustering of [normalizedTitles] (distinct, already-normalized): entries end
     * up in the same cluster iff they are connected by a chain of pairs whose [similarity] × 100
     * reaches [thresholdPercent]. Returns one root index per input position — equal roots = same
     * cluster. Deterministic for the same input in the same order.
     *
     * Performance contract: bigrams are computed once per title, and each pair is skipped without
     * computing Dice when the two entries already share a root or when the size bound
     * `2 * min(|A|, |B|) * 100 < thresholdPercent * (|A| + |B|)` proves the threshold unreachable.
     */
    fun cluster(normalizedTitles: List<String>, thresholdPercent: Int): IntArray {
        val n = normalizedTitles.size
        val parent = IntArray(n) { it }
        val size = IntArray(n) { 1 }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun union(x: Int, y: Int) {
            val rx = find(x)
            val ry = find(y)
            if (rx == ry) return
            // Union by size; ties broken by the smaller index becoming root so the result is
            // deterministic regardless of traversal order.
            when {
                size[rx] < size[ry] -> {
                    parent[rx] = ry
                    size[ry] += size[rx]
                }
                size[rx] > size[ry] -> {
                    parent[ry] = rx
                    size[rx] += size[ry]
                }
                rx < ry -> {
                    parent[ry] = rx
                    size[rx] += size[ry]
                }
                else -> {
                    parent[rx] = ry
                    size[ry] += size[rx]
                }
            }
        }

        // Bigrams computed once per title (never per pair) — the O(n^2) loop below only ever reads
        // these precomputed arrays.
        val bigrams = Array(n) { bigramsOf(normalizedTitles[it]) }

        for (i in 0 until n) {
            for (j in (i + 1) until n) {
                if (find(i) == find(j)) continue

                val bigramsI = bigrams[i]
                val bigramsJ = bigrams[j]
                val la = bigramsI.size
                val lb = bigramsJ.size
                if (la == 0 || lb == 0) continue

                // Upper bound of the Dice coefficient given only the bigram counts: if even a full
                // intersection couldn't reach the threshold, skip computing the actual intersection.
                if (2L * minOf(la, lb) * 100 < thresholdPercent.toLong() * (la + lb)) continue

                val dice = diceCoefficient(bigramsI, bigramsJ)
                if (dice * 100 >= thresholdPercent) union(i, j)
            }
        }

        return IntArray(n) { find(it) }
    }

    /** Character bigrams of [s] as `(char1 shl 16) or char2`, sorted for multiset intersection. */
    private fun bigramsOf(s: String): IntArray {
        if (s.length < 2) return IntArray(0)
        val bigrams = IntArray(s.length - 1)
        for (i in bigrams.indices) {
            bigrams[i] = (s[i].code shl 16) or s[i + 1].code
        }
        bigrams.sort()
        return bigrams
    }

    /** Sørensen–Dice over two already-sorted bigram multisets; `0.0` when either is empty. */
    private fun diceCoefficient(a: IntArray, b: IntArray): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var i = 0
        var j = 0
        var intersection = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> {
                    intersection++
                    i++
                    j++
                }
                a[i] < b[j] -> i++
                else -> j++
            }
        }
        return 2.0 * intersection / (a.size + b.size)
    }
}
// MIKO <--
