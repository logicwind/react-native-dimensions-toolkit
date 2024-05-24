package com.dimensionstoolkit

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager


class DimensionsToolkitPackage : ReactPackage {
  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
    val modules = mutableListOf<NativeModule>()

    // Add your custom module to the list
    modules.add(FoldMonitoringModule(reactContext))
    // Add the DimensionsToolkitModule from the previous package
    modules.add(DimensionsToolkitModule(reactContext))

    return modules
  }

  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
    return emptyList()
  }
}
