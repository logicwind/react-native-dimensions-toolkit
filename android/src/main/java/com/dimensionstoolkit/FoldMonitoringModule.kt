import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class FoldMonitoringModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private var isMonitoring = false

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

    // Add a lifecycle observer to automatically start and stop monitoring
    val lifecycleObserver = object : DefaultLifecycleObserver {
      override fun onStart(owner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
          foldingFeatureFlow
            .filterNotNull()
            .collect { foldingFeature ->
              val foldType = when {
                foldingFeature.isTableTop() -> "Table Top"
                foldingFeature.isBookPosture() -> "Book Posture"
                else -> "Normal Posture"
              }
              val event = Arguments.createMap()
              event.putString("foldType", foldType)
              reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onFold", event)
            }
        }
      }
    }

    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

    promise.resolve("Folding event monitoring started.")
  }


  override fun getName(): String {
    return "FoldMonitoringKit"
  }

  private fun FoldingFeature.isTableTop(): Boolean =
    state == FoldingFeature.State.HALF_OPENED && orientation == FoldingFeature.Orientation.HORIZONTAL

  private fun FoldingFeature.isBookPosture(): Boolean =
    state == FoldingFeature.State.HALF_OPENED && orientation == FoldingFeature.Orientation.VERTICAL
}
