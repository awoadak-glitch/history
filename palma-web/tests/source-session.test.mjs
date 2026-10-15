import test from 'node:test';
import assert from 'node:assert/strict';
import {playbackSource} from '../server/source.js';
import {bindSourceSession,mediaHeadersForURL,SESSION_ORIGIN} from '../server/source-session.js';
import {ticket,unticket} from '../server/http.js';
import {rewriteHLS} from '../server/media.js';
import {createResolver} from '../api/resolve.js';

const origin='https://cdn.mediaoscar.com';
const url=origin+'/test/master.m3u8';
const cookie='ot=synthetic-playback-session';
const native=(target=url)=>({url:target,deep_link:'tdmvideo://play?'+new URLSearchParams({url:target,ua:'TDMuaHLS99',cookies:cookie+'; unrelated=excluded'})});

test('approved PALMA session is paired with the selected native URL and source UA',()=>{
 const result=playbackSource(native(),{provider:'palma'});
 assert.equal(result.headers.Cookie,cookie);assert.equal(result.headers[SESSION_ORIGIN],origin);assert.equal(result.headers['User-Agent'],'TDMuaHLS99');
 assert.equal(playbackSource(native(),{provider:'drama'}).headers.Cookie,undefined);
 const encoded=native();encoded.deep_link='tdmvideo://play?'+new URLSearchParams({url:Buffer.from(url).toString('base64url'),cookies:cookie});
 assert.equal(playbackSource(encoded,{provider:'palma'}).headers.Cookie,cookie);
 const fallback={...native(),deep_link:'tdmvideo://play?'+new URLSearchParams({url:'invalid',cookies:cookie})};
 assert.equal(playbackSource(fallback,{provider:'palma'}).headers.Cookie,undefined);
});

test('only the two approved HTTPS source origins receive their own ot cookie',()=>{
 for(const allowed of [origin,'https://still-dust-c9ae.manon-lol2000.workers.dev'])assert.equal(playbackSource(native(allowed+'/test.m3u8'),{provider:'palma'}).headers.Cookie,cookie);
 for(const denied of ['https://cdn.mediaoscar.com.evil.example','http://cdn.mediaoscar.com','https://cdn.mediaoscar.com:8443','https://other.example'])assert.equal(playbackSource(native(denied+'/test.m3u8'),{provider:'palma'}).headers.Cookie,undefined);
 const bound=bindSourceSession({'User-Agent':'TDM'},origin,cookie);
 assert.equal(mediaHeadersForURL(url,bound).Cookie,cookie);
 for(const target of ['https://still-dust-c9ae.manon-lol2000.workers.dev/file','https://notice.shahidtv.net/file','https://other.example/file'])assert.equal(mediaHeadersForURL(target,bound).Cookie,undefined);
 assert.deepEqual(mediaHeadersForURL(url,{cookie:'unbound',AUTHORIZATION:'untrusted'}),{});
 assert.equal(bindSourceSession({},origin,'ot=x\r\nInjected: yes').Cookie,undefined);
});

test('encrypted media tickets conceal credentials and restore their exact origin scope',()=>{
 const headers=bindSourceSession({'User-Agent':'TDM'},origin,cookie);
 const token=ticket(url,headers);
 assert.match(token,/^v2\./);assert.ok(!token.includes(cookie));
 for(const part of token.split('.').slice(1))assert.ok(!Buffer.from(part,'base64url').toString().includes(cookie));
 const restored=unticket(token);assert.equal(restored.u,url);assert.equal(restored.h.Cookie,cookie);assert.equal(restored.h[SESSION_ORIGIN],origin);
 for(let part=1;part<4;part++){const pieces=token.split('.');pieces[part]=(pieces[part][0]==='A'?'B':'A')+pieces[part].slice(1);assert.throws(()=>unticket(pieces.join('.')));}
});

test('HLS variants, keys and segments retain their session only on the original source origin',()=>{
 const headers=bindSourceSession({},origin,cookie);
 const rewritten=rewriteHLS('#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXT-X-STREAM-INF:BANDWIDTH=100\n720.m3u8\nhttps://other.example/segment.ts',url,headers);
 const payloads=[...rewritten.matchAll(/\/api\/media\?t=([^"\n]+)/g)].map(m=>unticket(decodeURIComponent(m[1])));
 assert.equal(payloads.length,3);assert.equal(payloads[0].h.Cookie,cookie);assert.equal(payloads[1].h.Cookie,cookie);assert.equal(payloads[2].h.Cookie,undefined);
});

test('real resolver passes the authorized session to both watch and download preparation',async()=>{
 for(const download of ['0','1']){
  let received=false;
  const resolve=createResolver({getCatalogue:async()=>({data:{watch_links:[native()],download_links:[native()]}}),getMedia:async(target,headers)=>{assert.equal(target,url);assert.equal(headers.Cookie,cookie);received=true;return {type:'hls',playable:'/api/media?t=test'};}});
  const res={setHeader(){},end(value){this.body=JSON.parse(value);}};
  await resolve({method:'GET',url:'/api/resolve?provider=palma&kind=movie&id=1&download='+download},res);
  assert.equal(received,true);assert.equal(res.statusCode,200);
 }
});
