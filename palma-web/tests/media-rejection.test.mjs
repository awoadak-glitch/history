import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {EventEmitter} from 'node:events';
import {PassThrough} from 'node:stream';
import dns from 'node:dns/promises';
import https from 'node:https';
import {assertPlaybackMedia} from '../server/media-rejection.mjs';
import {resolveMedia,rewriteHLS} from '../server/media.js';
import {unticket} from '../server/http.js';

const notice=(await readFile(new URL('./fixtures/source-rejection.m3u8',import.meta.url),'utf8')).replaceAll('\r\n','\n');
const url='https://cdn.mediaoscar.com/sample/index.css';
const refused={status:403,code:'SOURCE_SESSION_REQUIRED'};

test('known rejection video is not accepted just because it is valid HLS under the media origin',()=>{
  assert.throws(()=>assertPlaybackMedia(url,notice),refused);
  assert.throws(()=>assertPlaybackMedia(url,'\uFEFF'+notice.replaceAll('\n','\r\n')+'\r\n'),refused);
  assert.throws(()=>assertPlaybackMedia('https://notice.shahidtv.net/notice.mp4'),refused);
});

test('short valid videos are not rejected based on duration or CSS transport',()=>{
  const short='#EXTM3U\n#EXTINF:60.8,\ntrailer.js\n#EXT-X-ENDLIST';
  assert.doesNotThrow(()=>assertPlaybackMedia(url,short));
  assert.doesNotThrow(()=>assertPlaybackMedia(url,notice.replace('1183272@0','1184000@0')));
  const rewritten=rewriteHLS(short,url);
  const target=new URL(rewritten.split('\n')[2],'https://palma.example');
  assert.equal(unticket(target.searchParams.get('t')).u,'https://cdn.mediaoscar.com/sample/trailer.js');
});

test('the media proxy refuses a known rejection in a nested playlist before rewriting it',()=>{
  assert.throws(()=>rewriteHLS(notice,url),refused);
});

test('resolver refuses a real-shape HTTP 200 CSS response containing the captured rejection playlist',async t=>{
  t.mock.method(dns,'lookup',async()=>[{address:'93.184.216.34',family:4}]);
  t.mock.method(https,'request',(_url,_options,callback)=>{
    const request=new EventEmitter();request.setTimeout=()=>request;
    request.end=()=>{
      const response=new PassThrough();
      Object.assign(response,{statusCode:200,headers:{'content-type':'text/css'}});
      callback(response);response.end(notice);
    };
    return request;
  });
  await assert.rejects(resolveMedia(url),refused);
});
