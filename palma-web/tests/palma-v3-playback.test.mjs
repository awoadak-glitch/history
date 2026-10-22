import test from 'node:test';
import assert from 'node:assert/strict';
import {createV3Playback} from '../server/palma/playback-v3.mjs';
import {PalmaV3Error} from '../server/palma/v3.mjs';
import {createResolver} from '../api/resolve.js';

test('V3 maps each supported content kind and resolves watch/download by server ID',async()=>{
  const cases=[
    [{kind:'movie',id:'12'},'api/v3/movies/show.php','movie',12],
    [{kind:'series',id:'12',episode:'34'},'api/v3/episodes/show.php','episode',34],
    [{kind:'anime',id:'12',episode:'34'},'api/v3/anime/episodes/show.php','anime',34],
    [{kind:'anime',id:'12'},'api/v3/anime/show.php','anime_movie',12],
    [{kind:'channel',id:'12'},'api/v3/channels/show.php','channel',12],
    [{kind:'wrestling',id:'12'},'api/v3/wrestling/show.php','wrestling',12],
  ];
  for(const [selection,route,type,cid] of cases)for(const download of [false,true]) {
    let calls=0;
    const get=createV3Playback({get:async(r,q)=>{assert.equal(r,route);assert.deepEqual(q,{id:cid});return {status:'success',data:{watch_links:[{id:81}],download_links:[{id:82}]}};},resolve:async body=>{calls++;assert.deepEqual(body,{t:type,cid,k:download?'download':'watch',lid:download?82:81});return {status:'success',data:{url:'https://cdn.example.com/test.mp4'}};}});
    const result=await get({...selection,download});assert.equal(calls,1);assert.equal(result.source.id,download?82:81);
  }
});

test('signed V3 links already present are used without an extra resolve request',async()=>{
  const get=createV3Playback({get:async()=>({data:{watch_links:[{id:81,url:'https://cdn.example.com/test.mp4'}]}}),resolve:()=>assert.fail('unnecessary resolve')});
  assert.equal((await get({kind:'movie',id:'12'})).source.url,'https://cdn.example.com/test.mp4');
});

test('V3 match highlights use the parent match ID and selected video ID',async()=>{
  const get=createV3Playback({get:async(route,query)=>{assert.equal(route,'api/v3/matches/event-videos.php');assert.equal(query.match_id,901);return {data:[{id:432},{id:433}]};},resolve:async body=>{assert.deepEqual(body,{t:'match_video',cid:901,k:'watch',lid:432});return {data:{url:'https://cdn.example.com/highlight.mp4'}};}});
  const result=await get({kind:'match-video',id:'432',matchId:'901'});assert.equal(result.source.id,432);assert.equal(result.servers.length,1);
});

test('account restriction, update requirement, and player verification cannot become playback URLs',async()=>{
  for(const item of [{premium:true},{update_required:true},{tdm_guard:{action:'block'}},{tamper_guard:{action:'block'}}]) {
    const get=createV3Playback({get:async()=>({data:{watch_links:[{id:81,url:'https://cdn.example.com/test.mp4',...item}]}}),resolve:()=>assert.fail('restricted')});
    await assert.rejects(get({kind:'movie',id:'12'}));
  }
});

test('a rejected Windows registration is returned by the desktop resolver without trying legacy links',async()=>{
  const resolve=createResolver({getCatalogue:()=>assert.fail('must not retry a rejected session through legacy API'),getMedia:()=>assert.fail('no media without registration'),getPalmaPlayback:createV3Playback({get:async()=>{throw new PalmaV3Error('مصدر المشاهدة لم يقبل تسجيل نسخة الكمبيوتر.',503,'PALMA_V3_DEVICE_REJECTED');}})});
  const res={setHeader(){},end(text){this.body=JSON.parse(text);}};
  await resolve({method:'GET',url:'/api/resolve?provider=palma&kind=movie&id=12'},res);
  assert.equal(res.statusCode,503);assert.equal(res.body.code,'PALMA_V3_DEVICE_REJECTED');
});

test('V3 media session travels through the actual resolver for playback and download',async()=>{
  const deep_link='tdmvideo://play?'+new URLSearchParams({url:'https://cdn.mediaoscar.com/test.m3u8',cookies:'ot=synthetic-only',ua:'TDMuaHLS99'});
  const getPalmaPlayback=createV3Playback({get:async()=>({data:{watch_links:[{id:81}],download_links:[{id:82}]}}),resolve:async()=>({data:{deep_link}})});
  for(const download of [false,true]) {
    const resolve=createResolver({getPalmaPlayback,getMedia:async(url,headers)=>{assert.equal(url,'https://cdn.mediaoscar.com/test.m3u8');assert.equal(headers.Cookie,'ot=synthetic-only');return {type:'hls'};}});
    const res={setHeader(){},end(text){this.body=JSON.parse(text);}};
    await resolve({method:'GET',url:'/api/resolve?provider=palma&kind=movie&id=12&download='+(download?'1':'0')},res);
    assert.equal(res.statusCode,200);assert.equal(res.body.type,'hls');
  }
});

test('legacy update-required response is diagnosed before fetching a stale video URL',async()=>{
  const resolve=createResolver({getCatalogue:async()=>({data:{update_required:true,watch_links:[{url:'https://cdn.example.com/stale.mp4'}]}}),getMedia:()=>assert.fail('must not fetch obsolete link')});
  const res={setHeader(){},end(text){this.body=JSON.parse(text);}};
  await resolve({method:'GET',url:'/api/resolve?provider=palma&kind=movie&id=12'},res);
  assert.equal(res.statusCode,503);assert.equal(res.body.code,'PALMA_UPDATE_REQUIRED');
});
