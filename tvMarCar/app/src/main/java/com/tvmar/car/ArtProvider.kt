package com.tvmar.car

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/**
 * Génère les images (pochettes) affichées par Android Auto : dégradé nuit + étoile dorée + symbole.
 * URI : content://com.tvmar.car.art/<type>  avec type = quran | qradio | mradio | letter | reciter | sura
 */
class ArtProvider : ContentProvider() {

    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/png"
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0

    @Synchronized
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val kind = (uri.lastPathSegment ?: "quran").filter { it.isLetter() }.ifEmpty { "quran" }
        val f = File(ctx.cacheDir, "art_$kind.png")
        if (!f.exists() || f.length() == 0L) {
            val tmp = File(ctx.cacheDir, "art_$kind.tmp")
            val bmp = render(kind)
            FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            tmp.renameTo(f)
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun render(kind: String): Bitmap {
        val s = 512
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (c1, c2) = when (kind) {
            "qradio" -> "#0A3A3A" to "#071225"
            "mradio" -> "#3A1A0A" to "#071225"
            "letter" -> "#0C2B44" to "#071225"
            "reciter" -> "#14304A" to "#0A1A33"
            "sura" -> "#0A3A3A" to "#0C2B44"
            else -> "#0C2B44" to "#071225"
        }
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, s * 0.6f, s.toFloat(), Color.parseColor(c1), Color.parseColor(c2), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), bg)

        val gold = Color.parseColor("#F5C542")
        val faint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.argb(70, 245, 197, 66); strokeWidth = 3f
        }
        // Motif discret en filigrane
        val cell = 128f
        var y = 0f
        while (y < s + cell) {
            var x = 0f
            while (x < s + cell) {
                c.save(); c.translate(x, y)
                c.drawRect(-38f, -38f, 38f, 38f, faint)
                c.rotate(45f); c.drawRect(-38f, -38f, 38f, 38f, faint)
                c.restore()
                x += cell
            }
            y += cell
        }

        // Étoile à 8 branches centrale
        val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = gold; strokeWidth = 8f }
        c.save(); c.translate(s / 2f, s / 2f)
        c.drawRect(-150f, -150f, 150f, 150f, star)
        c.rotate(45f); c.drawRect(-150f, -150f, 150f, 150f, star)
        c.restore()

        // Symbole central
        if (kind == "qradio" || kind == "mradio") {
            val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; color = gold; strokeWidth = 14f; strokeCap = Paint.Cap.ROUND
            }
            val cx = s / 2f; val cy = s / 2f
            c.drawCircle(cx, cy, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
            for (r in listOf(55f, 95f)) {
                c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -45f, 90f, false, wave)
                c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 135f, 90f, false, wave)
            }
        } else {
            val glyph = if (kind == "sura") "۞" else "ق"
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = gold; textSize = 190f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            }
            c.drawText(glyph, s / 2f, s / 2f + 65f, t)
        }
        return bmp
    }

    companion object {
        const val AUTH = "com.tvmar.car.art"
        fun uri(kind: String): Uri = Uri.parse("content://$AUTH/$kind")
    }
}
