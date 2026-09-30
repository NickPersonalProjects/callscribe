# CallScribe

CallScribe is a privacy-first Android phone-call recorder and on-device transcription app. It records carrier calls through Shizuku and transcribes them locally with sherpa-onnx and NVIDIA Parakeet. Audio and transcripts stay on the phone.

> [!IMPORTANT]
> **CallScribe is a fork of [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder).** It is not affiliated with or endorsed by the upstream project. ShizuCallRecorder's name, branding, and logos are not used by this fork.

## Features

- Non-root recording of incoming and outgoing carrier calls through Shizuku
- Fully local transcription with Parakeet TDT 0.6B v3 int8
- Searchable transcripts with timestamps and speaker labels
- Playback synchronized to transcript segments
- Watched-folder and share-sheet recording import
- Retention rules and TXT, Markdown, SRT, and audio export
- No analytics, advertising, or cloud speech service

## Install

The easiest installation is the prebuilt APK from GitHub:

1. On an Android 12 or newer phone, open the
   [latest CallScribe release](https://github.com/NickPersonalProjects/callscribe/releases/latest).
2. Download the APK listed under **Assets**.
3. Open the downloaded file. If Android blocks it, allow **Install unknown apps** for the browser or file manager you used, then try again.
4. Install and open
   [Shizuku from Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api).
   If Google Play is unavailable, use the [official GitHub release](https://github.com/RikkaApps/Shizuku/releases/latest).
   Start Shizuku using **Wireless debugging** by following its on-screen pairing steps.
5. Open CallScribe, review the legal notice, then tap **Accept and enable everything**. CallScribe automatically advances through setup and queues the default Parakeet model.
6. Approve the Android and Shizuku security confirmations as they appear. Android does not allow apps to approve these dialogs for you. The Parakeet download needs about 670 MB plus temporary download space and waits for Wi-Fi by default.
7. Make a short test call and verify that both sides are audible before relying on automatic recording.

Shizuku normally must be started again after every non-rooted phone reboot. The
[thedjchi Shizuku fork](https://github.com/thedjchi/Shizuku) is an optional alternative intended to make restart-after-boot easier.

For upgrades, download the newer APK and install it over the existing app. Do not uninstall first if you want to retain app-private recordings, transcripts, settings, and downloaded models.

See [the complete setup guide](docs/CALLSCRIBE_SETUP.md) for troubleshooting, imports, transcription, and developer benchmark instructions.

> [!WARNING]
> The current release is a development preview. Carrier-call capture depends on the phone manufacturer, Android version, call route, and Shizuku. It has been build-tested and emulator-tested, but physical-device compatibility must be verified on your phone.

## Build from source

The project uses JDK 17, Android SDK 36, Kotlin, and Jetpack Compose. Create `local.properties` with your Android SDK path, then run:

```powershell
.\gradlew.bat clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See [UPSTREAM.md](UPSTREAM.md) for the fork strategy.

## Privacy

Recordings and transcripts are intended to remain on-device. The app's internet access is reserved for explicit, user-initiated speech-model downloads. Recordings, transcripts, downloaded models, and app settings are excluded from Android cloud backup and device-to-device transfer.

## Legal notice

Call recording laws vary by jurisdiction. You are responsible for obtaining any consent required by law. CallScribe does not inject an audible recording tone and cannot guarantee recording succeeds on every device or call.

## License

GPL-3.0-or-later, including the additional Section 7 terms in [LICENSE](LICENSE). Upstream copyright notices are retained.
