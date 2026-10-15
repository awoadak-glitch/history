import {decodePlaybackLink} from './tdm-link.js';
import {sourceSessionHeaders} from './source-session.js';

// The native deep link is the actual playback contract. In particular, channel
// deep links can carry a different signed URL from stream_url. Keep URL and
// player identity together, including when a plain HTTP field also exists.
export function playbackSource(source,{provider}={}) {
  let decoded,selected;
  const candidates=[source.deep_link,source.url,source.stream_url,source.video_url,source.download_url,source.link].filter(Boolean);
  for(const candidate of candidates){try{decoded=decodePlaybackLink(candidate);selected=candidate;break;}catch{}}
  if(!decoded)return decodePlaybackLink('');
  const headers={...decoded.headers};
  const add=(name,value)=>{if(typeof value==='string'&&value.length>0&&value.length<1500&&!/[\x00-\x1f\x7f]/.test(value))headers[name]=value;};
  add('User-Agent',source.user_agent);add('Referer',source.referrer||source.referer);
  for(const [key,value] of Object.entries(source.headers||{})){
    const name={'user-agent':'User-Agent',referer:'Referer',origin:'Origin'}[key.toLowerCase()];if(name)add(name,value);
  }
  return {url:decoded.url,headers:provider==='palma'&&selected===source.deep_link?sourceSessionHeaders(source,decoded.url,headers):headers};
}
