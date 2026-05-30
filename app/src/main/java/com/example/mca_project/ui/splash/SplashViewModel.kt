package com.example.mca_project.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mca_project.ml.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SplashUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { modelManager.loadModels() }
                .onSuccess {
                    _uiState.value = SplashUiState(isLoading = false)
                }
                .onFailure { throwable ->
                    _uiState.value = SplashUiState(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Model load failed",
                    )
                }
        }
    }
}
