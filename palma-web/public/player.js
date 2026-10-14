import {icon,esc,modal,toast,readStore,writeStore} from './lib.js';
import {playbackTarget,mxPlayerLink,awrPlayerLink} from './external-player.js';
import {startDesktopDownload} from './desktop-downloads.js';
import {openPlayer} from './playback.js';
export {openPlayer,closePlayer} from './playback.js';

export async function resolveServer(item,episode,index,download=false,signal){
  if(download&&location.protocol==='palma:')return startDesktopDownload(item,episode,index);
  const waiting=modal(download?'تجهيز التنزيل':'تجهيز المشاهدة',`<p>جاري تجهيز رابط السيرفر…</p><span class="spinner"></span>`);
  const controller=new AbortController();const abort=()=>controller.abort();signal?.addEventListener('abort',abort,{once:true});
  waiting.addEventListener('close',abort,{once:true});
  try{
    const target=location.protocol==='palma:'?'browser':playbackTarget(readStore('playback-target','auto'),navigator.userAgent);
    const q=new URLSearchParams({provider:item.provider,kind:item.kind,id:item.id,episode,index:String(index),download:download?'1':'0',match_id:item.matchId||'',target:target==='browser'?'web':'external'});
    const response=await fetch(`/api/resolve?${q}`,{signal:controller.signal}),media=await response.json();
    if(!response.ok||media.error)throw Error(media.error||'تعذر تجهيز الرابط.');
    waiting.removeEventListener('close',abort);waiting.close();
    if(download){
      const values=readStore('downloads',[]);values.unshift({title:item.title,episode,date:Date.now(),item,index});writeStore('downloads',values.slice(0,100));
      const downloadURL=media.download||(media.playable?.startsWith('/api/media?')?media.playable+'&download=1':media.url);
      modal('الرابط جاهز',`<p>${esc(item.title)}${episode?' • الحلقة '+esc(item.episodeNumber||episode):''}</p><a class="btn primary" href="${esc(downloadURL)}" target="_blank" rel="noopener noreferrer" download>${icon('download')} تنزيل الملف</a> <a class="btn outline" href="${esc(media.url)}" target="_blank" rel="noopener noreferrer">فتح الرابط المباشر</a>`,[{text:'إغلاق',run:d=>d.close()}]);
    }else if(target!=='browser')openExternal(media,item,episode,target);
    else openPlayer(media,item,episode);
  }catch(e){waiting.removeEventListener('close',abort);waiting.close();if(e.name!=='AbortError')modal('تعذر تجهيز السيرفر',`<p>${esc(e.message)}</p>`,[{text:'حسناً',run:d=>d.close()}]);}
  finally{signal?.removeEventListener('abort',abort);}
}

function openExternal(media,item,episode,target){
  const name=target==='mx'?'MX Player':'AWR MX';
  const build=m=>target==='mx'?mxPlayerLink(m,item,episode,readStore('mx-edition','ad')):awrPlayerLink(m,item,episode);
  const options=[{...media,quality:media.quality||'تلقائي'},...(media.qualities||[]).map(q=>({...media,...q}))];
  const message=target==='mx'?'اضغط فتح المشغّل. إذا لم يكن MX Player مثبتًا ستفتح صفحته في المتجر.':'افتح AWR MX V7 مرة واحدة لربطه بالموقع، ثم اضغط فتح المشغّل ووافق على طلب المتصفح.';
  const d=modal(`المشاهدة في ${name}`,`<p>${esc(item.title)}${episode?' · الحلقة '+esc(parseInt(episode)||episode):''}</p><p class="muted">${message}</p>${options.length>1?`<label>الجودة<select id="external-quality">${options.map((m,i)=>`<option value="${i}">${esc(m.quality)}</option>`).join('')}</select></label>`:''}<a class="btn primary" id="external-open" href="${esc(build(media))}">${icon('play')} فتح ${name}</a><p class="muted" id="external-status" role="status"></p>${target==='awr'?'<a class="btn outline" href="#/player-setup">إعداد مشغّل الكمبيوتر</a>':''}`, [{text:'نسخ رابط الفيديو',run:async()=>{try{await navigator.clipboard.writeText(media.url);toast('تم نسخ رابط الفيديو.');}catch{toast('تعذر نسخ الرابط.');}}},{text:'إغلاق',run:d=>d.close()}]);
  const anchor=d.querySelector('#external-open');
  d.querySelector('#external-quality')?.addEventListener('change',e=>{anchor.href=build(options[Number(e.target.value)]);});
  anchor.addEventListener('click',()=>{d.querySelector('#external-status').textContent='تم طلب فتح المشغّل. إذا لم يفتح، تحقّق من تثبيته وربطه بالموقع.';});
  d.querySelector('a[href="#/player-setup"]')?.addEventListener('click',()=>d.close());
  // Some resolvers take longer than the browser's user-activation window.
  // Keep a real anchor available instead of pretending an async launch succeeded.
  if(navigator.userActivation?.isActive && options.length===1)anchor.click();
}
