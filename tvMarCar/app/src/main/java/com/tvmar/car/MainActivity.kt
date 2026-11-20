package com.tvmar.car

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import java.util.concurrent.Executor

class MainActivity : Activity() {

    private val ui = Executor { Handler(Looper.getMainLooper()).post(it) }
    private var browser: MediaBrowser? = null

    private val stack = ArrayList<Pair<String, String>>()
    private var curId = TvMarCarService.ROOT
    private var curTitle = "tvMar Car"
    private var shown: List<MediaItem> = emptyList()
    private var all: List<MediaItem> = emptyList()
    private var loadToken = 0

    private lateinit var titleView: TextView
    private lateinit var backBtn: TextView
    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var list: ListView
    private lateinit var miniTitle: TextView
    private lateinit var miniSub: TextView
    private lateinit var playBtn: TextView

    private val gold = Color.parseColor("#F5C542")
    private val adapter = RowAdapter()

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun round(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        cornerRadius = radiusDp.dp().toFloat()
        setColor(color)
        if (strokeColor != null) setStroke(1.dp(), strokeColor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#071225")
        window.navigationBarColor = Color.parseColor("#0A3A3A")
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = FrameLayout(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.addView(PatternBackground(this), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
        }

        // En-tête
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleView = TextView(this).apply {
            setTextColor(gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        backBtn = TextView(this).apply {
            text = "رجوع"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
            background = round(Color.parseColor("#26FFFFFF"), 18)
            visibility = View.GONE
            setOnClickListener { goBack() }
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(backBtn)
        col.addView(header)

        // Recherche
        search = EditText(this).apply {
            hint = "ابحث…"; setHintTextColor(Color.parseColor("#99FFFFFF")); setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); isSingleLine = true
            background = round(Color.parseColor("#1FFFFFFF"), 14)
            setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp())
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = applyFilter()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        col.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 12.dp(); bottomMargin = 8.dp()
        })

        // Liste
        status = TextView(this).apply {
            setTextColor(Color.parseColor("#CCFFFFFF")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER; setPadding(0, 24.dp(), 0, 24.dp()); visibility = View.GONE
        }
        col.addView(status)
        list = ListView(this).apply {
            divider = ColorDrawable(Color.TRANSPARENT); dividerHeight = 8.dp()
            selector = ColorDrawable(Color.TRANSPARENT); cacheColorHint = Color.TRANSPARENT
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> onItem(shown[pos]) }
        }
        col.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Mini-lecteur
        miniTitle = TextView(this).apply {
            text = "اختر تلاوة أو إذاعة"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        miniSub = TextView(this).apply {
            setTextColor(Color.parseColor("#B3FFFFFF")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(miniTitle); addView(miniSub)
        }
        fun ctl(sym: String, size: Float, action: () -> Unit) = TextView(this).apply {
            text = sym; setTextColor(gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setPadding(12.dp(), 6.dp(), 12.dp(), 6.dp()); setOnClickListener { action() }
        }
        playBtn = ctl("▶", 28f) { browser?.let { if (it.isPlaying) it.pause() else it.play() } }
        val mini = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 10.dp(), 8.dp(), 10.dp())
            background = round(Color.parseColor("#2E0F2A44"), 22, Color.parseColor("#55F5C542"))
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ctl("⏮", 22f) { browser?.seekToPreviousMediaItem() })
            addView(playBtn)
            addView(ctl("⏭", 22f) { browser?.seekToNextMediaItem() })
        }
        col.addView(mini, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 10.dp()
        })

        root.addView(col, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)

        titleView.text = curTitle
        showStatus("جارٍ الاتصال…")
        connect()
    }

    private fun connect() {
        val token = SessionToken(this, ComponentName(this, TvMarCarService::class.java))
        val f = MediaBrowser.Builder(this, token).buildAsync()
        f.addListener({
            try {
                val b = f.get()
                b.addListener(playerListener)
                browser = b
                updateMini()
                load(TvMarCarService.ROOT, "tvMar Car")
            } catch (e: Exception) {
                showStatus("تعذّر الاتصال بخدمة التشغيل")
            }
        }, ui)
    }

    // ---------- Navigation ----------

    private fun onItem(item: MediaItem) {
        if (item.mediaMetadata.isBrowsable == true) {
            stack.add(curId to curTitle)
            load(item.mediaId, item.mediaMetadata.title?.toString() ?: "")
        } else {
            browser?.run { setMediaItem(item); prepare(); play() }
        }
    }

    private fun goBack() {
        if (stack.isNotEmpty()) {
            val (id, t) = stack.removeAt(stack.size - 1)
            load(id, t)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.isNotEmpty()) goBack() else super.onBackPressed()
    }

    private fun load(id: String, title: String) {
        val b = browser ?: return
        curId = id; curTitle = title
        titleView.text = title
        backBtn.visibility = if (stack.isEmpty()) View.GONE else View.VISIBLE
        search.setText("")
        all = emptyList(); shown = emptyList(); adapter.notifyDataSetChanged()
        showStatus("جارٍ التحميل…")
        val token = ++loadToken
        val acc = ArrayList<MediaItem>()
        fun page(p: Int) {
            val f = b.getChildren(id, p, PAGE, null)
            f.addListener({
                if (token != loadToken) return@addListener
                val r = try { f.get() } catch (e: Exception) { null }
                if (r == null || r.resultCode != LibraryResult.RESULT_SUCCESS) {
                    showStatus("تعذّر التحميل — تحقق من الاتصال بالإنترنت"); return@addListener
                }
                val items = r.value ?: emptyList<MediaItem>()
                acc.addAll(items)
                if (items.size >= PAGE) page(p + 1)
                else {
                    all = acc
                    if (all.isEmpty()) showStatus("لا توجد نتائج") else { status.visibility = View.GONE; applyFilter() }
                }
            }, ui)
        }
        page(0)
    }

    private fun showStatus(msg: String) { status.text = msg; status.visibility = View.VISIBLE }

    private fun norm(s: String) = s
        .replace(Regex("[\\u064B-\\u0652\\u0640]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي').lowercase()

    private fun applyFilter() {
        val q = norm(search.text.toString().trim())
        shown = if (q.isEmpty()) all else all.filter {
            norm((it.mediaMetadata.title ?: "").toString() + " " + (it.mediaMetadata.artist ?: "")).contains(q)
        }
        adapter.notifyDataSetChanged()
    }

    // ---------- Mini-lecteur ----------

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updateMini()
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateMini()
        override fun onPlaybackStateChanged(playbackState: Int) = updateMini()
    }

    private fun updateMini() {
        val b = browser ?: return
        val md = b.mediaMetadata
        miniTitle.text = md.title ?: "اختر تلاوة أو إذاعة"
        miniSub.text = if (b.playbackState == Player.STATE_BUFFERING) "جارٍ التحميل…" else (md.artist ?: "")
        playBtn.text = if (b.isPlaying) "❚❚" else "▶"
    }

    override fun onDestroy() {
        browser?.release()
        browser = null
        super.onDestroy()
    }

    // ---------- Lignes de la liste ----------

    private inner class RowAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val ctx: Context = this@MainActivity
            val row = (convertView as? LinearLayout) ?: LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
                background = round(Color.parseColor("#1AFFFFFF"), 16)
                val icon = TextView(ctx).apply {
                    id = 1; setTextColor(gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); gravity = Gravity.CENTER
                }
                addView(icon, LinearLayout.LayoutParams(36.dp(), ViewGroup.LayoutParams.WRAP_CONTENT))
                val t = TextView(ctx).apply {
                    id = 2; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                }
                val s = TextView(ctx).apply {
                    id = 3; setTextColor(Color.parseColor("#B3FFFFFF")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                }
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(t); addView(s) }
                addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val chev = TextView(ctx).apply {
                    id = 4; text = "‹"; setTextColor(Color.parseColor("#80FFFFFF")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                }
                addView(chev)
            }
            val item = shown[position]
            val md = item.mediaMetadata
            val browsable = md.isBrowsable == true
            row.findViewById<TextView>(1).text = when {
                browsable -> "❖"
                md.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION -> "◉"
                else -> "♪"
            }
            row.findViewById<TextView>(2).text = md.title ?: ""
            val sub = (md.subtitle ?: md.artist)?.toString().orEmpty()
            row.findViewById<TextView>(3).apply { text = sub; visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE }
            row.findViewById<TextView>(4).visibility = if (browsable) View.VISIBLE else View.GONE
            return row
        }
    }

    companion object {
        private const val PAGE = 100
    }
}
