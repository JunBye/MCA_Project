package com.example.mca_project.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mca_project.data.repository.SessionRepository
import com.example.mca_project.domain.model.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _session = MutableStateFlow<Session?>(null)
    val session = _session.asStateFlow()

    fun load(sessionId: String) {
        viewModelScope.launch {
            _session.value = sessionRepository.getSession(sessionId)
        }
    }
}