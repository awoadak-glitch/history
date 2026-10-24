import test from 'node:test';
import assert from 'node:assert/strict';
import {decodePlaybackLink} from '../server/tdm-link.js';
import {playbackSource} from '../server/source.js';
import {rewriteHLS} from '../server/media.js';
import {unticket} from '../server/http.js';
import {createResolver} from '../api/resolve.js';

const signed='https://media.example/live/master.m3u8?sig=a%2Bb%2F&expires=123';
const deep=(url,extra={})=>'tdmvideo://play?'+new URLSearchParams({url,...extra});

test('TDM plain and both Base64 alphabets preserve the complete signed URL',()=>{
 for(const value of [signed,Buffer.from(signed).toString('base64'),Buffer.from(signed).toString('base64url')]) {
  const actual=decodePlaybackLink(deep(value,{user_agent:'Native-UA',ua:'unused'}));
  assert.equal(actual.url,signed);assert.equal(actual.headers['User-Agent'],'Native-UA');
 }
 assert.equal(decodePlaybackLink(deep(signed)).headers['User-Agent'],'TDMPlayer/2.2.2');
 assert.equal(decodePlaybackLink(signed).url,signed);
});

test('TDM URL-player transport matches an independently generated APK-format fixture',()=>{
 const payload='AjkfHkpOUQ8RajQdZHUjLjwNEnwkTzAaKVgkV0F_AR9CPVcKEF4vOCw8NEYPahJPT34tHVAMR31wOVcKYwEMKX1WRmAHEmNBPEwxGwFcHlMHHEMKZQ';
 for(const scheme of ['urlplayer','urlvplayer'])for(const host of ['play','web']) {
  const actual=decodePlaybackLink(`${scheme}://${host}?url=${payload}&agent=Source-UA`);
  assert.equal(actual.url,signed);assert.equal(actual.headers['User-Agent'],'Source-UA');
  assert.equal(actual.mode,host==='web'?'page':'direct');
 }
 assert.equal(decodePlaybackLink('urlplayer://open?'+new URLSearchParams({url:signed})).url,signed);
});

test('native signed channel URL and B2 user agent take precedence over the raw catalogue URL',()=>{
 const actual=playbackSource({url:'https://media.example/old.m3u8',deep_link:deep(signed,{ua:'TDMuaHLS99',cookies:'synthetic-not-forwarded'}),user_agent:''});
 assert.equal(actual.url,signed);assert.deepEqual(actual.headers,{'User-Agent':'TDMuaHLS99'});
 const playlist=rewriteHLS('#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\nsegment.ts',actual.url,actual.headers);
 const tokens=[...playlist.matchAll(/\/api\/media\?t=([^"\n]+)/g)].map(m=>unticket(decodeURIComponent(m[1])));
 assert.equal(tokens.length,2);for(const token of tokens)assert.deepEqual(token.h,{'User-Agent':'TDMuaHLS99'});
});

test('browser and downloader links retain their URL; malformed protocols cannot become media',()=>{
 for(const scheme of ['tdm','tdmbrowser','tdmstory','tmdownloader','tmhelper'])assert.equal(decodePlaybackLink(`${scheme}://open?${new URLSearchParams({url:signed})}`).url,signed);
 for(const url of ['javascript:alert(1)','file:///private','https://user:password@media.example/file.mp4','https://media.example/file\r\nInjected:yes'])assert.throws(()=>decodePlaybackLink(deep(url)));
 assert.throws(()=>decodePlaybackLink('urlplayer://play?url=broken'));
 assert.throws(()=>decodePlaybackLink('tdmvideo://play?url='+encodeURIComponent('a'.repeat(33000))));
 const result=playbackSource({url:signed,deep_link:'tdmvideo://play?url=invalid',headers:{Cookie:'secret',Authorization:'secret','User-Agent':'unsafe\r\nInjected:yes'}});
 assert.deepEqual(result,{url:signed,headers:{}});
});

test('episode resolver uses the fresh TDM contract and keeps selected quality through the media engine',async()=>{
 let checked=false;
 const handler=createResolver({getCatalogue:async()=>({data:{watch_links:[{url:'https://media.example/stale',quality:'720p',deep_link:deep(signed,{ua:'TDMuaHLS99'})}]}}),getMedia:async(url,headers,quality)=>{assert.equal(url,signed);assert.equal(headers['User-Agent'],'TDMuaHLS99');assert.equal(quality,'720p');checked=true;return {type:'hls',playable:'/api/media?t=test',quality};}});
 let response;await handler({method:'GET',url:'/api/resolve?provider=palma&kind=series&id=1&episode=2&index=0'},{setHeader(){},end(value){response=JSON.parse(value);}});
 assert.equal(checked,true);assert.equal(response.selection.episode,'2');assert.equal(response.quality,'720p');
});
