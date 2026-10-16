// Loaded only by the native --ez selfTest true entry point; no network URLs/tickets in reports.
import {openPlayer,closePlayer} from './playback.js';
const report={version:'1.0.0',platform:'Android emulator',checks:[],passed:false};
const wait=ms=>new Promise(r=>setTimeout(r,ms));
const check=(step,values={})=>{report.checks.push({step,...values});console.log('PALMA_TV_TEST '+JSON.stringify({step,...values}));};
async function until(predicate,label,timeout=65000){const end=Date.now()+timeout;while(Date.now()<end){const value=predicate();if(value)return value;await wait(200);}throw Error(label);}
async function api(route,params={}){const r=await fetch(route+'?'+new URLSearchParams(params),{signal:AbortSignal.timeout(120000)});const j=await r.json();if(!r.ok)throw Error('API '+r.status+' '+(j.code||''));return j;}
async function decoded(label){const video=await until(()=>document.querySelector('#player-dialog[open] video'),label+' player missing');video.muted=true;await until(()=>{if(document.querySelector('.player-error:not([hidden])'))throw Error(label+' playback error');return video.currentTime>3&&video.videoWidth>0&&video.getVideoPlaybackQuality().totalVideoFrames>0;},label+' decode timeout');check(label,{width:video.videoWidth,height:video.videoHeight,frames:video.getVideoPlaybackQuality().totalVideoFrames,duration:Number.isFinite(video.duration)?video.duration:'live'});return video;}
async function shot(name){console.log('PALMA_TV_TEST '+JSON.stringify({screenshot:name}));await wait(1600);}
try{
  report.webview=navigator.userAgent;report.viewport={width:innerWidth,height:innerHeight};
  let retries=0;
  await until(()=>{const card=document.querySelector('.media-card');if(card)return card;const retry=document.querySelector('.native-home-state .btn.primary');if(retry&&retries<2){check('Retry catalogue after connection error',{attempt:++retries});retry.click();}return false;},'PALMA home missing',130000);await document.fonts.ready;
  check('Native Node source catalogue and home',{cards:document.querySelectorAll('.media-card').length});
  const sidebar=document.getElementById('sidebar');if(sidebar.inert||sidebar.getBoundingClientRect().right>innerWidth+5)throw Error('TV navigation not visible');
  window.PalmaTV.focus(sidebar.querySelector('.nav-item'));check('remote-ready');
  const first=document.activeElement;await until(()=>document.activeElement!==first,'Real Android DPAD key did not move focus',15000);check('Android DPAD focus moved',{target:document.activeElement.getAttribute('href')});await shot('home');
  location.hash='#/detail?provider=palma&kind=movie&id=4528';await until(()=>document.querySelector('#detail-content .server-row'),'Movie details missing');await shot('movie-details');
  check('Resolving movie');const movie=await api('/api/resolve',{provider:'palma',kind:'movie',id:'4528',index:'1',target:'web'});check('Movie resolved',{type:movie.type});openPlayer(movie,{provider:'palma',kind:'movie',id:'4528',title:'Oldboy'},'');
  const video=await decoded('PALMA B2 movie decoded');if(video.duration<300)throw Error('Movie returned a short substitute');
  const box=document.querySelector('.player-wrap').getBoundingClientRect();if(Math.abs(box.width-innerWidth)>2||Math.abs(box.height-innerHeight)>2)throw Error('Player not true viewport fullscreen');check('Immersive fullscreen',{width:box.width,height:box.height});await shot('movie');
  window.PalmaTV.mediaKey(127);if(!video.paused)throw Error('Pause key failed');window.PalmaTV.mediaKey(126);await until(()=>!video.paused,'Play key failed');window.PalmaTV.mediaKey(90);await until(()=>video.currentTime>10,'Seek key failed');check('Remote play pause and seek');closePlayer();
  check('Resolving channel');const channel=await api('/api/resolve',{provider:'palma',kind:'channel',id:'63',index:'0',target:'web'});check('Channel resolved',{type:channel.type});openPlayer(channel,{provider:'palma',kind:'channel',id:'63',title:'PALMA live'},'');await decoded('PALMA live channel decoded');await shot('channel');closePlayer();
  location.hash='#/detail?provider=witcher&kind=anime&id=Naruto&area=main';await until(()=>document.querySelector('.anime-title-block h1')?.textContent==='Naruto','Anime detail missing');
  const tab=await until(()=>document.querySelector('[data-anime-tab="characters"]'),'Characters tab missing');tab.click();await until(()=>document.querySelector('[data-character-id="17"]'),'Characters missing');check('Anime characters and details');await shot('anime-details');
  const servers=await api('/api/catalog',{provider:'witcher',route:'anime/Naruto/episodes/001/servers'}),items=Array.isArray(servers)?servers:servers.items||servers.data||[];
  let played=false;const seen=new Set();for(let i=0;i<items.length;i++){const name=items[i].name;if(seen.has(name))continue;seen.add(name);if(seen.size>5)break;try{const anime=await api('/api/resolve',{provider:'witcher',kind:'anime',id:'Naruto',episode:'001',index:String(i),target:'web'});openPlayer(anime,{provider:'witcher',kind:'anime',id:'Naruto',title:'Naruto'},'001');const v=await decoded('Anime Witcher episode decoded');if(v.duration<120)throw Error('Anime substitute');played=true;await shot('anime');closePlayer();break;}catch{closePlayer();check('Anime server unavailable',{index:i});}}
  if(!played)throw Error('Anime failed on tested servers');
  location.hash='#/settings';await until(()=>document.querySelector('#playback-target'),'Settings missing');window.PalmaTV.picker(document.querySelector('#playback-target'));if(!document.querySelector('.tv-picker[open]'))throw Error('Remote select picker missing');window.PalmaTV.back();check('Remote select and back');
  localStorage.setItem('palma-tv-test-persistence','retained');check('Persisted local preferences');location.hash='#/home';report.passed=true;
}catch(error){report.error=String(error.message).slice(0,250);report.screenMessage=document.querySelector('#main .state p')?.textContent?.slice(0,200)||'';check('FAILED',{error:report.error,screenMessage:report.screenMessage});}
await fetch('/test/report',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(report)});
console.log('PALMA_TV_TEST '+JSON.stringify({done:true,passed:report.passed,report}));
