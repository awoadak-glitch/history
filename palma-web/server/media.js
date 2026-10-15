import { fetchPublic, readLimited, publicURL, SourceError, mediaURL, isPrivateIP } from './http.js';
import net from 'node:net';
import {inspectMedia} from './media-format.js';
import {decodePlaybackLink} from './tdm-link.js';
import {mediaHeadersForURL} from './source-session.js';

export function witcherSource(source) {
  let url;
  try { url = new URL(source?.url); } catch { throw new SourceError('لم يُرجع مصدر الأنمي رابط فيديو صالحًا.',422); }
  const host=url.hostname.replace(/^\[|\]$/g,'');
  if (!['https:','http:'].includes(url.protocol) || url.username || url.password || (url.port && !['80','443'].includes(url.port)) || !host.includes('.') || /(^|\.)(localhost|local|internal)$/.test(host) || (net.isIP(host) && isPrivateIP(host))) {
    throw new SourceError('لم يُرجع مصدر الأنمي رابط فيديو مباشرًا صالحًا.',422);
  }
  return {url:url.href,quality:source.quality||'',name:source.name||''};
}
export function witcherPlayback(source) {
  if(source?.direct!==true)throw new SourceError('لم يُرجع المصدر رابط فيديو مباشرًا.',422);
  const {url:raw}=witcherSource(source),url=new URL(raw);
  return { type:/\.m3u8(?:\?|$)/i.test(url.href)?'hls':'video',url:url.href,playable:url.href,download:url.href,quality:source.quality||'',name:source.name||'',qualities:[],delivery:'direct' };
}

export function unwrapURL(value) {
  return decodePlaybackLink(value).url;
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
  html=html.replaceAll('\\/','/').replaceAll('&quot;','"').replaceAll('&#39;',"'");
  const found = new Set();
  const patterns = [/(?:file|src|source|video_url|contentUrl|url)\s*[=:]\s*["']([^"']+)["']/gi, /<source[^>]+src=["']([^"']+)["']/gi, /<a[^>]+(?:id=["']downloadButton["'][^>]*href|href)=["']([^"']+)["'][^>]*(?:download|downloadButton)/gi, /["'](https?:[^"'\s]+\.(?:m3u8|mp4|webm|mkv|mpd|flv)(?:\?[^"'\s]*)?)["']/gi];
  for (const regex of patterns) for (const match of html.matchAll(regex)) {
    try { const url = new URL(match[1].replaceAll('\\/','/').replaceAll('&amp;','&'),base).href; if (/\.(m3u8|mp4|webm|mkv|mpd|flv)(\?|$)|\/api\/file\/|download\d+\.mediafire/.test(url)) found.add(url); } catch {}
  }
  return [...found];
}
export async function resolveMedia(raw, headers = {}, quality = '', external = false, state={seen:new Set(),depth:0}) {
  const decoded=decodePlaybackLink(raw);
  let url=decoded.url;
  headers={...decoded.headers,...headers};
  if(state.depth>5||state.seen.has(url))throw new SourceError('تعذر العثور على الفيديو داخل صفحة السيرفر.',422,'UNRESOLVED_MEDIA');
  state.seen.add(url);
  if (/pixeldrain\.com$/.test(new URL(url).hostname) && /^\/u\/[^/]+$/.test(new URL(url).pathname)) url = `https://pixeldrain.com/api/file/${new URL(url).pathname.split('/').pop()}`;
  const delivery=(url,h=headers)=>external?{playable:url,headers:mediaHeadersForURL(url,h),delivery:'external'}:{playable:mediaURL(url,h),delivery:'proxy'};
  const fetched=await fetchPublic(url,{headers:{...headers,Accept:'*/*'}});
  if(!fetched.response.ok){await fetched.response.body?.cancel();throw new SourceError('تعذر الاتصال بهذا السيرفر. اختر سيرفرًا آخر.',fetched.response.status===403?403:502,'MEDIA_DENIED');}
  const finalURL=fetched.url;
  if(new URL(finalURL).hostname==='notice.shahidtv.net'){await fetched.response.body?.cancel();throw new SourceError('المصدر أعاد رسالة رفض بدل البث. اختر سيرفرًا آخر.',403,'SOURCE_SESSION_REQUIRED');}
  const {type,response}=await inspectMedia(fetched.response,finalURL);
  if(type==='hls'){
    const text=await readLimited(response,2*1024*1024),variants=parseHLS(text,finalURL);
    return {type,url:finalURL,...delivery(finalURL),quality,live:!text.includes('#EXT-X-ENDLIST')&&!variants.length,qualities:variants.map(v=>({...v,...delivery(v.url)}))};
  }
  if(['video','mpegts','flv','dash'].includes(type)){
    await response.body?.cancel();return {type,url:finalURL,...delivery(finalURL),quality,qualities:[]};
  }
  const text=await readLimited(response,2*1024*1024);
  const urls=extractStreams(text,finalURL);
  // Public embed pages often nest the video player. Never evaluate page scripts here.
  for(const match of text.matchAll(/<iframe[^>]+src=["']([^"']+)["']/gi)){
    try{const next=new URL(match[1].replaceAll('&amp;','&'),finalURL);if(['https:','http:'].includes(next.protocol))urls.push(next.href);}catch{}
  }
  for(const next of [...new Set(urls)].slice(0,5)){
    try{return await resolveMedia(next,{...headers,Referer:finalURL},quality,external,{seen:state.seen,depth:state.depth+1});}catch(error){if(error.code==='INVALID_URL')throw error;}
  }
  throw new SourceError('لم يُرجع السيرفر ملف فيديو صالحًا. جرّب سيرفرًا آخر.',422,'UNRESOLVED_MEDIA');
}
export function rewriteHLS(text, base, headers = {}) {
  return text.split(/\r?\n/).map(line => {
    if (!line.trim()) return line;
    if (line.startsWith('#')) return line.replace(/URI="([^"]+)"/g, (_,url)=>`URI="${mediaURL(new URL(url,base).href,headers)}"`);
    return mediaURL(new URL(line.trim(),base).href,headers);
  }).join('\n');
}
