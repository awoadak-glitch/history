import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { params, fail, unticket, fetchPublic, readLimited, SourceError } from '../server/http.js';
import { rewriteHLS } from '../server/media.js';
import {inspectMedia} from '../server/media-format.js';
import {rewriteDASH,expandDashTicket} from '../server/dash.js';

export default async function handler(req,res) {
  const controller=new AbortController();
  const cancel=()=>controller.abort();res.once?.('close',cancel);
  try {
    if(!['GET','HEAD'].includes(req.method)) throw new SourceError('طريقة طلب غير مدعومة.',405);
    const payload=unticket(params(req).get('t'));
    const headers={...payload.h};
    if(req.headers.range && /^bytes=\d*-\d*$/.test(req.headers.range)) headers.Range=req.headers.range;
    const fetched=await fetchPublic(expandDashTicket(payload,params(req)),{headers,method:req.method,signal:controller.signal});
    let response=fetched.response;const url=fetched.url;
    if(!response.ok) { await response.body?.cancel(); throw new SourceError('تعذر تشغيل هذا السيرفر. اختر سيرفرًا آخر.',response.status===403?403:502); }
    const type=response.headers.get('content-type')||'';
    let format;
    if(req.method!=='HEAD'){const inspected=await inspectMedia(response,url);format=inspected.type;response=inspected.response;}
    if(format==='page'&&/text\/html|application\/json/i.test(type)) { await response.body?.cancel(); throw new SourceError('السيرفر لم يرجع فيديو صالحًا.',422); }
    res.setHeader('Cache-Control','private, max-age=120');
    if(req.method!=='HEAD'&&format==='dash'){
      const text=await readLimited(response,2*1024*1024);res.setHeader('Cache-Control','no-store');
      res.setHeader('Content-Type','application/dash+xml');res.end(rewriteDASH(text,url,payload.h));return;
    }
    if(req.method!=='HEAD' && format==='hls') {
      const text=await readLimited(response,2*1024*1024);
      res.setHeader('Cache-Control','no-store');
      res.setHeader('Content-Type','application/vnd.apple.mpegurl');
      res.end(rewriteHLS(text,url,payload.h));return;
    }
    res.statusCode=response.status;
    for(const name of ['content-type','content-length','content-range','accept-ranges','last-modified']) {const value=response.headers.get(name);if(value)res.setHeader(name,value);}
    if(format==='mpegts'){res.setHeader('Content-Type','video/mp2t');res.setHeader('Cache-Control','no-store');}
    if(format==='flv'){res.setHeader('Content-Type','video/x-flv');res.setHeader('Cache-Control','no-store');}
    if(params(req).get('download')==='1') res.setHeader('Content-Disposition','attachment; filename="PALMA-video.mp4"');
    if(req.method==='HEAD'){res.end();return;}
    await pipeline(Readable.fromWeb(response.body),res);
  }catch(e){if(!res.headersSent)fail(res,e);else res.destroy();}
  finally{res.removeListener?.('close',cancel);controller.abort();}
}
