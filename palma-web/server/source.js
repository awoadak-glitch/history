import {unwrapURL} from './media.js';

// Match Android's preference for the final media URL carried by the deep link.
// Do not copy cookies or arbitrary headers from source responses.
export function playbackSource(source) {
  const headers={};
  const add=(name,value)=>{if(typeof value==='string'&&value.length<1500&&!/[\r\n]/.test(value))headers[name]=value;};
  let deep;
  try { deep=new URL(source.deep_link); } catch {}
  for(const [parameter,name] of [['ua','User-Agent'],['referer','Referer'],['referrer','Referer'],['origin','Origin']]) add(name,deep?.searchParams.get(parameter));
  add('User-Agent',source.user_agent);add('Referer',source.referrer||source.referer);
  for(const [key,value] of Object.entries(source.headers||{})){
    const name={'user-agent':'User-Agent',referer:'Referer',origin:'Origin'}[key.toLowerCase()];if(name)add(name,value);
  }
  const candidates=[source.deep_link,source.url,source.stream_url,source.video_url,source.download_url,source.link].filter(Boolean);
  for(const candidate of candidates){try{return {url:unwrapURL(candidate),headers};}catch{}}
  return {url:unwrapURL(''),headers};
}
