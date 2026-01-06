package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.MainActivity


@Composable
fun AutoGlmParallelToolScreen(
    viewModel: AutoGlmParallelViewModel = viewModel(
        factory = AutoGlmParallelViewModelFactory(LocalContext.current)
    )
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()

    var appList by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("") }

    var selectedTask by remember { mutableStateOf<ParallelTaskUiState?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        OutlinedTextField(
            value = appList,
            onValueChange = { appList = it },
            label = { Text("App List (comma separated)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = template,
            onValueChange = { template = it },
            label = { Text("Prompt Template") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (uiState.isRunning) {
                    viewModel.cancelAll()
                } else {
                    viewModel.executeParallel(appList, template)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isRunning)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (uiState.isRunning) "Cancel All" else "Execute All")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 添加“添加到桌面”按钮
        Button(
            onClick = { addShortcut(context, "AutoGlmParallelTool") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Shortcut to Home")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Execution Log", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.tasks) { task ->
                ParallelTaskItem(
                    task = task,
                    onCancel = { viewModel.cancelTask(task.appName) },
                    onClick = { selectedTask = task }
                )
            }
        }
    }

    // 日志弹窗
    selectedTask?.let { task ->
        AlertDialog(
            onDismissRequest = { selectedTask = null },
            confirmButton = {
                TextButton(onClick = { selectedTask = null }) {
                    Text("Close")
                }
            },
            title = { Text("${task.appName} Log") },
            text = {
                Box(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(task.log)
                }
            }
        )
    }
}

private fun addShortcut(context: Context, shortcutName: String) {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
    }

    // 使用 adaptive icon 避免 null
    val icon = IconCompat.createWithResource(context, R.drawable.ic_launcher)

    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutName)
            .setShortLabel(shortcutName)
            .setIntent(intent)
            .setIcon(icon)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
    } else {
        Toast.makeText(context, "Device does not support adding shortcut", Toast.LENGTH_SHORT).show()
    }
}