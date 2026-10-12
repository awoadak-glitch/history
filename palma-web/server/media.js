import { fetchPublic, readLimited, publicURL, SourceError, mediaURL, isPrivateIP } from './http.js';
import net from 'node:net';

export function witcherPlayback(source) {
  let url;
  try { url = new URL(source?.url); } catch { throw new SourceError('لم يُرجع مصدر الأنمي رابط فيديو صالحًا.',422); }
  const host=url.hostname.replace(/^\[|\]$/g,'');
  if (source?.direct !== true || !['https:','http:'].includes(url.protocol) || url.username || url.password || (url.port && !['80','443'].includes(url.port)) || !host.includes('.') || /(^|\.)(localhost|local|internal)$/.test(host) || (net.isIP(host) && isPrivateIP(host))) {
    throw new SourceError('لم يُرجع مصدر الأنمي رابط فيديو مباشرًا صالحًا.',422);
  }
  return { type:/\.m3u8(?:\?|$)/i.test(url.href)?'hls':'video',url:url.href,playable:url.href,download:url.href,quality:source.quality||'',name:source.name||'',qualities:[],delivery:'direct' };
}

export function unwrapURL(value) {
  let raw = String(value || '').trim().replaceAll('&amp;', '&');
  for (let i = 0; i < 4; i++) {
    if (/^https?:\/\//i.test(raw)) return raw;
    try {
      const parsed = new URL(raw), embedded = ['url','link','video','src','stream','download'].map(k => parsed.searchParams.get(k)).find(Boolean);
      if (embedded) { raw = embedded; continue; }
    } catch {}
    try { const decoded = decodeURIComponent(raw); if (decoded !== raw) { raw = decoded; continue; } } catch {}
    break;
  }
  throw new SourceError('هذا السيرفر يستخدم رابطًا خاصًا بالمشغّل الأصلي، ولا يتيح رابط فيديو للويب.', 422, 'NATIVE_PLAYER_REQUIRED');
}
export function parseHLS(text, url) {
  const lines = text.split(/\r?\n/), out = [];
  for (let i = 0; i < lines.length; i++) {
    if (!lines[i].startsWith('#EXT-X-STREAM-INF:')) continue;
    const height = /RESOLUTION=\d+x(\d+)/.exec(lines[i])?.[1];
    const bandwidth = +(/BANDWIDTH=(\d+)/.exec(lines[i])?.[1] || 0);
    let next = i + 1; while (next < lines.length && (!lines[next].trim() || lines[next].startsWith('#'))) next++;
    if (lines[next]) out.push({ url:new URL(lines[next].trim(),url).href, quality:height ? `${height}p` : `${Math.round(bandwidth/1000)} kbps`, height:+height || 0 });
  }
  return out.sort((a,b) => b.height-a.height);
}
export function extractStreams(html, base) {
  const found = new Set();
  const patterns = [/(?:file|src|source|video_url|contentUrl)\s*[=:]\s*["']([^"']+)["']/gi, /<source[^>]+src=["']([^"']+)["']/gi, /<a[^>]+(?:id=["']downloadButton["'][^>]*href|href)=["']([^"']+)["'][^>]*(?:download|downloadButton)/gi, /["'](https?:[^"'\s]+\.(?:m3u8|mp4|webm|mkv)(?:\?[^"'\s]*)?)["']/gi];
  for (const regex of patterns) for (const match of html.matchAll(regex)) {
    try { const url = new URL(match[1].replaceAll('\\/','/').replaceAll('&amp;','&'),base).href; if (/\.(m3u8|mp4|webm|mkv)(\?|$)|\/api\/file\/|download\d+\.mediafire/.test(url)) found.add(url); } catch {}
  }
  return [...found];
}
export async function resolveMedia(raw, headers = {}, quality = '', external = false) {
  let url = unwrapURL(raw);
  await publicURL(url);
  if (/pixeldrain\.com$/.test(new URL(url).hostname) && /^\/u\/[^/]+$/.test(new URL(url).pathname)) url = `https://pixeldrain.com/api/file/${new URL(url).pathname.split('/').pop()}`;
  // Native players fetch media from the user's device and accept request headers.
  // Direct files do not need a Vercel proxy or a server-side playback probe.
  const delivery=(url,h=headers)=>external?{playable:url,headers:h,delivery:'external'}:{playable:mediaURL(url,h)};
  if(external && /\.(m3u8|mp4|mkv|webm|mpd)(\?|$)|\/api\/file\//i.test(url)) return {type:/\.m3u8(\?|$)/i.test(url)?'hls':'video',url,...delivery(url),quality,qualities:[]};
  const { response, url: finalURL } = await fetchPublic(url, { headers: { ...headers, Accept:'*/*' } });
  if (!response.ok) { await response.body?.cancel(); throw new SourceError('تعذر تجهيز هذا السيرفر. اختر سيرفرًا آخر.', response.status === 403 ? 403 : 502, 'MEDIA_DENIED'); }
  const contentType = response.headers.get('content-type') || '';
  if (/mpegurl/i.test(contentType) || /\.m3u8(\?|$)/i.test(finalURL)) {
    const text = await readLimited(response, 2*1024*1024);
    const variants = parseHLS(text,finalURL);
    return { type:'hls', url:finalURL, ...delivery(finalURL), quality, qualities:variants.map(v=>({...v,...delivery(v.url)})) };
  }
  if (/^(video\/|application\/octet-stream)/i.test(contentType) || /\.(mp4|mkv|webm)(\?|$)|\/api\/file\//i.test(finalURL)) {
    await response.body?.cancel();
    return { type:'video',url:finalURL,...delivery(finalURL),quality,qualities:[] };
  }
  const text = await readLimited(response, 2*1024*1024);
  const urls = extractStreams(text, finalURL);
  if (!urls.length) throw new SourceError('لم يُرجع هذا السيرفر رابط فيديو صالحًا للويب. اختر سيرفرًا آخر.', 422, 'UNRESOLVED_MEDIA');
  const next = urls[0];
  return { type:/\.m3u8(\?|$)/i.test(next)?'hls':'video',url:next,...delivery(next,{...headers,Referer:finalURL}),quality,qualities:[] };
}
export function rewriteHLS(text, base, headers = {}) {
  return text.split(/\r?\n/).map(line => {
    if (!line.trim()) return line;
    if (line.startsWith('#')) return line.replace(/URI="([^"]+)"/g, (_,url)=>`URI="${mediaURL(new URL(url,base).href,headers)}"`);
    return mediaURL(new URL(line.trim(),base).href,headers);
  }).join('\n');
}
