import {icon,esc,modal,toast,progressFor,saveProgress,timeText,readStore,writeStore} from './lib.js';

let hls,video,current,interval,spaceTimer,spaceHeld=false,originalRate=1;
function persist(){if(video&&current)saveProgress(current.item,current.episode,video.currentTime,Number.isFinite(video.duration)?video.duration:0);}
function destroy(){persist();clearInterval(interval);clearTimeout(spaceTimer);spaceHeld=false;hls?.destroy();hls=null;if(video){video.pause();video.removeAttribute('src');video.load();}video=null;current=null;}
export function closePlayer(){document.getElementById('player-dialog').close();destroy();}
const dialog=document.getElementById('player-dialog');
dialog.addEventListener('close',destroy);
window.addEventListener('pagehide',persist);
document.addEventListener('visibilitychange',()=>{if(document.hidden)persist();});
function playerError(message){if(!video)return;video.pause();const box=dialog.querySelector('.player-error');box.hidden=false;box.querySelector('p').textContent=message;}
async function load(media,position=0){
  hls?.destroy();hls=null;
  const source=media.playable||media.url;
  if(!source){playerError('لم يُرجع المصدر رابط فيديو صالحًا.');return;}
  video.onloadedmetadata=()=>{if(position>0&&(!Number.isFinite(video.duration)||position<video.duration-5))video.currentTime=position;video.play().catch(()=>toast('اضغط تشغيل لبدء الفيديو.'));};
  if(media.type==='hls'&&window.Hls?.isSupported()){
    hls=new Hls({maxBufferLength:35,enableWorker:true});hls.attachMedia(video);hls.loadSource(source);
    hls.on(Hls.Events.ERROR,(_,e)=>{if(e.fatal)playerError('تعذر تشغيل هذا السيرفر في المتصفح. ارجع إلى السيرفرات واختر سيرفرًا آخر.');});
    hls.on(Hls.Events.MANIFEST_PARSED,()=>{
      if(current?.qualities?.length)return;
      const q=dialog.querySelector('#quality-select');q.innerHTML='<option value="auto">تلقائي</option>'+hls.levels.map((l,i)=>`<option value="${i}">${l.height||'متعدد'}p</option>`).join('');
    });
  }else{video.src=source;video.load();}
}
export function openPlayer(media,item,episode=''){
  destroy();current={media,item,episode,qualities:media.qualities||[]};
  const saved=progressFor(item,episode),resume=readStore('auto-resume',true)&&saved?.time>10&&(!saved.duration||saved.time<saved.duration-12);
  const qualities=current.qualities;
  dialog.innerHTML=`<div class="player-wrap"><div class="player-header">${icon('play')}<strong>${esc(item.title)}${episode?' • الحلقة '+esc(episode):''}</strong><button class="icon-btn" id="player-close" aria-label="إغلاق المشغّل">${icon('close')}</button></div>${resume?`<div class="resume-prompt">متابعة من ${timeText(saved.time)} <button class="btn outline" id="restart-video">ابدأ من البداية</button></div>`:''}<div class="video-wrap"><video controls playsinline preload="metadata"></video><div class="player-error" hidden><h3>تعذر التشغيل</h3><p></p><button class="btn" id="player-return">العودة للسيرفرات</button></div></div><div class="player-footer"><label>الجودة <select id="quality-select"><option value="auto">تلقائي</option>${qualities.map((q,i)=>`<option value="q${i}">${esc(q.quality)}</option>`).join('')}</select></label><label>السرعة <select id="speed-select">${[.5,1,1.5,2,2.5,3,3.5,4].map(s=>`<option value="${s}" ${s===1?'selected':''}>${s}×</option>`).join('')}</select></label><button id="pip-button">صورة داخل صورة</button><button id="fullscreen-button">ملء الشاشة</button><button id="copy-video">نسخ الرابط</button><span class="player-hint">المسافة: تشغيل وإيقاف · ضغط مطوّل: 2× · الأسهم: تقديم وتأخير</span></div></div>`;
  video=dialog.querySelector('video');
  video.addEventListener('pause',persist);
  video.addEventListener('seeked',persist);
  video.addEventListener('ended',persist);
  video.onerror=()=>playerError('تعذر تشغيل الفيديو في المتصفح. قد يحتاج هذا السيرفر صيغة أو ترويسات غير متاحة. اختر سيرفرًا آخر.');
  dialog.querySelector('#player-close').onclick=closePlayer;
  dialog.querySelector('#player-return').onclick=closePlayer;
  dialog.querySelector('#restart-video')?.addEventListener('click',()=>{video.currentTime=0;dialog.querySelector('.resume-prompt').remove();});
  dialog.querySelector('#speed-select').onchange=e=>{video.playbackRate=Number(e.target.value);};
  dialog.querySelector('#quality-select').onchange=e=>{const value=e.target.value;if(value.startsWith('q')){const q=qualities[Number(value.slice(1))];load({...media,...q},video.currentTime);}else if(value==='auto'&&qualities.length)load(media,video.currentTime);else if(hls)hls.currentLevel=value==='auto'?-1:Number(value);};
  dialog.querySelector('#fullscreen-button').onclick=()=>{if(document.fullscreenElement)document.exitFullscreen?.();else dialog.requestFullscreen?.();};
  dialog.querySelector('#pip-button').onclick=async()=>{try{if(document.pictureInPictureElement)await document.exitPictureInPicture();else await video.requestPictureInPicture();}catch{toast('صورة داخل صورة غير متاحة في هذا المتصفح.');}};
  dialog.querySelector('#copy-video').onclick=async()=>{try{await navigator.clipboard.writeText(media.url);toast('تم نسخ رابط الفيديو.');}catch{toast('تعذر نسخ الرابط.');}};
  if(!dialog.open)dialog.showModal();load(media,resume?saved.time:0);interval=setInterval(persist,3000);
}
document.addEventListener('keydown',e=>{
  if(!video||!dialog.open||/INPUT|SELECT|TEXTAREA|BUTTON/.test(document.activeElement.tagName))return;
  if(e.code==='Space'){
    e.preventDefault();if(e.repeat)return;spaceHeld=false;originalRate=video.playbackRate;
    spaceTimer=setTimeout(()=>{if(video){spaceHeld=true;video.playbackRate=2;}},260);
  }else if(e.code==='ArrowRight'){e.preventDefault();video.currentTime=Math.min(video.duration||Infinity,video.currentTime+10);}
  else if(e.code==='ArrowLeft'){e.preventDefault();video.currentTime=Math.max(0,video.currentTime-10);}
});
document.addEventListener('keyup',e=>{if(e.code!=='Space'||!video||!dialog.open)return;e.preventDefault();clearTimeout(spaceTimer);if(spaceHeld){video.playbackRate=originalRate;spaceHeld=false;}else if(video.paused)video.play().catch(()=>{});else video.pause();});

