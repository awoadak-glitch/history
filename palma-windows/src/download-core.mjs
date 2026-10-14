import {createWriteStream} from 'node:fs';
import {mkdtemp,mkdir,writeFile,rename,rm,stat} from 'node:fs/promises';
import {Readable,Transform} from 'node:stream';
import {pipeline} from 'node:stream/promises';
import {spawn} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import path from 'node:path';

const aborted=signal=>{if(signal?.aborted)throw new Error('أُلغي التنزيل.');};
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));

export async function planHLS(url,{fetchResource,headers={},signal,maxSegments=Infinity}) {
  const blobs=[],manifests=[],known=new Map();
  const blob=raw=>{
    if(known.has(raw))return known.get(raw);
    if(blobs.length>=20000)throw Error('قائمة التنزيل أكبر من الحد المدعوم.');
    const extension=path.posix.extname(new URL(raw).pathname).toLowerCase();
    const suffix=['.mp4','.m4s','.ts','.aac','.m4a','.mp3','.key','.vtt'].includes(extension)?extension:'.bin';
    const name=`part-${blobs.length}${suffix}`;known.set(raw,name);blobs.push({url:raw,name});return name;
  };
  async function playlist(raw,depth=0){
    aborted(signal);if(depth>5)throw Error('قائمة فيديو متداخلة أكثر من الحد المدعوم.');
    const {response,url:finalURL}=await fetchResource(raw,{headers,signal});
    if(!response.ok){await response.body?.cancel();throw Error('تعذر قراءة قائمة التنزيل.');}
    const text=await response.text();
    if(text.length>2*1024*1024||!text.startsWith('#EXTM3U'))throw Error('قائمة تنزيل غير صالحة.');
    const lines=text.split(/\r?\n/),variants=[];
    for(let i=0;i<lines.length;i++)if(lines[i].startsWith('#EXT-X-STREAM-INF:')){
      let next=i+1;while(next<lines.length&&(!lines[next]||lines[next].startsWith('#')))next++;
      if(!lines[next])throw Error('قائمة الجودة غير مكتملة.');
      variants.push({index:i,next,bandwidth:Number(/BANDWIDTH=(\d+)/.exec(lines[i])?.[1]||0)});
    }
    const selected=variants.sort((a,b)=>b.bandwidth-a.bandwidth)[0];
    const audioGroup=selected?/AUDIO="([^"]+)"/.exec(lines[selected.index])?.[1]:null;
    const skip=new Set(variants.filter(x=>x!==selected).flatMap(x=>[x.index,x.next]));
    if(!selected&&!lines.includes('#EXT-X-ENDLIST'))throw Error('تنزيل البث المباشر غير مدعوم؛ اختر حلقة أو فيلمًا.');
    const name=`playlist-${manifests.length}.m3u8`,output=[];
    const manifest={name,text:''};manifests.push(manifest);let count=0;
    for(let i=0;i<lines.length;i++){
      let line=lines[i].trim();if(skip.has(i)||line.startsWith('#EXT-X-I-FRAME-STREAM-INF:'))continue;
      if(line.startsWith('#EXT-X-MEDIA:')&&(!audioGroup||!line.includes(`GROUP-ID="${audioGroup}"`)))continue;
      if(!selected&&count>=maxSegments&&line.startsWith('#EXTINF:'))break;
      if(/^#EXT-X-(?:SESSION-)?KEY:/.test(line)){
        if(!/METHOD=(?:NONE|AES-128)(?:,|$)/.test(line)||/KEYFORMAT="(?!identity")/.test(line))throw Error('هذا النوع من حماية الفيديو غير مدعوم للتنزيل.');
      }
      if(line.startsWith('#')&&line.includes('URI="')){
        const matches=[...line.matchAll(/URI="([^"]+)"/g)];
        for(const match of matches){const absolute=new URL(match[1],finalURL).href;const local=line.startsWith('#EXT-X-MEDIA:')?await playlist(absolute,depth+1):blob(absolute);line=line.replace(match[0],`URI="${local}"`);}
      } else if(line&&!line.startsWith('#')){
        line=selected?await playlist(new URL(line,finalURL).href,depth+1):blob(new URL(line,finalURL).href);count++;
      }
      output.push(line);
    }
    if(!selected&&!output.includes('#EXT-X-ENDLIST'))output.push('#EXT-X-ENDLIST');
    manifest.text=output.join('\n');return name;
  }
  const entry=await playlist(url);return {entry,manifests,blobs};
}

