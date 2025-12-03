package com.ai.assistance.operit.util

import android.util.Log
import com.ai.assistance.operit.util.ChatUtils

/**
 * Token缓存管理器，用于优化重复对话历史的token计算
 * 通过缓存之前计算过的对话历史token数量，避免重复计算相同的内容
 */
class TokenCacheManager {
    // 上一次的聊天历史
    private var previousChatHistory: List<Pair<String, String>> = emptyList()
    // 对应于previousChatHistory的token数量
    private var previousHistoryTokenCount = 0
    
    // 缓存的输入token数量（对应于previousChatHistory的公共前缀）
    private var _cachedInputTokenCount = 0
    
    // 当前请求的新增token数量
    private var _currentInputTokenCount = 0
    
    // 当前输出token数量
    private var _outputTokenCount = 0
    
    /**
     * 获取缓存的输入token数量
     */
    val cachedInputTokenCount: Int
        get() = _cachedInputTokenCount
    
    /**
     * 获取当前请求的输入token数量（不包括缓存）
     */
    val currentInputTokenCount: Int
        get() = _currentInputTokenCount
    
    /**
     * 获取总输入token数量（缓存 + 当前）
     */
    val totalInputTokenCount: Int
        get() = _cachedInputTokenCount + _currentInputTokenCount
    
    /**
     * 获取输出token数量
     */
    val outputTokenCount: Int
        get() = _outputTokenCount
    
    /**
     * 重置所有token计数和缓存
     */
    fun resetTokenCounts() {
        previousChatHistory = emptyList()
        previousHistoryTokenCount = 0
        _cachedInputTokenCount = 0
        _currentInputTokenCount = 0
        _outputTokenCount = 0
    }
    
    /**
     * 增加输出token数量
     */
    fun addOutputTokens(tokens: Int) {
        _outputTokenCount += tokens
    }
    
    /**
     * 使用API返回的实际token数据更新计数
     * 用于Gemini等支持服务端缓存统计的API
     * 
     * @param actualInput 实际的输入token数量（不包括缓存）
     * @param cachedInput 缓存命中的token数量
     */
    fun updateActualTokens(actualInput: Int, cachedInput: Int) {
        _currentInputTokenCount = actualInput
        _cachedInputTokenCount = cachedInput
    }
    
    /**
     * 计算输入token数量，利用缓存优化重复计算
     * 
     * @param message 当前用户消息
     * @param chatHistory 完整的聊天历史
     * @param toolsJson 工具定义的JSON字符串（可选）
     * @return 总的输入token数量
     */
    fun calculateInputTokens(
        message: String,
        chatHistory: List<Pair<String, String>>,
        toolsJson: String? = null
    ): Int {
        // 找到与之前历史的公共前缀长度
        val commonPrefixLength = findCommonPrefixLength(chatHistory, previousChatHistory)
        
        Log.d("TokenCacheManager", "聊天历史比较: 当前=${chatHistory.size}, 之前=${previousChatHistory.size}, 公共前缀=${commonPrefixLength}")
        
        if (commonPrefixLength > 0) {
            // 有公共前缀，可以使用缓存
            val cachedTokens = if (commonPrefixLength == previousChatHistory.size) {
                // 完全匹配之前的历史，直接使用缓存
                previousHistoryTokenCount
            } else {
                // 部分匹配，重新计算公共前缀的token数量
                val commonPrefix = chatHistory.take(commonPrefixLength)
                calculateTokensForHistory(commonPrefix)
            }
            
            // 计算新增部分的token数量
            val newPart = chatHistory.drop(commonPrefixLength)
            val newTokens = calculateTokensForHistory(newPart) + ChatUtils.estimateTokenCount(message)
            
            // 添加工具定义的token
            val toolsTokens = if (toolsJson != null) ChatUtils.estimateTokenCount(toolsJson) else 0
            
            _cachedInputTokenCount = cachedTokens
            _currentInputTokenCount = newTokens + toolsTokens
            
            Log.d("TokenCacheManager", "使用token缓存: 缓存=${_cachedInputTokenCount}, 新增=${_currentInputTokenCount}")
        } else {
            // 没有公共前缀，重新计算所有token
            val toolsTokens = if (toolsJson != null) ChatUtils.estimateTokenCount(toolsJson) else 0
            
            _cachedInputTokenCount = 0
            _currentInputTokenCount = calculateTokensForHistory(chatHistory) + ChatUtils.estimateTokenCount(message) + toolsTokens
            
            Log.d("TokenCacheManager", "重新计算所有tokens: ${_currentInputTokenCount} (包含工具定义: $toolsTokens)")
        }
        
        // 更新缓存的历史记录
        val newHistory = chatHistory.toMutableList()
        newHistory.add("user" to message)
        previousChatHistory = newHistory
        previousHistoryTokenCount = totalInputTokenCount
        
        return totalInputTokenCount
    }
    
    /**
     * 找到两个聊天历史列表的公共前缀长度
     */
    private fun findCommonPrefixLength(
        current: List<Pair<String, String>>,
        previous: List<Pair<String, String>>
    ): Int {
        val minLength = minOf(current.size, previous.size)
        var commonLength = 0
        
        for (i in 0 until minLength) {
            if (current[i] == previous[i]) {
                commonLength++
            } else {
                break
            }
        }
        
        return commonLength
    }
    
    /**
     * 计算聊天历史的token数量
     */
    private fun calculateTokensForHistory(history: List<Pair<String, String>>): Int {
        return history.sumOf { (_, content) -> ChatUtils.estimateTokenCount(content) }
    }
} 