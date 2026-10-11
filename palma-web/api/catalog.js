import { readFileSync } from 'node:fs';
import { params, json, fail, fetchJSON, SourceError } from '../server/http.js';

const contract = JSON.parse(readFileSync(new URL('../server/contracts.json', import.meta.url)));
const routes = new Set(contract.routes.map(r => r.route).filter(r => !/check-update|app\//.test(r)));
const allowedQueries = new Set([...contract.routes.flatMap(r => r.query), 'type', 'q', 'index', 'page', 'limit', 'resolve']);
const cache = new Map();
export async function catalogue(provider, route, query = {}) {
  if (!validateRoute(provider, route)) throw new SourceError('مسار محتوى غير صالح.', 400);
  const base = provider === 'witcher' ? (process.env.ANIME_API_BASE || 'https://awr-stream-web.vercel.app/api/') : provider === 'drama' ? (process.env.DRAMA_API_BASE || 'https://dwapp.arabypros.com/api/') : (process.env.PALMA_API_BASE || 'https://ostvapp.cam/');
  if (provider === 'drama' && !process.env.DRAMA_API_SUFFIX) throw new SourceError('مصدر عالم الدراما غير متصل حاليًا.', 503, 'SOURCE_NOT_CONFIGURED');
  const url = new URL(route + (provider === 'drama' ? process.env.DRAMA_API_SUFFIX : ''), base);
  for (const [key,value] of Object.entries(query)) if (allowedQueries.has(key) && String(value).length <= 500 && value != null) url.searchParams.set(key,String(value));
  if (provider === 'palma' && ['api/v2/home.php','api/series/','api/movies/','api/content-categories.php'].includes(route)) url.searchParams.set('app_version', process.env.PALMA_APP_VERSION || '17');
  const headers = provider === 'palma' && process.env.PALMA_API_TOKEN ? { Authorization: `Bearer ${process.env.PALMA_API_TOKEN}` } : {};
  return fetchJSON(url, headers);
}
export function validateRoute(provider, route) {
  if (route.length > 1500 || route.includes('..') || /[\r\n\\]/.test(route)) return false;
  try { if(route.split('/').some(s => ['.','..'].includes(decodeURIComponent(s)))) return false; } catch { return false; }
  if (provider === 'palma') return routes.has(route);
  if (provider === 'witcher') return /^(home|news|search|catalog|anime\/[^/?]+(?:\/episodes(?:\/[^/?]+\/servers)?)?)$/.test(route);
  if (provider === 'drama') return /^(first\/|genre\/all\/|category\/all\/|country\/all\/|serie\/by\/filtres\/[^/]+\/[^/]+\/\d+\/|movie\/by\/filtres\/[^/]+\/[^/]+\/\d+\/|channel\/by\/filtres\/[^/]+\/[^/]+\/\d+\/|search\/[^/]+\/\d+\/|movie\/by\/\d+\/|channel\/by\/\d+\/|season\/by\/serie\/\d+\/|movie\/source\/by\/\d+\/|episode\/source\/by\/\d+\/)$/.test(route);
  return false;
}
export default async function handler(req, res) {
  if (req.method !== 'GET') return json(res, 405, { error: 'طريقة طلب غير مدعومة.' });
  try {
    const p = params(req), provider = p.get('provider') || 'palma', route = p.get('route') || 'api/v2/home.php';
    if (!validateRoute(provider, route)) throw new SourceError('مسار محتوى غير صالح.', 400);
    const key = p.toString(), hit = cache.get(key), live = /watch_links|download_links|servers|matches/.test(route);
    let data;
    if (!live && hit && hit.time > Date.now() - 45000) data = hit.data;
    else { data = await catalogue(provider,route,Object.fromEntries(p)); if (!live) { if(cache.size > 80) cache.clear(); cache.set(key,{ time:Date.now(),data }); } }
    json(res,200,data);
  } catch (error) { fail(res,error); }
}
