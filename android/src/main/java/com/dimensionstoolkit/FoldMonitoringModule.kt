package com.dimensionstoolkit

import androidx.lifecycle.*
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FoldMonitoringModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  data class FoldingEvent(val displayStatus: String, val foldType: String)

  private val foldingEvent = MutableLiveData<FoldingEvent>()

  @ReactMethod
  suspend fun startFoldingEventMonitoring() {
    val lifecycleOwner = currentActivity as? LifecycleOwner
    if (lifecycleOwner == null) {
      return
    }

    val lifecycleScope = lifecycleOwner.lifecycleScope

    lifecycleScope.launchWhenStarted {
      WindowInfoTracker.getOrCreate(reactContext)
        .windowLayoutInfo(reactContext)
        .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
        .collect { layoutInfo ->
          val event = FoldingEvent(
            displayStatus = if (layoutInfo.displayFeatures.isNotEmpty()) "Multiple displays" else "Single display",
            foldType = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
              .firstOrNull { it -> it.isTableTop() }?.let { "Table Top" }
              ?: layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
                .firstOrNull { it -> it.isBookPosture() }?.let { "Book Posture" }
              ?: "Normal Posture"
          )

          // Switch to the main thread before invoking observe
          withContext(Dispatchers.Main) {
            foldingEvent.value = event
          }
        }
    }

    // Switch to the main thread before invoking observe
    withContext(Dispatchers.Main) {
      foldingEvent.observe(lifecycleOwner) { event ->
        // Handle the event, e.g., emit it to React Native
        reactContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit("onFold", event)
      }
    }
  }

  override fun getName(): String {
    return NAME
  }

  companion object {
    const val NAME = "FoldMonitoringKit"
  }
}

private fun FoldingFeature.isTableTop(): Boolean =
  state == FoldingFeature.State.HALF_OPENED &&
    orientation == FoldingFeature.Orientation.HORIZONTAL

private fun FoldingFeature.isBookPosture(): Boolean =
  state == FoldingFeature.State.HALF_OPENED &&
    orientation == FoldingFeature.Orientation.VERTICAL
