import {SourceError} from './http.js';

// Interoperability with the supplied TDM 2.0.6 APK. This is URI transport
// decoding, not a browser, account session, or DRM implementation.
const schemes=new Set(['tdmvideo:','tdm:','tdmbrowser:','tdmstory:','tmdownloader:','tmhelper:','urlplayer:','urlvplayer:']);
const transportKey=Buffer.from('jMkn9kbN4Xr8V3NKXdsRA7QwY4Ars9mv4Xr8V3NKXYFhbYgw');
const invalid=()=>new SourceError('لم يُرجع رابط المشغّل عنوان فيديو صالحًا.',422,'NATIVE_PLAYER_REQUIRED');
const safeHeader=value=>typeof value==='string'&&value.length>0&&value.length<1500&&!/[\x00-\x1f\x7f]/.test(value)?value:null;
function base64(value) {
  // Android accepts both Base64 alphabets; a literal + in an old deep-link
// query may have been decoded as a space by URLSearchParams.
  const normalized=value.replaceAll(' ','+');
  if(!/^[A-Za-z0-9+/_-]+={0,2}$/.test(normalized))throw invalid();
  return Buffer.from(normalized,'base64');
}
function transportDecode(value) {
  const bytes=base64(value);
  for(let i=0;i<bytes.length;i++)bytes[i]^=transportKey[i%transportKey.length];
  try{return decodeURIComponent(bytes.toString('utf8').replaceAll('+',' '));}catch{throw invalid();}
}
function rawParameter(url,name) {
  const value=url.search.slice(1).split('&').find(part=>part.startsWith(name+'='))?.slice(name.length+1);
  if(value==null)return '';
  try{return decodeURIComponent(value);}catch{throw invalid();}
}

export function decodePlaybackLink(value) {
  let raw=String(value||'').trim().replaceAll('&amp;','&');
  const headers={};let protocol='http',mode='direct';
  if(raw.length>32768)throw invalid();
  for(let depth=0;depth<6;depth++) {
    if(/^https?:\/\//i.test(raw)) {
      let url;try{url=new URL(raw);}catch{throw invalid();}
      if(url.username||url.password||/[\x00-\x1f\x7f]/.test(raw))throw invalid();
      // Preserve the original signed query: never decode an HTTP URL again.
      return {url:raw,headers,protocol,mode};
    }
    let parsed;try{parsed=new URL(raw);}catch{}
    if(parsed) {
      if(!schemes.has(parsed.protocol))throw invalid();
      protocol=parsed.protocol.slice(0,-1);
      const query=parsed.searchParams;
      const encoded=['url','link','video','src','stream','download'].map(key=>query.get(key)).find(Boolean);
      if(!encoded)throw invalid();
      const ua=safeHeader(query.get('user_agent')||query.get('ua')||query.get('agent'));
      if(ua)headers['User-Agent']=ua;
      else if(parsed.protocol==='tdmvideo:'&&!headers['User-Agent'])headers['User-Agent']='TDMPlayer/2.0.6';
      for(const [parameter,name] of [['referer','Referer'],['referrer','Referer'],['origin','Origin']]) {
        const header=safeHeader(query.get(parameter));if(header)headers[name]=header;
      }
      // TDM's tdmvideo handler does not consume Cookie query parameters.
      // Never import source cookies, Authorization, or browser session data.
      if(['urlplayer:','urlvplayer:'].includes(parsed.protocol)&&/(play|web)$/.test(parsed.hostname)) {
        raw=transportDecode(rawParameter(parsed,'url'));mode=parsed.hostname.endsWith('web')?'page':'direct';
      } else {
        raw=encoded;
        if(['tdm:','tdmbrowser:'].includes(parsed.protocol)&&['open','browse','newtab'].includes(parsed.hostname))mode='page';
      }
      continue;
    }
    try{const decoded=decodeURIComponent(raw);if(decoded!==raw){raw=decoded;continue;}}catch{}
    try{raw=base64(raw).toString('utf8');}catch{throw invalid();}
  }
  throw invalid();
}
