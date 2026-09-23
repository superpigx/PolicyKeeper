package com.baodan.keeper.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.baodan.keeper.R
import com.baodan.keeper.util.Money

/** 轻量柱状图：只画柱体 + 数值 + 年份标签，无第三方依赖 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var labels: List<String> = emptyList()
    private var values: List<Double> = emptyList()
    private var highlightIndex: Int = -1

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barDimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    init {
        barPaint.color = ContextCompat.getColor(context, R.color.chart_bar)
        barDimPaint.color = ContextCompat.getColor(context, R.color.chart_bar_dim)
        setWillNotDraw(false)
    }

    fun setData(labels: List<String>, values: List<Double>, highlightIndex: Int) {
        this.labels = labels
        this.values = values
        this.highlightIndex = highlightIndex
        invalidate()
    }

    private fun dp(v: Float): Float = v * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty() || width == 0) return

        val padTop = dp(30f)
        val padBottom = dp(26f)
        val chartH = height - padTop - padBottom
        if (chartH <= 0) return

        val slot = width.toFloat() / values.size
        val barW = Math.min(slot * 0.46f, dp(48f))
        val maxV = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

        // 基线
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.chart_grid)
            strokeWidth = dp(1f)
        }
        canvas.drawLine(0f, padTop + chartH, width.toFloat(), padTop + chartH, base)

        for (i in values.indices) {
            val cx = slot * i + slot / 2f
            val h = (values[i] / maxV * chartH).toFloat().coerceAtLeast(dp(3f))
            val left = cx - barW / 2f
            val right = cx + barW / 2f
            val top = padTop + (chartH - h)
            val bottom = padTop + chartH

            val paint = if (highlightIndex < 0 || i == highlightIndex) barPaint else barDimPaint
            val radius = dp(6f)
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)

            // 数值
            val valueText = Money.short(values[i])
            canvas.drawText(valueText, cx, top - dp(7f), valuePaint)

            // 年份
            val label = labels.getOrNull(i).orEmpty()
            canvas.drawText(label, cx, height - dp(7f), labelPaint)
        }
    }
}
