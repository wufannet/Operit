package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel.ParallelTaskUiState
import com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel.TaskStatus
import com.ai.assistance.operit.ui.features.toolbox.screens.autoglmparallel.TaskStatusIcon

@Composable
fun ParallelTaskItem(
    task: ParallelTaskUiState,
    onCancel: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column {
                Text(task.appName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = task.status.name,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {

                TaskStatusIcon(task.status)

                Spacer(modifier = Modifier.width(8.dp))

                if (task.status == TaskStatus.RUNNING) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}