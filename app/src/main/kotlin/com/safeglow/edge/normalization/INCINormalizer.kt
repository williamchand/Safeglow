package com.safeglow.edge.normalization

import com.safeglow.edge.data.knowledge.db.IngredientDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Four-stage INCI normalization pipeline (SCAN-03).
 *
 * Stage 1: Uppercase + strip non-INCI characters (keeps A-Z, 0-9, space, hyphen).
 * Stage 2: Synonym map lookup via [INCISynonymMap.resolve].
 * Stage 3a: Exact Room match via [IngredientDao.findExact].
 * Stage 3b: FTS search via [IngredientDao.ftsSearch].
 * Stage 4: Levenshtein fuzzy fallback (threshold ≤ 2, only for tokens length ≥ 6).
 *
 * Returns "UNRESOLVED" for tokens that fail all stages.
 *
 * Performance (RESEARCH.md Pitfall 5): [IngredientDao.getAll] is called ONCE per
 * [normalize] invocation (before the loop), not once per token. For an 80-ingredient
 * KB, the full list is ~2 KB in memory — caching is mandatory.
 *
 * Manual input (SCAN-02): same pipeline; input text is split externally into tokens
 * and passed to [normalize] — no difference in processing path.
 *
 * Input validation (ASVS V5): stage-1 regex strips control characters and non-INCI
 * characters. Max-length enforcement (1000 chars per token) prevents OOM from
 * adversarial input (T-2-03 mitigation).
 */
@Singleton
class INCINormalizer @Inject constructor(
    private val ingredientDao: IngredientDao
) {
    // INCISynonymMap is a Kotlin object singleton — referenced directly (not injected).
    private val synonymMap = INCISynonymMap

    companion object {
        /** Maximum token length before normalization; guards against OOM (ASVS V5). */
        const val MAX_TOKEN_LENGTH = 1000

        /** Minimum length for Levenshtein fuzzy matching; guards against false positives. */
        const val MIN_FUZZY_LENGTH = 6

        /** Maximum edit distance for fuzzy matching. */
        const val FUZZY_THRESHOLD = 2
    }

    /**
     * Normalizes a list of raw OCR or manual-input tokens to canonical INCI names.
     *
     * Loads all KB ingredient names once before the loop (Pitfall 5 mitigation).
     * Returns "UNRESOLVED" for tokens that cannot be mapped to a KB entry.
     */
    suspend fun normalize(rawTokens: List<String>): List<String> {
        // Pre-load all KB names once — avoids N×getAll() round-trips (Pitfall 5)
        val allKbNames = ingredientDao.getAll().map { it.inciName }
        return rawTokens.map { token -> normalizeOne(token, allKbNames) }
    }

    private suspend fun normalizeOne(raw: String, allKbNames: List<String>): String {
        // Guard: reject excessively long tokens (ASVS V5 / T-2-03)
        if (raw.length > MAX_TOKEN_LENGTH) return "UNRESOLVED"

        // Stage 1: uppercase + strip non-INCI characters
        val cleaned = raw.uppercase()
            .replace(Regex("[^A-Z0-9 \\-]"), "")
            .trim()
        if (cleaned.isBlank()) return "UNRESOLVED"

        // Stage 2: synonym map lookup
        val canonical = synonymMap.resolve(cleaned)

        // Stage 3a: exact Room match
        if (ingredientDao.findExact(canonical) != null) return canonical

        // Stage 3b: FTS search (handles partial / stemmed matches)
        val ftsResults = ingredientDao.ftsSearch(canonical)
        if (ftsResults.isNotEmpty()) return ftsResults.first().inciName

        // Stage 4: Levenshtein fuzzy — minimum-length guard prevents false positives
        // on short tokens (e.g. "CI", "PEG") per RESEARCH.md anti-pattern note.
        if (canonical.length >= MIN_FUZZY_LENGTH && allKbNames.isNotEmpty()) {
            val best = allKbNames.minByOrNull { LevenshteinDistance.compute(canonical, it) }
            if (best != null && LevenshteinDistance.compute(canonical, best) <= FUZZY_THRESHOLD) {
                return best
            }
        }

        return "UNRESOLVED"
    }
}
