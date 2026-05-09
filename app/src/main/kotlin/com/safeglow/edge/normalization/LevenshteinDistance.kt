package com.safeglow.edge.normalization

/**
 * Pure Kotlin Levenshtein edit distance — two-row space optimization.
 *
 * O(m*n) time, O(min(m,n)) space via row-swap.
 * No external dependencies.
 *
 * Used by [INCINormalizer] as the stage-4 fuzzy fallback when exact and FTS
 * lookups fail. The threshold is ≤ 2 edit distance for tokens with length ≥ 6
 * (guards against false matches on short tokens — see RESEARCH.md Pitfall 6 /
 * anti-pattern note on minimum-length guard).
 *
 * Source: Standard dynamic programming algorithm.
 */
object LevenshteinDistance {

    /**
     * Returns the Levenshtein edit distance between [a] and [b].
     *
     * Returns 0 if the strings are equal.
     * Returns [b].length if [a] is empty, [a].length if [b] is empty.
     */
    fun compute(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val m = a.length
        val n = b.length
        val prev = IntArray(n + 1) { it }
        val curr = IntArray(n + 1)

        for (i in 0 until m) {
            curr[0] = i + 1
            for (j in 0 until n) {
                curr[j + 1] = minOf(
                    curr[j] + 1,            // insertion
                    prev[j + 1] + 1,        // deletion
                    prev[j] + if (a[i] == b[j]) 0 else 1  // substitution
                )
            }
            System.arraycopy(curr, 0, prev, 0, n + 1)
        }
        return prev[n]
    }

    /**
     * Returns true if [a] and [b] are within [threshold] edit distance of each other.
     * Short-circuits immediately if the length difference exceeds [threshold].
     */
    fun isWithinThreshold(a: String, b: String, threshold: Int): Boolean {
        if (kotlin.math.abs(a.length - b.length) > threshold) return false
        return compute(a, b) <= threshold
    }
}