export async function resolveServer(item,episode,index,download=false,signal){
  const waiting=modal(download?'تجهيز التنزيل':'تجهيز المشاهدة',`<p>جاري تجهيز رابط السيرفر…</p><span class="spinner"></span>`);
  const controller=new AbortController();const abort=()=>controller.abort();signal?.addEventListener('abort',abort,{once:true});
  waiting.addEventListener('close',abort,{once:true});
  try{
    const q=new URLSearchParams({provider:item.provider,kind:item.kind,id:item.id,episode,index:String(index),download:download?'1':'0'});
    const response=await fetch(`/api/resolve?${q}`,{signal:controller.signal}),media=await response.json();
    if(!response.ok||media.error)throw Error(media.error||'تعذر تجهيز الرابط.');
    waiting.removeEventListener('close',abort);waiting.close();
    if(download){
      const values=readStore('downloads',[]);values.unshift({title:item.title,episode,date:Date.now(),item,index});writeStore('downloads',values.slice(0,100));
      modal('الرابط جاهز',`<p>${esc(item.title)}${episode?' • الحلقة '+esc(episode):''}</p><a class="btn primary" href="${esc(media.playable+'&download=1')}" download>${icon('download')} تنزيل الملف</a> <a class="btn outline" href="${esc(media.url)}" target="_blank" rel="noopener noreferrer">فتح الرابط المباشر</a>`,[{text:'إغلاق',run:d=>d.close()}]);
    }else if(media.qualities?.length){modal('اختر الجودة',`<p>${esc(item.title)}</p>` ,[{text:'تلقائي',primary:true,run:d=>{d.close();openPlayer(media,item,episode);}},...media.qualities.map(q=>({text:q.quality,run:d=>{d.close();openPlayer({...media,...q,qualities:media.qualities},item,episode);}}))]);}
    else openPlayer(media,item,episode);
  }catch(e){waiting.removeEventListener('close',abort);waiting.close();if(e.name!=='AbortError')modal('تعذر تجهيز السيرفر',`<p>${esc(e.message)}</p>`,[{text:'حسناً',run:d=>d.close()}]);}
  finally{signal?.removeEventListener('abort',abort);}
}
