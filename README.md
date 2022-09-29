# Media Bluetooth Headset Issue

This project demonstrate an issue using MediaSession and trying to capture play/pause headset button.

It works fine using wired headsets but not using a bluetooth headset.

## Classes

- `AudioMenuController` manages the connection to the background audio service's media session.
- `BackgroundAudioService` manages the media session and the notification.

## Problem

`BackgroundAudioService` is not receiving any calls to `onMediaButtonEvent` when using a bluetooth headset.

I guess I'm missing something because the sample project [UAMP](https://github.com/android/uamp) works fine.

Question is : what am I missing ?

I suppose it is the configuration of the media session, notification, manifest or permission but I cannot figure it out.
