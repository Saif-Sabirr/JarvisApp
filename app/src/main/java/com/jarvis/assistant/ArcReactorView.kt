package com.jarvis.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * A small living HUD icon in the style of an Iron Man arc reactor:
 *  - an outer ring of tick marks that slowly rotates
 *  - a middle ring that spins the opposite way, faster while listening
 *  - a pulsing glowing core, color-coded by state
 *
 * Used both as the collapsed floating icon and the expanded panel's orb.
 */
class ArcReactorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { IDLE, LISTENING, SPEAKING }

    private var state = State.IDLE
    private var outerAngle = 0f
    private var innerAngle = 0f
    private var pulse = 0f

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; strokeCap = Paint.Cap.ROUND }
    private val midRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val outerAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 9000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { outerAngle = it.animatedValue as Float; invalidate() }
    }
    private val innerAnimator = ValueAnimator.ofFloat(360f, 0f).apply {
        duration = 4000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { innerAngle = it.animatedValue as Float; invalidate() }
    }
    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
        duration = 1200; repeatCount = ValueAnimator.INFINITE
        addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
    }

    init {
        outerAnimator.start(); innerAnimator.start(); pulseAnimator.start()
    }

    fun setState(newState: State) {
        state = newState
        innerAnimator.duration = if (newState == State.LISTENING) 1400 else 4000
        pulseAnimator.duration = if (newState == State.IDLE) 2200 else 700
        invalidate()
    }

    private fun stateColor(): Int = when (state) {
        State.IDLE -> Color.parseColor("#4FD8FF")
        State.LISTENING -> Color.parseColor("#FFB454")
        State.SPEAKING -> Color.parseColor("#7CFFB2")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - 6f
        if (radius <= 0) return

        val color = stateColor()
        outerRingPaint.color = ColorUtils_alpha(color, 140)
        midRingPaint.color = ColorUtils_alpha(color, 200)
        tickPaint.color = color

        // Outer rotating tick ring
        canvas.save()
        canvas.rotate(outerAngle, cx, cy)
        canvas.drawCircle(cx, cy, radius, outerRingPaint)
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30).toDouble())
            val x1 = cx + (radius - 2) * Math.cos(a).toFloat()
            val y1 = cy + (radius - 2) * Math.sin(a).toFloat()
            val x2 = cx + (radius - 9) * Math.cos(a).toFloat()
            val y2 = cy + (radius - 9) * Math.sin(a).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }
        canvas.restore()

        // Middle counter-rotating ring, spins faster while listening
        canvas.save()
        canvas.rotate(innerAngle, cx, cy)
        val midRadius = radius * 0.68f
        canvas.drawArc(cx - midRadius, cy - midRadius, cx + midRadius, cy + midRadius, 0f, 260f, false, midRingPaint)
        canvas.restore()

        // Pulsing glowing core
        val coreRadius = radius * (0.34f + pulse * 0.06f)
        coreGlowPaint.shader = RadialGradient(
            cx, cy, coreRadius * 2.2f,
            intArrayOf(ColorUtils_alpha(color, 90), ColorUtils_alpha(color, 0)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius * 2.2f, coreGlowPaint)

        corePaint.shader = RadialGradient(
            cx - coreRadius * 0.3f, cy - coreRadius * 0.3f, coreRadius * 1.4f,
            intArrayOf(Color.WHITE, color, ColorUtils_alpha(color, 200)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius, corePaint)
    }

    private fun ColorUtils_alpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    override fun onDetachedFromWindow() {
        outerAnimator.cancel(); innerAnimator.cancel(); pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }
}
