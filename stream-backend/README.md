# ZUKO TV Public Stream Backend

A small Railway-ready Node.js service that searches Internet Archive movie metadata and returns a direct playable URL only when the item's metadata identifies a public-domain or Creative Commons basis. It does not scrape subscription services or bypass access controls.

## Deploy on Railway

1. Push this project to a GitHub repository (or place this folder in a repository).
2. In Railway, create a project and deploy from that repository.
3. Set the service **Root Directory** to `/stream-backend` if the app repository also contains the Android project.
4. Railway detects `package.json`; the start command is `npm start`.
5. Generate a public domain for the service.
6. Test `https://YOUR-DOMAIN/healthz` and confirm it returns `{ "ok": true, ... }`.
7. Set `STREAM_API_BASE=https://YOUR-DOMAIN` in Android `local.properties`, then rebuild the app.

## Endpoints

- `GET /healthz`
- `GET /api/v1/public/search?query=Night%20of%20the%20Living%20Dead`
- `GET /api/v1/public/stream?title=Night%20of%20the%20Living%20Dead&year=1968`

The stream route responds with `{ "url": "https://archive.org/download/...", "title": "...", "identifier": "...", "format": "mp4", "rights": "..." }`. The Android player uses Media3's default media-source factory so it can play supported MP4 and HLS URLs.

## Limitations

- The Internet Archive catalogue is not a source for every TMDB title.
- Rights metadata is not a legal guarantee; check the item's details and license before redistributing or publicly exhibiting it.
- Some items may be unavailable, have non-compatible encodings, or block direct playback. A no-match response is expected for many modern commercial films.
- No video files are proxied or stored by this service; playback is direct from Internet Archive.
