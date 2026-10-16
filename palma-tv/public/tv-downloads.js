import {esc,modal,toast} from './lib.js';
import {openPlayer} from './playback.js';
async function command(body){const r=await fetch('/api/tv/downloads',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}),result=await r.json();if(!r.ok||result.error)throw Error(result.error||'تعذر إكمال العملية.');return result;}
export async function startTVDownload(item,episode,index){try{await command({action:'start',item,episode,index});toast('بدأ التنزيل. تابع تقدّمه من قائمة التنزيلات.');}catch(error){modal('التنزيل',`<p>${esc(error.message)}</p>`,[{text:'إغلاق',run:d=>d.close()}]);}}
const labels={preparing:'تجهيز التنزيل',downloading:'جاري التنزيل',completed:'جاهز للمشاهدة دون إنترنت',failed:'تعذر التنزيل',cancelled:'أُلغي التنزيل',interrupted:'توقف التنزيل'};
export function renderTVDownloads(main,signal){
  main.innerHTML='<div class="page"><div class="page-heading"><h1>التنزيلات</h1><p>محفوظة على هذا التلفاز للمشاهدة داخل بالما. اترك التطبيق مفتوحًا حتى يكتمل التنزيل.</p></div><div id="tv-download-list"></div></div>';
  const list=main.querySelector('#tv-download-list');let jobs=[],busy=false;
  async function refresh(){if(signal.aborted||busy)return;busy=true;try{const response=await fetch('/api/tv/downloads',{signal});jobs=await response.json();if(!Array.isArray(jobs))throw Error();const active=document.activeElement,restore=active?.closest('[data-job]')?{id:active.closest('[data-job]').dataset.job,action:active.dataset.do}:null;
    list.innerHTML=jobs.length?jobs.map(j=>`<article class="tv-download" data-job="${j.id}"><h3>${esc(j.item.title)}${j.episode?' · الحلقة '+esc(j.episode):''}</h3><p>${labels[j.state]} · ${(j.bytes/1048576).toFixed(1)} MB${j.total?' · '+j.completed+'/'+j.total:''}</p>${j.total?`<progress max="${j.total}" value="${j.completed}"></progress>`:''}${j.error?`<p>${esc(j.error)}</p>`:''}<div class="chips">${j.state==='completed'?'<button data-do="play" class="btn primary">مشاهدة</button>':['preparing','downloading'].includes(j.state)?'<button data-do="cancel" class="btn outline">إلغاء التنزيل</button>':'<button data-do="retry" class="btn primary">إعادة المحاولة</button>'}<button data-do="delete" class="btn outline">حذف</button></div></article>`).join(''):'<div class="state"><h3>لا توجد تنزيلات بعد</h3><p>اختر «تنزيل» من سيرفرات الفيلم أو الحلقة.</p><a class="btn primary" href="#/home">تصفح بالما</a></div>';
    if(restore)list.querySelector(`[data-job="${restore.id}"] [data-do="${restore.action}"]`)?.focus({preventScroll:true});
  }catch(e){if(e.name!=='AbortError'&&!jobs.length)list.innerHTML='<p>تعذر قراءة التنزيلات.</p>';}finally{busy=false;}}
  list.onclick=async e=>{const button=e.target.closest('[data-do]'),job=jobs.find(j=>j.id===button?.closest('[data-job]').dataset.job);if(!job)return;const action=button.dataset.do;
    if(action==='play'){openPlayer({type:job.type,playable:job.playable,name:'محفوظ على التلفاز',qualities:[]},job.item,job.episode);return;}
    const run=async()=>{try{await command({action,id:job.id});await refresh();}catch(error){toast(error.message);}};
    if(action==='delete')modal('حذف التنزيل؟',`<p>${esc(job.item.title)}</p>`,[{text:'إلغاء',run:d=>d.close()},{text:'حذف',run:d=>{d.close();run();}}]);else await run();
  };
  refresh();const timer=setInterval(refresh,1800);signal.addEventListener('abort',()=>clearInterval(timer),{once:true});
}
