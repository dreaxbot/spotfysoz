package com.sozler.spotify

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock

data class Track(
    val ok: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val playing: Boolean = false,
    val base: Double = 0.0,      // sn, poll anındaki konum
    val t: Long = 0L,            // poll anı (elapsedRealtime ms)
    val speed: Float = 1f,
    val duration: Double = 0.0   // sn
)

object SpotifyReader {
    @Volatile var state = Track()

    fun poll(ctx: Context) {
        try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(ComponentName(ctx, NotifListener::class.java))
            val c = sessions.firstOrNull { it.packageName.contains("spotify", ignoreCase = true) }
            val md = c?.metadata
            val ps = c?.playbackState
            if (c == null || md == null || ps == null) {
                state = Track(); return
            }
            val now = SystemClock.elapsedRealtime()
            val playing = ps.state == PlaybackState.STATE_PLAYING
            var pos = ps.position / 1000.0
            if (playing) {
                val el = (now - ps.lastPositionUpdateTime).coerceAtLeast(0L)
                pos += el * ps.playbackSpeed / 1000.0
            }
            state = Track(
                ok = true,
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                album = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
                playing = playing,
                base = pos,
                t = now,
                speed = if (ps.playbackSpeed == 0f) 1f else ps.playbackSpeed,
                duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000.0
            )
        } catch (e: Exception) {
            // izin yok / oturum yok
            state = Track()
        }
    }
}
