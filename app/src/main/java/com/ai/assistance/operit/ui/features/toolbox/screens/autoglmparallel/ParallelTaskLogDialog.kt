package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@Composable
fun ParallelTaskLogDialog(
    task: ParallelTaskUiState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("${task.appName} Log") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                if (task.headerLog.isNotBlank()) {
                    item(key = "header") {
                        LogTextBlock(text = task.headerLog)
                    }
                }

                items(
                    items = task.logSteps,
                    key = { step -> "step-${step.stepIndex}" }
                ) { step ->
                    ParallelTaskLogStepItem(step = step)
                }

                if (task.footerLog.isNotBlank()) {
                    item(key = "footer") {
                        LogTextBlock(
                            text = task.footerLog,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ParallelTaskLogStepItem(step: ParallelTaskLogStep) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        step.screenshotPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Step ${step.stepIndex} screenshot",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        if (step.text.isNotBlank()) {
            LogTextBlock(
                text = step.text,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun LogTextBlock(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace
    )
}
