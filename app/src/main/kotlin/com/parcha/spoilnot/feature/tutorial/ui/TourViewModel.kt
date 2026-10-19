package com.parcha.spoilnot.feature.tutorial.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.core.prefs.AppPreferences
import com.parcha.spoilnot.feature.tutorial.domain.Tour
import com.parcha.spoilnot.feature.tutorial.domain.TourEvent
import com.parcha.spoilnot.feature.tutorial.domain.TourStep
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The app tour (DESIGN 5.8): starts once per phone in the household app, and again when Settings asks for it
 * ([AppPreferences.tourDone] back to false). Finishing or skipping it is remembered.
 */
@HiltViewModel
class TourViewModel @Inject constructor(private val preferences: AppPreferences) : ViewModel() {
    private val _step = MutableStateFlow<TourStep?>(null)

    /** The step on screen, or null when no tour is running. */
    val step: StateFlow<TourStep?> = _step.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.tourDone.collect { done -> if (!done && _step.value == null) _step.value = Tour.first }
        }
    }

    fun on(event: TourEvent) {
        val current = _step.value ?: return
        val next = Tour.next(current, event)
        _step.value = next
        if (next == null) viewModelScope.launch { preferences.setTourDone(true) }
    }
}
