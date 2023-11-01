package com.dimensionstoolkit

import android.content.Context.WINDOW_SERVICE
import android.graphics.Point
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.facebook.react.bridge.*
import java.lang.Exception

class DimensionsToolkitModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  private val density: Float by lazy { reactApplicationContext.resources.displayMetrics.density }

  override fun getName(): String {
    return NAME
  }

  @ReactMethod
  fun getScreenSize(promise: Promise) {

    promise.resolve(getScreenSizeMap())
  }

  private fun getScreenSizeMap(): WritableMap {

    // 跟 ios 保持一致，获取的是物理屏尺寸
    val screenSize = getRealScreenSize()

    val map = Arguments.createMap()
    map.putInt("width", (screenSize.x / density).toInt())
    map.putInt("height", (screenSize.y / density).toInt())

    return map
  }
  private fun getRealScreenSize(): Point {

    val windowManager = reactContext.getSystemService(WINDOW_SERVICE) as WindowManager
    val display = (reactContext.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay

    val size = Point()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val windowMetrics = windowManager.getMaximumWindowMetrics()
      val widthInDp = windowMetrics.getBounds().width()
      val heightInDp = windowMetrics.getBounds().height()
      size.x = widthInDp
      size.y = heightInDp
    } else
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      try {
        display.getRealSize(size)
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
    return size
  }

  private fun onStartFold(): Point {

    val deviceListeners = Point()

    lifecycleScope.launch(Dispatchers.Main){
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        WindowInfoTracker.getOrCreate(this@DimensionsToolkitModule)
          .windowLayoutInfo(this@DimensionsToolkitModule)
          .collect{layoutInfo->
            if (layoutInfo.displayFeatures.isNotEmpty()){
              deviceListeners.displayStatus = "Multiple displays"
            }else{
              deviceListeners.displayStatus = "Single display"
            }

            val foldingFeature = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
              .firstOrNull() ?: return@collect

            when{
              foldingFeature.isTableTop() -> deviceListeners.foldType ="Table Top"
              foldingFeature.isBookPosture() -> deviceListeners.foldType= "Book Posture"
              else -> deviceListeners.foldType="Normal Posture"
            }
          }
        // Get EventEmitter from context and send event thanks to it
        this.reactContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit("onFold", deviceListeners)
      }
    }

  }

  private fun FoldingFeature.isTableTop() : Boolean =
    state == FoldingFeature.State.HALF_OPENED &&
      orientation == FoldingFeature.Orientation.HORIZONTAL

  private fun FoldingFeature.isBookPosture() : Boolean =
    state == FoldingFeature.State.HALF_OPENED &&
      orientation == FoldingFeature.Orientation.VERTICAL



  companion object {
    const val NAME = "DimensionsToolkit"
  }
}
