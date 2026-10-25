# ZUKO TV

ZUKO TV is an Android TV and mobile streaming interface.

## Build

Install Android Studio / Android SDK and Java 17, then run:

```bash
./gradlew :app:assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Release builds

Configure private build values through CI secrets or a local, untracked `local.properties` file. Never commit API credentials, signing keys, passwords, or private service configuration. Release builds use code shrinking and obfuscation.

## Attribution

This product uses TMDB data but is not endorsed or certified by TMDB.
