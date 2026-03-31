package com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride

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
fun AutoGlmRideScreen(
    viewModel: AutoGlmRideViewModel = viewModel(factory = AutoGlmRideViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
//    var task by remember { mutableStateOf(RideDidiPrompt.ride_didi_use) }
    var task by remember { mutableStateOf("") }
    var app by remember { mutableStateOf("滴滴") }
    var batchSize by remember { mutableStateOf(20) }
    var logDir by remember { mutableStateOf("/sdcard/.0logs/{current_time}_滴滴_解决点您_v1_p21_c1_p30_360p_20_1/") }
    var start by remember { mutableStateOf("吾悦广场3号门") } //新增tv 1.数据属性
    var destination by remember { mutableStateOf("白马广场") }

    AutoGlmToolContent(
        uiState = uiState,
        task = task,
        app = app,
        start = start,
        destination = destination,
        batchSize = batchSize,
        logDir = logDir,
        onTaskChange = { task = it },
        onAppChange = { app = it },
        onStartChange = { start = it },
        onDestinationChange = { destination = it },
        onExecute = { viewModel.executeTask(it) },
        onCancel = { viewModel.cancelTask() },
        onStartApp = { viewModel.onStartApp(it) },
        onSwitchDisplay = { viewModel.onSwitchDisplay(it) },
        onExecuteTaskBatch = { task, batchSize, logDir,appName,start,dest ->
            viewModel.executeTaskBatchBiz(task, batchSize, logDir,appName,start,dest)
        },
        batchSizeChange = { batchSize = it.toIntOrNull() ?: 20 },
        executeTaskBatchCancel = { viewModel.executeTaskBatchCancel() },
        logDirChange = { logDir = it },
    )
}

@Composable
private fun AutoGlmToolContent(
    uiState: com.ai.assistance.operit.ui.features.toolbox.screens.autoglmride.AutoGlmUiState,
    task: String,
    app: String,
    start: String,
    destination: String,
    batchSize: Int,
    logDir: String,
    onTaskChange: (String) -> Unit,
    onAppChange: (String) -> Unit,
    onStartChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onExecute: (String) -> Unit,
    onCancel: () -> Unit,
    onStartApp: (String) -> Unit,
    onSwitchDisplay: (String) -> Unit,
    onExecuteTaskBatch: (String, Int, String,String, String, String) -> Unit,
    batchSizeChange: (String) -> Unit,
    executeTaskBatchCancel: () -> Unit,
    logDirChange: (String) -> Unit,
) {
    val logScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(5.dp)
    ) {
//        OutlinedTextField(
//            value = task,
//            onValueChange = onTaskChange,
//            label = { Text("Enter Task") },
//            modifier = Modifier.fillMaxWidth(),
//            maxLines = 3
//        )
//
//        Spacer(modifier = Modifier.height(2.dp))

        OutlinedTextField(
            value = app,
            onValueChange = onAppChange,
            label = { Text("Enter app") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(3.dp))

        OutlinedTextField(
            value = start,
            onValueChange = onStartChange ,//新增tv 2.空间, 这个控件值变动同步到数据的变动属于样板代码. 数据到控件的同步在哪?
            label = { Text("请输入起点,不填则是定位位置") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(3.dp))

        OutlinedTextField(
            value = destination,
            onValueChange = onDestinationChange,
            label = { Text("请输入终点") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )
        Spacer(modifier = Modifier.height(3.dp))

        //添加 tv模板
//        OutlinedTextField(
//            value = app,
//            onValueChange = onAppChange,
//            label = { Text("Enter app") },
//            modifier = Modifier.fillMaxWidth(),
//            maxLines = 2
//        )
//
//        Spacer(modifier = Modifier.height(3.dp))

        //批量执行次数
        OutlinedTextField(
            value = batchSize.toString(),
            onValueChange = batchSizeChange,
            label = { Text("Enter batchSize") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(3.dp))

        OutlinedTextField(
            value = logDir,
            onValueChange = logDirChange,
            label = { Text("Enter logDir") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(3.dp))

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

        Button(
            onClick = {
                onExecuteTaskBatch(task,batchSize,logDir,app,start,destination)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("executeTaskBatch")
        }

        Button(
            onClick = {
                executeTaskBatchCancel()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("executeTaskBatchCancel")
        }



        Spacer(modifier = Modifier.height(3.dp))

        Text("Execution Log", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(3.dp))

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
