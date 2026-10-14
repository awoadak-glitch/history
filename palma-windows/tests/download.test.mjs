import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {planHLS,fetchFile} from '../src/download-core.mjs';

test('HLS plan keeps selected quality, maps init/segments locally, and bounds test samples',async()=>{
 const playlists={
  'https://media.example/master.m3u8':'#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nhigh.m3u8\n',
  'https://media.example/high.m3u8':'#EXTM3U\n#EXT-X-MAP:URI="init.mp4"\n#EXTINF:4,\n1.m4s\n#EXTINF:4,\n2.m4s\n#EXTINF:4,\n3.m4s\n#EXT-X-ENDLIST\n',
 };
 const calls=[];const fetchResource=async url=>{calls.push(url);assert.ok(playlists[url]);return {url,response:new Response(playlists[url])};};
 const plan=await planHLS('https://media.example/master.m3u8',{fetchResource,maxSegments:2});
 assert.equal(plan.blobs.length,3);assert.equal(calls.length,2);assert.ok(!calls.some(x=>x.includes('low')));
 assert.ok(plan.manifests.every(x=>!x.text.includes('https:')));
 assert.ok(plan.manifests[1].text.includes('#EXT-X-ENDLIST'));
 assert.match(plan.blobs[0].name,/\.mp4$/);assert.match(plan.blobs[1].name,/\.m4s$/);
});
test('unsupported protected streams and endless live playlists are rejected before download',async()=>{
 for(const text of ['#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI="key"\n#EXTINF:4,\n1.ts\n#EXT-X-ENDLIST','#EXTM3U\n#EXTINF:4,\n1.ts']){
  await assert.rejects(planHLS('https://media.example/video.m3u8',{fetchResource:async url=>({url,response:new Response(text)})}),/غير مدعوم/);
 }
});
test('interrupted file resumes at actual saved byte offset without duplicating data',async()=>{
 const root=await mkdtemp(path.join(tmpdir(),'palma-download-test-'));let calls=0;
 try{
  const bytes=await fetchFile('https://media.example/part',path.join(root,'part'),{fetchResource:async(url,options)=>{
   calls++;
   if(calls===1)return {url,response:new Response(new ReadableStream({start(controller){controller.enqueue(new TextEncoder().encode('abc'));setTimeout(()=>controller.error(Error('interrupted')),30);}}))};
   assert.equal(options.headers.Range,'bytes=3-');return {url,response:new Response('def',{status:206,headers:{'Content-Range':'bytes 3-5/6'}})};
  }});
  assert.equal(bytes,6);assert.equal(await readFile(path.join(root,'part'),'utf8'),'abcdef');assert.equal(calls,2);
 }finally{await rm(root,{recursive:true,force:true});}
});
