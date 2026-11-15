# Chromecast in this fork

This fork adds Google Cast (Chromecast, Chromecast Audio, Google/Nest displays and speakers) to
Transistor. Upstream has no Cast support.

## What it does

- A cast icon sits in the player sheet next to the play button. It opens the standard device
  chooser.
- When a device is connected, tuning a station sends the stream URL to the receiver and pauses
  local playback. The receiver fetches the stream itself, so the phone can sleep or leave the
  network.
- While connected, the play button controls the receiver instead of the local ExoPlayer.
- Ending the session returns control to the phone; the next tap starts local playback.
- Casting uses Google's **Default Media Receiver**, so no receiver app has to be hosted or
  registered.

## Why the content type matters

The Default Media Receiver does not probe a stream. It trusts the `contentType` in `MediaInfo`.
Sending the wrong type is the usual reason a radio station refuses to start on a Chromecast.
This fork maps the station to the type the receiver expects:

| Station | Content type |
| --- | --- |
| MP3 / MPEG | `audio/mpeg` |
| AAC, AAC+ | `audio/aac` |
| OGG, Opus, Vorbis | `audio/ogg` |
| FLAC | `audio/flac` |
| HLS (`*.m3u8`, `application/vnd.apple.mpegurl*`) | `application/x-mpegurl` |

Transistor already detects most of these in `Station.streamContent`; the mapping falls back to the
stream URL. The mapping is covered by `CastManagerTest`.

## Building

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Requirements: JDK 17, Android SDK platform 32 or newer.

## Limitations

- The Cast SDK needs Google Play Services. This build therefore cannot be published on F-Droid;
  it is a personal fork, not a replacement for the upstream app.
- The receiver shows station name and artwork, but no ICY "now playing" titles. Live metadata
  would require a custom receiver app.
- Streams that require custom HTTP headers or a browser user agent cannot be cast; the receiver
  requests the URL on its own.
- Some broadcasters only serve HTTPS to Cast devices. Those stations fail on the receiver even
  though they play locally.

## Files

- `app/src/main/java/org/y20k/transistor/playback/CastManager.kt` – session handling, loading,
  MIME mapping
- `app/src/main/java/org/y20k/transistor/playback/CastOptionsProvider.kt` – Cast options, Default
  Media Receiver
- `app/src/main/java/org/y20k/transistor/PlayerFragment.kt` – cast button, play/pause routing
- `app/src/main/res/layout/bottom_sheet_playback_controls.xml` – cast button in the player sheet
