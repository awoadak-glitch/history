import test from 'node:test';
import assert from 'node:assert/strict';
import {createResolver} from '../api/resolve.js';
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
test('Witcher resolves the actual media through the shared engine and retains server selection',async()=>{
  const calls=[];
  const resolve=createResolver({getCatalogue:async(provider,route,query)=>{calls.push({provider,route,query});return query?{url:'https://pixeldrain.com/api/file/example',direct:true,name:'PD',quality:'1080p'}:{items:[{name:'PD',quality:'1080p'},{name:'MF',quality:'720p'}]};},getMedia:async(url,headers,quality,external)=>{assert.equal(url,'https://pixeldrain.com/api/file/example');assert.equal(external,false);return {type:'video',playable:'/api/media?t=signed',quality,delivery:'proxy'};}});
  const res={statusCode:0,setHeader(){},end(value){this.body=JSON.parse(value);}};
  await resolve({method:'GET',url:'/api/resolve?provider=witcher&kind=anime&id=Naruto&episode=001&index=0'},res);
  assert.equal(res.statusCode,200);assert.equal(res.body.delivery,'proxy');assert.equal(res.body.selection.episode,'001');assert.equal(res.body.servers.length,2);
  assert.equal(calls[0].route,'anime/Naruto/episodes/001/servers');assert.equal(calls[0].query.resolve,0);
});
test('Match highlights are selected by video ID within the match, never treated as episodes',async()=>{
  const resolve=createResolver({getCatalogue:async(provider,route,query)=>{assert.equal(route,'api/matches/event_videos.php');assert.equal(query.match_id,'901');return {data:[{id:432,video_url:'https://cdn.example.com/highlight.mp4'}]};},getMedia:async(url)=>({url,playable:url,type:'video'})});
  const res={setHeader(){},end(value){this.body=JSON.parse(value);}};
  await resolve({method:'GET',url:'/api/resolve?kind=match-video&id=432&match_id=901'},res);
  assert.equal(res.statusCode,200);assert.equal(res.body.url,'https://cdn.example.com/highlight.mp4');
  assert.equal(navigationState('match').active,'matches');
});
