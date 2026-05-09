package com.safeglow.edge.session

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Exposes ephemeral session context (PROF-01, PROF-03).
 *
 * PROF-03 compliance:
 * - Uses plain [MutableStateFlow] — NOT [androidx.lifecycle.SavedStateHandle].
 * - Data is NOT written to Room, SharedPreferences, DataStore, or any disk-backed store.
 * - [onCleared] fires on Activity finish / process death; no explicit clear needed.
 * - Verify compliance: `grep -r 'SharedPreferences\|getSharedPreferences\|PreferenceManager'
 *   app/src/main/kotlin` must return zero matches.
 *
 * Downstream consumers (Phase 3 RAG context builder, Phase 4 prompt assembler)
 * collect [profile] as a StateFlow and pass the values as context filters.
 */
@HiltViewModel
class SessionViewModel @Inject constructor() : ViewModel() {

    private val _profile = MutableStateFlow(SessionProfile())
    val profile: StateFlow<SessionProfile> = _profile.asStateFlow()

    /** Updates pregnancy/breastfeeding status. Cleared on process death (PROF-03). */
    fun setPregnancyStatus(status: PregnancyStatus) {
        _profile.update { it.copy(pregnancyStatus = status) }
    }

    /** Updates regulatory jurisdiction for ingredient display. Cleared on process death. */
    fun setCountry(country: Country) {
        _profile.update { it.copy(country = country) }
    }

    /** Updates primary skin concern for SOLVE-tag filtering. Cleared on process death. */
    fun setSkinConcern(concern: SkinConcern) {
        _profile.update { it.copy(skinConcern = concern) }
    }

    /**
     * Resets all session values to NOT_SET.
     * Can be called by UI when user explicitly wants to clear session context.
     * Also fires automatically via [onCleared] when the Activity finishes.
     */
    fun clearSession() {
        _profile.value = SessionProfile()
    }

    // onCleared() is called by Android ViewModel framework on process death / Activity finish.
    // No override needed — MutableStateFlow is GC'd with the ViewModel.
}
