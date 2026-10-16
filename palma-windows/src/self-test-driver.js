import {openPlayer,closePlayer} from '/player.js';
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(predicate,label,timeout=40000){const end=Date.now()+timeout;while(Date.now()<end){const result=predicate();if(result)return result;await wait(150);}throw Error(label);}
const check=async(step,values={})=>{await fetch('/__test/check',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({step,...values})});};
const shot=name=>fetch('/__test/screenshot?name='+name);
async function decoded(label){
 const video=await until(()=>{
  const message=document.querySelector('#dialog[open] h3');
  if(message?.textContent.includes('تعذر'))throw Error(label+': '+document.querySelector('#dialog p')?.textContent);
  return document.querySelector('#player-dialog[open] video');
 },label+' did not open',80000);video.muted=true;
 await until(()=>{
  const message=document.querySelector('#player-dialog[open] .player-error:not([hidden]) p');
  if(message)throw Error(label+': '+message.textContent);
  return video.currentTime>3&&video.videoWidth>0&&video.getVideoPlaybackQuality().totalVideoFrames>0;
 },label+' did not decode video',65000);
 await check(label,{width:video.videoWidth,height:video.videoHeight,time:video.currentTime,duration:Number.isFinite(video.duration)?video.duration:'live',frames:video.getVideoPlaybackQuality().totalVideoFrames});return video;
}
try{
 const {playback,playbackError,item,episode}=await (await fetch('/__test/media')).json();
 await until(()=>document.querySelector('.media-card'),'Home did not render');await document.fonts.ready;
 const cover=document.querySelector('.hero-cover');if(cover)await Promise.race([cover.decode(),wait(12000)]);
 await check('home rendered with source images',{cards:document.querySelectorAll('.media-card').length,cover_loaded:cover?.naturalWidth>0});await shot('home');
 if(document.querySelectorAll('.home-matches .match-card').length!==document.querySelectorAll('.home-matches a.match-card').length)throw Error('Some home matches are not navigable');
 const match=await until(()=>document.querySelector('.home-matches .match-card'),'Home match card absent');const matchId=new URLSearchParams(match.hash.split('?')[1]).get('id');match.click();
 await until(()=>document.body.dataset.view==='match'&&document.querySelector('.match-hero'),'Home match did not open match details');
 const expected=(await (await fetch('/api/catalog?'+new URLSearchParams({provider:'palma',route:'api/matches/show.php',id:matchId}))).json()).data;
 if(!document.querySelector('h1').textContent.includes(expected.home_team?.name||expected.home_team_name))throw Error('Home match opened wrong teams');
 const channelLinks=[...document.querySelectorAll('#match-streams .media-card a')].map(a=>new URLSearchParams(a.hash.split('?')[1]).get('id'));
 const actual=(expected.channels||[]).filter(c=>c.channel_id).map(c=>String(c.channel_id));
 if(!actual.length||actual.some(id=>!channelLinks.includes(id)))throw Error('Match channel association IDs used instead of channel IDs');
 await check('home match opens correct teams and channel IDs',{match_id:matchId,channels:channelLinks});await shot('match');
 location.hash='#/detail?provider=palma&kind=movie&id=4528';
 const actor=await until(()=>document.querySelector('.cast-card'),'Film cast did not render');const actorId=actor.dataset.actorId;actor.click();
 await until(()=>document.querySelector('body[data-view="detail"] .detail-poster')&&location.hash.includes('kind=actor')&&!document.querySelector('.skeleton')&&document.querySelector('#detail-content .media-card'),'Actor filmography did not render');
 const works=document.querySelectorAll('#detail-content .media-card').length;await check('cast portrait opens actor filmography',{actor_id:actorId,works});await shot('actor');
 const playbackFailures=[];
 try{
  if(!playback)throw Error(playbackError);
  openPlayer(playback,item,episode);const video=await decoded('PALMA episode decoded');
  if(video.duration<300)throw Error('Episode source returned a short replacement video');
  video.currentTime=90;await until(()=>video.currentTime>=90&&video.readyState>=2,'Episode seek did not decode');
  await shot('player');
 }catch(error){playbackFailures.push(error.message);await check('episode or player failure',{error:error.message});await shot('player-failure');}
 closePlayer();
 try{
  const response=await fetch('/api/resolve?'+new URLSearchParams({provider:'palma',kind:'movie',id:'4528',index:'1',target:'web'}));
  const movie=await response.json();if(!response.ok)throw Error('Movie source: '+response.status+' '+movie.code);
  openPlayer(movie,{provider:'palma',kind:'movie',id:'4528',title:'Oldboy'},'');
  const video=await decoded('PALMA movie B2 decoded');if(video.duration<300)throw Error('Movie source returned a short replacement video');
  await shot('movie');
 }catch(error){playbackFailures.push(error.message);await check('movie failure',{error:error.message});await shot('movie-failure');}
 closePlayer();
 try{
  openPlayer(playback||{type:'video'},item,episode);
  const fullscreen=await (await fetch('/__test/fullscreen')).json();await check('native fullscreen state',fullscreen);if(!fullscreen.native||!fullscreen.element||!fullscreen.player_fills_window)throw Error('Player failed native fullscreen');
  await shot('fullscreen');const exited=await (await fetch('/__test/fullscreen?exit=1')).json();await check('native fullscreen exited',exited);if(exited.native||exited.element)throw Error('Player did not exit native fullscreen');
 }catch(error){playbackFailures.push(error.message);await check('fullscreen failure',{error:error.message});}
 closePlayer();
 try{
  location.hash='#/detail?provider=palma&kind=channel&id=63';
  const channelButton=await until(()=>document.querySelector('#detail-content .server-row button'),'Channel servers missing');channelButton.click();
  const channelVideo=await decoded('PALMA live channel decoded');if(Number.isFinite(channelVideo.duration))throw Error('Channel source returned a recorded replacement instead of live media');
  await shot('channel');
 }catch(error){playbackFailures.push(error.message);await check('channel failure',{error:error.message});await shot('channel-failure');}
 closePlayer();document.getElementById('dialog').close();
 location.hash='#/detail?provider=witcher&kind=anime&id=Naruto&area=main';
 const animePage=await until(()=>document.querySelector('.anime-detail-page[data-characters-loaded="true"]'),'Anime characters did not load',60000);
 if(animePage.querySelector('.anime-airing').dataset.airingState!=='finished')throw Error('Completed Naruto received a future episode countdown');
 await until(()=>animePage.querySelector('.anime-detail-poster')?.naturalWidth>0,'Anime poster did not load');
 if(!animePage.textContent.includes('Studio Pierrot')||!animePage.textContent.includes('220 حلقة'))throw Error('Anime source metadata missing');
 await check('anime detail metadata and completed schedule',{title:animePage.querySelector('h1').textContent,characters_total:Number(animePage.querySelector('[data-characters-count]').textContent),poster_loaded:true});await shot('anime-details');
 document.getElementById('anime-tab-characters').click();document.querySelector('.anime-detail-tabs').scrollIntoView({block:'start'});
 const naruto=await until(()=>document.querySelector('#anime-panel-characters [data-character-id="17"]'),'Naruto main character absent');
 await until(()=>naruto.querySelector('img')?.naturalWidth>0,'Character portrait did not load');
 await shot('anime-characters');naruto.click();
 const voice=await until(()=>document.querySelector('.anime-character-dialog[open] .anime-voice-grid img'),'Voice actor portrait missing');
 await until(()=>voice.naturalWidth>0,'Voice actor portrait failed to load');
 if(!document.querySelector('.anime-character-dialog').textContent.includes('Takeuchi'))throw Error('Wrong voice actor for Naruto');
 await check('anime main character opens real voice actor portraits',{character_id:'17',voices:document.querySelectorAll('.anime-voice-grid>div').length,portrait_loaded:true});await shot('anime-character-profile');document.getElementById('dialog').close();
 const beforeCharacters=document.querySelectorAll('#anime-panel-characters [data-character-id]').length;
 document.querySelector('[data-more-characters]').click();
 await until(()=>document.querySelectorAll('#anime-panel-characters [data-character-id]').length>beforeCharacters,'Character pagination failed',60000);
 const ids=[...document.querySelectorAll('#anime-panel-characters [data-character-id]')].map(x=>x.dataset.characterId);
 if(new Set(ids).size!==ids.length)throw Error('Character pagination contains duplicate IDs');
 await check('anime character pagination',{first_page:beforeCharacters,loaded:ids.length});
 const scheduledId='Hyouken no Majutsushi ga Sekai wo Suberu II';
 const scheduledRaw=await (await fetch('/api/catalog?'+new URLSearchParams({provider:'witcher',route:'anime/'+encodeURIComponent(scheduledId)}))).json();
 location.hash='#/detail?'+new URLSearchParams({provider:'witcher',kind:'anime',id:scheduledId,area:'main'});
 const schedule=await until(()=>document.querySelector('.anime-airing[data-airing-state="scheduled"]'),'Real upcoming episode countdown missing');
 const expectedAt=Number(scheduledRaw.nextEpTimeInSec),actualAt=Date.parse(schedule.querySelector('time').dateTime)/1000;
 if(Math.abs(expectedAt-actualAt)>.01)throw Error('Countdown does not use source timestamp');
 const beforeSeconds=[...schedule.querySelectorAll('[data-countdown-part]')].map(x=>x.textContent).join(':');
 await wait(1300);const afterSeconds=[...schedule.querySelectorAll('[data-countdown-part]')].map(x=>x.textContent).join(':');
 if(beforeSeconds===afterSeconds)throw Error('Episode countdown is not ticking');
 schedule.scrollIntoView({block:'center'});await check('anime source timestamp and ticking countdown',{anime:scheduledId,source_timestamp:expectedAt,before:beforeSeconds,after:afterSeconds});await shot('anime-countdown');
 location.hash='#/detail?provider=witcher&kind=anime&id=Naruto&area=main';
 await until(()=>document.querySelector('.anime-title-block h1')?.textContent==='Naruto','Return to Naruto details failed');
 const detachedCount=schedule.textContent;await wait(1100);if(schedule.textContent!==detachedCount)throw Error('Anime countdown timer survived navigation');
 document.querySelector('[data-anime-watch]').click();
 const firstEpisode=await until(()=>document.querySelector('#anime-panel-episodes:not([hidden]) .episode-card'),'Anime watch action did not open episodes');
 await check('anime watch action opens episode list',{episodes:document.querySelectorAll('#anime-panel-episodes .episode-card').length});firstEpisode.click();
 await until(()=>document.body.dataset.view==='servers'&&document.querySelector('#detail-content .server-row button'),'Witcher episode servers missing');
 if(new URLSearchParams(location.hash.split('?')[1]).get('episode')!=='001')throw Error('First anime episode opens the wrong server identity');
 // Exercise a real visible server choice. Report every attempted server by label only.
 let animePlayed=false;const buttons=[...document.querySelectorAll('#detail-content .server-row button')].filter(b=>b.textContent.includes('مشاهدة'));
 const candidates=buttons.length?buttons:[...document.querySelectorAll('#detail-content .server-row .btn')].filter(b=>b.tagName==='BUTTON').filter((_,i)=>i%2===0);
 const seen=new Set(),different=candidates.filter(button=>{const name=button.closest('.server-row').querySelector('.server-name strong')?.textContent;if(seen.has(name))return false;seen.add(name);return true;});
 for(const button of different.slice(0,6)){
  const label=button.closest('.server-row').querySelector('.server-name')?.textContent.trim().slice(0,80);button.click();
  try{
   await until(()=>document.querySelector('#player-dialog[open] video')||document.querySelector('#dialog[open] h3')?.textContent.includes('تعذر'),'Anime resolver timeout',85000);
   if(!document.querySelector('#player-dialog[open] video')){const message=document.querySelector('#dialog p')?.textContent;await check('anime source attempt',{label,error:message});document.getElementById('dialog').close();continue;}
   const animeVideo=await decoded('Witcher anime episode decoded');if(animeVideo.duration<120)throw Error('Anime source returned a short replacement');document.querySelector('.player-wrap').focus();await wait(3500);if(!document.querySelector('.controls-hidden'))throw Error('Playback controls did not hide');await check('anime selected server',{label});await shot('anime');animePlayed=true;closePlayer();break;
  }catch(error){await check('anime source attempt',{label,error:error.message});closePlayer();document.getElementById('dialog').close();}
 }
 if(!animePlayed)playbackFailures.push('Witcher episode could not decode on the tested source servers');
 if(playbackFailures.length)throw Error(playbackFailures.join('; '));
 await fetch('/__test/result');
}catch(error){await fetch('/__test/failure?message='+encodeURIComponent(error.message));}
