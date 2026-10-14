import { readFileSync } from 'node:fs';
import { params, json, fail, fetchJSON, SourceError } from '../server/http.js';
import {PalmaClient, PalmaAPIError, APP_PROFILE} from '../server/palma/client.mjs';
import {normalizeImages} from '../server/palma/images.mjs';

const contract = JSON.parse(readFileSync(new URL('../server/contracts.json', import.meta.url)));
const routes = new Set(contract.routes.map(r => r.route).filter(r => !/check-update|app\//.test(r)));
const allowedQueries = new Set([...contract.routes.flatMap(r => r.query), 'type', 'q', 'index', 'page', 'limit', 'resolve']);
const cache = new Map();
let palmaClient, palmaConfig;
export async function catalogue(provider, route, query = {}) {
  if (!validateRoute(provider, route)) throw new SourceError('مسار محتوى غير صالح.', 400);
  const filtered = Object.fromEntries(Object.entries(query).filter(([key,value]) => allowedQueries.has(key) && value != null && String(value).length <= 500));
  if (provider === 'palma') {
    const config = JSON.stringify([process.env.PALMA_API_BASE, process.env.PALMA_CERT_SHA256, process.env.PALMA_APP_VERSION]);
    if (!palmaClient || palmaConfig !== config) {
      palmaClient = new PalmaClient({base:process.env.PALMA_API_BASE, profile:{...APP_PROFILE,
        certificate_sha256:process.env.PALMA_CERT_SHA256 || APP_PROFILE.certificate_sha256,
        version_code:process.env.PALMA_APP_VERSION || APP_PROFILE.version_code,
      }});
      palmaConfig = config;
    }
    try {return normalizeImages((await palmaClient.get(route, filtered)).body, process.env.PALMA_IMAGE_BASE || palmaClient.base.href);}
    catch (error) {
      if (error instanceof PalmaAPIError) throw new SourceError(error.status===404?'المحتوى غير متاح حاليًا.':'تعذر تحديث محتوى بالما. أعد المحاولة.',error.status===404?404:502,error.code);
      throw error;
    }
  }
  const base = provider === 'witcher' ? (process.env.ANIME_API_BASE || 'https://awr-stream-web.vercel.app/api/') : provider === 'drama' ? (process.env.DRAMA_API_BASE || 'https://dwapp.arabypros.com/api/') : (process.env.PALMA_API_BASE || 'https://ostvapp.cam/');
  if (provider === 'drama' && !process.env.DRAMA_API_SUFFIX) throw new SourceError('مصدر عالم الدراما غير متصل حاليًا.', 503, 'SOURCE_NOT_CONFIGURED');
  const url = new URL(route + (provider === 'drama' ? process.env.DRAMA_API_SUFFIX : ''), base);
  for (const [key,value] of Object.entries(filtered)) url.searchParams.set(key,String(value));
  return fetchJSON(url);
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
    const key = p.toString(), hit = cache.get(key), live = /watch_links|download_links|servers|matches|show\.php/.test(route);
    let data;
    if (!live && hit && hit.time > Date.now() - 45000) data = hit.data;
    else { data = await catalogue(provider,route,Object.fromEntries(p)); if (!live) { if(cache.size > 80) cache.clear(); cache.set(key,{ time:Date.now(),data }); } }
    if(provider==='palma') {
      res.setHeader('X-Palma-Origin',palmaClient?.base.origin||'');
      res.setHeader('X-Palma-Route',route);
      if (/^\d+$/.test(p.get('id')||'')) res.setHeader('X-Palma-Item',p.get('id'));
    }
    json(res,200,data);
  } catch (error) { fail(res,error); }
}
