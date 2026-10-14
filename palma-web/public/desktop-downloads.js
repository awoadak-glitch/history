import {esc,icon,modal,toast} from './lib.js';

async function command(action,values={}){
  const response=await fetch('/api/desktop/downloads',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action,...values})});
  const result=await response.json();if(!response.ok)throw Error(result.error||'تعذر إجراء التنزيل.');return result;
}
export async function startDesktopDownload(item,episode,index){
  const title=item.title+(episode?' - الحلقة '+(item.episodeNumber||episode):'');
  const waiting=modal('حفظ الفيديو','<p>اختر مكان حفظ الملف في نافذة Windows.</p><span class="spinner"></span>');
  try{
    const result=await command('start',{provider:item.provider,kind:item.kind,id:item.id,episode,index,title});waiting.close();
    if(result.cancelled)return;
    modal('بدأ التنزيل','<p>يمكنك متابعة المشاهدة. يجمع بالما الفيديو ويحفظه في ملف MP4.</p>',[{text:'التنزيلات',primary:true,run:d=>{d.close();location.hash='#/downloads';}},{text:'متابعة التصفح',run:d=>d.close()}]);
  }catch(error){waiting.close();modal('تعذر التنزيل',`<p>${esc(error.message)}</p>`,[{text:'حسنًا',run:d=>d.close()}]);}
}
export function renderDesktopDownloads(element,signal){
  const statuses={preparing:'تجهيز الرابط',downloading:'تنزيل الأجزاء',merging:'تجميع ملف الفيديو',completed:'اكتمل',failed:'تعذر التنزيل',cancelled:'أُلغي'};
  const size=bytes=>bytes>1024**3?(bytes/1024**3).toFixed(2)+' GB':(bytes/1024**2).toFixed(1)+' MB';
  element.innerHTML='<div class="page-heading"><h1>التنزيلات</h1><p>فيديوهات محفوظة على جهازك</p></div><div id="desktop-download-list"><span class="spinner"></span></div>';
  const list=element.querySelector('#desktop-download-list');
  let timer;
  const refresh=async()=>{
    try{
      const response=await fetch('/api/desktop/downloads',{signal}),data=await response.json();if(!response.ok)throw Error(data.error);
      list.innerHTML=data.jobs.length?data.jobs.map(job=>{
        const running=job.status==='running',state=running?job.phase:job.status,percent=job.total?Math.floor(job.completed/job.total*100):0;
        return `<article class="download-item native-download"><div class="download-title"><strong>${esc(job.title)}</strong><small>${esc(statuses[state]||state)} · <bdi>${size(job.bytes||0)}</bdi>${running&&job.total?' · '+percent+'%':''}</small>${running?`<div class="progress"><span style="width:${percent}%"></span></div>`:''}${job.error?`<small>${esc(job.error)}</small>`:''}<small dir="auto">${esc(job.filename)}</small></div><div class="actions">${running?`<button class="btn outline" data-download-action="cancel" data-download-id="${job.id}">إلغاء</button>`:job.status==='completed'?`<button class="btn outline" data-download-action="reveal" data-download-id="${job.id}">${icon('folder')} فتح المجلد</button>`:`<button class="btn outline" data-download-action="retry" data-download-id="${job.id}">${icon('refresh')} إعادة المحاولة</button>`}</div></article>`;
      }).join(''):'<div class="state"><p>اختر سيرفر تنزيل من صفحة الحلقة أو الفيلم ليظهر هنا.</p></div>';
    }catch(error){if(error.name!=='AbortError')list.textContent=error.message;}
    if(!signal.aborted)timer=setTimeout(refresh,1000);
  };
  element.onclick=async event=>{const button=event.target.closest('[data-download-action]');if(!button)return;button.disabled=true;try{await command(button.dataset.downloadAction,{id:button.dataset.downloadId});}catch(error){toast(error.message);}finally{button.disabled=false;}};
  signal.addEventListener('abort',()=>{clearTimeout(timer);element.onclick=null;},{once:true});refresh();
}
