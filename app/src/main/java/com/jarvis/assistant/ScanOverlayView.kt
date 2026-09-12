package com.jarvis.assistant

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Draws the Iron-Man-style HUD on top of the camera preview:
 *  - animated corner brackets + scan line around whatever object/barcode was detected
 *  - a fading glow trail connecting the last few fingertip positions when a hand is shown
 *
 * All coordinates are normalized (0f..1f) so this view doesn't need to know the
 * camera's raw resolution - callers just pass fractional positions.
 */
class ScanOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    var currentBox: NormRect? = null
        private set
    private var targetBox: NormRect?
        get() = currentBox
        set(value) { currentBox = value }
    private var targetLabel: String? = null
    private val fingerTrail = ArrayDeque<PointF>()
    private val maxTrailPoints = 18

    private var scanLinePhase = 0f

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FD8FF")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB454")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC0B121C")
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D6E8F5")
        textSize = 32f
        isFakeBoldText = true
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FD8FF")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val trailDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB454")
        style = Paint.Style.FILL
    }

    /** Call from the analyzer whenever a barcode/object bounding box updates (null = clear). */
    fun setTarget(box: NormRect?, label: String?) {
        targetBox = box
        targetLabel = label
        postInvalidateOnAnimation()
    }

    /** Call from the hand-landmark callback with the fingertip's normalized position (null = hand lost). */
    fun pushFingertip(point: PointF?) {
        if (point == null) {
            fingerTrail.clear()
        } else {
            fingerTrail.addLast(point)
            while (fingerTrail.size > maxTrailPoints) fingerTrail.removeFirst()
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        targetBox?.let { box ->
            drawBrackets(canvas, box.left * w, box.top * h, box.right * w, box.bottom * h)
            targetLabel?.let { drawLabel(canvas, it, box.left * w, box.bottom * h + 12f) }
        }

        if (fingerTrail.size >= 2) {
            val path = Path()
            var first = true
            for (p in fingerTrail) {
                val x = p.x * w; val y = p.y * h
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            }
            canvas.drawPath(path, trailPaint)
            val tip = fingerTrail.last()
            canvas.drawCircle(tip.x * w, tip.y * h, 14f, trailDotPaint)
        }

        if (targetBox != null) {
            scanLinePhase = (scanLinePhase + 0.02f) % 1f
            postInvalidateOnAnimation()
        }
    }

    private fun drawBrackets(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val len = (r - l).coerceAtMost(b - t) * 0.22f
        // top-left
        canvas.drawLine(l, t, l + len, t, bracketPaint)
        canvas.drawLine(l, t, l, t + len, bracketPaint)
        // top-right
        canvas.drawLine(r, t, r - len, t, bracketPaint)
        canvas.drawLine(r, t, r, t + len, bracketPaint)
        // bottom-left
        canvas.drawLine(l, b, l + len, b, bracketPaint)
        canvas.drawLine(l, b, l, b - len, bracketPaint)
        // bottom-right
        canvas.drawLine(r, b, r - len, b, bracketPaint)
        canvas.drawLine(r, b, r, b - len, bracketPaint)
        // moving scan line
        val y = t + (b - t) * scanLinePhase
        canvas.drawLine(l, y, r, y, scanLinePaint)
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float) {
        val padding = 10f
        val textWidth = labelTextPaint.measureText(text)
        canvas.drawRect(x, y, x + textWidth + padding * 2, y + 44f, labelBgPaint)
        canvas.drawText(text, x + padding, y + 32f, labelTextPaint)
    }
}
