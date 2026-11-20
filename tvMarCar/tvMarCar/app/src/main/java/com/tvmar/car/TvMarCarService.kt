package com.tvmar.car

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executors

class TvMarCarService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private val io = Executors.newCachedThreadPool()

    override fun onCreate() {
        super.onCreate()
        Repo.init(applicationContext)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        io.shutdown()
        super.onDestroy()
    }

    private fun <T> bg(block: () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        io.execute {
            try { f.set(block()) } catch (e: Throwable) { f.setException(e) }
        }
        return f
    }

    // ---------- Éléments ----------

    private fun folder(id: String, title: String, subtitle: String? = null, art: String = "quran") = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(ArtProvider.uri(art))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        ).build()

    private fun suraItem(r: Repo.Reciter, m: Repo.Moshaf, n: Int) = MediaItem.Builder()
        .setMediaId("s:${m.id}:$n")
        .setUri(Catalog.suraUrl(m.server, n))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("$n. سورة ${Catalog.suras[n - 1]}")
                .setArtist(r.name)
                .setAlbumTitle(m.name)
                .setArtworkUri(ArtProvider.uri("sura"))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        ).build()

    private fun radioItem(prefixId: String, r: Repo.Radio, fallbackArt: String) = MediaItem.Builder()
        .setMediaId(prefixId)
        .setUri(r.url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(r.name)
                .setArtist("بث مباشر")
                .setArtworkUri(r.icon?.let { Uri.parse(it) } ?: ArtProvider.uri(fallbackArt))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .build()
        ).build()

    private fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> listOf(
            folder(QURAN, "القرآن الكريم", "جميع القرّاء والروايات", "quran"),
            folder(QRADIO, "إذاعات القرآن الكريم", "بث مباشر", "qradio"),
            folder(MRADIO, "الإذاعات المغربية", "بث مباشر", "mradio")
        )
        parentId == QURAN -> Repo.lettersSorted().map { folder("letter:$it", "حرف $it", null, "letter") }
        parentId.startsWith("letter:") ->
            Repo.recitersOfLetter(parentId.removePrefix("letter:")).map { folder("rec:${it.id}", it.name, null, "reciter") }
        parentId.startsWith("rec:") -> {
            val r = parentId.removePrefix("rec:").toIntOrNull()?.let { Repo.reciter(it) }
            when {
                r == null -> emptyList()
                r.moshafs.size == 1 -> r.moshafs[0].surahs.map { suraItem(r, r.moshafs[0], it) }
                else -> r.moshafs.map { folder("mo:${it.id}", it.name, r.name, "reciter") }
            }
        }
        parentId.startsWith("mo:") -> {
            val p = parentId.removePrefix("mo:").toIntOrNull()?.let { Repo.moshaf(it) }
            if (p == null) emptyList() else p.second.surahs.map { suraItem(p.first, p.second, it) }
        }
        parentId == QRADIO -> Repo.getQuranRadios().map { radioItem("qr:${it.id}", it, "qradio") }
        parentId == MRADIO -> Repo.getMoroccanRadios().map { radioItem("mr:${it.id}", it, "mradio") }
        else -> emptyList()
    }

    private fun resolve(id: String): MediaItem? = when {
        id == ROOT -> folder(ROOT, "tvMar Car")
        id == QURAN -> folder(QURAN, "القرآن الكريم", null, "quran")
        id == QRADIO -> folder(QRADIO, "إذاعات القرآن الكريم", null, "qradio")
        id == MRADIO -> folder(MRADIO, "الإذاعات المغربية", null, "mradio")
        id.startsWith("letter:") -> folder(id, "حرف ${id.removePrefix("letter:")}", null, "letter")
        id.startsWith("rec:") -> id.removePrefix("rec:").toIntOrNull()?.let { Repo.reciter(it) }
            ?.let { folder(id, it.name, null, "reciter") }
        id.startsWith("mo:") -> id.removePrefix("mo:").toIntOrNull()?.let { Repo.moshaf(it) }
            ?.let { folder(id, it.second.name, it.first.name, "reciter") }
        id.startsWith("qr:") -> Repo.radio(id.removePrefix("qr:"))?.let { radioItem(id, it, "qradio") }
        id.startsWith("mr:") -> Repo.radio(id.removePrefix("mr:"))?.let { radioItem(id, it, "mradio") }
        id.startsWith("s:") -> {
            val p = id.split(":")
            val mo = p.getOrNull(1)?.toIntOrNull()?.let { Repo.moshaf(it) }
            val n = p.getOrNull(2)?.toIntOrNull()
            if (mo != null && n != null && n in 1..114) suraItem(mo.first, mo.second, n) else null
        }
        else -> null
    }

    // ---------- Callback (ce que voient Android Auto et l'application) ----------

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "tvMar Car"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = bg {
            try {
                val all = children(parentId)
                val slice = if (pageSize > 0) all.drop(page * pageSize).take(pageSize) else all
                LibraryResult.ofItemList(ImmutableList.copyOf(slice), params)
            } catch (e: Exception) {
                LibraryResult.ofError<ImmutableList<MediaItem>>(LibraryResult.RESULT_ERROR_UNKNOWN)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = bg {
            val item = try { resolve(mediaId) } catch (e: Exception) { null }
            if (item != null) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = bg {
            mediaItems.map { resolve(it.mediaId) ?: it }.toMutableList()
        }

        // Une sourate choisie => on met toute la riwaya en file d'attente (enchaînement automatique).
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = bg {
            val first = mediaItems.singleOrNull()
            if (first != null && first.mediaId.startsWith("s:")) {
                val mo = first.mediaId.split(":").getOrNull(1)?.toIntOrNull()?.let { Repo.moshaf(it) }
                if (mo != null) {
                    val queue = mo.second.surahs.map { suraItem(mo.first, mo.second, it) }
                    val idx = queue.indexOfFirst { it.mediaId == first.mediaId }.coerceAtLeast(0)
                    return@bg MediaSession.MediaItemsWithStartPosition(queue, idx, startPositionMs)
                }
            }
            MediaSession.MediaItemsWithStartPosition(
                mediaItems.map { resolve(it.mediaId) ?: it }, startIndex, startPositionMs
            )
        }
    }

    companion object {
        const val ROOT = "root"
        private const val QURAN = "quran"
        private const val QRADIO = "qradio"
        private const val MRADIO = "mradio"
    }
}
