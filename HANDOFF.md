# CallScribe handoff

## Build

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The upstream repository intentionally ignores Gradle launcher files. If they are missing locally, regenerate a Gradle 9.4.1 wrapper or use Android Studio's bundled Gradle launcher. `local.properties` must point to the Android SDK.

## Architecture

- `com.kitsumed.shizucallrecorder`: upstream recording/call-detection stack. Keep edits minimal and mark them `CallScribe:`.
- `com.nicholaston.callscribe.data`: Room, FTS, settings, repositories.
- `models`: pinned model registry and verified resumable downloads.
- `transcription`: MediaCodec decode, 16 kHz resample, channel detection, Silero VAD, sherpa-onnx ASR.
- `work`: transcription, retention, and import jobs.
- `hooks`: bridge from finalized upstream recordings into CallScribe.
- `ui`: Calls/search, player/transcript, models, settings, About.

See `UPSTREAM.md` before merging upstream changes.

## Known blockers

- A physical Android 12+ phone is required to validate carrier-call capture, channel separation, Bluetooth, locked-screen behavior, and Android 17.
- Parakeet performance and memory must be measured with the debug `AsrBenchmarkActivity`.
- Git pushes that change `.github/workflows` require an OAuth token with the GitHub `workflow` scope.

## Release checklist

1. Complete `docs/device-notes.md`.
2. Confirm Shizuku failure and consent notifications.
3. Run the full Gradle command above and an arm64 release build.
4. Replace/add current screenshots under fastlane metadata.
5. Sign via CI environment variables and compare reproducible hashes.
