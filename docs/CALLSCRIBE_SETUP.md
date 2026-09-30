# CallScribe setup

## Requirements

- Android 12 or newer
- A device that supports Shizuku call-audio capture
- The [thedjchi Shizuku fork](https://github.com/thedjchi/Shizuku), recommended because it can restart more reliably after boot
- About 800 MB free storage for the default Parakeet model and temporary download file

## Configure recording

1. Install and start Shizuku through Android Wireless debugging.
2. Open CallScribe and accept the legal notice.
3. Grant phone, contacts, call-log, notification, battery, and Shizuku permissions shown by onboarding.
4. Keep **App-private storage** selected unless recordings must also be visible to another app.
5. Make a short test call and confirm both people are audible before relying on automatic recording.

Shizuku normally needs to be restarted after a non-rooted phone reboots. CallScribe cannot record while Shizuku is unavailable.

## Download speech recognition

Open **Models** and download **Parakeet TDT 0.6B v3 (int8)**. The download is about 670 MB, is pinned to a specific model revision, supports HTTP resume, and is verified with SHA-256 before use.

The internet permission is used for model downloads only. Call audio and transcripts are not uploaded.

## Transcription

By default, transcription starts after a recording is finalized. Settings can restrict it to charging or make it manual. Calls are decoded, resampled to 16 kHz, voice activity is detected with Silero VAD, and Parakeet runs locally through sherpa-onnx.

The Calls screen shows queued, active, completed, and failed states. Open a call to play audio, follow the highlighted transcript, seek by tapping a segment, or export TXT, Markdown, SRT, or audio.

## Debug benchmark

Debug builds include `AsrBenchmarkActivity`. Push a WAV and model files:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$root = "/sdcard/Android/data/com.nicholaston.callscribe.debug/files/benchmark"
& $adb push input.wav "$root/input.wav"
& $adb push encoder.int8.onnx decoder.int8.onnx joiner.int8.onnx tokens.txt "$root/models/parakeet-tdt-0.6b-v3-int8/"
& $adb shell am start -n com.nicholaston.callscribe.debug/com.nicholaston.callscribe.debug.AsrBenchmarkActivity
```

The benchmark reports model load time, decode time, real-time factor, peak RSS, and the resulting transcript.

## Legal notice

Call-recording consent laws vary. You are responsible for informing participants and obtaining consent where required. CallScribe cannot guarantee capture on every device or inject a recording tone into the call.
