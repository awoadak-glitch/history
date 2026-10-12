import test from 'node:test';
import assert from 'node:assert/strict';
import resolve from '../api/resolve.js';
import {witcherPlayback} from '../server/media.js';
import {mainNavigation,worldNavigation,navigationState} from '../public/navigation.js';

test('APK main navigation keeps Source World third and all eleven original tabs',()=>{
  assert.deepEqual(mainNavigation.map(t=>t[0]),['home','series','world','movies','channels','matches','anime','wrestling','favorites','search','settings']);
  assert.deepEqual(worldNavigation.map(t=>t[1]),['الرئيسية','الأنمي','المسلسلات','الأفلام','القنوات']);
});
test('Source World films, channels and episode pages retain the correct selected navigation',()=>{
  assert.equal(navigationState('world',new URLSearchParams('tab=movies')).active,'world?tab=movies');
  assert.equal(navigationState('servers',new URLSearchParams('provider=drama&kind=channel')).active,'world?tab=channels');
  const mainAnime=navigationState('detail',new URLSearchParams('provider=witcher&kind=series&area=main'));
  assert.equal(mainAnime.world,false);assert.equal(mainAnime.active,'anime');
});
test('Witcher direct URLs are preserved for playback and download, and unsafe URLs are rejected',()=>{
  const url='https://pixeldrain.com/api/file/hMfbxwb4?token=example';
  const media=witcherPlayback({url,direct:true,quality:'1080p'});
  assert.equal(media.playable,url);assert.equal(media.download,url);assert.equal(media.type,'video');
  assert.equal(witcherPlayback({url:'https://cdn.example.com/master.m3u8?x=1',direct:true}).type,'hls');
  for(const url of ['javascript:alert(1)','http://127.0.0.1/video.mp4','http://169.254.169.254/latest','https://name:pass@cdn.example.com/video.mp4'])assert.throws(()=>witcherPlayback({url,direct:true}));
  assert.throws(()=>witcherPlayback({url:'https://example.com/file',direct:false}));
});
test('reported Vercel anime error is fixed with MEDIA_TICKET_SECRET absent',async()=>{
  const originalFetch=globalThis.fetch,vercel=process.env.VERCEL,secret=process.env.MEDIA_TICKET_SECRET;
  process.env.VERCEL='1';delete process.env.MEDIA_TICKET_SECRET;
  const calls=[];globalThis.fetch=async(url)=>{calls.push(String(url));return new Response(JSON.stringify({url:'https://pixeldrain.com/api/file/hMfbxwb4',direct:true,name:'PD',quality:'1080p'}),{headers:{'Content-Type':'application/json'}});};
  const res={statusCode:0,setHeader(){},end(value){this.body=JSON.parse(value);}};
  try{
    await resolve({method:'GET',url:'/api/resolve?provider=witcher&kind=anime&id=Naruto&episode=001&index=0'},res);
    assert.equal(res.statusCode,200);assert.equal(res.body.delivery,'direct');assert.equal(res.body.playable,'https://pixeldrain.com/api/file/hMfbxwb4');
    assert.equal(calls.length,1);assert.match(calls[0],/^https:\/\/awr-stream-web\.vercel\.app\/api\/anime\/Naruto\/episodes\/001\/servers\?resolve=0$/);
  }finally{globalThis.fetch=originalFetch;if(vercel===undefined)delete process.env.VERCEL;else process.env.VERCEL=vercel;if(secret===undefined)delete process.env.MEDIA_TICKET_SECRET;else process.env.MEDIA_TICKET_SECRET=secret;}
});
