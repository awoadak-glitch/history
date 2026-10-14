import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {Readable} from 'node:stream';
import {pipeline} from 'node:stream/promises';
import {createAppHandler,invokeHandler} from '../src/bridge.mjs';

test('desktop protocol serves only bundled files and refuses foreign origins',async()=>{
 const root=await mkdtemp(path.join(tmpdir(),'palma-'));
 try{
  await writeFile(path.join(root,'index.html'),'<main>PALMA</main>');
  const serve=createAppHandler({root,handlers:{'/api/catalog':(_q,r)=>{r.setHeader('Content-Type','application/json');r.end('{"ok":true}');}}});
  const home=await serve(new Request('palma://app/'));assert.equal(await home.text(),'<main>PALMA</main>');assert.match(home.headers.get('content-security-policy'),/script-src 'self'/);
  assert.equal((await serve(new Request('palma://other/'))).status,404);
  assert.equal((await serve(new Request('palma://app/%2e%2e%2fsecret'))).status,400);
  const foreign=new Request('palma://app/api/catalog');foreign.initiatorOrigin='https://untrusted.example';assert.equal((await serve(foreign)).status,403);
  assert.equal((await serve(new Request('palma://app/api/catalog',{method:'POST'}))).status,405);
  assert.deepEqual(await (await serve(new Request('palma://app/api/catalog'))).json(),{ok:true});
 }finally{await rm(root,{recursive:true,force:true});}
});
test('media adapter streams ranges without buffering entire videos',async()=>{
 const request=new Request('palma://app/api/media?t=test',{headers:{Range:'bytes=0-1023'}});
 const response=await invokeHandler(async(req,res)=>{
  assert.equal(req.headers.range,'bytes=0-1023');res.statusCode=206;res.setHeader('Content-Type','video/mp4');res.setHeader('Content-Range','bytes 0-1023/5000');
  await pipeline(Readable.from([Buffer.alloc(512,1),Buffer.alloc(512,2)]),res);
 },request);
 assert.equal(response.status,206);assert.equal(response.headers.get('content-range'),'bytes 0-1023/5000');const bytes=new Uint8Array(await response.arrayBuffer());assert.equal(bytes.length,1024);assert.equal(bytes[1023],2);
});
