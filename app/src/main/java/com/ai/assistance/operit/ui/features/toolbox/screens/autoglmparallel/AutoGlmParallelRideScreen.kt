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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class RidePrecheckUiState {
    data object Checking : RidePrecheckUiState()

    enum class OkMode {
        NO_VIRTUAL_REQUIRED,
        READY
    }

    data class Ok(val mode: OkMode) : RidePrecheckUiState()

    enum class ErrorKind {
        SHIZUKU_NOT_READY,
        CHECK_FAILED
    }

    data class Error(
        val kind: ErrorKind,
        val exceptionText: String? = null,
        val shizukuInstalled: Boolean = false,
        val shizukuRunning: Boolean = false,
        val shizukuPermissionGranted: Boolean = false,
        val experimentalEnabled: Boolean = true,
    ) : RidePrecheckUiState()
}


@Composable
fun AutoGlmParallelRideScreen(
    viewModel: AutoGlmParallelViewModel = viewModel(
        factory = AutoGlmParallelViewModelFactory(LocalContext.current)
    ),
    onNavigateToPermissionPage: () -> Unit = {}
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()

    var appList by remember { mutableStateOf("滴滴,高德,花小猪") }
    var template by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") } //新增tv 1.数据属性
    var destination by remember { mutableStateOf("白马广场") }

    var selectedTask by remember { mutableStateOf<ParallelTaskUiState?>(null) }

    var precheckState by remember { mutableStateOf<RidePrecheckUiState>(RidePrecheckUiState.Checking) }
    LaunchedEffect(Unit) {
        precheckState = RidePrecheckUiState.Checking
        precheckState = withContext(Dispatchers.IO) {
            try {
                val preferredLevel =
                    androidPermissionPreferences.getPreferredPermissionLevel()
                        ?: AndroidPermissionLevel.STANDARD

                var isAdbOrHigher = when (preferredLevel) {
                    AndroidPermissionLevel.DEBUGGER,
                    AndroidPermissionLevel.ADMIN,
                    AndroidPermissionLevel.ROOT -> true
                    else -> false
                }

                val experimentalEnabled = try {
                    DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled()
                } catch (_: Exception) {
                    true
                }

                if (isAdbOrHigher && !experimentalEnabled) {
                    // 与 PhoneAgent 保持一致：实验虚拟屏关闭时，等同于不需要虚拟屏能力
                    isAdbOrHigher = false
                }

                if (!isAdbOrHigher) {
                    return@withContext RidePrecheckUiState.Ok(
                        mode = RidePrecheckUiState.OkMode.NO_VIRTUAL_REQUIRED
                    )
                }

                // 仅在 DEBUGGER 模式下要求 Shizuku 运行 + 权限
                if (preferredLevel == AndroidPermissionLevel.DEBUGGER) {
                    val shizukuInstalled = ShizukuAuthorizer.isShizukuInstalled(context)
                    val isShizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning()
                    val hasShizukuPermission =
                        if (isShizukuRunning) ShizukuAuthorizer.hasShizukuPermission() else false

                    if (!isShizukuRunning || !hasShizukuPermission) {
                        return@withContext RidePrecheckUiState.Error(
                            kind = RidePrecheckUiState.ErrorKind.SHIZUKU_NOT_READY,
                            shizukuInstalled = shizukuInstalled,
                            shizukuRunning = isShizukuRunning,
                            shizukuPermissionGranted = hasShizukuPermission,
                            experimentalEnabled = experimentalEnabled
                        )
                    }
                }

                return@withContext RidePrecheckUiState.Ok(
                    mode = RidePrecheckUiState.OkMode.READY
                )
            } catch (e: Exception) {
                RidePrecheckUiState.Error(
                    kind = RidePrecheckUiState.ErrorKind.CHECK_FAILED,
                    exceptionText = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // 状态卡片：进入页面就展示虚拟屏/Shizuku 是否就绪
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (val s = precheckState) {
                    is RidePrecheckUiState.Checking -> {
                        Text(
                            text = stringResource(R.string.ride_precheck_checking),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    is RidePrecheckUiState.Ok -> {
                        Text(
                            text = stringResource(R.string.ride_precheck_status_normal),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (s.mode) {
                                RidePrecheckUiState.OkMode.NO_VIRTUAL_REQUIRED ->
                                    stringResource(R.string.ride_precheck_ok_no_virtual_message)

                                RidePrecheckUiState.OkMode.READY ->
                                    stringResource(R.string.ride_precheck_ok_ready_message)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    is RidePrecheckUiState.Error -> {
                        Text(
                            text = stringResource(R.string.ride_precheck_status_abnormal),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (s.kind) {
                                RidePrecheckUiState.ErrorKind.SHIZUKU_NOT_READY ->
                                    stringResource(R.string.ride_precheck_error_shizuku_unavailable)

                                RidePrecheckUiState.ErrorKind.CHECK_FAILED ->
                                    stringResource(
                                        R.string.ride_precheck_error_check_failed,
                                        s.exceptionText ?: ""
                                    )
                            },
                            style = MaterialTheme.typography.bodySmall
                        )

                        // Details：安装/运行/授权 + 实验虚拟屏开关
                        Spacer(modifier = Modifier.height(8.dp))
                        val yesNoInstalled =
                            if (s.shizukuInstalled) stringResource(R.string.yes) else stringResource(R.string.no)
                        val yesNoRunning =
                            if (s.shizukuRunning) stringResource(R.string.yes) else stringResource(R.string.no)
                        val permissionText =
                            if (s.shizukuPermissionGranted) stringResource(R.string.authorized) else stringResource(R.string.unauthorized)
                        val yesNoExperimental =
                            if (s.experimentalEnabled) stringResource(R.string.yes) else stringResource(R.string.no)

                        val detailsText = buildString {
                            append(stringResource(R.string.ride_precheck_shizuku_installed_label, yesNoInstalled))
                            append("\n")
                            append(stringResource(R.string.ride_precheck_shizuku_running_label, yesNoRunning))
                            append("\n")
                            append(stringResource(R.string.ride_precheck_shizuku_permission_label, permissionText))
                            append("\n")
                            append(stringResource(R.string.ride_precheck_experimental_virtual_display_label, yesNoExperimental))
                        }
                        Text(text = detailsText, style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onNavigateToPermissionPage,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.ride_precheck_grant_permission_button))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = appList,
            onValueChange = { appList = it },
            label = { Text("App List (comma separated)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

//        OutlinedTextField(
//            value = template,
//            onValueChange = { template = it },
//            label = { Text("Prompt Template") },
//            modifier = Modifier.fillMaxWidth(),
//            maxLines = 4
//        )
//
//        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = start,
            onValueChange = { start = it }, //新增tv 2.空间, 这个控件值变动同步到数据的变动属于样板代码. 数据到控件的同步在哪?
            label = { Text("请输入起点,不填则是定位位置") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            label = { Text("请输入终点") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            enabled = uiState.isRunning || precheckState !is RidePrecheckUiState.Error,
            onClick = {
                if (uiState.isRunning) {
                    viewModel.cancelAll()
                } else {
                    viewModel.executeParallelRide(appList, template,start,destination)
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

        if (!uiState.isRunning) {
            val totalMs = uiState.totalSuccessDurationMillis
            Text(
                text = if (totalMs != null) {
                    "总耗时: ${formatDuration(totalMs)} (最慢成功: ${uiState.slowestSuccessAppName ?: "-"})"
                } else {
                    "总耗时: -"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 添加“添加到桌面”按钮 隐藏添加快捷方式
//        Button(
//            onClick = { addShortcut(context, "AutoGlmParallelTool") },
//            modifier = Modifier.fillMaxWidth()
//        ) {
//            Text("Add Shortcut to Home")
//        }
//
//        Spacer(modifier = Modifier.height(16.dp))

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

private fun formatDuration(ms: Long): String {
    val seconds = ms.toDouble() / 1000.0
    return String.format(Locale.getDefault(), "%.1fs", seconds)
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