import assert from 'node:assert/strict';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import path from 'node:path';
import {APP_ORIGIN} from './bridge.mjs';
import {downloadVideo,runFFmpeg} from './download-core.mjs';

// Runs only with --self-test. Uses live catalogue and media, never canned content.
// Reports contain counts/status, never signed source URLs or playback tickets.
export async function runSelfTest({window,handler,setTestHandler,ffmpeg,fetchResource,unticket}) {
  const directory=path.resolve(process.env.PALMA_TEST_OUTPUT||'test-output');
  await mkdir(directory,{recursive:true});
  const checks=[];
  const report={platform:process.platform,versions:{electron:process.versions.electron,node:process.versions.node},verified_at:new Date().toISOString(),checks};
  async function json(route,query={}){
    const response=await handler(new Request(APP_ORIGIN+route+'?'+new URLSearchParams(query)));
    const value=await response.json();
    if(!response.ok)throw Error(`${query.route||route}: ${response.status} ${value.code||''}`);
    checks.push({step:query.route||route,status:response.status});return value;
  }
  try {
    const preferences=window.webContents.getLastWebPreferences();
    assert.equal(preferences.nodeIntegration,false);assert.equal(preferences.contextIsolation,true);assert.equal(preferences.sandbox,true);
    const home=(await json('/api/catalog',{provider:'palma',route:'api/v2/home.php'})).data;
    assert.ok(home.sections.length>0);assert.ok(home.banners.length>0);
    Object.assign(checks.at(-1),{sections:home.sections.length,banners:home.banners.length});
    const series=home.banners.find(x=>x.item_type==='series');assert.ok(series);
    const id=String(series.item_id);
    const detail=(await json('/api/catalog',{provider:'palma',route:'api/series/show.php',id})).data;
    const seasons=(await json('/api/catalog',{provider:'palma',route:'api/seasons/',series_id:id})).data;assert.ok(seasons.length);
    const episodes=(await json('/api/catalog',{provider:'palma',route:'api/episodes/',season_id:String(seasons[0].id),page:'1',per_page:'20'})).data;assert.ok(episodes.length);
    const episode=String(episodes[0].id);
    const episodeData=(await json('/api/catalog',{provider:'palma',route:'api/episodes/show.php',id:episode})).data;assert.ok(episodeData.watch_links.length);
    const index=Math.max(0,episodeData.watch_links.findIndex(x=>x.is_abr===1));
    const playback=await json('/api/resolve',{provider:'palma',kind:'series',id,episode,index:String(index),target:'web'});
    assert.equal(playback.type,'hls');
    report.series_id=id;report.episode_id=episode;report.title=detail.title_ar;
    let next=new URL(playback.playable,APP_ORIGIN).href;
    for(let level=0;level<3;level++){
      const response=await handler(new Request(next,{headers:level===2?{Range:'bytes=0-1023'}:{}}));
      assert.ok(response.ok,`Media ${level}: ${response.status}`);
      if(level===2){const reader=response.body.getReader();const sample=await reader.read();await reader.cancel();assert.ok(sample.value.length>0);checks.push({step:'video bytes',status:response.status,content_type:response.headers.get('content-type'),bytes:sample.value.length});}
      else{const manifest=await response.text();assert.ok(manifest.startsWith('#EXTM3U'));const child=manifest.split(/\r?\n/).find(x=>x&&!x.startsWith('#'));assert.ok(child.startsWith('/api/media?'));next=new URL(child,APP_ORIGIN).href;checks.push({step:level?'HLS variant':'HLS master',status:response.status});}
    }
    const payload=unticket(new URL(playback.playable,APP_ORIGIN).searchParams.get('t'));
    const sample=path.join(directory,'download-sample.mp4');
    const saved=await downloadVideo({url:payload.u,headers:payload.h,hls:true,output:sample,cacheRoot:path.join(directory,'cache'),ffmpeg,fetchResource,maxSegments:2});
    assert.ok(saved.bytes>1024);
    await runFFmpeg(ffmpeg,['-hide_banner','-loglevel','error','-nostdin','-i',sample,'-t','1','-f','null','-']);
    checks.push({step:'HLS downloaded, remuxed to MP4 and decoded',bytes:saved.bytes,sample_segments:2});
    let completed,failed;
    const rendered=new Promise((resolve,reject)=>{completed=resolve;failed=reject;});
    const timer=setTimeout(()=>failed(Error('Requested desktop flow exceeded eight minutes')),480000);
    const script=await readFile(new URL('./self-test-driver.js',import.meta.url),'utf8');
    setTestHandler(request=>{
      const url=new URL(request.url);
      if(url.pathname==='/')return (async()=>{const response=await handler(request);const html=(await response.text()).replace('</body>','<script type="module" src="/__test/driver.js"></script></body>');return new Response(html,{headers:response.headers});})();
      if(url.pathname==='/__test/driver.js')return new Response(script,{headers:{'Content-Type':'text/javascript'}});
      if(url.pathname==='/__test/media')return Response.json({playback,item:{provider:'palma',kind:'series',id,title:detail.title_ar,episodeNumber:episodeData.episode_number},episode});
      if(url.pathname==='/__test/check')return (async()=>{const value=await request.json();checks.push(value);console.log(JSON.stringify(value));return new Response('ok');})();
      if(url.pathname==='/__test/screenshot')return (async()=>{const name=url.searchParams.get('name');if(!/^[a-z]+$/.test(name))return new Response('invalid',{status:400});await writeFile(path.join(directory,name+'.png'),(await window.capturePage()).toPNG());return new Response('ok');})();
      if(url.pathname==='/__test/fullscreen')return (async()=>{
        await window.webContents.executeJavaScript("document.querySelector('#fullscreen-button').click()",true);
        for(let i=0;i<30&&!window.isFullScreen();i++)await new Promise(resolve=>setTimeout(resolve,100));
        const element=await window.webContents.executeJavaScript("!!document.fullscreenElement && document.fullscreenElement.classList.contains('player-wrap')");
        return Response.json({native:window.isFullScreen(),element,bounds:window.getBounds()});
      })();
      if(url.pathname==='/__test/result'){completed(true);return new Response('ok');}
      if(url.pathname==='/__test/failure'){failed(Error(url.searchParams.get('message')));return new Response('ok');}
      return undefined;
    });
    window.show();
    await window.loadURL(APP_ORIGIN+'/#/home');
    let result;try{result=await rendered;}finally{clearTimeout(timer);}
    assert.equal(result,true);
    report.success=true;
  }catch(error){report.success=false;report.error=error.message;await writeFile(path.join(directory,'failure.png'),(await window.capturePage()).toPNG()).catch(()=>{});throw error;}
  finally{await writeFile(path.join(directory,'verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));}
}
