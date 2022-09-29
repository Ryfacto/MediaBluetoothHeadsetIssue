package fr.ryfacto.mediabluetoothheadsetissue

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.RemoteException
import android.speech.tts.TextToSpeech
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import java.util.*

class AudioMenuController(
    private val context: Context
) {
    private var mediaBrowser: MediaBrowserCompat? = null
    private var mediaController: MediaControllerCompat? = null
    private var currentPlaybackState = 0
    private lateinit var mediaBrowserConnectionCallback: MediaBrowserCompat.ConnectionCallback
    private lateinit var mediaControllerCallback: MediaControllerCompat.Callback

    private val textToSpeech by lazy {
        TextToSpeech(context) {
            if (it != TextToSpeech.ERROR) {
                Log.e("AudioMenuController", "Did fail to initialize TTS.")
            }
        }
    }

    fun activate() {
        textToSpeech.language = Locale.ENGLISH
        mediaController?.transportControls?.play()
        currentPlaybackState = PlaybackStateCompat.STATE_PLAYING
        mediaBrowser?.connect()
    }

    fun deactivate() {
        mediaController?.unregisterCallback(mediaControllerCallback)
        currentPlaybackState = PlaybackStateCompat.STATE_STOPPED
        mediaController?.transportControls?.stop()
        mediaBrowser?.disconnect()
    }

    fun attachTo(activity: Activity) {
        mediaControllerCallback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                super.onPlaybackStateChanged(state)

                if (state == null) {
                    return
                }

                currentPlaybackState = state.state
            }

            override fun onSessionEvent(event: String?, extras: Bundle?) {
                when (event?.let(AudioMenuMediaSessionEvents::valueOf)) {
                    AudioMenuMediaSessionEvents.TRIGGER -> trigger()
                    AudioMenuMediaSessionEvents.INTERRUPTION_BEGAN -> stopSpeaking()
                    AudioMenuMediaSessionEvents.INTERRUPTION_ENDED_AND_SHOULD_RESUME -> trigger()
                    else -> {
                        /* no-op */
                    }
                }
            }
        }

        mediaBrowserConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                super.onConnected()

                val mediaBrowser = mediaBrowser ?: return

                try {
                    mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken)
                    mediaController?.registerCallback(mediaControllerCallback)
                    MediaControllerCompat.setMediaController(activity, mediaController)
                    activity.mediaController.transportControls.play()
                    if (currentPlaybackState == PlaybackStateCompat.STATE_PLAYING) {
                        audioSessionActivated()
                    }
                } catch (e: RemoteException) {
                    Log.e("MainApp", "onConnected() failed. $e")
                }
            }
        }

        mediaBrowser = MediaBrowserCompat(activity, ComponentName(activity, BackgroundAudioService::class.java), mediaBrowserConnectionCallback, activity.intent.extras)
    }

    private fun audioSessionActivated() {
        say("Audio Menu has been activated.")
    }

    private fun trigger() {
        if (textToSpeech.isSpeaking) {
            stopSpeaking()
        } else {
            say("Audio Menu has been triggered.")
        }
    }

    private fun stopSpeaking() {
        textToSpeech.stop()
    }

    private fun say(utterance: String) {
        textToSpeech.speak(utterance, TextToSpeech.QUEUE_ADD, null, utterance)
    }
}
