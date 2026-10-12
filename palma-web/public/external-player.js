// Shared, pure URL builders. Native applications receive only a prepared media
// URL and display metadata, never the page URL or an arbitrary command.
export const PLAYER_OPTIONS = [['auto','تلقائي حسب الجهاز'],['mx','MX Player للهاتف'],['awr','AWR MX للكمبيوتر'],['browser','داخل المتصفح']];

export function playbackTarget(preference='auto',userAgent='') {
  if (['mx','awr','browser'].includes(preference)) return preference;
  if (/Android/i.test(userAgent)) return 'mx';
  if (/Windows/i.test(userAgent)) return 'awr';
  return 'browser';
}

export function externalSource(raw) {
  if (typeof raw!=='string' || raw.length>10000 || /[\r\n\x00]/.test(raw)) throw Error('رابط الفيديو غير صالح.');
  let url; try { url=new URL(raw); } catch { throw Error('رابط الفيديو غير صالح.'); }
  if (!['https:','http:'].includes(url.protocol) || url.username || url.password) throw Error('هذا الرابط يحتاج تجهيزًا قبل فتح المشغّل.');
  return url.href;
}

export function playbackIdentity(item,episode='') {
  return `palma:${item.provider}:${item.kind}:${encodeURIComponent(item.id)}:${encodeURIComponent(episode||'full')}`;
}

export function playbackTitle(item,episode='') {return `${item.title||'PALMA VISION'}${episode?' · الحلقة '+(parseInt(episode)||episode):''}`.slice(0,400);}

export function mxPlayerLink(media,item,episode='',edition='ad') {
  const url=new URL(externalSource(media.url));
  // '#' delimits Intent extras; encode a media fragment so it cannot append extras.
  let data=url.href.slice(url.protocol.length).replaceAll('#','%23');
  const headers=Object.entries(media.headers||{}).filter(([key,value])=>['referer','user-agent','origin'].includes(key.toLowerCase())&&typeof value==='string'&&value.length<=1024&&!/[\r\n\x00]/.test(value));
  if(headers.length)data+='|'+headers.map(([key,value])=>`${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&');
  const pkg=edition==='pro'?'com.mxtech.videoplayer.pro':'com.mxtech.videoplayer.ad';
  const title=encodeURIComponent(playbackTitle(item,episode));
  const fallback=encodeURIComponent(`https://play.google.com/store/apps/details?id=${pkg}`);
  return `intent:${data}#Intent;scheme=${url.protocol.slice(0,-1)};package=${pkg};action=android.intent.action.VIEW;type=video/*;S.title=${title};S.browser_fallback_url=${fallback};end`;
}

export function awrPlayerLink(media,item,episode='') {
  const payload={v:1,source:externalSource(media.url),title:playbackTitle(item,episode),historyId:playbackIdentity(item,episode)};
  for(const [key,value] of Object.entries(media.headers||{})) {
    if(typeof value!=='string'||/[\r\n\x00]/.test(value))continue;
    if(key.toLowerCase()==='referer' && value.length<=1024)payload.referer=value;
    if(key.toLowerCase()==='user-agent' && value.length<=500)payload.userAgent=value;
  }
  const bytes=new TextEncoder().encode(JSON.stringify(payload));
  const encoded=btoa(Array.from(bytes,b=>String.fromCharCode(b)).join('')).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');
  return `awrmx://play?data=${encoded}`;
}
