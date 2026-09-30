package com.nicholaston.callscribe.ui.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nicholaston.callscribe.models.ModelDownloadWorker
import com.nicholaston.callscribe.models.ModelRegistry
import com.nicholaston.callscribe.models.ModelStore
import com.nicholaston.callscribe.models.DownloadableModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ModelUiState(
    val model: DownloadableModel,
    val installed: Boolean,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val error: String? = null,
)

class ModelsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ModelStore(application)
    private val workManager = WorkManager.getInstance(application)
    private val mutableModels = MutableStateFlow(
        ModelRegistry.downloadableModels.map { ModelUiState(it, installed = store.isInstalled(it)) },
    )

    val models: StateFlow<List<ModelUiState>> = mutableModels.asStateFlow()

    init {
        ModelRegistry.downloadableModels.forEach(::observeDownload)
    }

    fun download(modelId: String, wifiOnly: Boolean = true) {
        ModelDownloadWorker.enqueue(getApplication(), modelId, wifiOnly)
    }

    fun delete(modelId: String) {
        val model = ModelRegistry.requireDownloadable(modelId)
        check(store.delete(model)) { "Unable to delete ${model.displayName}" }
        update(model.id) { it.copy(installed = false, downloading = false, progress = 0, error = null) }
    }

    private fun observeDownload(model: DownloadableModel) {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow("model-download-${model.id}").collectLatest { infos ->
                val info = infos.maxByOrNull { it.runAttemptCount }
                val progress = info?.progress?.getInt(ModelDownloadWorker.KEY_PROGRESS, 0) ?: 0
                val error = info?.outputData?.getString(ModelDownloadWorker.KEY_ERROR)
                update(model.id) { current ->
                    current.copy(
                        installed = store.isInstalled(model),
                        downloading = info?.state == WorkInfo.State.RUNNING ||
                            info?.state == WorkInfo.State.ENQUEUED,
                        progress = progress,
                        error = error,
                    )
                }
            }
        }
    }

    private fun update(modelId: String, transform: (ModelUiState) -> ModelUiState) {
        mutableModels.value = mutableModels.value.map { state ->
            if (state.model.id == modelId) transform(state) else state
        }
    }
}
