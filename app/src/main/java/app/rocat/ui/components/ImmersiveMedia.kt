package app.rocat.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Shared helpers for the immersive full-screen media viewers (Tahap 31). Both the image
 * and the video full-screen dialogs reuse these so the system bars are hidden (with
 * swipe-to-reveal) on the dialog window and, as a fallback, the hosting activity window —
 * giving a true edge-to-edge, non-"flat" playback/preview experience.
 */

/** Walks up the [ContextWrapper] chain to find the hosting [Activity]. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Hides the system bars on [window], allowing a swipe to temporarily reveal them. */
internal fun Window.hideSystemBars() {
    WindowCompat.getInsetsController(this, decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

/** Restores the system bars on [window]. */
internal fun Window.showSystemBars() {
    WindowCompat.getInsetsController(this, decorView).apply {
        show(WindowInsetsCompat.Type.systemBars())
    }
}