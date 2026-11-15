/*
 * CastManager.kt
 * Implements the CastManager class
 * CastManager bridges Transistor's live radio streams to a Chromecast / Google Cast receiver
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.playback

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import org.y20k.transistor.Keys
import org.y20k.transistor.core.Station
import org.y20k.transistor.helpers.LogHelper
import java.util.Locale


/*
 * CastManager class
 *
 * Casting is deliberately independent from PlayerService: the receiver fetches the stream URL
 * itself, so the phone can go to sleep while a station keeps playing on the Chromecast. Local
 * playback is paused as soon as a session is connected.
 */
class CastManager private constructor() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CastManager::class.java)


    /* Listener interface for the UI */
    interface Listener {
        fun onCastStateChanged(connected: Boolean, deviceName: String?)
        fun onCastStarted(deviceName: String?)
        fun onCastPlaybackChanged(isPlaying: Boolean)
        fun onCastFailed()
    }


    /* Main class variables */
    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var castSession: CastSession? = null
    private var remoteMediaClientCallback: RemoteMediaClient.Callback? = null
    private val listeners: MutableList<Listener> = mutableListOf()
    private var available: Boolean = false


    /* Public read-only state */
    val isAvailable: Boolean
        get() = available

    val isConnected: Boolean
        get() = castSession != null

    val deviceName: String?
        get() = castSession?.castDevice?.friendlyName

    val isPlaying: Boolean
        get() = castSession?.remoteMediaClient?.isPlaying == true

    /** True while the receiver has a media item loaded (playing, paused or buffering). */
    val hasMedia: Boolean
        get() = castSession?.remoteMediaClient?.mediaStatus != null


    /* Initializes the Cast context - safe to call more than once */
    fun init(context: Context) {
        if (available) return

        try {
            val appContext = context.applicationContext
            val cast = CastContext.getSharedInstance(appContext)
            val manager = cast.sessionManager
            manager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
            castContext = cast
            sessionManager = manager
            available = true
            LogHelper.i(TAG, "Cast context initialized.")
        } catch (e: Exception) {
            // Play Services missing or Cast options not configured - casting stays unavailable
            available = false
            LogHelper.e(TAG, "Cast is not available on this device: ${e.message}")
        }
    }


    /* Registers a listener */
    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }


    /* Unregisters a listener */
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }


    /* Loads a station onto the connected receiver */
    fun play(station: Station): Boolean {
        val client = castSession?.remoteMediaClient ?: return false
        val streamUri = station.getStreamUri()
        if (streamUri.isEmpty()) return false

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        metadata.putString(MediaMetadata.KEY_TITLE, station.name)
        metadata.putString(MediaMetadata.KEY_ARTIST, LIVE_RADIO_LABEL)

        val artworkUri = artworkUriOf(station)
        if (artworkUri != null) metadata.addImage(WebImage(artworkUri))

        val mediaInfo = MediaInfo.Builder(streamUri)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(contentTypeOf(station))
            .setMetadata(metadata)
            .build()

        client.load(mediaInfo, true)
        LogHelper.i(TAG, "Casting ${station.name} (${mediaInfo.contentType}).")
        return true
    }


    /* Toggles playback on the receiver */
    fun togglePlayback() {
        val client = castSession?.remoteMediaClient ?: return
        when {
            client.isPlaying || client.isBuffering -> client.pause()
            else -> client.play()
        }
    }


    /* Pauses playback on the receiver */
    fun stop() {
        castSession?.remoteMediaClient?.pause()
    }


    /* Ends the current Cast session */
    fun disconnect() {
        sessionManager?.endCurrentSession(true)
    }


    /* Notifies all listeners about a connection change */
    private fun notifyConnectionChanged(freshlyStarted: Boolean = false) {
        val connected = isConnected
        val name = deviceName
        listeners.toList().forEach { it.onCastStateChanged(connected, name) }
        if (freshlyStarted && connected) {
            listeners.toList().forEach { it.onCastStarted(name) }
        }
    }


    /* Notifies all listeners about a playback change */
    private fun notifyPlaybackChanged() {
        val playing = isPlaying
        listeners.toList().forEach { it.onCastPlaybackChanged(playing) }
    }


    /* Creates a cast image URI only for absolute http(s) URLs */
    private fun artworkUriOf(station: Station): Uri? {
        val candidates = listOf(station.remoteImageLocation, station.image)
        for (candidate in candidates) {
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                return Uri.parse(candidate)
            }
        }
        return null
    }


    /* Session manager callbacks */
    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = attachSession(session, true)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attachSession(session, false)

        override fun onSessionEnded(session: CastSession, error: Int) = detachSession()
        override fun onSessionSuspended(session: CastSession, reason: Int) = detachSession()

        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            listeners.toList().forEach { it.onCastFailed() }
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
    }


    /* Attaches to a running session */
    private fun attachSession(session: CastSession, freshlyStarted: Boolean) {
        castSession = session

        val callback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() = notifyPlaybackChanged()
        }
        remoteMediaClientCallback = callback
        session.remoteMediaClient?.registerCallback(callback)

        notifyConnectionChanged(freshlyStarted)
    }


    /* Detaches from a session that ended or was suspended */
    private fun detachSession() {
        remoteMediaClientCallback?.let { castSession?.remoteMediaClient?.unregisterCallback(it) }
        remoteMediaClientCallback = null
        castSession = null
        notifyConnectionChanged()
    }


    /* Companion object: singleton and MIME type mapping */
    companion object {

        private const val LIVE_RADIO_LABEL = "Live radio"

        val instance: CastManager = CastManager()

        /**
         * The Default Media Receiver does not probe a stream, it trusts MediaInfo's content type.
         * Transistor detects most types already ([Station.streamContent]); fall back to the URL.
         */
        fun contentTypeOf(station: Station): String {
            val declared = station.streamContent.lowercase(Locale.ROOT)
            val path = station.getStreamUri().lowercase(Locale.ROOT).substringBefore('?')

            return when {
                path.endsWith(".m3u8") || declared.contains("mpegurl") || declared.contains("hls") ->
                    Keys.CAST_MIME_TYPE_HLS
                declared.contains("aac") || path.endsWith(".aac") ->
                    Keys.CAST_MIME_TYPE_AAC
                declared.contains("ogg") || declared.contains("opus") || declared.contains("vorbis") ||
                    path.endsWith(".ogg") || path.endsWith(".opus") ->
                    Keys.CAST_MIME_TYPE_OGG
                declared.contains("flac") || path.endsWith(".flac") ->
                    Keys.CAST_MIME_TYPE_FLAC
                else ->
                    Keys.CAST_MIME_TYPE_MPEG
            }
        }
    }
}
