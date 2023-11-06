package com.dimensionstoolkit

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.facebook.react.bridge.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments

class FoldMonitoringModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private var foldingEventCallback: ((WritableMap) -> Unit)? = null
  private var lastFoldEvent: WritableMap? = null

  @ReactMethod
  fun startFoldingEventMonitoring(promise: Promise) {
    val lifecycleOwner = currentActivity as? LifecycleOwner
    if (lifecycleOwner == null) {
      promise.reject("LIFECYCLE_ERROR", "Could not find a valid LifecycleOwner.")
      return
    }

    val lifecycleScope = lifecycleOwner.lifecycleScope

    foldingEventCallback = { event ->
      promise.resolve(event)
    }

    lifecycleScope.launchWhenStarted {
      try {
        monitorFoldingEvents(lifecycleOwner)
      } catch (e: Exception) {
        promise.reject("MONITORING_ERROR", e.message, e)
      }
    }
  }

  private suspend fun monitorFoldingEvents(lifecycleOwner: LifecycleOwner) {
    val windowInfoTracker = WindowInfoTracker.getOrCreate(reactContext)

    val foldingFeatureFlow = windowInfoTracker
      .windowLayoutInfo(reactContext)
      .distinctUntilChanged()  // Emit events only when the folding state changes
      .collect { layoutInfo ->
        val foldType = layoutInfo.displayFeatures
          .filterIsInstance<FoldingFeature>()
          .firstOrNull { it.isTableTop() }?.let { "Table Top" }
          ?: layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
            .firstOrNull { it.isBookPosture() }?.let { "Book Posture" }
          ?: "Normal Posture"

        val event = Arguments.createMap()
        event.putString("foldType", foldType)

        if (lastFoldEvent == null || lastFoldEvent != event) {
          lastFoldEvent = event
          foldingEventCallback?.invoke(event)
        }
      }
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
