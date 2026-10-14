import {readFile,writeFile,mkdir,rm} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import path from 'node:path';
import {APP_ORIGIN,invokeHandler} from './bridge.mjs';
import {downloadVideo} from './download-core.mjs';

export async function createDownloadManager({app,window,dialog,shell,resolve,unticket,fetchResource,ffmpeg}) {
  const record=path.join(app.getPath('userData'),'downloads.json');
  const cacheRoot=path.join(app.getPath('userData'),'download-cache');
  await rm(cacheRoot,{recursive:true,force:true});await mkdir(cacheRoot,{recursive:true});
  const jobs=new Map();let saving=Promise.resolve();
  try{for(const item of JSON.parse(await readFile(record,'utf8')).slice(0,100)){if(item.status==='running'){item.status='failed';item.error='توقف التنزيل عند إغلاق التطبيق. يمكنك إعادة المحاولة.';}jobs.set(item.id,item);}}catch{}
  const active=()=>[...jobs.values()].filter(x=>x.status==='running');
  const publicJob=job=>({id:job.id,title:job.title,status:job.status,phase:job.phase,completed:job.completed,total:job.total,bytes:job.bytes,error:job.error,date:job.date,filename:path.basename(job.output)});
  function persist(){const values=[...jobs.values()].slice(-100).map(({controller,task,...item})=>item);saving=saving.catch(()=>{}).then(()=>writeFile(record,JSON.stringify(values)));}
  function launch(job){
    job.controller=new AbortController();job.status='running';job.phase='preparing';job.bytes=0;job.completed=0;job.total=0;job.error='';persist();
    job.task=(async()=>{
      try{
        const params={...job.selection,download:'1',target:'web'};
        const response=await invokeHandler(resolve,new Request(APP_ORIGIN+'/api/resolve?'+new URLSearchParams(params),{signal:job.controller.signal}));
        const media=await response.json();if(!response.ok)throw Error(media.error||'تعذر تجهيز التنزيل.');
        let url=media.url,headers={};
        if(media.playable?.startsWith('/api/media?')){const payload=unticket(new URL(media.playable,APP_ORIGIN).searchParams.get('t'));url=payload.u;headers=payload.h;}
        await downloadVideo({url,headers,hls:media.type==='hls',output:job.output,cacheRoot,ffmpeg,fetchResource,signal:job.controller.signal,onProgress:progress=>{Object.assign(job,progress);if(!window.isDestroyed())window.setProgressBar(progress.total?Math.min(.99,progress.completed/progress.total):2);}});
        job.status='completed';
      }catch(error){job.status=job.controller.signal.aborted?'cancelled':'failed';job.error=job.status==='cancelled'?'أُلغي التنزيل.':error.message;}
      finally{delete job.controller;delete job.task;persist();if(!window.isDestroyed()&&!active().length)window.setProgressBar(-1);}
    })();
  }
  async function handle(request){
    const url=new URL(request.url);
    if(url.protocol!=='palma:'||url.host!=='app'||url.pathname!=='/api/desktop/downloads')return undefined;
    if(request.initiatorOrigin!==APP_ORIGIN)return Response.json({error:'طلب غير مسموح.'},{status:403});
    if(request.method==='GET')return Response.json({jobs:[...jobs.values()].reverse().map(publicJob)});
    if(request.method!=='POST')return Response.json({error:'طريقة غير مدعومة.'},{status:405});
    try{
      const text=await request.text();if(text.length>8000)throw Error('طلب تنزيل غير صالح.');
      const body=JSON.parse(text);
      if(body.action==='start'){
        if(active().length>=3)throw Error('توجد ثلاثة تنزيلات جارية. انتظر اكتمال أحدها.');
        const selection=Object.fromEntries(['provider','kind','id','episode','index'].map(k=>[k,String(body[k]??'')]));
        if(!['palma','witcher','drama'].includes(selection.provider)||!selection.id||selection.id.length>300||selection.episode.length>80||!/^\d{1,3}$/.test(selection.index))throw Error('اختيار التنزيل غير صالح.');
        const title=String(body.title||'PALMA').slice(0,160);
        const filename=('PALMA - '+title).replace(/[<>:"/\\|?*\x00-\x1f]/g,' ').replace(/[. ]+$/,'')+'.mp4';
        const result=await dialog.showSaveDialog(window,{title:'حفظ فيديو بالما',defaultPath:path.join(app.getPath('downloads'),filename),filters:[{name:'MP4 Video',extensions:['mp4']}],properties:['showOverwriteConfirmation','createDirectory']});
        if(result.canceled||!result.filePath||request.signal.aborted)return Response.json({cancelled:true});
        const output=result.filePath.endsWith('.mp4')?result.filePath:result.filePath+'.mp4';
        if(active().some(x=>x.output===output))throw Error('يوجد تنزيل جارٍ إلى هذا الملف.');
        const job={id:randomUUID(),title,selection,output,date:Date.now()};jobs.set(job.id,job);launch(job);return Response.json({job:publicJob(job)});
      }
      const job=jobs.get(body.id);if(!job)throw Error('التنزيل غير موجود.');
      if(body.action==='cancel'){job.controller?.abort();return Response.json({ok:true});}
      if(body.action==='retry'){
        if(!['failed','cancelled'].includes(job.status))throw Error('لا يمكن إعادة هذا التنزيل الآن.');
        if(active().length>=3)throw Error('انتظر اكتمال تنزيل جارٍ.');launch(job);return Response.json({job:publicJob(job)});
      }
      if(body.action==='reveal'&&job.status==='completed'){shell.showItemInFolder(job.output);return Response.json({ok:true});}
      throw Error('إجراء تنزيل غير صالح.');
    }catch(error){return Response.json({error:error.message},{status:400});}
  }
  return {handle,active:()=>active().length,shutdown:async()=>{const running=active();for(const job of running)job.controller?.abort();await Promise.allSettled(running.map(job=>job.task));await saving.catch(()=>{});}};
}
