package com.fyp.nextshot.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.fyp.nextshot.data.local.models.AiTipEntity
import com.fyp.nextshot.data.repository.TipsRepository
import kotlinx.coroutines.launch

class TipsViewModel(private val repository: TipsRepository) : ViewModel() {

    // Observable list of AI tips from the local database
    val tips: LiveData<List<AiTipEntity>> = repository.latestTips.asLiveData()

    // Loading state for the UI
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error state for the UI
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    /**
     * Generate AI tips based on recent session data.
     * Uses cache to avoid redundant API calls.
     */
    fun generateTips() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                repository.generateTips(forceRefresh = false)
            } catch (e: Exception) {
                Log.e("TipsViewModel", "Tip generation failed: ${e.message}", e)
                _error.value = "Could not generate tips. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Force refresh — bypasses cache and regenerates tips.
     */
    fun refreshTips() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                repository.generateTips(forceRefresh = true)
            } catch (e: Exception) {
                Log.e("TipsViewModel", "Tip refresh failed: ${e.message}", e)
                _error.value = "Could not refresh tips. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }
}

/**
 * Factory to create TipsViewModel with repository dependency.
 */
class TipsViewModelFactory(private val repository: TipsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TipsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TipsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
