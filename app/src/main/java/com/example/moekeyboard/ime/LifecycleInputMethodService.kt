package com.example.moekeyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

abstract class LifecycleInputMethodService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    protected fun setupComposeTreeOwners(targetView: View) {
        window?.window?.decorView?.let { decorView ->
            if (decorView.findViewTreeLifecycleOwner() == null) {
                decorView.setViewTreeLifecycleOwner(this)
            }
            if (decorView.findViewTreeViewModelStoreOwner() == null) {
                decorView.setViewTreeViewModelStoreOwner(this)
            }
            if (decorView.findViewTreeSavedStateRegistryOwner() == null) {
                decorView.setViewTreeSavedStateRegistryOwner(this)
            }
        }

        if (targetView.findViewTreeLifecycleOwner() == null) {
            targetView.setViewTreeLifecycleOwner(this)
        }
        if (targetView.findViewTreeViewModelStoreOwner() == null) {
            targetView.setViewTreeViewModelStoreOwner(this)
        }
        if (targetView.findViewTreeSavedStateRegistryOwner() == null) {
            targetView.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
