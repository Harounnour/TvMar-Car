package com.tvmar.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

/** Dégradé nuit bleu-vert + motif géométrique islamique (étoile à 8 branches) en filigrane doré. */
class PatternBackground(ctx: Context) : View(ctx) {

    private val density = resources.displayMetrics.density
    private val bg = Paint()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#26F5C542")
        strokeWidth = 1.2f * density
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        bg.shader = LinearGradient(
            0f, 0f, w * 0.4f, h.toFloat(),
            intArrayOf(Color.parseColor("#071225"), Color.parseColor("#0C2B44"), Color.parseColor("#0A3A3A")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(c: Canvas) {
        c.drawPaint(bg)
        val cell = 88f * density
        val r = cell * 0.30f
        var y = 0f
        while (y < height + cell) {
            var x = 0f
            while (x < width + cell) {
                c.save()
                c.translate(x, y)
                c.drawRect(-r, -r, r, r, line)
                c.rotate(45f)
                c.drawRect(-r, -r, r, r, line)
                c.restore()
                c.drawCircle(x, y, r * 0.38f, line)
                x += cell
            }
            y += cell
        }
    }
}
