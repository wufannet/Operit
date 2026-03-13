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
    var task by remember { mutableStateOf("""任务指令：
       1. 启动与进入： 打开滴滴出行进入主页,请执行具体操作do(action="Launch", app="滴滴出行").
       2. 触发搜索： 在主页点击“您想去哪儿”中的“您”字,请执行具体操作do(action="Tap", element=[272,509]).
         - 异常处理： 若进入“预约打车”或“立即选车”页面，请点击返回键回到主页重试，确保进入的是带键盘的“目的地搜索页面”。
       3. 输入目的地： 在键盘已经显示意味着输入框已经处于激活状态的目的地搜索页面输入“{destination}”,请执行具体操作do(action="Type", text="{destination}").
       4. 选择目标： 在搜索结果列表中，点击最匹配的选项（通常是第一项）。
       5. 确认已进入了打车选择页面：
         - 严禁操作: 不允许叫车,禁止点击"呼叫x种车型"按钮，不要点击"呼叫x种车型"按钮来完成订单,严禁代替用户下单呼叫。
         - 情况 A - 打车选择页面： 如果页面已经显示了多种车型的实时价格（如：惊喜特价 ¥XX、滴滴快车 ¥XX）和底部的“呼叫x种车型”按钮且终点地址正确，则任务已全部成功完成,不需要再找到和点击“确认下车点”，视为任务成功请执行具体操作finish。
         - 情况 B - 地址确认页： 如果页面底部出现“确认下车点”按钮，请点击它以进入打车选择页面,如果没有就代表不需要点击“确认下车点”。
       任务完成检查：
         - 成功标准： 只要屏幕上出现了实时报价列表且终点地址正确，即视为任务成功。
         - 终态动作： 停留在报价页面等待加载完成即可。
         - 严禁操作： 禁止点击底部的“呼叫x种车型”按钮，严禁代替用户下单呼叫。
       严禁操作: 不允许叫车,禁止点击"呼叫x种车型"按钮，不要点击"呼叫x种车型"按钮来完成订单,严禁代替用户下单呼叫。“确认下车点”按钮不是下单可以点击.""") }
    var app by remember { mutableStateOf("滴滴") }
    var batchSize by remember { mutableStateOf(10) }
    var logDir by remember { mutableStateOf("/sdcard/.0logs/{current_time}_滴滴_解决叫车_v1_p20_c1_p30_720p_10_1/") }

    AutoGlmToolContent(
        uiState = uiState,
        task = task,
        app = app,
        batchSize = batchSize,
        logDir = logDir,
        onTaskChange = { task = it },
        onAppChange = { app = it },
        onExecute = { viewModel.executeTask(it) },
        onCancel = { viewModel.cancelTask() },
        onStartApp = { viewModel.onStartApp(it) },
        onSwitchDisplay = { viewModel.onSwitchDisplay(it) },
        onExecuteTaskBatch = { task, batchSize, logDir ->
            viewModel.executeTaskBatch(task, batchSize, logDir)
        },
        batchSizeChange = { batchSize = it.toIntOrNull() ?: 20 },
        executeTaskBatchCancel = { viewModel.executeTaskBatchCancel() },
        logDirChange = { logDir = it },
    )
}

@Composable
private fun AutoGlmToolContent(
    uiState: AutoGlmUiState,
    task: String,
    app: String,
    batchSize: Int,
    logDir: String,
    onTaskChange: (String) -> Unit,
    onAppChange: (String) -> Unit,
    onExecute: (String) -> Unit,
    onCancel: () -> Unit,
    onStartApp: (String) -> Unit,
    onSwitchDisplay: (String) -> Unit,
    onExecuteTaskBatch: (String,Int,String) -> Unit,
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
        OutlinedTextField(
            value = task,
            onValueChange = onTaskChange,
            label = { Text("Enter Task") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(2.dp))

        OutlinedTextField(
            value = app,
            onValueChange = onAppChange,
            label = { Text("Enter app") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
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
                onExecuteTaskBatch(task,batchSize,logDir)
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
