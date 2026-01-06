package com.ai.assistance.operit.ui.features.workflow.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.data.model.ConditionNode
import com.ai.assistance.operit.data.model.ConditionOperator
import com.ai.assistance.operit.data.model.ExecuteNode
import com.ai.assistance.operit.data.model.ExtractMode
import com.ai.assistance.operit.data.model.ExtractNode
import com.ai.assistance.operit.data.model.LogicNode
import com.ai.assistance.operit.data.model.LogicOperator
import com.ai.assistance.operit.data.model.NodePosition
import com.ai.assistance.operit.data.model.ParameterValue
import com.ai.assistance.operit.data.model.TriggerNode
import com.ai.assistance.operit.data.model.WorkflowNodeConnection
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.repository.WorkflowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 工作流ViewModel
 * 管理工作流的状态和业务逻辑
 */
class WorkflowViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WorkflowRepository(application)
    
    var workflows by mutableStateOf<List<Workflow>>(emptyList())
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    var currentWorkflow by mutableStateOf<Workflow?>(null)
        private set
    
    // 节点执行状态 Map
    private val _nodeExecutionStates = MutableStateFlow<Map<String, NodeExecutionState>>(emptyMap())
    val nodeExecutionStates: StateFlow<Map<String, NodeExecutionState>> = _nodeExecutionStates.asStateFlow()
    
    init {
        loadWorkflows()
    }
    
    /**
     * 加载所有工作流
     */
    fun loadWorkflows() {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getAllWorkflows().fold(
                onSuccess = { workflows = it },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }

    /**
     * 更新连接条件
     */
    fun updateConnectionCondition(
        workflowId: String,
        connectionId: String,
        condition: String?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null

            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold

                    val updatedConnections = workflow.connections.map { connection ->
                        if (connection.id == connectionId) {
                            connection.copy(condition = condition)
                        } else {
                            connection
                        }
                    }

                    val updatedWorkflow = workflow.copy(
                        connections = updatedConnections,
                        updatedAt = System.currentTimeMillis()
                    )

                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "更新连接条件失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )

            isLoading = false
        }
    }

    fun createChatTemplateWorkflow(onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildChatTemplateWorkflow(
                name = "对话模板 $time",
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: "创建工作流失败" }
            )

            isLoading = false
        }
    }

    fun createConditionTemplateWorkflow(onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildConditionTemplateWorkflow(
                name = "判断模板 $time",
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: "创建工作流失败" }
            )

            isLoading = false
        }
    }

    fun createLogicAndTemplateWorkflow(onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildLogicTemplateWorkflow(
                operator = LogicOperator.AND,
                name = "逻辑AND模板 $time",
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: "创建工作流失败" }
            )

            isLoading = false
        }
    }

    fun createLogicOrTemplateWorkflow(onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildLogicTemplateWorkflow(
                operator = LogicOperator.OR,
                name = "逻辑OR模板 $time",
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: "创建工作流失败" }
            )

            isLoading = false
        }
    }

    fun createExtractTemplateWorkflow(onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildExtractTemplateWorkflow(
                name = "提取模板 $time",
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: "创建工作流失败" }
            )

            isLoading = false
        }
    }

    private fun templateNodePosition(index: Int): NodePosition {
        val maxPerColumn = 5
        val startX = 120f
        val startY = 120f
        val columnSpacing = 720f
        val rowSpacing = 420f
        val column = index / maxPerColumn
        val row = index % maxPerColumn
        return NodePosition(
            x = startX + column * columnSpacing,
            y = startY + row * rowSpacing
        )
    }

    private fun buildChatTemplateWorkflow(name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val startId = UUID.randomUUID().toString()
        val createChatId = UUID.randomUUID().toString()
        val sendId = UUID.randomUUID().toString()
        val stopId = UUID.randomUUID().toString()
        val closeDisplaysId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = "手动触发",
            triggerType = "manual",
            position = templateNodePosition(0)
        )

        val startChat = ExecuteNode(
            id = startId,
            name = "启动悬浮窗",
            actionType = "start_chat_service",
            position = templateNodePosition(1)
        )

        val createChat = ExecuteNode(
            id = createChatId,
            name = "创建对话",
            actionType = "create_new_chat",
            actionConfig = mapOf(
                "group" to ParameterValue.StaticValue("workflow")
            ),
            position = templateNodePosition(2)
        )

        val sendMessage = ExecuteNode(
            id = sendId,
            name = "发送消息",
            actionType = "send_message_to_ai",
            actionConfig = mapOf(
                "message" to ParameterValue.StaticValue("你好")
            ),
            position = templateNodePosition(3)
        )

        val stopChat = ExecuteNode(
            id = stopId,
            name = "停止悬浮窗",
            actionType = "stop_chat_service",
            position = templateNodePosition(4)
        )

        val closeAllDisplays = ExecuteNode(
            id = closeDisplaysId,
            name = "关闭所有虚拟屏幕",
            actionType = "close_all_virtual_displays",
            position = templateNodePosition(5)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = startId),
            WorkflowNodeConnection(sourceNodeId = startId, targetNodeId = createChatId),
            WorkflowNodeConnection(sourceNodeId = createChatId, targetNodeId = sendId),
            WorkflowNodeConnection(sourceNodeId = sendId, targetNodeId = stopId),
            WorkflowNodeConnection(sourceNodeId = stopId, targetNodeId = closeDisplaysId)
        )

        return Workflow(
            name = name,
            description = description,
            nodes = listOf(trigger, startChat, createChat, sendMessage, stopChat, closeAllDisplays),
            connections = connections
        )
    }

    private fun buildConditionTemplateWorkflow(name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val visitId = UUID.randomUUID().toString()
        val extractVisitKeyId = UUID.randomUUID().toString()
        val conditionId = UUID.randomUUID().toString()
        val followLinkId = UUID.randomUUID().toString()
        val fallbackVisitId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = "手动触发",
            triggerType = "manual",
            position = templateNodePosition(0)
        )

        val visitWeb = ExecuteNode(
            id = visitId,
            name = "访问网页",
            actionType = "visit_web",
            actionConfig = mapOf(
                "url" to ParameterValue.StaticValue("https://example.com")
            ),
            position = templateNodePosition(1)
        )

        val extractVisitKey = ExtractNode(
            id = extractVisitKeyId,
            name = "提取 visit_key",
            source = ParameterValue.NodeReference(visitId),
            mode = ExtractMode.REGEX,
            expression = "Visit key:\\s*([0-9a-fA-F-]+)",
            group = 1,
            defaultValue = "",
            position = templateNodePosition(2)
        )

        val condition = ConditionNode(
            id = conditionId,
            name = "网页是否包含关键字",
            left = ParameterValue.NodeReference(visitId),
            operator = ConditionOperator.CONTAINS,
            right = ParameterValue.StaticValue("Example Domain"),
            position = templateNodePosition(3)
        )

        val followFirstLink = ExecuteNode(
            id = followLinkId,
            name = "命中 -> 打开第1个链接",
            actionType = "visit_web",
            actionConfig = mapOf(
                "visit_key" to ParameterValue.NodeReference(extractVisitKeyId),
                "link_number" to ParameterValue.StaticValue("1")
            ),
            position = templateNodePosition(4)
        )

        val fallbackVisit = ExecuteNode(
            id = fallbackVisitId,
            name = "未命中 -> 访问备用页面",
            actionType = "visit_web",
            actionConfig = mapOf(
                "url" to ParameterValue.StaticValue("https://example.org")
            ),
            position = templateNodePosition(5)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = visitId),
            WorkflowNodeConnection(sourceNodeId = visitId, targetNodeId = extractVisitKeyId),
            WorkflowNodeConnection(sourceNodeId = extractVisitKeyId, targetNodeId = conditionId),
            WorkflowNodeConnection(sourceNodeId = conditionId, targetNodeId = followLinkId),
            WorkflowNodeConnection(sourceNodeId = conditionId, targetNodeId = fallbackVisitId, condition = "false")
        )

        return Workflow(
            name = name,
            description = description,
            nodes = listOf(
                trigger,
                visitWeb,
                extractVisitKey,
                condition,
                followFirstLink,
                fallbackVisit
            ),
            connections = connections
        )
    }

    private fun buildLogicTemplateWorkflow(operator: LogicOperator, name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val visitId = UUID.randomUUID().toString()
        val conditionAId = UUID.randomUUID().toString()
        val conditionBId = UUID.randomUUID().toString()
        val logicId = UUID.randomUUID().toString()
        val extractVisitKeyId = UUID.randomUUID().toString()
        val followLinkId = UUID.randomUUID().toString()
        val fallbackVisitId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = "手动触发",
            triggerType = "manual",
            position = templateNodePosition(0)
        )

        val visitWeb = ExecuteNode(
            id = visitId,
            name = "访问网页",
            actionType = "visit_web",
            actionConfig = mapOf(
                "url" to ParameterValue.StaticValue("https://example.com")
            ),
            position = templateNodePosition(1)
        )

        val conditionA = ConditionNode(
            id = conditionAId,
            name = "条件A: 包含 Example Domain",
            left = ParameterValue.NodeReference(visitId),
            operator = ConditionOperator.CONTAINS,
            right = ParameterValue.StaticValue("Example Domain"),
            position = templateNodePosition(2)
        )

        val conditionB = ConditionNode(
            id = conditionBId,
            name = "条件B: 包含 More information",
            left = ParameterValue.NodeReference(visitId),
            operator = ConditionOperator.CONTAINS,
            right = ParameterValue.StaticValue("More information"),
            position = templateNodePosition(3)
        )

        val logic = LogicNode(
            id = logicId,
            name = "逻辑判断",
            operator = operator,
            position = templateNodePosition(4)
        )

        val extractVisitKey = ExtractNode(
            id = extractVisitKeyId,
            name = "提取 visit_key",
            source = ParameterValue.NodeReference(visitId),
            mode = ExtractMode.REGEX,
            expression = "Visit key:\\s*([0-9a-fA-F-]+)",
            group = 1,
            defaultValue = "",
            position = templateNodePosition(5)
        )

        val followFirstLink = ExecuteNode(
            id = followLinkId,
            name = "逻辑为真 -> 打开第1个链接",
            actionType = "visit_web",
            actionConfig = mapOf(
                "visit_key" to ParameterValue.NodeReference(extractVisitKeyId),
                "link_number" to ParameterValue.StaticValue("1")
            ),
            position = templateNodePosition(6)
        )

        val fallbackVisit = ExecuteNode(
            id = fallbackVisitId,
            name = "逻辑为假 -> 访问备用页面",
            actionType = "visit_web",
            actionConfig = mapOf(
                "url" to ParameterValue.StaticValue("https://example.org")
            ),
            position = templateNodePosition(7)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = visitId),
            WorkflowNodeConnection(sourceNodeId = visitId, targetNodeId = conditionAId),
            WorkflowNodeConnection(sourceNodeId = visitId, targetNodeId = conditionBId),
            WorkflowNodeConnection(sourceNodeId = conditionAId, targetNodeId = logicId),
            WorkflowNodeConnection(sourceNodeId = conditionBId, targetNodeId = logicId),
            WorkflowNodeConnection(sourceNodeId = visitId, targetNodeId = extractVisitKeyId),
            WorkflowNodeConnection(sourceNodeId = extractVisitKeyId, targetNodeId = logicId),
            WorkflowNodeConnection(sourceNodeId = logicId, targetNodeId = followLinkId),
            WorkflowNodeConnection(sourceNodeId = logicId, targetNodeId = fallbackVisitId, condition = "false")
        )

        return Workflow(
            name = name,
            description = description,
            nodes = listOf(
                trigger,
                logic,
                visitWeb,
                conditionA,
                conditionB,
                extractVisitKey,
                followFirstLink,
                fallbackVisit
            ),
            connections = connections
        )
    }

    private fun buildExtractTemplateWorkflow(name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val visitId = UUID.randomUUID().toString()
        val extractVisitKeyId = UUID.randomUUID().toString()
        val followLinkId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = "手动触发",
            triggerType = "manual",
            position = templateNodePosition(0)
        )

        val visitWeb = ExecuteNode(
            id = visitId,
            name = "访问网页",
            actionType = "visit_web",
            actionConfig = mapOf(
                "url" to ParameterValue.StaticValue("https://example.com")
            ),
            position = templateNodePosition(1)
        )

        val extractVisitKey = ExtractNode(
            id = extractVisitKeyId,
            name = "提取 visit_key",
            source = ParameterValue.NodeReference(visitId),
            mode = ExtractMode.REGEX,
            expression = "Visit key:\\s*([0-9a-fA-F-]+)",
            group = 1,
            defaultValue = "",
            position = templateNodePosition(2)
        )

        val followFirstLink = ExecuteNode(
            id = followLinkId,
            name = "跟进第1个链接",
            actionType = "visit_web",
            actionConfig = mapOf(
                "visit_key" to ParameterValue.NodeReference(extractVisitKeyId),
                "link_number" to ParameterValue.StaticValue("1")
            ),
            position = templateNodePosition(3)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = visitId),
            WorkflowNodeConnection(sourceNodeId = visitId, targetNodeId = extractVisitKeyId),
            WorkflowNodeConnection(sourceNodeId = extractVisitKeyId, targetNodeId = followLinkId)
        )

        return Workflow(
            name = name,
            description = description,
            nodes = listOf(trigger, visitWeb, extractVisitKey, followFirstLink),
            connections = connections
        )
    }
    
    /**
     * 根据ID加载工作流
     */
    fun loadWorkflow(id: String) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(id).fold(
                onSuccess = { currentWorkflow = it },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 创建工作流
     */
    fun createWorkflow(name: String, description: String, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            val workflow = Workflow(
                name = name,
                description = description
            )
            
            repository.createWorkflow(workflow).fold(
                onSuccess = { 
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: "创建工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新工作流
     */
    fun updateWorkflow(workflow: Workflow, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.updateWorkflow(workflow).fold(
                onSuccess = { 
                    currentWorkflow = it
                    loadWorkflows()
                    onSuccess()
                },
                onFailure = { error = it.message ?: "更新工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 删除工作流
     */
    fun deleteWorkflow(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.deleteWorkflow(id).fold(
                onSuccess = { 
                    if (it) {
                        loadWorkflows()
                        onSuccess()
                    } else {
                        error = "删除工作流失败"
                    }
                },
                onFailure = { error = it.message ?: "删除工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 触发工作流
     */
    fun triggerWorkflow(id: String, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            // 不设置全局 isLoading，以便用户可以看到执行过程
            error = null
            _nodeExecutionStates.value = emptyMap()
            
            repository.triggerWorkflowWithCallback(id) { nodeId, state ->
                // 实时更新节点执行状态，用户可以在画布上看到执行进度
                _nodeExecutionStates.value = _nodeExecutionStates.value + (nodeId to state)
            }.fold(
                onSuccess = { message -> 
                    // 刷新工作流列表以显示更新的执行统计
                    loadWorkflows()
                    // 如果当前正在查看这个工作流，也刷新它
                    if (currentWorkflow?.id == id) {
                        loadWorkflow(id)
                    }
                    onComplete(message)
                },
                onFailure = { error -> 
                    // 即使失败也刷新，因为失败状态也会被记录
                    loadWorkflows()
                    if (currentWorkflow?.id == id) {
                        loadWorkflow(id)
                    }
                    this@WorkflowViewModel.error = error.message ?: "触发工作流失败"
                    onComplete("执行失败: ${error.message}")
                }
            )
        }
    }
    
    /**
     * 清除节点执行状态
     */
    fun clearNodeExecutionStates() {
        _nodeExecutionStates.value = emptyMap()
    }
    
    /**
     * 添加节点到工作流
     */
    fun addNode(workflowId: String, node: com.ai.assistance.operit.data.model.WorkflowNode, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        nodes = workflow.nodes + node,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "添加节点失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 删除节点
     */
    fun deleteNode(workflowId: String, nodeId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        nodes = workflow.nodes.filter { it.id != nodeId },
                        connections = workflow.connections.filter { 
                            it.sourceNodeId != nodeId && it.targetNodeId != nodeId 
                        },
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "删除节点失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新节点
     */
    fun updateNode(workflowId: String, nodeId: String, updatedNode: com.ai.assistance.operit.data.model.WorkflowNode, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        nodes = workflow.nodes.map { if (it.id == nodeId) updatedNode else it },
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "更新节点失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新节点（重载方法，直接接受节点对象）
     */
    fun updateNode(workflowId: String, updatedNode: com.ai.assistance.operit.data.model.WorkflowNode, onSuccess: () -> Unit = {}) {
        updateNode(workflowId, updatedNode.id, updatedNode, onSuccess)
    }
    
    /**
     * 创建连接
     */
    fun createConnection(
        workflowId: String,
        sourceId: String,
        targetId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    
                    // 检查连接是否已存在
                    val connectionExists = workflow.connections.any {
                        it.sourceNodeId == sourceId && it.targetNodeId == targetId
                    }
                    
                    if (connectionExists) {
                        error = "连接已存在"
                        isLoading = false
                        return@fold
                    }
                    
                    val newConnection = com.ai.assistance.operit.data.model.WorkflowNodeConnection(
                        sourceNodeId = sourceId,
                        targetNodeId = targetId
                    )
                    
                    val updatedWorkflow = workflow.copy(
                        connections = workflow.connections + newConnection,
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "创建连接失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 删除连接
     */
    fun deleteConnection(
        workflowId: String,
        connectionId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        connections = workflow.connections.filter { it.id != connectionId },
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: "删除连接失败" }
                    )
                },
                onFailure = { error = it.message ?: "加载工作流失败" }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新节点位置
     */
    fun updateNodePosition(
        workflowId: String,
        nodeId: String,
        x: Float,
        y: Float,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedNodes = workflow.nodes.map { node ->
                        if (node.id == nodeId) {
                            node.position.x = x
                            node.position.y = y
                            node
                        } else {
                            node
                        }
                    }
                    val updatedWorkflow = workflow.copy(
                        nodes = updatedNodes,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            onSuccess()
                        },
                        onFailure = { /* 静默失败，位置更新不是关键操作 */ }
                    )
                },
                onFailure = { /* 静默失败 */ }
            )
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        error = null
    }
    
    /**
     * Schedule a workflow
     */
    fun scheduleWorkflow(workflowId: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val success = repository.scheduleWorkflow(workflowId)
                if (success) {
                    loadWorkflows()
                    onSuccess()
                } else {
                    onFailure("无法调度工作流")
                }
            } catch (e: Exception) {
                onFailure(e.message ?: "调度工作流失败")
            }
        }
    }
    
    /**
     * Unschedule a workflow
     */
    fun unscheduleWorkflow(workflowId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.unscheduleWorkflow(workflowId)
                loadWorkflows()
                onSuccess()
            } catch (e: Exception) {
                error = e.message ?: "取消调度失败"
            }
        }
    }
    
    /**
     * Check if workflow is scheduled
     */
    fun isWorkflowScheduled(workflowId: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            repository.isWorkflowScheduled(workflowId)
        }
    }
    
    /**
     * Get next execution time for workflow
     */
    fun getNextExecutionTime(workflowId: String, onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            val nextTime = repository.getNextExecutionTime(workflowId)
            onResult(nextTime)
        }
    }
}

