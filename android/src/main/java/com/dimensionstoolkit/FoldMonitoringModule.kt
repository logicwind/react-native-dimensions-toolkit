package com.dimensionstoolkit
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

object LifecycleLogger {
  private const val TAG = "LifecycleLogger"

  fun log(message: String) {
    Log.d(TAG, message)
  }
}

class FoldMonitoringModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private var isMonitoring = false
  private var foldingObserver: DefaultLifecycleObserver? = null

  @ReactMethod
  fun startFoldingEventMonitoring(promise: Promise) {
    if (isMonitoring) {
      promise.reject("ALREADY_MONITORING", "Monitoring is already in progress.")
      return
    }

    val lifecycleOwner = currentActivity as? LifecycleOwner
    if (lifecycleOwner == null) {
      promise.reject("LIFECYCLE_ERROR", "Could not find a valid LifecycleOwner.")
      return
    }

    val foldingFeatureFlow: Flow<FoldingFeature?> = flow {
      WindowInfoTracker.getOrCreate(reactContext)
        .windowLayoutInfo(reactContext)
        .collect { layoutInfo ->
          val foldingFeature = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
          emit(foldingFeature)
        }
    }

    isMonitoring = true

    val mainHandler = Handler(Looper.getMainLooper())

    // Add a lifecycle observer to automatically start and stop monitoring on the main thread
    foldingObserver = object : DefaultLifecycleObserver {
      override fun onStart(owner: LifecycleOwner) {
        // Log a message when the lifecycle enters the onStart event
        LifecycleLogger.log("Lifecycle onStart event")

        // Your existing code to start monitoring goes here

        mainHandler.post {
          lifecycleOwner.lifecycleScope.launch {
            foldingFeatureFlow
              .filterNotNull()
              .collect { foldingFeature ->
                Log.d(foldingFeature.toString(), "foldingFeature:>>")

                val foldType = when {
                  foldingFeature.isTableTop() -> "Table Top"
                  foldingFeature.isBookPosture() -> "Book Posture"
                  else -> "Normal Posture"
                }

                Log.d(foldType.toString(), "foldType:>>")

                val event = Arguments.createMap()
                event.putString("foldType", foldType)
                reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                  .emit("onFold", event)
              }
          }
        }
      }

      override fun onStop(owner: LifecycleOwner) {
        // Log a message when the lifecycle enters the onStop event
        LifecycleLogger.log("Lifecycle onStop event")

        // Your existing code to stop monitoring goes here
      }
    }

    mainHandler.post {
      lifecycleOwner.lifecycle.addObserver(foldingObserver as DefaultLifecycleObserver)
    }

    promise.resolve("Folding event monitoring started.")
  }

  @ReactMethod
  fun stopFoldingEventMonitoring(promise: Promise) {
    if (!isMonitoring) {
      promise.reject("NOT_MONITORING", "Monitoring is not in progress.")
      return
    }

    val lifecycleOwner = currentActivity as? LifecycleOwner
    if (lifecycleOwner != null && foldingObserver != null) {
      val mainHandler = Handler(Looper.getMainLooper())
      mainHandler.post {
        lifecycleOwner.lifecycle.removeObserver(foldingObserver!!)
      }
    }

    isMonitoring = false
    promise.resolve("Folding event monitoring stopped.")
  }

  override fun getName(): String {
    return "FoldMonitoringKit"
  }

  private val mainHandler = Handler(Looper.getMainLooper())

  private fun FoldingFeature.isTableTop(): Boolean =
    state == FoldingFeature.State.HALF_OPENED && orientation == FoldingFeature.Orientation.HORIZONTAL

  private fun FoldingFeature.isBookPosture(): Boolean =
    state == FoldingFeature.State.HALF_OPENED && orientation == FoldingFeature.Orientation.VERTICAL
}
