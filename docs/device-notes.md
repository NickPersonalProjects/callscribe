# Device validation notes

Complete this document on every physical test device before release.

## Device

- Manufacturer/model:
- Android version/build:
- CallScribe commit:
- Shizuku version:
- Carrier/SIM:

## Recording matrix

| Scenario | Both sides audible | Separate channels | Notes |
|---|---|---|---|
| Outgoing, earpiece |  |  |  |
| Incoming, earpiece |  |  |  |
| Speakerphone |  |  |  |
| Bluetooth |  |  |  |
| Wired headset |  |  |  |
| Locked screen |  |  |  |
| Call waiting/conference |  |  |  |

## ASR benchmark

| Model | Threads | Provider | Audio duration | Load | Decode | RTF | Peak RSS | Notes |
|---|---:|---|---:|---:|---:|---:|---:|---|
| Parakeet TDT 0.6B v3 int8 | 2 | CPU |  |  |  |  |  |  |
| Parakeet TDT 0.6B v3 int8 | 4 | CPU |  |  |  |  |  |  |
| Parakeet TDT 0.6B v3 int8 | 6 | CPU |  |  |  |  |  |  |

## Reliability

- Reboot with Shizuku stopped:
- 60-minute call:
- Airplane-mode transcription:
- Charging-only scheduling:
- Low battery/storage:
- Thermal throttling:
- Import and export:

## Release decision

- Default model/threads/provider:
- Speaker strategy (channels/diarization/off):
- Known device limitations:
