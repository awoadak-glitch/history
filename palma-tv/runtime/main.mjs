import http from 'node:http';
import {timingSafeEqual} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {createReadStream} from 'node:fs';
import {stat,readFile,writeFile} from 'node:fs/promises';
import catalog from './web/api/catalog.js';
import resolve from './web/api/resolve.js';
import media from './web/api/media.js';
import {json} from './web/server/http.js';
import {Downloads} from './downloads.mjs';

const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.png':'image/png','.ttf':'font/ttf','.m3u8':'application/vnd.apple.mpegurl','.ts':'video/mp2t','.mp4':'video/mp4','.m4s':'video/mp4','.vtt':'text/vtt','.aac':'audio/aac','.key':'application/octet-stream'};
const equal=(a,b)=>typeof a==='string'&&a.length===b.length&&timingSafeEqual(Buffer.from(a),Buffer.from(b));
export async function serveFile(req,res,file){
  let info;try{info=await stat(file);}catch{return json(res,404,{error:'الملف غير موجود.'});}
  if(!info.isFile())return json(res,404,{error:'الملف غير موجود.'});
  res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');res.setHeader('Accept-Ranges','bytes');
  let start=0,end=info.size-1;
  if(req.headers.range){
    const m=/^bytes=(\d*)-(\d*)$/.exec(req.headers.range);
    if(m&&(m[1]||m[2])){start=m[1]?Number(m[1]):Math.max(0,info.size-Number(m[2]));end=m[1]&&m[2]?Math.min(Number(m[2]),end):end;}
    if(!m||(!m[1]&&!m[2])||!Number.isSafeInteger(start)||!Number.isSafeInteger(end)||start>end||start>=info.size){res.statusCode=416;res.setHeader('Content-Range',`bytes */${info.size}`);return res.end();}
    res.statusCode=206;res.setHeader('Content-Range',`bytes ${start}-${end}/${info.size}`);
  }
  res.setHeader('Content-Length',Math.max(0,end-start+1));
  if(req.method==='HEAD'||!info.size)return res.end();
  const stream=createReadStream(file,{start,end});stream.on('error',()=>res.destroy());res.on('close',()=>stream.destroy());stream.pipe(res);
}
export async function readJSON(req){let size=0,parts=[];for await(const chunk of req){size+=chunk.length;if(size>32768)throw Error('الطلب أكبر من المسموح.');parts.push(chunk);}return JSON.parse(Buffer.concat(parts).toString());}
export function createTVServer({token,port,storage,publicRoot,test=false}){
  const origin=`http://127.0.0.1:${port}`,downloads=new Downloads(storage);
  const server=http.createServer(async(req,res)=>{
    res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('Cache-Control','no-store');
    try{
      if(req.headers.host!==`127.0.0.1:${port}`||req.headers.origin&&req.headers.origin!==origin)return json(res,403,{error:'طلب غير مسموح.'});
      const url=new URL(req.url,origin);
      if(url.pathname==='/'&&equal(url.searchParams.get('session'),token)){
        res.setHeader('Set-Cookie',`palma_session=${token}; HttpOnly; SameSite=Strict; Path=/`);res.statusCode=302;res.setHeader('Location','/');return res.end();
      }
      const cookie=/(?:^|;\s*)palma_session=([^;]+)/.exec(req.headers.cookie||'')?.[1];
      if(!equal(cookie,token)&&!equal(req.headers.authorization,`Bearer ${token}`))return json(res,401,{error:'أعد فتح التطبيق.'});
      if(url.pathname==='/health')return json(res,200,{ready:true,version:'1.0.0'});
      if(url.pathname==='/api/tv/downloads'){
        await downloads.ready;
        if(req.method==='GET')return json(res,200,downloads.list());
        if(req.method!=='POST')return json(res,405,{error:'طلب غير مدعوم.'});
        const body=await readJSON(req);return json(res,200,await downloads.command(body));
      }
      if(url.pathname.startsWith('/offline/')){
        await downloads.ready;const file=downloads.file(url.pathname);if(!file)return json(res,404,{error:'التنزيل غير متاح.'});return serveFile(req,res,file);
      }
      if(test&&url.pathname==='/test/report'&&req.method==='POST'){
        const result=await readJSON(req);await writeFile(path.join(storage,'verification.json'),JSON.stringify(result,null,2));console.log('PALMA_TV_REPORT '+JSON.stringify(result));return json(res,200,{ok:true});
      }
      const handler={'/api/catalog':catalog,'/api/resolve':resolve,'/api/media':media}[url.pathname];if(handler)return await handler(req,res);
      if(!['GET','HEAD'].includes(req.method))return json(res,405,{error:'طلب غير مدعوم.'});
      const name=decodeURIComponent(url.pathname);if(name.includes('\0')||name.includes('\\'))return json(res,400,{error:'مسار غير صالح.'});
      const file=path.resolve(publicRoot,'.'+(name==='/'?'/index.html':name));if(!file.startsWith(publicRoot+path.sep))return json(res,404,{error:'الملف غير موجود.'});
      if(name==='/'&&test){res.setHeader('Content-Type','text/html; charset=utf-8');return res.end((await readFile(file,'utf8')).replace('</body>','<script type="module" src="/tv-self-test.js"></script></body>'));}
      return serveFile(req,res,file);
    }catch{if(!res.headersSent)json(res,500,{error:'تعذر إكمال العملية. أعد المحاولة.'});else res.destroy();}
  });
  server.on('close',()=>downloads.close());return server;
}
if(process.argv[1]===fileURLToPath(import.meta.url)){
  const [, ,rawPort,token,storage,mode]=process.argv;
  if(!/^\d+$/.test(rawPort)||!token||token.length!==64||!storage)throw Error('Invalid local runtime configuration');
  const root=fileURLToPath(new URL('./web/public',import.meta.url));
  createTVServer({port:Number(rawPort),token,storage,publicRoot:root,test:mode==='test'}).listen(Number(rawPort),'127.0.0.1',()=>console.log('PALMA TV runtime ready'));
}
