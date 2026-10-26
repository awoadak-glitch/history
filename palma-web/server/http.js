import dns from 'node:dns/promises';
import net from 'node:net';
import crypto from 'node:crypto';
import http from 'node:http';
import https from 'node:https';
import { Readable } from 'node:stream';
import {readFileSync} from 'node:fs';
import {SESSION_ORIGIN,bindSourceSession,mediaHeadersForURL} from './source-session.js';
import {tdmMediaHeaders} from './tdm-media-headers.mjs';

export class SourceError extends Error {
  constructor(message, status = 502, code = 'SOURCE_ERROR') { super(message); this.status = status; this.code = code; }
}
export function json(res, status, body) {
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store');
  res.end(JSON.stringify(body));
}
export function fail(res, error) {
  const known=error instanceof SourceError;
  json(res, known ? error.status : 502, { error: known ? error.message : 'تعذر الاتصال بالسيرفر. أعد المحاولة أو اختر سيرفرًا آخر.', code: known ? error.code : 'SOURCE_ERROR' });
}
export function params(req) { return new URL(req.url, 'http://localhost').searchParams; }
export async function readLimited(response, limit = 8 * 1024 * 1024) {
  if (+response.headers.get('content-length') > limit) throw new SourceError('استجابة المصدر أكبر من الحد المسموح.');
  const reader = response.body?.getReader();
  if (!reader) return '';
  const parts = []; let size = 0;
  while (true) {
    const { value, done } = await reader.read(); if (done) break;
    size += value.byteLength;
    if (size > limit) { await reader.cancel(); throw new SourceError('استجابة المصدر أكبر من الحد المسموح.'); }
    parts.push(Buffer.from(value));
  }
  return Buffer.concat(parts).toString('utf8');
}
export async function fetchJSON(url, headers = {}, signal) {
  const response = await fetch(url, { headers: { Accept: 'application/json', 'User-Agent': 'PALMA-VISION-Web/1.1.7', ...headers }, signal: signal || AbortSignal.timeout(24000), redirect: 'manual' });
  if (response.status === 401 || response.status === 403) {
    await response.body?.cancel();
    throw new SourceError('مصدر هذا القسم لا يسمح باتصال الويب حاليًا. أعد المحاولة لاحقًا.', 403, 'SOURCE_DENIED');
  }
  if (!response.ok) { await response.body?.cancel(); throw new SourceError(`تعذر تحميل المحتوى من المصدر (${response.status}).`, response.status === 404 ? 404 : 502); }
  const text = await readLimited(response);
  let value; try { value = JSON.parse(text); } catch { throw new SourceError('أعاد المصدر صفحة غير صالحة. حاول لاحقًا.'); }
  if (value?.error || ['error', 'failed'].includes(value?.status)) throw new SourceError(String(value.error || value.message || 'تعذر تحميل المحتوى.').slice(0, 200));
  return value;
}

