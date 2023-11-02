import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.facebook.react.bridge.*

import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import kotlinx.coroutines.flow.flow


class FoldMonitoringModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  data class FoldingEvent(val displayStatus: String, val foldType: String)

  private val foldingEvent = MutableLiveData<FoldingEvent>()

  @ReactMethod
  fun startFoldingEventMonitoring(promise: Promise) {
    val lifecycleOwner = currentActivity as? LifecycleOwner
    if (lifecycleOwner == null) {
      promise.reject("LIFECYCLE_ERROR", "Could not find a valid LifecycleOwner.")
      return
    }

    val lifecycleScope = lifecycleOwner.lifecycleScope

    lifecycleScope.launchWhenStarted {
      try {
        val event = monitorFoldingEvents(lifecycleOwner)
        promise.resolve(event.toWritableMap())
      } catch (e: Exception) {
        promise.reject("MONITORING_ERROR", e.message, e)
      }
    }
  }


  private suspend fun monitorFoldingEvents(lifecycleOwner: LifecycleOwner): FoldingEvent {
    return suspendCancellableCoroutine { continuation ->
      val foldingFeatureFlow = flow {
        WindowInfoTracker.getOrCreate(reactContext)
          .windowLayoutInfo(reactContext)
          .collect() { layoutInfo ->
            val event = FoldingEvent(
              displayStatus = if (layoutInfo.displayFeatures.isNotEmpty()) "Multiple displays" else "Single display",
              foldType = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
                .firstOrNull { it.isTableTop() }?.let { "Table Top" }
                ?: layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
                  .firstOrNull { it.isBookPosture() }?.let { "Book Posture" }
                ?: "Normal Posture"
            )
            emit(event)
          }
      }

      val job = lifecycleOwner.lifecycleScope.launch {
        try {
          foldingFeatureFlow.collect { event ->
            continuation.resume(event)
          }
        } catch (e: Exception) {
          continuation.resumeWithException(e)
        }
      }

      continuation.invokeOnCancellation {
        job.cancel()
      }
    }
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
