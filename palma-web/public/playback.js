import {icon,esc,toast,progressFor,saveProgress,timeText,readStore,writeStore} from './lib.js';

const dialog=document.getElementById('player-dialog');
let video,current,hls,transport,dash,interval,hideTimer,holdTimer,holding=false,spaceDown=false,oldRate=1,loadId=0,request,nativeFullscreen=false;
const $=selector=>dialog.querySelector(selector);
function persist(){if(video&&current&&!current.media.live)saveProgress(current.item,current.episode,video.currentTime,Number.isFinite(video.duration)?video.duration:0);}
function stopEngine(){loadId++;hls?.destroy();hls=null;transport?.destroy();transport=null;dash?.reset();dash=null;if(video){video.pause();video.removeAttribute('src');video.load();}}
function restoreRate(){clearTimeout(holdTimer);if(holding&&video)video.playbackRate=oldRate;holding=false;spaceDown=false;$('.speed-hold')?.setAttribute('hidden','');}
function destroy(){persist();request?.abort();request=null;restoreRate();clearInterval(interval);clearTimeout(hideTimer);stopEngine();video=null;current=null;}
export function closePlayer(){if(nativeFullscreen)setNativeFullscreen(false).catch(()=>{});if(document.fullscreenElement)document.exitFullscreen().catch(()=>{});dialog.close();destroy();}
dialog.addEventListener('close',destroy);
dialog.addEventListener('cancel',event=>{if(nativeFullscreen){event.preventDefault();setNativeFullscreen(false).catch(()=>{});}else if(document.fullscreenElement){event.preventDefault();document.exitFullscreen().catch(()=>{});}});
window.addEventListener('pagehide',persist);
window.addEventListener('blur',restoreRate);
document.addEventListener('visibilitychange',()=>{if(document.hidden){persist();restoreRate();}});
function showControls(){
  if(!video)return;$('.player-wrap').classList.remove('controls-hidden');clearTimeout(hideTimer);
  hideTimer=setTimeout(()=>{if(video&&!video.paused&&!$('.player-settings').open&&!$('.player-controls').querySelector(':focus-visible')&&!$('.player-header').querySelector(':focus-visible'))$('.player-wrap').classList.add('controls-hidden');},3000);
}
function busy(value){$('.player-loading')?.toggleAttribute('hidden',!value);}
function error(message){if(!video)return;video.pause();busy(false);showControls();$('.player-error').hidden=false;$('.player-error p').textContent=message;}
function timeline(){
  if(!video)return;
  const start=video.seekable.length?video.seekable.start(0):0,end=video.seekable.length?video.seekable.end(video.seekable.length-1):Number.isFinite(video.duration)?video.duration:0;
  const range=$('#player-seek');range.min=start;range.max=end;range.disabled=end<=start;range.value=video.currentTime;
  const fraction=end>start?Math.max(0,Math.min(1,(video.currentTime-start)/(end-start))):0;
  range.style.setProperty('--played',`${fraction*100}%`);
  $('#player-time').textContent=current.media.live?'مباشر':`${timeText(video.currentTime)} / ${timeText(Number.isFinite(video.duration)?video.duration:0)}`;
  $('#play-toggle').innerHTML=video.paused?icon('play'):'<svg class="icon" viewBox="0 0 24 24"><path d="M8 4v16M16 4v16"/></svg>';
  $('#play-toggle').setAttribute('aria-label',video.paused?'تشغيل':'إيقاف مؤقت');
  $('#mute-toggle').textContent=video.muted||!video.volume?'صامت':'صوت';$('#mute-toggle').setAttribute('aria-pressed',String(video.muted));
}
function tracks(){
  const audio=$('#audio-select'),sub=$('#subtitle-select');
  audio.innerHTML=(hls?.audioTracks||[]).map((track,i)=>`<option value="${i}">${esc(track.name||track.lang||'مسار '+(i+1))}</option>`).join('')||'<option>الافتراضي</option>';
  audio.disabled=!hls?.audioTracks?.length;
  sub.innerHTML='<option value="-1">بدون ترجمة</option>'+(hls?.subtitleTracks||Array.from(video?.textTracks||[])).map((track,i)=>`<option value="${i}">${esc(track.name||track.label||track.lang||track.language||'ترجمة '+(i+1))}</option>`).join('');
}
function qualityOptions(){
  const values=current.media.qualities||[];
  $('#quality-select').innerHTML='<option value="auto">تلقائي</option>'+values.map((q,i)=>`<option value="q${i}">${esc(q.quality)}</option>`).join('');
}
async function load(media,position=0,qualityOnly=false){
  const rate=video?.playbackRate||1;stopEngine();const generation=loadId,activeVideo=video;current.media=media;if(!qualityOnly)current.automaticMedia=media;
  $('.player-error').hidden=true;busy(true);qualityOptions();
  $('#live-edge').hidden=!media.live;$('.live-badge').hidden=!media.live;
  const servers=media.servers||[];
  $('#server-select').innerHTML=servers.length?servers.map(s=>`<option value="${s.index}" ${s.index===media.selection?.index?'selected':''}>${esc(s.name)}${s.quality?' · '+esc(s.quality):''}</option>`).join(''):`<option>${esc(media.name||'السيرفر الحالي')}</option>`;
  $('#server-select').disabled=servers.length<2;
  const source=media.playable||media.url;
  if(!source){error('لم يُرجع المصدر رابط فيديو صالحًا.');return;}
  activeVideo.onloadedmetadata=()=>{
    if(generation!==loadId)return;
    activeVideo.playbackRate=rate;
    if(!media.live&&position>0&&(!Number.isFinite(activeVideo.duration)||position<activeVideo.duration-5))activeVideo.currentTime=position;
    activeVideo.play().catch(()=>{busy(false);showControls();});tracks();timeline();
  };
  try{
    if(media.type==='hls'&&window.Hls?.isSupported()){
      const engine=hls=new window.Hls({maxBufferLength:35,enableWorker:true,backBufferLength:30,liveDurationInfinity:true});let recovered=0;
      engine.on(window.Hls.Events.ERROR,(_,event)=>{
        if(generation!==loadId||!event.fatal)return;
        if(event.type===window.Hls.ErrorTypes.MEDIA_ERROR&&recovered++<1){engine.recoverMediaError();return;}
        error('تعذر تشغيل هذا البث. أعد الاتصال أو اختر سيرفرًا آخر.');
      });
      engine.on(window.Hls.Events.MANIFEST_PARSED,()=>{
        if(generation!==loadId)return;
        if(!(media.qualities||[]).length)$('#quality-select').innerHTML='<option value="auto">تلقائي</option>'+engine.levels.map((l,i)=>`<option value="${i}">${l.height||Math.round(l.bitrate/1000)}${l.height?'p':' kbps'}</option>`).join('');
        tracks();
      });
      engine.on(window.Hls.Events.AUDIO_TRACKS_UPDATED,tracks);engine.on(window.Hls.Events.SUBTITLE_TRACKS_UPDATED,tracks);
      engine.attachMedia(activeVideo);engine.loadSource(source);
    }else if(['mpegts','flv'].includes(media.type)){
      if(!window.mpegts?.isSupported())throw Error('هذا الجهاز لا يدعم محرك البث المباشر.');
      transport=window.mpegts.createPlayer({type:media.type,isLive:!!media.live,url:source},{enableWorker:true,enableStashBuffer:!media.live,liveBufferLatencyChasing:!!media.live,liveBufferLatencyMaxLatency:8,liveBufferLatencyMinRemain:2});
      transport.on(window.mpegts.Events.ERROR,()=>{if(generation===loadId)error('انقطع بث القناة. أعد الاتصال أو بدّل السيرفر.');});
      transport.attachMediaElement(activeVideo);transport.load();
    }else if(media.type==='dash'){
      if(!window.dashjs)throw Error('محرك DASH غير متاح.');
      dash=window.dashjs.MediaPlayer().create();dash.initialize(activeVideo,source,true);
      dash.on(window.dashjs.MediaPlayer.events.ERROR,()=>{if(generation===loadId)error('تعذر تشغيل بث DASH. جرّب إعادة الاتصال.');});
    }else{activeVideo.src=source;activeVideo.load();}
  }catch(e){error(e.message);}
}
async function reconnect(index){
  if(!current)return;const selected=current,position=video.currentTime;request?.abort();request=new AbortController();
  busy(true);$('.player-error').hidden=true;
  try{
    if(!selected.media.selection){await load(selected.media,position);return;}
    const q=new URLSearchParams({...selected.media.selection,index:String(index??selected.media.selection.index),target:'web'});
    const response=await fetch('/api/resolve?'+q,{signal:request.signal}),media=await response.json();
    if(!response.ok)throw Error(media.error||'تعذر الاتصال بالسيرفر.');
    if(current===selected)await load(media,position);
  }catch(e){if(e.name!=='AbortError'&&current===selected)error(e.message);}
}
async function setNativeFullscreen(value){
  const response=await fetch('/api/desktop/fullscreen',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({fullscreen:value})});
  if(!response.ok)throw Error('تعذر تغيير وضع الشاشة.');
  nativeFullscreen=(await response.json()).fullscreen;
  $('.player-wrap')?.setAttribute('data-fullscreen',String(nativeFullscreen));
  $('#fullscreen-button')?.setAttribute('aria-label',nativeFullscreen?'إنهاء ملء الشاشة':'ملء الشاشة');
  showControls();
}
async function fullscreen(){
  try{
    if(location.protocol==='palma:'){
      const response=await fetch('/api/desktop/fullscreen'),state=await response.json();await setNativeFullscreen(!state.fullscreen);
    }else if(document.fullscreenElement)await document.exitFullscreen();else await $('.player-wrap').requestFullscreen();
  }catch{error('تعذر تفعيل ملء الشاشة. جرّب من قائمة عرض في التطبيق.');}
}

