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

app.use((_req, res) => res.status(404).json({ error: 'Endpoint not found. See the backend README for supported routes.' }));
app.listen(PORT, '0.0.0.0', () => console.log(`ZUKO public stream backend listening on ${PORT}`));
