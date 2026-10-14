import { catalogue } from './catalog.js';
import { params, json, fail, SourceError } from '../server/http.js';
import { resolveMedia, witcherPlayback } from '../server/media.js';

const array = o => Array.isArray(o) ? o : arrayFind(o);
function arrayFind(o) { if(!o) return []; for(const k of ['items','data','sources','streams','links']) if(Array.isArray(o[k])) return o[k]; return []; }
const body = o => o?.data && !Array.isArray(o.data) ? o.data : o;
export default async function handler(req,res) {
  if(req.method !== 'GET') return json(res,405,{error:'طريقة طلب غير مدعومة.'});
  try {
    const p=params(req), provider=p.get('provider')||'palma',id=p.get('id')||'',episode=p.get('episode')||'',kind=p.get('kind')||'movie',index=Number(p.get('index')||0),download=p.get('download')==='1';
    if(!id || id.length>300 || !Number.isInteger(index) || index<0 || index>300) throw new SourceError('اختيار السيرفر غير صالح.',400);
    let source;
    if(provider==='witcher') {
      if(!episode || episode.length>80) throw new SourceError('الحلقة غير صالحة.',400);
      source=await catalogue('witcher',`anime/${encodeURIComponent(id)}/episodes/${encodeURIComponent(episode)}/servers`,{resolve:index});
      // Anime Witcher's own web player consumes this already-resolved URL directly.
      // Do not require PALMA's optional media-proxy secret or resolve the file twice.
      return json(res,200,witcherPlayback(source));
    } else if(provider==='drama') {
      if(!/^\d+$/.test(episode||id)) throw new SourceError('العمل غير صالح.',400);
      const value=await catalogue('drama',kind==='channel'?`channel/by/${id}/`:`${episode?'episode':'movie'}/source/by/${episode||id}/`);
      source=array(kind==='channel'?body(value)?.sources:value)[index];
    } else if(provider==='palma') {
      if(!/^\d+$/.test(id) || (episode&&!/^\d+$/.test(episode))) throw new SourceError('العمل غير صالح.',400);
      if(episode && kind!=='anime') {
        // Episode detail issues fresh CDN URLs. The legacy watch_links endpoint
        // returns storage-origin URLs that the original player no longer uses.
        const value=body(await catalogue('palma','api/episodes/show.php',{id:episode}));
        source=array(download?value.download_links:value.watch_links)[index];
      }
      else {
        const route={movie:'api/movies/show.php',channel:'api/channels/show.php',anime:episode?'api/anime/episodes/show.php':'api/anime/show.php',wrestling:'api/wrestling/show.php',video:'api/matches/event_videos.php'}[kind];
        if(!route) throw new SourceError('مسار مشاهدة غير صالح.',400);
        const value=body(await catalogue('palma',route,{id:episode||id}));
        const list=download?(value.download_links||value.downloadLinks):(value.watch_links||value.watchLinks||value.streams||value.sources);
        source=array(list)[index];
      }
    } else throw new SourceError('المصدر غير صالح.',400);
    if(!source) throw new SourceError('السيرفر غير متاح حاليًا.',404);
    if(source.premium && !['0',0,'false',false,'no'].includes(source.premium)) throw new SourceError('هذا السيرفر يحتاج حسابًا لدى المصدر.',403,'PREMIUM_SOURCE');
    const url=source.url||source.stream_url||source.video_url||source.download_url||source.link||source.deep_link;
    const headers={};
    if (source.deep_link) {
      try {
        const deep = new URL(source.deep_link);
        for (const [parameter, header] of [['ua','User-Agent'],['referer','Referer'],['origin','Origin']]) {
          const value = deep.searchParams.get(parameter);
          if (value && value.length < 1000 && !/[\r\n]/.test(value)) headers[header]=value;
        }
      } catch {}
    }
    for(const [k,v] of Object.entries(source.headers||{})) if(['referer','origin','user-agent'].includes(k.toLowerCase()) && typeof v==='string' && v.length<1000 && !/[\r\n]/.test(v)) headers[k]=v;
    const media=await resolveMedia(url,headers,source.quality||'',p.get('target')==='external'&&!download);
    json(res,200,media);
  } catch(e) { fail(res,e); }
}
