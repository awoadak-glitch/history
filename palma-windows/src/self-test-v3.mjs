import assert from 'node:assert/strict';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import path from 'node:path';
import {APP_ORIGIN} from './bridge.mjs';
import {downloadVideo,runFFmpeg} from './download-core.mjs';

// Explicit release check: real source, packaged Windows storage, browser decoding,
// and a short real HLS download. No credentials or signed URLs enter the report.
export async function runSelfTest({window,handler,setTestHandler,ffmpeg,fetchResource,unticket}) {
  const directory=path.resolve(process.env.PALMA_TEST_OUTPUT||'test-output');
  await mkdir(directory,{recursive:true});
  const checks=[],report={platform:process.platform,versions:{electron:process.versions.electron,node:process.versions.node},verified_at:new Date().toISOString(),checks};
  const json=async(route,query)=>{
    const response=await handler(new Request(APP_ORIGIN+route+'?'+new URLSearchParams(query)));
    const value=await response.json();
    if(!response.ok)throw Error(`${query.route||route}: ${response.status} ${value.code||''}`);
    return value;
  };
  let timer;
  try {
    assert.equal(process.platform,'win32');
    const preferences=window.webContents.getLastWebPreferences();
    assert.equal(preferences.nodeIntegration,false);assert.equal(preferences.contextIsolation,true);assert.equal(preferences.sandbox,true);
    const home=(await json('/api/catalog',{provider:'palma',route:'api/v2/home.php'})).data;
    const series=home.banners.find(item=>item.item_type==='series');assert.ok(series);
    const seriesId=String(series.item_id);
    const seasons=(await json('/api/catalog',{provider:'palma',route:'api/seasons/',series_id:seriesId})).data;assert.ok(seasons.length);
    const episodes=(await json('/api/catalog',{provider:'palma',route:'api/episodes/',season_id:String(seasons[0].id),page:'1',per_page:'20'})).data;assert.ok(episodes.length);
    const episode=String(episodes[0].id);
    const cases=[
      {label:'movie HLS',kind:'movie',id:'4655',index:'0',minDuration:300},
      {label:'movie B2',kind:'movie',id:'4528',index:'1',minDuration:300},
      {label:'series episode',kind:'series',id:seriesId,episode,index:'0',minDuration:300},
      {label:'live channel',kind:'channel',id:'63',index:'0',live:true},
    ];
    let completed,failed;
    const rendered=new Promise((resolve,reject)=>{completed=resolve;failed=reject;});
    timer=setTimeout(()=>failed(Error('Windows V3 playback verification timed out')),480000);
    const driver=await readFile(new URL('./self-test-v3-driver.js',import.meta.url),'utf8');
    setTestHandler(request=>{
      const url=new URL(request.url);
      if(url.pathname==='/')return (async()=>{const response=await handler(request);return new Response((await response.text()).replace('</body>','<script type="module" src="/__test/v3-driver.js"></script></body>'),{headers:response.headers});})();
      if(url.pathname==='/__test/v3-driver.js')return new Response(driver,{headers:{'Content-Type':'text/javascript'}});
      if(url.pathname==='/__test/cases')return Response.json(cases);
      if(url.pathname==='/__test/check')return (async()=>{const value=await request.json();checks.push(value);console.log(JSON.stringify(value));return new Response('ok');})();
      if(url.pathname==='/__test/shot')return (async()=>{const name=url.searchParams.get('name');if(!/^[a-z-]+$/.test(name))return new Response('invalid',{status:400});await writeFile(path.join(directory,name+'.png'),(await window.capturePage()).toPNG());return new Response('ok');})();
      if(url.pathname==='/__test/result'){completed(true);return new Response('ok');}
      if(url.pathname==='/__test/failure'){failed(Error(url.searchParams.get('message')));return new Response('ok');}
      return undefined;
    });
    window.show();await window.loadURL(APP_ORIGIN+'/#/home');await rendered;clearTimeout(timer);
    try {
    const download=await json('/api/resolve',{provider:'palma',kind:'movie',id:'4655',index:'0',download:'1'});
    assert.equal(download.type,'hls');
    const payload=unticket(new URL(download.playable,APP_ORIGIN).searchParams.get('t'));
    // Check the download playlist too: a decodable notice is not a successful
    // download. Follow the source's first variant for this release check.
    let manifestURL=payload.u,verifiedDuration=false;
    for(let depth=0;depth<3;depth++){
      const {response,url}=await fetchResource(manifestURL,{headers:payload.h});
      assert.ok(response.ok);const playlist=await response.text();
      const duration=[...playlist.matchAll(/^#EXTINF:([\d.]+)/gm)].reduce((sum,m)=>sum+Number(m[1]),0);
      if(duration){assert.ok(duration>300,'Download returned a short replacement');verifiedDuration=true;break;}
      assert.ok(playlist.includes('#EXT-X-STREAM-INF'),'Download playlist has no video segments');
      const variant=playlist.split(/\r?\n/).find(line=>line.trim()&&!line.startsWith('#'));assert.ok(variant);
      manifestURL=new URL(variant.trim(),url).href;
    }
    assert.ok(verifiedDuration,'Download duration was not verified');
    const sample=path.join(directory,'download-sample.mp4');
    const saved=await downloadVideo({url:payload.u,headers:payload.h,hls:true,output:sample,cacheRoot:path.join(directory,'cache'),ffmpeg,fetchResource,maxSegments:2});
    assert.ok(saved.bytes>1024);
    await runFFmpeg(ffmpeg,['-hide_banner','-loglevel','error','-nostdin','-i',sample,'-t','1','-f','null','-']);
    checks.push({step:'V3 download resolved, HLS remuxed and decoded',success:true,bytes:saved.bytes,sample_segments:2});
    }catch(error){checks.push({step:'V3 download',success:false,error:error.message.replace(/https?:\/\/[^\s]+/g,'[source URL]')});}
    const failures=checks.filter(check=>check.success===false);
    if(failures.length)throw Error(`${failures.length} Windows playback/download checks failed`);
    report.success=true;
  }catch(error){report.success=false;report.error=error.message;await writeFile(path.join(directory,'failure.png'),(await window.capturePage()).toPNG()).catch(()=>{});throw error;}
  finally{clearTimeout(timer);await writeFile(path.join(directory,'verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));}
}
