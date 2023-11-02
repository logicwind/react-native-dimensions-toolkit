package com.dimensionstoolkit

import androidx.lifecycle.*
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.modules.core.DeviceEventManagerModule

class FoldMonitoringModule(private val reactContext: ReactApplicationContext):
  ReactContextBaseJavaModule(reactContext)  {

  data class FoldingEvent(val displayStatus: String, val foldType: String)

  private val foldingEvent = MutableLiveData<FoldingEvent>()

  fun startFoldingEventMonitoring(lifecycleOwner: LifecycleOwner) {
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

          foldingEvent.value = event
        }
    }

    foldingEvent.observe(lifecycleOwner) { event ->
      // Handle the event, e.g., emit it to React Native
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("onFold", event)
    }
  }

  override fun getName(): String {
    TODO("Not yet implemented")
  }
}

private fun FoldingFeature.isTableTop() : Boolean =
  state == FoldingFeature.State.HALF_OPENED &&
    orientation == FoldingFeature.Orientation.HORIZONTAL

private fun FoldingFeature.isBookPosture() : Boolean =
  state == FoldingFeature.State.HALF_OPENED &&
    orientation == FoldingFeature.Orientation.VERTICAL





