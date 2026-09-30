package com.nicholaston.callscribe.ui.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nicholaston.callscribe.data.CallRecord
import com.nicholaston.callscribe.data.CallRepository
import com.nicholaston.callscribe.data.TranscriptSegment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CallDetailUiState(
    val call: CallRecord? = null,
    val transcript: List<TranscriptSegment> = emptyList(),
)

class CallDetailViewModel(callId: Long, repository: CallRepository) : ViewModel() {
    val state: StateFlow<CallDetailUiState> = combine(
        repository.observeCall(callId),
        repository.observeTranscript(callId),
        ::CallDetailUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CallDetailUiState(),
    )

    companion object {
        fun factory(callId: Long, repository: CallRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CallDetailViewModel(callId, repository) as T
            }
    }
}
