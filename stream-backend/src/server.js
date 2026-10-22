import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';

const app = express();
const PORT = Number(process.env.PORT || 3000);
const IA = 'https://archive.org';
const USER_AGENT = 'ZUKO-TV-PublicStream/1.0 (public-domain media resolver)';

app.disable('x-powered-by');
app.use(helmet());
app.use(express.json({ limit: '32kb' }));
app.use(rateLimit({ windowMs: 60_000, limit: 90, standardHeaders: 'draft-7', legacyHeaders: false }));

function cleanText(value, max = 160) {
  return typeof value === 'string' ? value.trim().replace(/[\u0000-\u001f]/g, '').slice(0, max) : '';
}
function rightsAllow(metadata) {
  const rights = [metadata?.rights, metadata?.licenseurl, metadata?.license, metadata?.description]
    .flat().filter(Boolean).join(' ').toLowerCase();
  return /public domain|creativecommons\.org|creative commons|cc0|cc-by|cc by|cc-by-sa|cc by-sa|no known copyright/.test(rights);
}
function pickPlayable(files = []) {
  const candidates = files.filter(file => {
    const name = String(file.name || '').toLowerCase();
    const format = String(file.format || '').toLowerCase();
    return !file.private && (name.endsWith('.mp4') || name.endsWith('.m3u8') || format.includes('mpeg4') || format.includes('h.264') || format.includes('h264'));
  });
  // Prefer direct MP4 because it is broadly compatible; HLS is supported as a fallback.
  candidates.sort((a, b) => {
    const score = f => String(f.name || '').toLowerCase().endsWith('.mp4') ? 0 : 1;
    return score(a) - score(b) || Number(a.size || 0) - Number(b.size || 0);
  });
  return candidates[0] || null;
}
async function archiveJson(url) {
  const response = await fetch(url, { headers: { 'User-Agent': USER_AGENT, 'Accept': 'application/json' }, signal: AbortSignal.timeout(12_000) });
  if (!response.ok) throw new Error(`Internet Archive returned HTTP ${response.status}`);
  return response.json();
}
async function findItems(query, year) {
  const titleQuery = query.replace(/[\"\\{}\[\]^~*?:]/g, ' ').replace(/\s+/g, ' ').trim();
  if (!titleQuery) return [];
  const searchText = `title:"${titleQuery}" AND mediatype:movies`;
  const params = new URLSearchParams({ q: searchText, 'fl[]': 'identifier,title,year', rows: '8', page: '1', output: 'json', sort: 'downloads desc' });
  const data = await archiveJson(`${IA}/advancedsearch.php?${params}`);
  let docs = data?.response?.docs || [];
  if (year) docs = docs.sort((a, b) => Number(String(b.year || '').slice(0, 4) === year) - Number(String(a.year || '').slice(0, 4) === year));
  return docs;
}

app.get('/healthz', (_req, res) => res.json({ ok: true, service: 'zuko-tv-public-stream-backend' }));

app.get('/api/v1/public/search', async (req, res) => {
  const query = cleanText(req.query.query, 100);
  if (query.length < 2) return res.status(400).json({ error: 'Enter a movie title with at least two characters.' });
  try {
    const docs = await findItems(query, cleanText(req.query.year, 4));
    res.json({ results: docs.map(d => ({ identifier: d.identifier, title: d.title || d.identifier, year: d.year || null })) });
  } catch (error) {
    res.status(502).json({ error: 'Could not search the public movie archive right now. Please try again shortly.' });
  }
});

app.get('/api/v1/public/stream', async (req, res) => {
  const title = cleanText(req.query.title, 160);
  const year = cleanText(req.query.year, 4);
  if (title.length < 2) return res.status(400).json({ error: 'A movie title is required to search public streams.' });
  try {
    const docs = await findItems(title, year);
    for (const item of docs) {
      if (!item.identifier) continue;
      const metadataResponse = await archiveJson(`${IA}/metadata/${encodeURIComponent(item.identifier)}`);
      const metadata = metadataResponse?.metadata || {};
      if (!rightsAllow(metadata)) continue;
      const file = pickPlayable(metadataResponse?.files || []);
      if (!file?.name) continue;
      const url = `${IA}/download/${encodeURIComponent(item.identifier)}/${file.name.split('/').map(encodeURIComponent).join('/')}`;
      return res.json({ url, title: metadata.title || item.title || title, identifier: item.identifier, format: file.name.toLowerCase().endsWith('.m3u8') ? 'hls' : 'mp4', rights: metadata.rights || metadata.licenseurl || 'Publicly identified license metadata' });
    }
    return res.status(404).json({ error: `No playable public-domain or Creative Commons stream was found for "${title}". Try a classic film title or another movie.` });
  } catch (error) {
    console.error('public stream resolution failed:', error?.message || error);
    return res.status(502).json({ error: 'The public movie archive could not be reached. Check the backend logs and try again.' });
  }
});


// TMDB proxy: keeps the TMDB credential on the backend.
const tmdbCache = new Map();
const TMDB_BASE = 'https://api.themoviedb.org/3/';
const TMDB_CACHE_TTL = 5 * 60 * 1000;
const TMDB_CACHE_MAX = 300;

const TMDB_ALLOWED_PATHS = [
  /^trending\/(movie|tv)\/(day|week)$/,
  /^movie\/\d+$/,
  /^movie\/(popular|top_rated|now_playing|upcoming)$/,
  /^tv\/\d+$/,
  /^tv\/(popular|top_rated|on_the_air|airing_today)$/,
  /^tv\/\d+\/season\/\d+$/,
  /^search\/(movie|tv)$/,
  /^discover\/movie$/,
  /^genre\/(movie|tv)\/list$/,
  /^movie\/\d+\/(credits|similar|recommendations)$/,
  /^tv\/\d+\/(credits|similar|recommendations)$/,
  /^configuration$/
];

const TMDB_ALLOWED_QUERY = new Set([
  'language', 'page', 'query', 'include_adult', 'with_genres',
  'sort_by', 'append_to_response', 'year', 'first_air_date_year',
  'primary_release_year', 'region', 'with_original_language',
  'include_video', 'with_watch_providers', 'watch_region'
]);

app.get('/api/tmdb/3/*', async (req, res) => {
  const apiKey = process.env.TMDB_API_KEY;
  if (!apiKey) {
    return res.status(503).json({
      error: 'TMDB proxy is not configured. Set TMDB_API_KEY on this Railway service.'
    });
  }

  const path = String(req.params[0] || '').replace(/^\/+|\/+$/g, '');
  if (!path || path.includes('..') || !TMDB_ALLOWED_PATHS.some(rule => rule.test(path))) {
    return res.status(400).json({ error: 'TMDB endpoint is not allowed.' });
  }

  const params = new URLSearchParams();
  for (const [key, raw] of Object.entries(req.query)) {
    if (!TMDB_ALLOWED_QUERY.has(key)) continue;
    const value = Array.isArray(raw) ? raw[0] : raw;
    if (typeof value === 'string' && value.length <= 300) {
      params.set(key, value);
    }
  }
  params.set('api_key', apiKey);

  const cacheKey = `${path}?${[...params.entries()]
    .filter(([key]) => key !== 'api_key')
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')}`;

  const cached = tmdbCache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) {
    res.set('X-TMDB-Cache', 'HIT');
    return res.status(cached.status).json(cached.body);
  }

  try {
    const upstream = await fetch(`${TMDB_BASE}${path}?${params.toString()}`, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(10000)
    });

    const bodyText = await upstream.text();
    let body;
    try {
      body = JSON.parse(bodyText);
    } catch {
      return res.status(502).json({ error: 'TMDB returned an invalid response.' });
    }

    if (!upstream.ok) {
      const status = upstream.status === 401 ? 502 :
                     upstream.status === 429 ? 429 :
                     upstream.status >= 500 ? 502 : upstream.status;
      return res.status(status).json({
        error: upstream.status === 401
          ? 'TMDB rejected the backend API key. Check TMDB_API_KEY on Railway.'
          : upstream.status === 429
            ? 'TMDB is rate limiting requests. Please try again shortly.'
            : 'TMDB request failed.'
      });
    }

    if (tmdbCache.size >= TMDB_CACHE_MAX) {
      const firstKey = tmdbCache.keys().next().value;
      if (firstKey) tmdbCache.delete(firstKey);
    }
    tmdbCache.set(cacheKey, {
      status: upstream.status,
      body,
      expiresAt: Date.now() + TMDB_CACHE_TTL
    });

    res.set('X-TMDB-Cache', 'MISS');
    return res.status(200).json(body);
  } catch (error) {
    console.error('TMDB proxy request failed:', error?.message || error);
    return res.status(502).json({ error: 'TMDB is temporarily unavailable.' });
  }
});

app.use((_req, res) => res.status(404).json({ error: 'Endpoint not found. See the backend README for supported routes.' }));
app.listen(PORT, '0.0.0.0', () => console.log(`ZUKO public stream backend listening on ${PORT}`));
