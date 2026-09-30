# Upstream tracking

CallScribe is an edited fork of [kitsumed/ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder).

- Upstream remote: `https://github.com/kitsumed/ShizuCallRecorder.git`
- Initial upstream commit: `41d1dc7be09ec4d1ca648ff62d9cf1aff6e0cd8a`
- Upstream Kotlin namespace retained: `com.kitsumed.shizucallrecorder`
- New CallScribe code namespace: `com.nicholaston.callscribe`

## Intentional upstream touch points

Edits to upstream-owned files are marked with `CallScribe:` where practical.

1. `settings.gradle.kts`: project name.
2. `app/build.gradle.kts`: application ID, debug suffix, dependencies, generated assets, and ABI configuration.
3. `app/src/main/res/values/strings.xml`: public app identity and fork notice.
4. `app/src/main/java/com/kitsumed/shizucallrecorder/AppUrls.kt`: fork repository links.
5. Recording lifecycle integration points will call `com.nicholaston.callscribe.hooks.CallScribeHooks`.
6. Navigation exposes the calls library, models, settings, and About screens.

## Sync procedure

```powershell
git fetch upstream
git merge upstream/main
.\gradlew.bat :app:assembleDebug
```

Resolve conflicts by preserving upstream recording behavior and reapplying only the documented CallScribe integration points.
