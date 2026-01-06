package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel

enum class TaskStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED
}

data class ParallelTaskUiState(
    val appName: String,
    val prompt: String,
    val status: TaskStatus = TaskStatus.IDLE,
    val log: String = ""
)

data class AutoGlmParallelUiState(
    val isRunning: Boolean = false,
    val tasks: List<ParallelTaskUiState> = emptyList()
)