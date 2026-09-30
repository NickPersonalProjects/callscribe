# CallScribe

CallScribe is a privacy-first Android phone-call recorder and on-device transcription app. It records carrier calls through Shizuku and transcribes them locally with sherpa-onnx and NVIDIA Parakeet. Audio and transcripts stay on the phone.

> [!IMPORTANT]
> **CallScribe is a fork of [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder).** It is not affiliated with or endorsed by the upstream project. ShizuCallRecorder's name, branding, and logos are not used by this fork.

## Planned features

- Non-root recording of incoming and outgoing carrier calls through Shizuku
- Fully local transcription with Parakeet TDT 0.6B v3 int8
- Searchable transcripts with timestamps and speaker labels
- Playback synchronized to transcript segments
- Recording import, retention rules, and TXT/Markdown/SRT export
- No analytics, advertising, or cloud speech service

## Current status

CallScribe is under active development. The upstream recording engine is present, but transcription and the calls library are not yet release-ready. Android 12 or newer is required. Device compatibility varies by OEM and Android version.

## Development

The project uses JDK 17, Android SDK 36, Kotlin, and Jetpack Compose.

```powershell
.\gradlew.bat :app:assembleDebug
```

See [UPSTREAM.md](UPSTREAM.md) for the fork strategy and [docs/SUPPORT.md](docs/SUPPORT.md) for Shizuku setup inherited from upstream.

## Privacy

Recordings and transcripts are intended to remain on-device. The app's internet access is reserved for explicit speech-model downloads. Recordings, transcripts, and downloaded models are excluded from Android cloud backup.

## Legal notice

Call recording laws vary by jurisdiction. You are responsible for obtaining any consent required by law. CallScribe does not inject an audible recording tone and cannot guarantee recording succeeds on every device or call.

## License

GPL-3.0-or-later, including the additional Section 7 terms in [LICENSE](LICENSE). Upstream copyright notices are retained.
