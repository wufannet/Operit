package com.ai.assistance.operit.api.speech

import android.content.Context
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** 语音识别服务工厂类 用于创建和管理不同类型的语音识别服务 */
object SpeechServiceFactory {
    /** 语音识别服务类型 */
    enum class SpeechServiceType {
        /** 基于Sherpa-ncnn的本地识别实现 */
        SHERPA_NCNN,
        /** 基于Sherpa-mnn的本地识别实现，集成VAD能力 */
        SHERPA_MNN,
        OPENAI_STT,
        DEEPGRAM_STT,
    }

    /**
     * 创建语音识别服务实例
     *
     * @param context 应用上下文
     * @return 对应类型的语音识别服务实例
     */
    fun createSpeechService(
        context: Context
    ): SpeechService {
        val prefs = SpeechServicesPreferences(context)
        val type = runBlocking { prefs.sttServiceTypeFlow.first() }

        return createSpeechService(context, type)
    }

    fun createWakeSpeechService(
        context: Context,
    ): SpeechService {
        val prefs = SpeechServicesPreferences(context)
        val selectedType = runBlocking { prefs.sttServiceTypeFlow.first() }
        val effectiveType = when (selectedType) {
            SpeechServiceType.OPENAI_STT,
            SpeechServiceType.DEEPGRAM_STT,
            -> SpeechServiceType.SHERPA_NCNN
            else -> selectedType
        }
        return createSpeechService(context, effectiveType)
    }

    fun createSpeechService(
        context: Context,
        type: SpeechServiceType,
    ): SpeechService {
        val prefs = SpeechServicesPreferences(context)
        return runBlocking {
            when (type) {
                SpeechServiceType.SHERPA_NCNN -> SherpaSpeechProvider(context)
                SpeechServiceType.SHERPA_MNN -> SherpaMnnSpeechProvider(context)
                SpeechServiceType.OPENAI_STT -> {
                    val sttConfig = prefs.sttHttpConfigFlow.first()
                    OpenAISttProvider(
                        context = context,
                        endpointUrl = sttConfig.endpointUrl,
                        apiKey = sttConfig.apiKey,
                        model = sttConfig.modelName,
                    )
                }
                SpeechServiceType.DEEPGRAM_STT -> {
                    val sttConfig = prefs.sttHttpConfigFlow.first()
                    DeepgramSttProvider(
                        context = context,
                        endpointUrl = sttConfig.endpointUrl,
                        apiKey = sttConfig.apiKey,
                        model = sttConfig.modelName,
                    )
                }
            }
        }
    }

    // 单例实例缓存
    private var instance: SpeechService? = null
    private var currentType: SpeechServiceType? = null

    /**
     * 获取语音识别服务单例实例
     *
     * @param context 应用上下文
     * @return 语音识别服务实例
     */
    fun getInstance(
        context: Context,
    ): SpeechService {
        val prefs = SpeechServicesPreferences(context)
        val selectedType = runBlocking { prefs.sttServiceTypeFlow.first() }
        
        val needNewInstance = instance == null || selectedType != currentType
        
        if (needNewInstance) {
            instance?.shutdown()
            instance = createSpeechService(context)
            currentType = selectedType
        }
        return instance!!
    }

    /** 重置单例实例 在需要更改语音识别服务类型或释放资源时调用 */
    fun resetInstance() {
        instance?.shutdown()
        instance = null
        currentType = null
    }
}
