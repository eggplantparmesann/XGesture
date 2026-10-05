package com.slideindex.app.overlay

import android.util.DisplayMetrics
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 「两侧都是线」模式的命中几何（需要真实 android.graphics.Rect，故走 Robolectric）。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class FloatBallBothLinesHitRectTest {
  private val metrics = DisplayMetrics().apply {
    density = 3f
    widthPixels = 1080
    heightPixels = 2400
  }

  @Test
  fun bothLines_mode_uses_line_strip_as_ball_hit_rect_on_active_side() {
    val settings = settings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.BOTH_LINES,
        floatBallActiveSide = FloatBallSide.RIGHT,
        floatBallLineWidthFraction = 0.10f,
        floatBallLineHeightFraction = 0.20f,
        floatBallPositionYFraction = 0.5f,
      ),
    )
    val sceneState = FloatBallSceneState(settings)
    val strip = FloatBallLayout.lineStripBounds(
      settings,
      metrics,
      FloatBallSide.RIGHT,
      metrics.widthPixels,
      metrics.heightPixels,
    )
    val ballHit = sceneState.ballHitRect(
      settings,
      metrics,
      FloatBallSide.RIGHT,
      metrics.widthPixels,
      metrics.heightPixels,
    )

    assertTrue(FloatBallLayout.shouldShowLine(settings))
    assertEquals(strip.left, ballHit.left)
    assertEquals(strip.top, ballHit.top)
    assertEquals(strip.right, ballHit.right)
    assertEquals(strip.bottom, ballHit.bottom)
    // 两侧同规格：球侧命中区宽度 == 线条触发区宽度，且贴右边缘。
    assertEquals(1080, ballHit.right)
    assertTrue(ballHit.right - ballHit.left < FloatBallLayout.ballSizePx(settings, metrics.density))
  }

  @Test
  fun singleSide_mode_keeps_square_ball_hit_rect() {
    val settings = settings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.RIGHT,
        floatBallSizeDp = 48f,
      ),
    )
    val sceneState = FloatBallSceneState(settings)
    val ballHit = sceneState.ballHitRect(
      settings,
      metrics,
      FloatBallSide.RIGHT,
      metrics.widthPixels,
      metrics.heightPixels,
    )
    val ballSizePx = FloatBallLayout.ballSizePx(settings, metrics.density)

    assertEquals(ballSizePx, ballHit.right - ballHit.left)
    assertEquals(ballSizePx, ballHit.bottom - ballHit.top)
  }

  private fun settings(floatBall: FloatBallSettings) = AppSettings(
    freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
    floatBall = floatBall,
  )
}
