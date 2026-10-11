import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import catalogue from '../api/catalog.js';
import resolve from '../api/resolve.js';
import media from '../api/media.js';
try { process.loadEnvFile('.env.local'); } catch {}
const root=path.resolve(fileURLToPath(new URL('../public/',import.meta.url)));
const handlers={'/api/catalog':catalogue,'/api/resolve':resolve,'/api/media':media};
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.png':'image/png','.ttf':'font/ttf','.webmanifest':'application/manifest+json'};
const server=http.createServer(async(req,res)=>{
  try{
    const url=new URL(req.url,'http://localhost');
    if(handlers[url.pathname]){await handlers[url.pathname](req,res);return;}
    let pathname=decodeURIComponent(url.pathname);if(pathname==='/')pathname='/index.html';
    const filename=path.resolve(root,'.'+pathname);
    if(!filename.startsWith(root+path.sep)&&filename!==path.join(root,'index.html')){res.writeHead(400).end();return;}
    let content;try{content=await readFile(filename);}catch{if(!path.extname(pathname))content=await readFile(path.join(root,'index.html'));else{res.writeHead(404).end();return;}}
    res.setHeader('Content-Type',types[path.extname(filename)]||'application/octet-stream');res.end(content);
  }catch(e){if(!res.headersSent){res.writeHead(500,{'Content-Type':'application/json'});res.end(JSON.stringify({error:e.message}));}else res.destroy();}
});
const port=Number(process.env.PORT||4173);server.listen(port,'0.0.0.0',()=>console.log(`PALMA VISION listening on http://localhost:${port}`));
