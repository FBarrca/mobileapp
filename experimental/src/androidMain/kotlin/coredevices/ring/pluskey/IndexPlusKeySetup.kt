package coredevices.ring.pluskey

import coredevices.libindex.di.LibIndexCoroutineScope
import coredevices.ring.agent.LlmMode
import coredevices.ring.database.Preferences
import coredevices.ring.model.CactusModelProvider
import coredevices.util.CommonBuildKonfig
import coredevices.util.CoreConfigHolder
import coredevices.util.models.CactusSTTMode
import coredevices.util.models.weightsVersionFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

internal object IndexPlusKeySetup {
    data class State(val busy: Boolean = false, val message: String = "Tap Prepare to check your selected services before enabling the key.", val ready: Boolean = false)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()

    fun prepareLocal() {
        if (mutable.value.busy) return
        mutable.value = State(true, "Preparing Index language model…")
        val koin = KoinPlatform.getKoin()
        koin.get<LibIndexCoroutineScope>().launch(Dispatchers.IO) {
            try {
                val models = koin.get<CactusModelProvider>()
                val endpoints = coredevices.ring.endpoints.CustomEndpoints.read()
                endpoints.llm.validate(); endpoints.speech.validate()
                if (!endpoints.llm.enabled) models.getLMModelPath()
                mutable.value = State(true, "Downloading multilingual speech model. Keep the app open…")
                val speechModel = CommonBuildKonfig.CACTUS_STT_MODEL
                if (!endpoints.speech.enabled) models.getSTTModelPath(speechModel, weightsVersionFor(speechModel))
                koin.get<Preferences>().setLlmMode(LlmMode.LocalOnly)
                val config = koin.get<CoreConfigHolder>()
                config.update(config.config.value.copy(enableIndex = true,
                    sttConfig = config.config.value.sttConfig.copy(mode = CactusSTTMode.LocalOnly, modelName = speechModel)))
                mutable.value = State(message = "Index is ready. LLM: ${if (endpoints.llm.enabled) endpoints.llm.model else "on-device"}. Speech: ${if (endpoints.speech.enabled) endpoints.speech.model else "on-device"}.", ready = true)
            } catch (e: Exception) {
                mutable.value = State(message = "Model setup failed: ${e.message}. Tap Prepare to retry.")
            }
        }
    }
}
