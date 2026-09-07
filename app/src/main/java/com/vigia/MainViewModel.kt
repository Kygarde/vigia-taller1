package com.vigia

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.adaptation.SamplingPolicy
import com.vigia.decision.AdaptationEngine
import com.vigia.model.*
import com.vigia.processing.ContextManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val contextManager = ContextManager(app)
    private val engine = AdaptationEngine()
    private val samplingPolicy = SamplingPolicy(contextManager.accelerometer)

    private val _snapshot = MutableStateFlow(ContextSnapshot())
    val snapshot = _snapshot.asStateFlow()

    private val _decision = MutableStateFlow(AdaptationDecision())
    val decision = _decision.asStateFlow()

    private val _samplingLabel = MutableStateFlow("1 Hz")
    val samplingLabel = _samplingLabel.asStateFlow()

    init {
        contextManager.start()
        viewModelScope.launch {
            contextManager.snapshots.collect { ctx ->
                _snapshot.value = ctx
                val d = engine.decide(ctx)            // DECISIÓN
                _decision.value = d
                samplingPolicy.apply(d.mode)          // ADAPTACIÓN
                _samplingLabel.value = samplingPolicy.currentConfig.label
                android.util.Log.d("VIGIA", "${d.mode} | ${d.reason}")
            }
        }
    }

    override fun onCleared() { contextManager.stop() }
}