export async function fetchFile(url,file,{fetchResource,headers={},signal,onBytes=()=>{}}) {
  let lastError;
  for(let attempt=0;attempt<3;attempt++){
    aborted(signal);let offset=await stat(file).then(x=>x.size).catch(()=>0);
    try{
      const {response}=await fetchResource(url,{headers:{...headers,...(offset?{Range:`bytes=${offset}-`}:{})},signal});
      if(!response.ok){await response.body?.cancel();throw Error('انقطع الاتصال بمصدر التنزيل.');}
      if(offset&&response.status===206&&!response.headers.get('content-range')?.startsWith(`bytes ${offset}-`)){await response.body?.cancel();throw Error('استجابة استكمال الملف غير صالحة.');}
      if(response.status!==206)offset=0;
      const progress=new Transform({transform(chunk,_encoding,callback){offset+=chunk.length;onBytes(offset);callback(null,chunk);}});
      await pipeline(Readable.fromWeb(response.body),progress,createWriteStream(file,{flags:offset?'a':'w'}),{signal});
      return offset;
    }catch(error){lastError=error;aborted(signal);if(attempt<2)await delay(500*(attempt+1));}
  }
  throw lastError;
}

export function runFFmpeg(executable,args,{signal}={}) {
  return new Promise((resolve,reject)=>{
    aborted(signal);
    const process=spawn(executable,args,{windowsHide:true,shell:false,stdio:['ignore','ignore','pipe']});
    // Never expose stderr: FFmpeg can include signed URLs or local filenames.
    process.stderr.resume();
    const abort=()=>process.kill();signal?.addEventListener('abort',abort,{once:true});
    process.once('error',()=>reject(Error('تعذر تشغيل محرك حفظ الفيديو.')));
    process.once('close',code=>{signal?.removeEventListener('abort',abort);if(signal?.aborted)reject(Error('أُلغي التنزيل.'));else if(code===0)resolve();else reject(Error('تعذر تجميع الفيديو. أعد تجهيز رابط التنزيل.'));});
  });
}

export async function downloadVideo({url,headers={},hls=false,output,cacheRoot,ffmpeg,fetchResource,signal,onProgress=()=>{},maxSegments}) {
  const controller=new AbortController();signal=signal?AbortSignal.any([signal,controller.signal]):controller.signal;
  await mkdir(cacheRoot,{recursive:true});
  const work=await mkdtemp(path.join(cacheRoot,'palma-download-'));
  const partial=output+'.'+randomUUID()+'.part.mp4';let bytes=0;
  try{
    onProgress({phase:'preparing',completed:0,total:0,bytes});
    let input;
    if(hls){
      const plan=await planHLS(url,{fetchResource,headers,signal,maxSegments});
      for(const file of plan.manifests)await writeFile(path.join(work,file.name),file.text);
      let cursor=0,completed=0;const sizes=new Map();
      const workers=Array.from({length:Math.min(4,plan.blobs.length)},async()=>{
        while(cursor<plan.blobs.length){aborted(signal);const item=plan.blobs[cursor++];
          await fetchFile(item.url,path.join(work,item.name),{fetchResource,headers,signal,onBytes:size=>{sizes.set(item.name,size);bytes=[...sizes.values()].reduce((a,b)=>a+b,0);onProgress({phase:'downloading',completed,total:plan.blobs.length,bytes});}});
          completed++;onProgress({phase:'downloading',completed,total:plan.blobs.length,bytes});
        }
      });
      const outcomes=await Promise.allSettled(workers.map(worker=>worker.catch(error=>{controller.abort();throw error;})));
      const failure=outcomes.find(result=>result.status==='rejected');if(failure)throw failure.reason;
      input=path.join(work,plan.entry);
    }else{
      input=path.join(work,'source.bin');bytes=await fetchFile(url,input,{fetchResource,headers,signal,onBytes:size=>{bytes=size;onProgress({phase:'downloading',completed:0,total:0,bytes});}});
    }
    aborted(signal);onProgress({phase:'merging',completed:1,total:1,bytes});
    await runFFmpeg(ffmpeg,['-hide_banner','-loglevel','error','-nostdin','-protocol_whitelist','file,crypto',...(hls?['-allowed_extensions','ALL']:[]),'-i',input,'-map','0:v:0?','-map','0:a:0?','-c','copy','-movflags','+faststart','-f','mp4','-y',partial],{signal});
    aborted(signal);await rename(partial,output);const size=(await stat(output)).size;
    onProgress({phase:'completed',completed:1,total:1,bytes:size});return {bytes:size};
  }finally{await rm(work,{recursive:true,force:true});await rm(partial,{force:true});}
}
