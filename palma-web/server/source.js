import {unwrapURL} from './media.js';

// Prefer the fresh HTTP URL returned by the catalogue. Native deep links are
// a fallback; their player identity may require a paired session cookie.
// Do not copy cookies or arbitrary headers from source responses.
export function playbackSource(source) {
  const headers={};
  const add=(name,value)=>{if(typeof value==='string'&&value.length<1500&&!/[\r\n]/.test(value))headers[name]=value;};
  let deep;
  try { deep=new URL(source.deep_link); } catch {}
  const direct=[source.url,source.stream_url,source.video_url,source.download_url,source.link].filter(value=>typeof value==='string'&&/^https?:\/\//i.test(value));
  if(!direct.length&&!deep?.searchParams.has('cookies'))for(const [parameter,name] of [['ua','User-Agent'],['referer','Referer'],['referrer','Referer'],['origin','Origin']]) add(name,deep?.searchParams.get(parameter));
  add('User-Agent',source.user_agent);add('Referer',source.referrer||source.referer);
  for(const [key,value] of Object.entries(source.headers||{})){
    const name={'user-agent':'User-Agent',referer:'Referer',origin:'Origin'}[key.toLowerCase()];if(name)add(name,value);
  }
  const candidates=[...direct,source.deep_link,source.url,source.stream_url,source.video_url,source.download_url,source.link].filter(Boolean);
  for(const candidate of candidates){try{return {url:unwrapURL(candidate),headers};}catch{}}
  return {url:unwrapURL(''),headers};
}
