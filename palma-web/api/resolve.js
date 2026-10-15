import { catalogue } from './catalog.js';
import { params, json, fail, SourceError } from '../server/http.js';
import { resolveMedia, witcherSource } from '../server/media.js';
import {playbackSource} from '../server/source.js';

const array = o => Array.isArray(o) ? o : arrayFind(o);
function arrayFind(o) { if(!o) return []; for(const k of ['items','data','sources','streams','links']) if(Array.isArray(o[k])) return o[k]; return []; }
const body = o => o?.data && !Array.isArray(o.data) ? o.data : o;
export function createResolver({getCatalogue=catalogue,getMedia=resolveMedia}={}) {
return async function handler(req,res) {
  if(req.method !== 'GET') return json(res,405,{error:'طريقة طلب غير مدعومة.'});
  try {
    const p=params(req), provider=p.get('provider')||'palma',id=p.get('id')||'',episode=p.get('episode')||'',kind=p.get('kind')||'movie',index=Number(p.get('index')||0),download=p.get('download')==='1';
    if(!id || id.length>300 || !Number.isInteger(index) || index<0 || index>300) throw new SourceError('اختيار السيرفر غير صالح.',400);
    let source, servers=[];
    if(provider==='witcher') {
      if(!episode || episode.length>80) throw new SourceError('الحلقة غير صالحة.',400);
      source=await getCatalogue('witcher',`anime/${encodeURIComponent(id)}/episodes/${encodeURIComponent(episode)}/servers`,{resolve:index});
      const direct=witcherSource(source);
      const media=await getMedia(direct.url,{},direct.quality,p.get('target')==='external'&&!download);
      const available=await getCatalogue('witcher',`anime/${encodeURIComponent(id)}/episodes/${encodeURIComponent(episode)}/servers`).catch(()=>({items:[]}));
      return json(res,200,{...media,name:direct.name,selection:{provider,kind,id,episode,index},servers:array(available).map((s,index)=>({index,name:s.name||'سيرفر '+(index+1),quality:s.quality||''}))});
    } else if(provider==='drama') {
      if(!/^\d+$/.test(episode||id)) throw new SourceError('العمل غير صالح.',400);
      const value=await getCatalogue('drama',kind==='channel'?`channel/by/${id}/`:`${episode?'episode':'movie'}/source/by/${episode||id}/`);
      source=array(kind==='channel'?body(value)?.sources:value)[index];
    } else if(provider==='palma') {
      if(!/^\d+$/.test(id) || (episode&&!/^\d+$/.test(episode))) throw new SourceError('العمل غير صالح.',400);
      if(kind==='match-video') {
        const matchId=p.get('match_id');
        if(!/^\d+$/.test(matchId||''))throw new SourceError('المباراة غير صالحة.',400);
        const videos=array(await getCatalogue('palma','api/matches/event_videos.php',{match_id:matchId,page:1,limit:100}));
        source=videos.find(video=>String(video.id)===id);
      } else if(episode && kind!=='anime') {
        // Episode detail issues fresh CDN URLs. The legacy watch_links endpoint
        // returns storage-origin URLs that the original player no longer uses.
        const value=body(await getCatalogue('palma','api/episodes/show.php',{id:episode}));
        servers=array(download?value.download_links:value.watch_links);source=servers[index];
      }
      else {
        const route={movie:'api/movies/show.php',channel:'api/channels/show.php',anime:episode?'api/anime/episodes/show.php':'api/anime/show.php',wrestling:'api/wrestling/show.php',video:'api/matches/event_videos.php'}[kind];
        if(!route) throw new SourceError('مسار مشاهدة غير صالح.',400);
        const value=body(await getCatalogue('palma',route,{id:episode||id}));
        const list=download?(value.download_links||value.downloadLinks):(value.watch_links||value.watchLinks||value.streams||value.sources);
        servers=array(list);source=servers[index];
      }
    } else throw new SourceError('المصدر غير صالح.',400);
    if(!source) throw new SourceError('السيرفر غير متاح حاليًا.',404);
    if(source.premium && !['0',0,'false',false,'no'].includes(source.premium)) throw new SourceError('هذا السيرفر يحتاج حسابًا لدى المصدر.',403,'PREMIUM_SOURCE');
    const {url,headers}=playbackSource(source);
    const media=await getMedia(url,headers,source.quality||'',p.get('target')==='external'&&!download);
    json(res,200,{...media,live:kind==='channel'||media.live||false,name:source.server_name||source.name||'',selection:{provider,kind,id,episode,index,match_id:p.get('match_id')||''},servers:servers.map((s,index)=>({index,name:s.server_name||s.name||'سيرفر '+(index+1),quality:s.quality||''}))});
  } catch(e) { fail(res,e); }
}
}
export default createResolver();
