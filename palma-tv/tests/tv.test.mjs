import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,rm,mkdir,readFile} from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {createTVServer} from '../build/runtime/main.mjs';
import {Downloads} from '../build/runtime/downloads.mjs';
import {nearest} from '../public/tv-focus.js';
import {once} from 'node:events';
import http from 'node:http';

test('Remote focus follows screen geometry in RTL rails and stays in aligned rows',()=>{
  const r=(left,top)=>({left,top,right:left+100,bottom:top+80});const options=[{value:'left',rect:r(100,100)},{value:'below',rect:r(200,220)},{value:'diagonal',rect:r(320,190)}];
  assert.equal(nearest(r(200,100),options,'ArrowLeft'),'left');assert.equal(nearest(r(200,100),options,'ArrowDown'),'below');assert.equal(nearest(r(200,100),options,'ArrowRight'),'diagonal');assert.equal(nearest(r(200,100),options,'ArrowUp'),null);
});
test('Local runtime requires a session, rejects foreign origins/hosts, supports offline byte ranges',async()=>{
  const root=await mkdtemp(path.join(os.tmpdir(),'palma-tv-')),token='a'.repeat(64);await mkdir(path.join(root,'public'));await writeFile(path.join(root,'public/index.html'),'PALMA');await writeFile(path.join(root,'public/sample.mp4'),'0123456789');
  const probe=http.createServer().listen(0,'127.0.0.1');await once(probe,'listening');const port=probe.address().port;await new Promise(r=>probe.close(r));
  const server=createTVServer({token,port,storage:path.join(root,'downloads'),publicRoot:path.join(root,'public')}).listen(port,'127.0.0.1');await once(server,'listening');const base=`http://127.0.0.1:${port}`,auth={Authorization:`Bearer ${token}`};
  try{
    assert.equal((await fetch(base+'/health')).status,401);assert.equal((await fetch(base+'/health',{headers:auth})).status,200);
    assert.equal((await fetch(base+'/health',{headers:{...auth,Origin:'https://example.org'}})).status,403);
    const foreignHost=await new Promise((resolve,reject)=>{const req=http.get(base+'/health',{headers:{...auth,Host:'evil.example'}},res=>{res.resume();resolve(res.statusCode);});req.on('error',reject);});assert.equal(foreignHost,403);
    const login=await fetch(base+'/?session='+token,{redirect:'manual'});assert.equal(login.status,302);assert.match(login.headers.get('set-cookie'),/HttpOnly; SameSite=Strict/);
    const response=await fetch(base+'/sample.mp4',{headers:{...auth,Range:'bytes=3-6'}});assert.equal(response.status,206);assert.equal(response.headers.get('content-range'),'bytes 3-6/10');assert.equal(await response.text(),'3456');
    assert.equal((await fetch(base+'/sample.mp4',{headers:{...auth,Range:'bytes=100-'}})).status,416);
    assert.equal((await fetch(base+'/offline/../../outside',{headers:auth})).status,404);
    assert.equal((await fetch(base+'/test/report',{method:'POST',headers:auth,body:'{}'})).status,405);
  }finally{server.closeAllConnections();await new Promise(r=>server.close(r));await rm(root,{recursive:true,force:true});}
});
test('Offline HLS persists local playlists, supports removal and never stores source credentials',async()=>{
  const root=await mkdtemp(path.join(os.tmpdir(),'palma-tv-dl-'));
  const manager=new Downloads(root,{resolver:async()=>({type:'hls',url:'https://video.example/film.m3u8?secret=hidden',headers:{Cookie:'secret-cookie'}}),fetchResource:async url=>({url,response:new Response(url.includes('.m3u8')?'#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXTINF:5,\nclip.ts\n#EXT-X-ENDLIST':'video-data')})});
  try{
    await manager.ready;const {id}=await manager.command({action:'start',item:{provider:'palma',id:'1',kind:'movie',title:'Test'},episode:'',index:0});await manager.active.get(id).promise;
    const job=manager.list()[0];assert.equal(job.state,'completed');assert.equal(job.bytes,10);assert.ok(manager.file(job.playable));assert.equal(manager.file('/offline/'+id+'/..'),null);
    const manifest=await readFile(manager.file(job.playable),'utf8');assert.match(manifest,/part-0.ts/);assert.doesNotMatch(manifest,/https:|secret/);
    assert.doesNotMatch(await readFile(path.join(root,'jobs.json'),'utf8'),/hidden|secret-cookie/);
    const restarted=new Downloads(root);await restarted.ready;assert.equal(restarted.list()[0].state,'completed');await restarted.command({action:'delete',id});assert.equal(restarted.list().length,0);
  }finally{manager.close();await rm(root,{recursive:true,force:true});}
});
