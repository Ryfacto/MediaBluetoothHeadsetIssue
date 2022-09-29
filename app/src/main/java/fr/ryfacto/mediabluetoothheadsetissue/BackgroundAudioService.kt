package fr.ryfacto.mediabluetoothheadsetissue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver

private const val EMPTY_MEDIA_ROOT_ID = "empty_root_id"
private const val NOTIFICATION_ID = 0x1b38

class BackgroundAudioService : MediaBrowserServiceCompat(), AudioManager.OnAudioFocusChangeListener {

    private var mediaSession: MediaSessionCompat? = null

    private val audioAttributes = AudioAttributes.Builder().apply {
        setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setUsage(AudioAttributes.USAGE_ASSISTANT)
        } else {
            setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        }
    }.build()

    private val audioFocusRequest by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(audioAttributes)
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
        } else {
            TODO("This must not be called on Android VERSION.SDK_INT < O")
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            sendAudioMenuMediaSessionEvent(AudioMenuMediaSessionEvents.INTERRUPTION_BEGAN)
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            super.onPlay()
            if (!successfullyRetrievedAudioFocus()) {
                return
            }

            initMediaSessionMetadata()
            mediaSession?.isActive = true
            setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING)
            showPlayingNotification()
        }

        override fun onPause() {
            super.onPause()

            setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            showPausedNotification()
        }

        override fun onStop() {
            super.onStop()

            stopForeground(true)
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
            val validKeyCodes = setOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK)
            if (keyEvent.action == KeyEvent.ACTION_DOWN && validKeyCodes.contains(keyEvent.keyCode)) {
                sendAudioMenuMediaSessionEvent(AudioMenuMediaSessionEvents.TRIGGER)
            }
            return true
        }
    }

    override fun onCreate() {
        super.onCreate()

        initMediaSession()
        initNoisyReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        sendAudioMenuMediaSessionEvent(AudioMenuMediaSessionEvents.INTERRUPTION_BEGAN)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        } else {
            audioManager.abandonAudioFocus(this)
        }
        unregisterReceiver(noisyReceiver)
        mediaSession?.release()
        hideNotification()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return BrowserRoot(EMPTY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(null)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                sendAudioMenuMediaSessionEvent(AudioMenuMediaSessionEvents.INTERRUPTION_BEGAN)
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                sendAudioMenuMediaSessionEvent(AudioMenuMediaSessionEvents.INTERRUPTION_ENDED_AND_SHOULD_RESUME)
            }
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(applicationContext, "MyMovEO.BackgroundAudioService").apply {
            setCallback(mediaSessionCallback)
        }
        sessionToken = mediaSession!!.sessionToken
    }

    private fun initNoisyReceiver() {
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    private fun initMediaSessionMetadata() {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Audio Menu")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "Audio Menu is activated")
            .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, 1)
            .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1)
        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun successfullyRetrievedAudioFocus(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result = audioManager.requestAudioFocus(audioFocusRequest)
        } else {
            result = audioManager.requestAudioFocus(this, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        }
        return result == AudioManager.AUDIOFOCUS_GAIN
    }

    private fun setMediaPlaybackState(state: Int) {
        val playbackStateBuilder = PlaybackStateCompat.Builder()
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PAUSE)
        } else {
            playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY)
        }
        playbackStateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
        mediaSession?.setPlaybackState(playbackStateBuilder.build())
    }

    private fun sendAudioMenuMediaSessionEvent(event: AudioMenuMediaSessionEvents) {
        mediaSession?.sendSessionEvent(event.toString(), null)
    }

    private fun showPlayingNotification() {
        mediaSession?.let { mediaSession ->
            val notification = MediaStyleHelper.from(this, mediaSession).apply {
                addAction(NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this@BackgroundAudioService, PlaybackStateCompat.ACTION_PLAY_PAUSE)
                ))
                setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0).setMediaSession(mediaSession.sessionToken))
                setSmallIcon(R.mipmap.ic_launcher)
                setOngoing(true)
            }.build()

            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, this.javaClass))
            this.startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showPausedNotification() {
        mediaSession?.let { mediaSession ->
            MediaStyleHelper.from(this, mediaSession).apply {
                addAction(NotificationCompat.Action(
                    android.R.drawable.ic_media_play,
                    "Play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this@BackgroundAudioService, PlaybackStateCompat.ACTION_PLAY_PAUSE)
                ))
                setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0).setMediaSession(mediaSession.sessionToken))
                setSmallIcon(R.mipmap.ic_launcher)
                NotificationManagerCompat.from(this@BackgroundAudioService).notify(NOTIFICATION_ID, build())
            }
        }
    }

    private fun hideNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }
}
