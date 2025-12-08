package com.fyp.nextshot.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.fyp.nextshot.data.local.models.SessionEntity
import com.fyp.nextshot.data.repository.SessionRepository
import kotlinx.coroutines.launch

class SessionViewModel(private val repository: SessionRepository) : ViewModel() {

    // Expose all sessions as LiveData (observing the Room Flow)
    val allSessions: LiveData<List<SessionEntity>> = repository.allSessions.asLiveData()

    // Function to insert data using coroutine
    fun insert(session: SessionEntity) = viewModelScope.launch {
        repository.insert(session)
    }

    // Function to delete data using coroutine (for completeness)
    fun delete(session: SessionEntity) = viewModelScope.launch {
        repository.delete(session)
    }
}

// Factory class required to create a ViewModel with a constructor argument (the Repository)
class SessionViewModelFactory(private val repository: SessionRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SessionViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}