function seek(delta){if(!video||!video.seekable.length)return;const start=video.seekable.start(0),end=video.seekable.end(video.seekable.length-1);video.currentTime=Math.max(start,Math.min(end-.1,video.currentTime+delta));showControls();}
function toggle(){if(video?.paused)video.play().catch(()=>{});else video?.pause();}
export function openPlayer(media,item,episode=''){
  destroy();current={media,item,episode};
  const saved=progressFor(item,episode),resume=!media.live&&readStore('auto-resume',true)&&saved?.time>10&&(!saved.duration||saved.time<saved.duration-12);
  dialog.innerHTML=`<div class="player-wrap" tabindex="-1"><video playsinline preload="metadata"></video><div class="player-header"><div><span class="player-brand">PALMA VISION</span><strong>${esc(item.title)}${episode?' · الحلقة '+esc(item.episodeNumber||episode):''}</strong></div><span class="live-badge" hidden>مباشر</span><button class="icon-btn" id="player-close" aria-label="إغلاق المشغّل">${icon('close')}</button></div><div class="player-loading" role="status"><span class="spinner"></span><span>جاري تشغيل الفيديو…</span></div><div class="speed-hold" hidden>2×</div><div class="player-error" hidden><h3>تعذر التشغيل</h3><p></p><div class="actions"><button class="btn primary" id="player-retry">إعادة الاتصال</button><button class="btn outline" id="player-return">العودة للسيرفرات</button></div></div>${resume?`<div class="resume-prompt">متابعة من ${timeText(saved.time)} <button id="restart-video">ابدأ من البداية</button></div>`:''}<div class="player-controls"><div class="player-timeline"><input id="player-seek" type="range" min="0" max="0" step=".1" value="0" aria-label="موضع الفيديو"><span id="player-time" dir="ltr">0:00 / 0:00</span></div><div class="control-row"><button class="icon-btn" id="play-toggle" aria-label="تشغيل">${icon('play')}</button><button id="seek-back" aria-label="رجوع عشر ثوان">−10</button><button id="seek-forward" aria-label="تقديم عشر ثوان">+10</button><button id="mute-toggle" aria-label="كتم الصوت">صوت</button><input id="volume-slider" type="range" min="0" max="1" step=".05" aria-label="مستوى الصوت"><button id="live-edge" hidden>العودة للمباشر</button><span class="control-spacer"></span><details class="player-settings"><summary aria-label="إعدادات المشغل">${icon('settings')}<span>الإعدادات</span></summary><div class="settings-popover"><label>السيرفر<select id="server-select"></select></label><label>الجودة<select id="quality-select"></select></label><label>السرعة<select id="speed-select">${[.5,1,1.5,2,2.5,3,3.5,4].map(s=>`<option value="${s}" ${s===1?'selected':''}>${s}×</option>`).join('')}</select></label><label>الصوت<select id="audio-select"></select></label><label>الترجمة<select id="subtitle-select"></select></label><label>حجم الصورة<select id="fit-select"><option value="contain">الحجم الأصلي</option><option value="cover">تعبئة الشاشة</option><option value="fill">تمديد</option></select></label></div></details><button class="icon-btn" id="pip-button" aria-label="صورة داخل صورة">${icon('external')}</button><button class="icon-btn" id="fullscreen-button" aria-label="ملء الشاشة">${icon('fullscreen')}</button></div><p class="player-hint">Space تشغيل / إيقاف · ضغط مطوّل 2× · الأسهم للتقديم والصوت · F ملء الشاشة</p></div></div>`;
  video=$('video');video.volume=Math.max(0,Math.min(1,Number(readStore('volume',1))||0));$('#volume-slider').value=video.volume;
  for(const event of ['timeupdate','durationchange','progress','volumechange','play','pause'])video.addEventListener(event,timeline);
  for(const event of ['pause','seeked','ended'])video.addEventListener(event,persist);
  video.addEventListener('playing',()=>{busy(false);$('.player-error').hidden=true;showControls();});
  video.addEventListener('waiting',()=>busy(true));video.addEventListener('pause',showControls);
  video.onerror=()=>{if(video?.currentSrc)error('تعذر فك ترميز الفيديو أو انقطع المصدر. أعد الاتصال أو غيّر السيرفر.');};
  video.onclick=toggle;video.ondblclick=fullscreen;
  $('.player-wrap').onpointermove=showControls;$('.player-wrap').onfocusin=showControls;
  $('#player-close').onclick=closePlayer;$('#player-return').onclick=closePlayer;$('#player-retry').onclick=()=>reconnect();
  $('#play-toggle').onclick=toggle;$('#seek-back').onclick=()=>seek(-10);$('#seek-forward').onclick=()=>seek(10);
  $('#player-seek').oninput=e=>{video.currentTime=Number(e.target.value);};
  $('#mute-toggle').onclick=()=>{video.muted=!video.muted;};
  $('#volume-slider').oninput=e=>{video.volume=Number(e.target.value);video.muted=false;writeStore('volume',video.volume);};
  $('#live-edge').onclick=()=>{if(video.seekable.length)video.currentTime=video.seekable.end(video.seekable.length-1)-1;video.play().catch(()=>{});};
  $('#speed-select').onchange=e=>{video.playbackRate=Number(e.target.value);};
  $('#fit-select').onchange=e=>{video.style.objectFit=e.target.value;};
  $('#server-select').onchange=e=>reconnect(Number(e.target.value));
  $('#quality-select').onchange=e=>{const value=e.target.value;if(value.startsWith('q')){const original=current.automaticMedia;load({...original,...original.qualities[Number(value.slice(1))]},video.currentTime,true);$('#quality-select').value=value;}else if(value==='auto'&&current.automaticMedia!==current.media){load(current.automaticMedia,video.currentTime);}else if(hls)hls.currentLevel=value==='auto'?-1:Number(value);};
  $('#audio-select').onchange=e=>{if(hls)hls.audioTrack=Number(e.target.value);};
  $('#subtitle-select').onchange=e=>{if(hls)hls.subtitleTrack=Number(e.target.value);else Array.from(video.textTracks).forEach((track,i)=>{track.mode=i===Number(e.target.value)?'showing':'disabled';});};
  $('#fullscreen-button').onclick=fullscreen;
  $('#pip-button').hidden=!document.pictureInPictureEnabled;
  $('#pip-button').onclick=async()=>{try{if(document.pictureInPictureElement)await document.exitPictureInPicture();else await video.requestPictureInPicture();}catch{toast('صورة داخل صورة غير متاحة الآن.');}};
  $('#restart-video')?.addEventListener('click',()=>{video.currentTime=0;$('.resume-prompt')?.remove();});
  if(!dialog.open)dialog.showModal();$('.player-wrap').focus();load(media,resume?saved.time:0);interval=setInterval(persist,3000);showControls();
}
document.addEventListener('fullscreenchange',()=>{if(video){$('#fullscreen-button').setAttribute('aria-label',document.fullscreenElement?'إنهاء ملء الشاشة':'ملء الشاشة');showControls();}});
document.addEventListener('keydown',event=>{
  if(!video||!dialog.open||event.ctrlKey||event.metaKey||event.altKey)return;
  const tag=document.activeElement?.tagName;
  if(/INPUT|SELECT|TEXTAREA/.test(tag))return;
  if(event.code==='KeyF'){event.preventDefault();if(!event.repeat)fullscreen();}
  else if(event.code==='KeyM'){video.muted=!video.muted;showControls();}
  else if(event.code==='ArrowRight'){event.preventDefault();seek(10);}
  else if(event.code==='ArrowLeft'){event.preventDefault();seek(-10);}
  else if(event.code==='ArrowUp'||event.code==='ArrowDown'){event.preventDefault();video.volume=Math.max(0,Math.min(1,video.volume+(event.code==='ArrowUp'?0.05:-0.05)));video.muted=false;$('#volume-slider').value=video.volume;showControls();}
  else if(event.code==='Space'&&!/BUTTON|SUMMARY/.test(tag)){event.preventDefault();if(event.repeat)return;spaceDown=true;oldRate=video.playbackRate;holdTimer=setTimeout(()=>{if(video){holding=true;video.playbackRate=2;$('.speed-hold').hidden=false;}},260);}
});
document.addEventListener('keyup',event=>{if(event.code!=='Space'||!video||!spaceDown)return;event.preventDefault();const held=holding;restoreRate();if(!held)toggle();showControls();});