export function isPrivateIP(ip) {
  const value = ip.toLowerCase().replace(/^\[|\]$/g, '');
  if (value.includes(':')) {
    if (value.startsWith('::ffff:')) {
      const tail = value.slice(7);
      if (net.isIP(tail) === 4) return isPrivateIP(tail);
      const pieces = tail.split(':');
      if (pieces.length === 2) { const n = parseInt(pieces[0], 16) * 65536 + parseInt(pieces[1], 16); return isPrivateIP([24,16,8,0].map(b => (n >>> b) & 255).join('.')); }
    }
    return value === '::' || value === '::1' || /^(fc|fd|fe8|fe9|fea|feb|ff)/.test(value) || !/^[23]/.test(value);
  }
  const a = value.split('.').map(Number);
  return a[0] === 0 || a[0] === 10 || a[0] === 127 || a[0] >= 224 || (a[0] === 169 && a[1] === 254) || (a[0] === 172 && a[1] >= 16 && a[1] <= 31) || (a[0] === 192 && a[1] === 168) || (a[0] === 100 && a[1] >= 64 && a[1] <= 127) || (a[0] === 198 && [18,19].includes(a[1]));
}
export async function publicURL(raw) {
  let url; try { url = new URL(raw); } catch { throw new SourceError('رابط السيرفر غير صالح.', 400, 'INVALID_URL'); }
  // PALMA's live-channel gateway redirects to public HTTP media on port 8080.
  // Validate/pin every destination as usual; source cookies stay origin-bound.
  if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password || (url.port && !['80', '443', '8080'].includes(url.port))) throw new SourceError('رابط السيرفر غير مدعوم.', 400, 'INVALID_URL');
  const host = url.hostname.replace(/^\[|\]$/g, '');
  const addresses = net.isIP(host) ? [{ address: host }] : await dns.lookup(host, { all: true });
  if (!addresses.length || addresses.some(x => isPrivateIP(x.address))) throw new SourceError('عنوان السيرفر غير مدعوم.', 400, 'INVALID_URL');
  url.pinnedAddress = addresses[0].address;
  return url;
}
function pinnedFetch(url, options = {}) {
  return new Promise((resolve,reject) => {
    const family = net.isIP(url.pinnedAddress), transport = url.protocol === 'https:' ? https : http;
    const request = transport.request(url, {
      method: options.method || 'GET', headers: {'Accept-Encoding':'identity',...options.headers}, signal: options.signal,
      lookup: (_hostname, opts, callback) => opts?.all ? callback(null,[{address:url.pinnedAddress,family}]) : callback(null,url.pinnedAddress,family)
    }, response => {
      const headers = new Headers();for(const [k,v] of Object.entries(response.headers)) if(v!==undefined)headers.set(k,Array.isArray(v)?v.join(', '):v);
      const noBody=options.method==='HEAD'||[204,304].includes(response.statusCode);
      resolve(new Response(noBody?null:Readable.toWeb(response),{status:response.statusCode,headers}));
      if(noBody)response.resume();
    });
    request.setTimeout(25000,()=>request.destroy(new Error('انتهت مهلة اتصال السيرفر.')));
    request.on('error',reject);request.end();
  });
}
export async function fetchPublic(raw, options = {}) {
  let url = await publicURL(raw);
  for (let i = 0; i < 5; i++) {
    const scoped=mediaHeadersForURL(url.href,options.headers);
    const response = await pinnedFetch(url, { ...options, headers:Object.fromEntries(Object.entries(tdmMediaHeaders(url.href,scoped))), signal: options.signal || AbortSignal.timeout(55000) });
    if ([301,302,303,307,308].includes(response.status)) {
      const location = response.headers.get('location'); await response.body?.cancel();
      if (!location) throw new SourceError('لم يرسل السيرفر رابطًا صالحًا.');
      const next = await publicURL(new URL(location, url).href);
      options={...options,headers:mediaHeadersForURL(next.href,scoped)};
      url = next; continue;
    }
    return { response, url: url.href };
  }
  throw new SourceError('تجاوز السيرفر عدد إعادة التوجيه المسموح.');
}
const devSecret = crypto.randomBytes(32).toString('hex');
let deploymentSecret;
function secret() {
  if (process.env.MEDIA_TICKET_SECRET) return process.env.MEDIA_TICKET_SECRET;
  if (!deploymentSecret) {
    try {
      const key=JSON.parse(readFileSync(new URL('./generated/media-key.json',import.meta.url),'utf8')).key;
      if (/^[a-f0-9]{64}$/.test(key)) deploymentSecret=key;
    } catch {}
  }
  if (deploymentSecret) return deploymentSecret;
  if (process.env.VERCEL) throw new SourceError('إعداد تشغيل الفيديو غير مكتمل.', 503, 'MISSING_MEDIA_CONFIG');
  return devSecret;
}
export function ticket(url, headers = {}, options = {}) {
  headers=mediaHeadersForURL(url,headers);
  const data=JSON.stringify({u:url,h:headers,...(headers[SESSION_ORIGIN]?{o:headers[SESSION_ORIGIN]}:{}),...(options.variables?{v:options.variables}:{}),e:Date.now()+8*3600*1000});
  // The playback credential must not be readable from a media URL or manifest.
  const nonce=crypto.randomBytes(12),cipher=crypto.createCipheriv('aes-256-gcm',crypto.createHash('sha256').update(secret()).digest(),nonce);
  const encrypted=Buffer.concat([cipher.update(data,'utf8'),cipher.final()]);
  return ['v2',nonce.toString('base64url'),encrypted.toString('base64url'),cipher.getAuthTag().toString('base64url')].join('.');
}
export function unticket(raw) {
  if (!raw || raw.length > 16000) throw new SourceError('رابط المشاهدة غير صالح.', 400);
  if(raw.startsWith('v2.')){
    let payload;
    try{
      const parts=raw.split('.');if(parts.length!==4)throw Error();
      const [nonce,data,tag]=parts.slice(1).map(part=>{const bytes=Buffer.from(part,'base64url');if(bytes.toString('base64url')!==part)throw Error();return bytes;});
      if(nonce.length!==12||tag.length!==16)throw Error();
      const decipher=crypto.createDecipheriv('aes-256-gcm',crypto.createHash('sha256').update(secret()).digest(),nonce);decipher.setAuthTag(tag);
      payload=JSON.parse(Buffer.concat([decipher.update(data),decipher.final()]).toString('utf8'));
    }catch{throw new SourceError('رابط المشاهدة غير صالح.',400);}
    if(payload.e<Date.now())throw new SourceError('انتهت صلاحية رابط المشاهدة. اختر السيرفر مرة أخرى.',410);
    payload.h=mediaHeadersForURL(payload.u,bindSourceSession(payload.h,payload.o,payload.h?.Cookie));
    return payload;
  }
  const [data, sig] = raw.split('.');
  const expected = crypto.createHmac('sha256', secret()).update(data || '').digest('base64url');
  if (!sig || sig.length !== expected.length || !crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) throw new SourceError('رابط المشاهدة غير صالح.', 400);
  let payload; try { payload = JSON.parse(Buffer.from(data, 'base64url')); } catch { throw new SourceError('رابط المشاهدة غير صالح.', 400); }
  if (payload.e < Date.now()) throw new SourceError('انتهت صلاحية رابط المشاهدة. اختر السيرفر مرة أخرى.', 410);
  payload.h=mediaHeadersForURL(payload.u,payload.h);
  return payload;
}
export const mediaURL = (url, headers = {}, options = {}) => `/api/media?t=${encodeURIComponent(ticket(url, headers, options))}`;
