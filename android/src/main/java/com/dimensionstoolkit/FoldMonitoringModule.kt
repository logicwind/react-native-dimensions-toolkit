package com.dimensionstoolkit

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.facebook.react.bridge.*
import kotlinx.coroutines.launch
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import kotlinx.coroutines.flow.callbackFlow

interface FoldMonitoringCallback {
  fun onFoldingEvent(event: WritableMap)
  fun onError(errorCode: String, errorMessage: String, error: Throwable)
}

class FoldMonitoringModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  data class FoldingEvent(val displayStatus: String, val foldType: String)

  private var foldMonitoringCallback: FoldMonitoringCallback? = null

  @ReactMethod
  fun startFoldingEventMonitoring(promise: Promise) {
    val lifecycleOwner = currentActivity as? LifecycleOwner
    if (lifecycleOwner == null) {
      promise.reject("LIFECYCLE_ERROR", "Could not find a valid LifecycleOwner.")
      return
    }

    val lifecycleScope = lifecycleOwner.lifecycleScope

    val callback = object : FoldMonitoringCallback {
      override fun onFoldingEvent(event: WritableMap) {
        promise.resolve(event)
      }

      override fun onError(errorCode: String, errorMessage: String, error: Throwable) {
        promise.reject(errorCode, errorMessage, error)
      }
    }

    foldMonitoringCallback = callback

    lifecycleScope.launchWhenStarted {
      try {
        monitorFoldingEvents(lifecycleOwner)
      } catch (e: Exception) {
        callback.onError("MONITORING_ERROR", e.message ?: "Unknown error", e)
      }
    }
  }

  private suspend fun monitorFoldingEvents(lifecycleOwner: LifecycleOwner) {
    val foldingFeatureFlow = callbackFlow {
      WindowInfoTracker.getOrCreate(reactContext)
        .windowLayoutInfo(reactContext)
        .collect { layoutInfo ->
          val event = FoldingEvent(
            displayStatus = if (layoutInfo.displayFeatures.isNotEmpty()) "Multiple displays" else "Single display",
            foldType = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
              .firstOrNull { it.isTableTop() }?.let { "Table Top" }
              ?: layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
                .firstOrNull { it.isBookPosture() }?.let { "Book Posture" }
              ?: "Normal Posture"
          )

          val eventMap = event.toWritableMap()
          send(eventMap)
        }
    }

    val job = lifecycleOwner.lifecycleScope.launch {
      try {
        foldingFeatureFlow.collect { event ->
          foldMonitoringCallback?.onFoldingEvent(event)
        }
      } catch (e: Exception) {
        foldMonitoringCallback?.onError("MONITORING_ERROR", e.message ?: "Unknown error", e)
      }
    }

    lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onDestroy(owner: LifecycleOwner) {
        job.cancel()
        foldMonitoringCallback = null
      }
    })
  }

  private fun FoldingEvent.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    map.putString("displayStatus", displayStatus)
    map.putString("foldType", foldType)
    return map
  }

  override fun getName(): String {
    return "FoldMonitoringKit"
  }
}

private fun FoldingFeature.isTableTop(): Boolean =
  state == FoldingFeature.State.HALF_OPENED &&
    orientation == FoldingFeature.Orientation.HORIZONTAL

private fun FoldingFeature.isBookPosture(): Boolean =
  state == FoldingFeature.State.HALF_OPENED &&
    orientation == FoldingFeature.Orientation.VERTICAL
