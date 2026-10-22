# ZUKO TV

Android TV / phone movie UI with TMDB catalog and **Runflix** streaming via your Railway Stremio addon.

## Configure

`local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
TMDB_API_KEY=your_tmdb_v3_key
RUNFLIX_ADDON_BASE=https://fuzzy-fortnight-production.up.railway.app
# optional public-domain backend
# STREAM_API_BASE=https://your-backend.up.railway.app
```

## Build

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/`

## Playback

1. Browse TMDB rows (trending, popular, etc.)
2. Open a title → Play
3. App searches the Runflix addon by title and plays the best MP4 stream

Addon endpoints used:

- `/catalog/movie/runflix-search/search=<title>.json`
- `/stream/movie/runflix:<id>.json`


## Cinematic transitions

The app keeps its vector-based animated ZUKO TV opening intro and now uses subtle fade, scale, and directional slide transitions when moving between Home, Details, and Player screens.
