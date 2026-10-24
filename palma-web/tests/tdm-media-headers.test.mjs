import test from 'node:test';
import assert from 'node:assert/strict';
import {bindSourceSession} from '../server/source-session.js';
import {tdmMediaHeaders} from '../server/tdm-media-headers.mjs';
const origin='https://cdn.mediaoscar.com';
const session=bindSourceSession({'User-Agent':'source-supplied-player'},origin,'ot=synthetic');

test('source playlist and segment requests preserve credentials and get distinct transport metadata',()=>{
 const playlist=tdmMediaHeaders(origin+'/index.css',session),segment=tdmMediaHeaders(origin+'/v.js',session);
 assert.equal(playlist.Cookie,'ot=synthetic');assert.equal(playlist['User-Agent'],'source-supplied-player');
 assert.equal(playlist.Accept,'text/css,*/*;q=0.1');assert.equal(playlist['Sec-Fetch-Dest'],'style');
 assert.equal(segment.Accept,'*/*');assert.equal(segment['Sec-Fetch-Dest'],'script');
 assert.equal(segment['Sec-Fetch-Mode'],'no-cors');assert.equal(segment.Referer,origin+'/');
 assert.equal(session.Referer,undefined);
});

test('transport metadata is not enabled for unbound cookies or cross-origin media',()=>{
 for(const [url,headers] of [[origin+'/index.css',{Cookie:'ot=unbound'}],['https://other.example/v.js',session]]){
  const result=tdmMediaHeaders(url,headers);assert.equal(result.Cookie,undefined);assert.equal(result.Referer,undefined);assert.equal(result['Sec-Fetch-Dest'],undefined);
 }
 assert.equal(tdmMediaHeaders(origin+'/normal.m3u8',session)['Sec-Fetch-Dest'],undefined);
});
