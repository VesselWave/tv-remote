package lean.tvremote

import android.content.Context
import android.view.ViewGroup.LayoutParams
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView

/** Preserve the original frame-batched pointer acceleration and scroll-strip gesture locking. */
class TvTouchpadView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  override val shouldUseAndroidLayout = true
  private var natural = true
  private var pad: Touchpad? = null
  init { install() }
  fun setNaturalScrolling(value: Boolean) {
    if (value != natural) { natural = value; install() }
  }
  private fun install() {
    removeAllViews()
    pad = Touchpad(context, 0xFF1E222A.toInt(), 0xFF8A93A6.toInt(), natural, object : Touchpad.Pointer {
      override fun move(dx: Int, dy: Int) { TvRemoteModule.pointer { it.move(dx, dy) } }
      override fun click() { TvRemoteModule.pointer { it.click() } }
      override fun scroll(dx: Int, dy: Int) { TvRemoteModule.pointer { it.scroll(dx, dy) } }
    })
    addView(pad, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
  }
  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    pad?.layout(0, 0, right - left, bottom - top)
  }
}
