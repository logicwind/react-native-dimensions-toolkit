package com.dimensionstoolkit

import androidx.lifecycle.*
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

class FoldMonitoringModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  data class FoldingEvent(val displayStatus: String, val foldType: String)

  private val foldingEvent = MutableLiveData<FoldingEvent>()

  @ReactMethod
  fun startFoldingEventMonitoring() {
    val lifecycleOwner = currentActivity as? LifecycleOwner ?: return

    val lifecycleScope = lifecycleOwner.lifecycleScope

    lifecycleScope.launchWhenStarted {
      WindowInfoTracker.getOrCreate(reactContext)
        .windowLayoutInfo(reactContext)
        .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
        .collect { layoutInfo ->
          val event = FoldingEvent(
            displayStatus = if (layoutInfo.displayFeatures.isNotEmpty()) "Multiple displays" else "Single display",
            foldType = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
              .firstOrNull { it.isTableTop() }?.let { "Table Top" }
              ?: layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
                .firstOrNull { it.isBookPosture() }?.let { "Book Posture" }
              ?: "Normal Posture"
          )

          foldingEvent.postValue(event)
        }
    }

    foldingEvent.observe(lifecycleOwner) { event ->
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("onFold", Arguments.makeNativeMap(event.toWritableMap()))
    }
  }
  private fun FoldingEvent.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    map.putString("displayStatus", displayStatus)
    map.putString("foldType", foldType)
    return map
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
