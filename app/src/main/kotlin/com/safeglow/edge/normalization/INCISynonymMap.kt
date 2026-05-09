package com.safeglow.edge.normalization

/**
 * Curated synonym map: common alias/trade name → canonical INCI uppercase name.
 *
 * Data sourced from CIR Cosmetic Ingredient Dictionary and INCIdecoder.com
 * cross-reference. Not a library — a curated Kotlin object.
 *
 * Coverage: 20+ seed entries for the 80-priority-ingredient knowledge base.
 * Categories: parabens, retinoids, sunscreens, fragrances, preservatives,
 * humectants, acids, surfactants.
 *
 * TODO (DATA-01 Phase 2): Extend to ~60+ synonym entries covering all 80 KB
 * ingredients. Each new synonym should be validated against CIR/INCIdecoder
 * and added alongside its corresponding SQL INSERT in tools/build_seed_db.sh.
 *
 * Usage: call [resolve] after stage-1 normalization (uppercase + strip).
 */
object INCISynonymMap {

    /**
     * Synonym map: key is cleaned alias (uppercase, non-INCI chars stripped),
     * value is the canonical INCI name as it appears in the knowledge base.
     *
     * 20 seed entries — extend as KB grows to 80 ingredients.
     */
    private val synonyms: Map<String, String> = mapOf(
        // Parabens (RESEARCH.md Pattern 5)
        "METHYL PARABEN"                    to "METHYLPARABEN",
        "NIPAGIN"                           to "METHYLPARABEN",
        "METHYL 4-HYDROXYBENZOATE"          to "METHYLPARABEN",
        "PROPYL PARABEN"                    to "PROPYLPARABEN",
        "NIPASOL"                           to "PROPYLPARABEN",
        "ETHYL PARABEN"                     to "ETHYLPARABEN",
        "BUTYL PARABEN"                     to "BUTYLPARABEN",

        // Retinoids
        "VITAMIN A"                         to "RETINOL",
        "RETINOIC ACID"                     to "TRETINOIN",
        "RETINALDEHYDE"                     to "RETINAL",
        "RETINYL PALMITATE"                 to "RETINYL PALMITATE",

        // Sunscreens
        "BENZOPHENONE-3"                    to "OXYBENZONE",
        "OCTYL METHOXYCINNAMATE"            to "ETHYLHEXYL METHOXYCINNAMATE",
        "PARSOL 1789"                       to "BUTYL METHOXYDIBENZOYLMETHANE",
        "AVOBENZONE"                        to "BUTYL METHOXYDIBENZOYLMETHANE",

        // Fragrances
        "FRAGRANCE"                         to "PARFUM",
        "PERFUME"                           to "PARFUM",

        // Preservatives
        "2-PHENOXYETHANOL"                  to "PHENOXYETHANOL",

        // Acids / actives
        "BETA HYDROXY ACID"                 to "SALICYLIC ACID",
        "BHA"                               to "SALICYLIC ACID",

        // Humectants / solvents
        "GLYCERIN"                          to "GLYCEROL"
        // TODO: add remaining ~40 synonym entries as DATA-01 KB is extended
    )

    /**
     * Returns the canonical INCI name for [cleaned] if a synonym entry exists,
     * otherwise returns [cleaned] unchanged.
     *
     * [cleaned] must already be uppercase with non-INCI characters stripped
     * (stage-1 output of [INCINormalizer]).
     */
    fun resolve(cleaned: String): String = synonyms[cleaned] ?: cleaned
}
