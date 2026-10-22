import {SESSION_ORIGIN,mediaHeadersForURL} from './source-session.js';

// TDM 2.2.2 sd.a.K: the source transports some HLS playlists as .css and
// segments as .js. Recompute their request metadata for every media request.
// Only an already scoped source-issued session enables this transport.
export function tdmMediaHeaders(raw,headers={}) {
  const result=mediaHeadersForURL(raw,headers),url=new URL(raw);
  if(!result.Cookie||result[SESSION_ORIGIN]!==url.origin)return result;
  const extension=/\.(css|js)$/i.exec(url.pathname)?.[1]?.toLowerCase();
  if(!extension)return result;
  const ref=result.Referer||url.origin+'/';
  let site='same-origin';
  try {
    const other=new URL(ref);
    if(other.hostname!==url.hostname)site=other.hostname.split('.').slice(-2).join('.')===url.hostname.split('.').slice(-2).join('.')?'same-site':'cross-site';
  }catch{}
  return {...result,Accept:extension==='css'?'text/css,*/*;q=0.1':'*/*','Accept-Language':'en-US,en;q=0.9',
    'Sec-Fetch-Dest':extension==='css'?'style':'script','Sec-Fetch-Mode':'no-cors','Sec-Fetch-Site':site,Referer:ref};
}
