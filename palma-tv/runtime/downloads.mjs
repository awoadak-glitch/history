import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {mkdir,readFile,writeFile,rename,rm,stat} from 'node:fs/promises';
import resolve from './web/api/resolve.js';
import {unticket,fetchPublic} from './web/server/http.js';
import {planHLS,fetchFile} from './download-core.mjs';

async function resolveSelection(selection){let status=200,body;await resolve({method:'GET',url:'/api/resolve?'+new URLSearchParams({...selection,download:'1',target:'web'})},{setHeader(){},set statusCode(value){status=value;},end(value){body=JSON.parse(value);}});if(status!==200||body.error)throw Error(body.error||'تعذر تجهيز التنزيل.');return body;}
export class Downloads{
  constructor(root,{resolver=resolveSelection,fetchResource=fetchPublic}={}){this.root=root;this.resolver=resolver;this.fetchResource=fetchResource;this.jobs=[];this.active=new Map();this.saves=Promise.resolve();this.ready=this.load();}
  async load(){await mkdir(this.root,{recursive:true});try{this.jobs=JSON.parse(await readFile(path.join(this.root,'jobs.json'),'utf8')).filter(j=>/^[a-f0-9-]{36}$/.test(j.id)).slice(0,100);for(const j of this.jobs)if(['preparing','downloading'].includes(j.state)){j.state='interrupted';j.error='توقف التنزيل عند إغلاق التطبيق. اختر إعادة المحاولة.';}}catch{this.jobs=[];}}
  list(){return this.jobs.map(({selection,...j})=>({...j,playable:j.state==='completed'?`/offline/${j.id}/${j.entry}`:null}));}
  save(){const snapshot=JSON.stringify(this.jobs);this.saves=this.saves.catch(()=>{}).then(async()=>{await writeFile(path.join(this.root,'jobs.tmp'),snapshot);await rename(path.join(this.root,'jobs.tmp'),path.join(this.root,'jobs.json'));});return this.saves;}
  async command(body){
    if(body.action==='start'){
      if(this.active.size>=2)throw Error('انتظر اكتمال أحد التنزيلات أو ألغِه أولًا.');if(this.jobs.length>=100)throw Error('احذف بعض التنزيلات أولًا.');
      const item=body.item;if(!item||!['palma','witcher','drama'].includes(item.provider)||typeof item.id!=='string'||item.id.length>300||!Number.isInteger(body.index)||body.index<0||body.index>300)throw Error('اختيار غير صالح.');
      if(item.kind==='channel'||item.kind==='match-video')throw Error('تنزيل البث المباشر غير مدعوم.');
      const clean={id:item.id,title:String(item.title||'تنزيل').slice(0,200),provider:item.provider,kind:String(item.kind||'movie'),poster:String(item.poster||'').slice(0,2000)};
      const job={id:randomUUID(),item:clean,episode:String(body.episode||'').slice(0,80),selection:{provider:clean.provider,kind:clean.kind,id:clean.id,episode:String(body.episode||''),index:String(body.index)},created:Date.now(),state:'preparing',bytes:0,completed:0,total:0};this.jobs.unshift(job);await this.save();this.run(job);return {id:job.id};
    }
    const job=this.jobs.find(j=>j.id===body.id);if(!job)throw Error('التنزيل غير موجود.');
    if(body.action==='cancel'){this.active.get(job.id)?.controller.abort();await this.active.get(job.id)?.promise;return {ok:true};}
    if(body.action==='delete'){this.active.get(job.id)?.controller.abort();await this.active.get(job.id)?.promise;await rm(path.join(this.root,job.id),{recursive:true,force:true});this.jobs=this.jobs.filter(j=>j!==job);await this.save();return {ok:true};}
    if(body.action==='retry'){if(this.active.has(job.id)||job.state==='completed')return {ok:true};if(this.active.size>=2)throw Error('انتظر اكتمال التنزيل الحالي.');await rm(path.join(this.root,job.id),{recursive:true,force:true});this.run(job);return {ok:true};}
    throw Error('عملية غير صالحة.');
  }
  run(job){const controller=new AbortController(),state={controller,promise:null};this.active.set(job.id,state);state.promise=this.download(job,controller.signal).catch(error=>{job.state=controller.signal.aborted?'cancelled':'failed';job.error=controller.signal.aborted?'أُلغي التنزيل.':error.code==='ENOSPC'?'مساحة التخزين غير كافية.':'تعذر إكمال التنزيل. أعد المحاولة أو اختر سيرفرًا آخر.';}).finally(async()=>{this.active.delete(job.id);await this.save();});}
  async download(job,signal){
    Object.assign(job,{state:'preparing',bytes:0,completed:0,total:0,error:null});await this.save();
    const media=await this.resolver(job.selection);if(signal.aborted)throw Error('cancelled');
    if(media.live||!['hls','video'].includes(media.type))throw Error('هذا البث غير قابل للتنزيل.');
    const source=media.playable?.startsWith('/api/media?')?unticket(new URL(media.playable,'http://local').searchParams.get('t')):{u:media.url,h:media.headers||{}};
    const folder=path.join(this.root,job.id);await mkdir(folder,{recursive:true});
    const opts={fetchResource:this.fetchResource,headers:source.h,signal};let last=0;
    const progress=()=>{if(Date.now()-last>1000){last=Date.now();this.save().catch(()=>{});}};
    job.type=media.type;job.state='downloading';
    if(media.type==='hls'){
      const plan=await planHLS(source.u,opts);job.total=plan.blobs.length;job.entry=plan.entry;
      // Sequential bounded transfers are suitable for low-memory TVs. Secrets/remote URLs remain in memory only.
      for(const blob of plan.blobs){const before=job.bytes;await fetchFile(blob.url,path.join(folder,blob.name),{...opts,onBytes:n=>{job.bytes=before+n;progress();}});job.completed++;}
      for(const manifest of plan.manifests)await writeFile(path.join(folder,manifest.name),manifest.text);
    }else{job.entry='video.mp4';await fetchFile(source.u,path.join(folder,job.entry),{...opts,onBytes:n=>{job.bytes=n;progress();}});job.total=job.completed=1;}
    if(signal.aborted)throw Error('cancelled');job.state='completed';await this.save();
  }
  file(url){const m=/^\/offline\/([a-f0-9-]{36})\/([a-zA-Z0-9.-]+)$/.exec(url);if(!m||m[2].includes('..'))return null;const job=this.jobs.find(j=>j.id===m[1]&&j.state==='completed');return job?path.join(this.root,m[1],m[2]):null;}
  close(){for(const state of this.active.values())state.controller.abort();}
}
