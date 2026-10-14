import {PassThrough,Readable} from 'node:stream';
import {readFile} from 'node:fs/promises';
import path from 'node:path';

export const APP_ORIGIN='palma://app';
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.png':'image/png','.webp':'image/webp','.svg':'image/svg+xml','.ttf':'font/ttf','.woff2':'font/woff2','.webmanifest':'application/manifest+json'};
const csp="default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data:; font-src 'self'; connect-src 'self' https:; media-src 'self' https: blob:; worker-src 'self' blob:; object-src 'none'; frame-src 'none'; base-uri 'self'";

// Adapt the shared Node handlers to Electron's Request/Response protocol.
// No TCP listener or renderer-side Node/IPC bridge is exposed.
export function invokeHandler(handler,request) {
  return new Promise((resolve,reject)=>{
    const headers=new Headers();
    class ResponseStream extends PassThrough {
      statusCode=200;headersSent=false;
      setHeader(name,value){headers.set(name,String(value));}
      getHeader(name){return headers.get(name);}
      commit(){
        if(this.headersSent)return;
        this.headersSent=true;
        headers.set('X-Content-Type-Options','nosniff');
        const empty=request.method==='HEAD'||[204,304].includes(this.statusCode);
        resolve(new Response(empty?null:Readable.toWeb(this),{status:this.statusCode,headers}));
        if(empty)this.resume();
      }
      _transform(chunk,encoding,callback){this.commit();super._transform(chunk,encoding,callback);}
      _final(callback){this.commit();callback();}
    }
    const output=new ResponseStream();
    output.on('error',reject);
    const abort=()=>output.destroy(new Error('Request cancelled'));
    request.signal?.addEventListener('abort',abort,{once:true});
    output.once('close',()=>request.signal?.removeEventListener('abort',abort));
    const url=new URL(request.url);
    const req={method:request.method,url:url.pathname+url.search,headers:Object.fromEntries(request.headers)};
    Promise.resolve().then(()=>handler(req,output)).catch(error=>output.destroy(error));
  });
}

export function createAppHandler({root,handlers}) {
  root=path.resolve(root);
  return async request=>{
    const url=new URL(request.url);
    if(url.protocol!=='palma:'||url.host!=='app'||url.username||url.password) return new Response('Not found',{status:404});
    if(request.initiatorOrigin && request.initiatorOrigin!==APP_ORIGIN) return new Response('Forbidden',{status:403});
    if(!['GET','HEAD'].includes(request.method)) return new Response('Method not allowed',{status:405});
    if(handlers[url.pathname]) return invokeHandler(handlers[url.pathname],request);
    if(url.pathname.startsWith('/api/'))return new Response('Not found',{status:404});
    let decoded;try{decoded=decodeURIComponent(url.pathname);}catch{return new Response('Bad path',{status:400});}
    if(decoded.includes('\\')||decoded.includes('\0'))return new Response('Bad path',{status:400});
    const file=path.resolve(root,'.'+(decoded==='/'?'/index.html':decoded));
    const relative=path.relative(root,file);
    if(!relative||relative.startsWith('..')||path.isAbsolute(relative))return new Response('Bad path',{status:400});
    try {
      const bytes=await readFile(file);
      return new Response(request.method==='HEAD'?null:bytes,{headers:{'Content-Type':types[path.extname(file)]||'application/octet-stream','Content-Security-Policy':csp,'X-Content-Type-Options':'nosniff'}});
    } catch {return new Response('Not found',{status:404});}
  };
}
