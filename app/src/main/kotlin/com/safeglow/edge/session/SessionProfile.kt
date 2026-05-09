package com.safeglow.edge.session

/**
 * Ephemeral session context for health-first ingredient analysis.
 *
 * All fields default to NOT_SET. Values are cleared on app process death
 * (PROF-03) — this data class is never written to Room, SharedPreferences,
 * or SavedStateHandle. It lives exclusively in [SessionViewModel]'s StateFlow.
 *
 * Downstream consumers (Phase 3 RAG, Phase 4 inference) filter knowledge base
 * results and prompt context using these values.
 */
data class SessionProfile(
    val pregnancyStatus: PregnancyStatus = PregnancyStatus.NOT_SET,
    val country: Country = Country.NOT_SET,
    val skinConcern: SkinConcern = SkinConcern.NOT_SET
)

/**
 * Whether the user is pregnant or breastfeeding — affects ingredient safety tags
 * (e.g., retinoids, parabens flagged with additional DANGER level for pregnant users).
 *
 * NOT_SET means the user has not disclosed; the app uses the most conservative
 * (general population) safety assessment.
 */
enum class PregnancyStatus {
    NOT_SET,
    PREGNANT,
    NOT_PREGNANT
}

/**
 * Regulatory jurisdiction for ingredient status display.
 *
 * EU/US/CN/JP cover the four major regulatory frameworks represented in the KB
 * (CosIng Annex II-VI, FDA OTC, Chinese NMPA, Japanese MHLW).
 *
 * NOT_SET shows the most restrictive cross-jurisdiction status.
 */
enum class Country {
    NOT_SET,
    EU,
    US,
    CN,
    JP
}

/**
 * Primary skin concern — used to surface relevant SOLVE-tagged alternatives
 * and to contextualize safety assessments (e.g., fragrances flagged more
 * prominently for SENSITIVE skin).
 */
enum class SkinConcern {
    NOT_SET,
    NORMAL,
    SENSITIVE,
    DRY,
    OILY
}
