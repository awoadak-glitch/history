#!/usr/bin/env node
// Real read-only integration check. Never print signed media URLs or tickets.
import http from 'node:http';
import {writeFile} from 'node:fs/promises';
import catalog from '../../palma-web/api/catalog.js';
import resolve from '../../palma-web/api/resolve.js';
import media from '../../palma-web/api/media.js';

const handlers={'/api/catalog':catalog,'/api/resolve':resolve,'/api/media':media};
const server=http.createServer(async(req,res)=>{
  try {await handlers[new URL(req.url,'http://localhost').pathname](req,res);}
  catch {if(!res.headersSent)res.writeHead(500);res.end();}
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const base=process.env.VERIFY_BASE || `http://127.0.0.1:${server.address().port}`;
const checks=[];
async function json(path,query){
  const response=await fetch(base+path+'?'+new URLSearchParams(query),{signal:AbortSignal.timeout(55000)});
  const value=await response.json();
  if(!response.ok)throw Error(`${path}: HTTP ${response.status}; ${value.code||'failed'}`);
  checks.push({step:query.route||'resolve',status:response.status});
  return value;
}
try {
  const home=(await json('/api/catalog',{provider:'palma',route:'api/v2/home.php'})).data;
  checks.at(-1).sections=home.sections.length;checks.at(-1).banners=home.banners.length;
  const banner=home.banners.find(b=>b.item_type==='series');
  if(!banner)throw Error('No series banner returned');
  const id=String(banner.item_id);
  const detail=(await json('/api/catalog',{provider:'palma',route:'api/series/show.php',id})).data;
  const seasons=(await json('/api/catalog',{provider:'palma',route:'api/seasons/',series_id:id})).data;
  const episodes=(await json('/api/catalog',{provider:'palma',route:'api/episodes/',season_id:String(seasons[0].id),page:'1',per_page:'20'})).data;
  const episode=String(episodes[0].id);
  const e=(await json('/api/catalog',{provider:'palma',route:'api/episodes/show.php',id:episode})).data;
  checks.at(-1).watch_links=e.watch_links.length;
  const index=Math.max(0,e.watch_links.findIndex(x=>x.is_abr===1));
  const result=await json('/api/resolve',{provider:'palma',kind:'series',id,episode,index:String(index),target:'web'});
  if(result.type!=='hls'||!result.playable?.startsWith('/api/media?'))throw Error('Expected a proxied HLS source');
  let next=new URL(result.playable,base).href;
  for(let level=0;level<3;level++){
    const response=await fetch(next,{headers:level===2?{Range:'bytes=0-1023'}:{},signal:AbortSignal.timeout(45000)});
    if(!response.ok)throw Error(`Media level ${level}: HTTP ${response.status}`);
    if(level===2){
      const reader=response.body.getReader(),sample=await reader.read();await reader.cancel();
      checks.push({step:'video bytes through proxy',status:response.status,content_type:response.headers.get('content-type'),sample_bytes:sample.value?.length||0});
    }else{
      const text=await response.text();
      if(!text.startsWith('#EXTM3U'))throw Error('Media response was not an HLS playlist');
      const line=text.split(/\r?\n/).find(line=>line.trim()&&!line.startsWith('#'));
      if(!line?.startsWith('/api/media?t='))throw Error('Playlist child was not rewritten');
      checks.push({step:level===0?'HLS master through proxy':'HLS variant through proxy',status:response.status});
      next=new URL(line,base).href;
    }
  }
  const evidence={verified_at:new Date().toISOString(),environment:process.env.VERIFY_BASE?'deployment':'local HTTP API',title:detail.title_ar,series_id:id,episode_id:episode,checks};
  console.log(JSON.stringify(evidence,null,2));
  if(process.env.VERIFY_REPORT)await writeFile(process.env.VERIFY_REPORT,JSON.stringify(evidence,null,2)+'\n');
}catch(error){console.error(JSON.stringify({error:error.message,completed:checks}));process.exitCode=1;}
finally{server.closeAllConnections();server.close();}
