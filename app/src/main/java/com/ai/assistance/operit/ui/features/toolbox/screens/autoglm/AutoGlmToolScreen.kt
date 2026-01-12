package com.ai.assistance.operit.ui.features.toolbox.screens.autoglm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AutoGlmToolScreen(
    viewModel: AutoGlmViewModel = viewModel(factory = AutoGlmViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    var task by remember { mutableStateOf("") }
    var app by remember { mutableStateOf("") }

    AutoGlmToolContent(
        uiState = uiState,
        task = task,
        app = app,
        onTaskChange = { task = it },
        onAppChange = { app = it },
        onExecute = { viewModel.executeTask(it) },
        onCancel = { viewModel.cancelTask() },
        onStartApp = { viewModel.onStartApp(it) },
        onSwitchDisplay = { viewModel.onSwitchDisplay(it) },

    )
}

@Composable
private fun AutoGlmToolContent(
    uiState: AutoGlmUiState,
    task: String,
    app: String,
    onTaskChange: (String) -> Unit,
    onAppChange: (String) -> Unit,
    onExecute: (String) -> Unit,
    onCancel: () -> Unit,
    onStartApp: (String) -> Unit,
    onSwitchDisplay: (String) -> Unit,
) {
    val logScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = task,
            onValueChange = onTaskChange,
            label = { Text("Enter Task") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = app,
            onValueChange = onAppChange,
            label = { Text("Enter app") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (uiState.isLoading) {
                    onCancel()
                } else {
                    onExecute(task)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isLoading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (uiState.isLoading) "Cancel" else "Execute")
        }

        Button(
            onClick = {
                onStartApp(app)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("onStartApp")
        }

        Button(
            onClick = {
                onSwitchDisplay(app)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("onSwitchDisplay")
        }


        Spacer(modifier = Modifier.height(16.dp))

        Text("Execution Log", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            LaunchedEffect(uiState.log) {
                logScrollState.animateScrollTo(logScrollState.maxValue)
            }

            Text(
                text = uiState.log,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(logScrollState)
            )
        }
    }
}
