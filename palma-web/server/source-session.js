// The owner explicitly authorized PALMA's source-issued playback Cookie for
// these two origins. This module never reads browser/account cookies.
export const SESSION_ORIGIN=Symbol('palma.sourceSessionOrigin');
const approved=new Set(['https://cdn.mediaoscar.com','https://still-dust-c9ae.manon-lol2000.workers.dev']);
const originOf=value=>{try{const u=new URL(value);return !u.username&&!u.password?u.origin:'';}catch{return '';}};

export function bindSourceSession(headers,origin,cookie) {
  const result={...headers};
  if(approved.has(origin)&&typeof cookie==='string'&&cookie.length<=4096&&!/[\x00-\x1f\x7f]/.test(cookie)) {
    // Only the source's observed playback credential, never unrelated cookies.
    const ot=cookie.split(';').map(part=>part.trim()).find(part=>/^ot=.+$/.test(part));
    if(ot){result.Cookie=ot;result[SESSION_ORIGIN]=origin;}
  }
  return result;
}

export function mediaHeadersForURL(url,headers={}) {
  const result={},origin=originOf(url),bound=headers[SESSION_ORIGIN];
  for(const [name,value] of Object.entries(headers)) {
    if(name.toLowerCase()==='authorization')continue;
    if(name.toLowerCase()==='cookie') {
      if(origin===bound&&approved.has(origin))result.Cookie=value;
    }else result[name]=value;
  }
  if(result.Cookie)result[SESSION_ORIGIN]=bound;
  return result;
}

export function sourceSessionHeaders(source,url,headers) {
  try {
    const deep=new URL(source.deep_link);
    if(!['tdmvideo:','tmdownloader:'].includes(deep.protocol))return headers;
    let target=deep.searchParams.get('url')||'';
    if(!/^https?:\/\//.test(target)&&/^[A-Za-z0-9+/_=-]+$/.test(target))target=Buffer.from(target,'base64').toString('utf8');
    if(originOf(target)!==originOf(url))return headers;
    return bindSourceSession(headers,originOf(url),deep.searchParams.get('cookies'));
  }catch{return headers;}
}
