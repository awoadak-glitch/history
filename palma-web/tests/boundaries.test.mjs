import test from 'node:test';
import assert from 'node:assert/strict';
import {validateRoute} from '../api/catalog.js';
import {isPrivateIP,ticket,unticket,publicURL} from '../server/http.js';
import {parseHLS,rewriteHLS,unwrapURL,extractStreams} from '../server/media.js';
import {card,keyOf,playbackKey,saveProgress,progressFor,readStore,toggleFavorite,favorite} from '../public/lib.js';

test('only extracted catalogue routes, no arbitrary URLs or traversal',()=>{
  assert.equal(validateRoute('palma','api/series/'),true);
  assert.equal(validateRoute('witcher','anime/Naruto/episodes/001/servers'),true);
  assert.equal(validateRoute('palma','https://example.com/api/series/'),false);
  assert.equal(validateRoute('palma','api/../secret'),false);
  assert.equal(validateRoute('palma','api/app/check-update.php'),false);
  assert.equal(validateRoute('drama','search/Naruto/0/'),true);
});
test('media disallows local and metadata addresses, including mapped IPv6',async()=>{
  for(const ip of ['127.0.0.1','10.0.0.2','172.20.1.2','192.168.1.1','169.254.169.254','100.100.100.200','::1','fd00::1','::ffff:127.0.0.1','::ffff:7f00:1'])assert.equal(isPrivateIP(ip),true,ip);
  assert.equal(isPrivateIP('8.8.8.8'),false);
  assert.equal(isPrivateIP('2606:4700:4700::1111'),false);
  await assert.rejects(publicURL('http://127.0.0.1/api'),/غير مدعوم/);
  await assert.rejects(publicURL('file:///etc/passwd'),/غير مدعوم/);
});
test('media tickets cannot be changed to another resource or arbitrary headers',()=>{
  const token=ticket('https://cdn.example.com/video.mp4',{Referer:'https://example.com/'});
  assert.equal(unticket(token).u,'https://cdn.example.com/video.mp4');
  assert.throws(()=>unticket(token.slice(0,-1)+'X'));
});
test('HLS variants preserve quality and manifests rewrite segments and encryption keys',()=>{
  const master='#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080\n1080/stream.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720\n720/stream.m3u8';
  const variants=parseHLS(master,'https://cdn.example.com/master.m3u8');assert.deepEqual(variants.map(v=>v.quality),['1080p','720p']);
  const text=rewriteHLS('#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXTINF:5,\nseg.ts','https://cdn.example.com/a/playlist.m3u8');
  assert.match(text,/URI="\/api\/media\?t=/);assert.match(text,/\n\/api\/media\?t=/);
});
test('deep links must contain a real HTTP video, HTML media detection rejects assets',()=>{
  assert.equal(unwrapURL('tdm://open?url=https%3A%2F%2Fcdn.example.com%2Fvideo.mp4'),'https://cdn.example.com/video.mp4');
  assert.throws(()=>unwrapURL('tdm://open?token=private'));
  assert.deepEqual(extractStreams('<script src="/ads.js"></script><source src="/film.mp4">','https://cdn.example.com/'),['https://cdn.example.com/film.mp4']);
});
test('Palma and Witcher schemas retain stable identity, distinct provider IDs',()=>{
  const anime=card({objectID:'Naruto',name:{ar:'ناروتو'},aniList_poster:{large:'https://example.com/poster.jpg'}},'witcher','anime');
  assert.equal(anime.title,'ناروتو');assert.equal(anime.poster,'https://example.com/poster.jpg');
  const movie=card({id:11,title_ar:'الفيلم',release_year:2026},'palma','movie');assert.equal(movie.year,'2026');
  assert.notEqual(keyOf(movie),keyOf({...movie,provider:'drama'}));
});
test('progress survives a client restart and link changes; favorites remain reversible',()=>{
  const map=new Map();globalThis.localStorage={getItem:k=>map.get(k)||null,setItem:(k,v)=>map.set(k,v)};
  const item=card({id:'Naruto',name:'Naruto'},'witcher','anime');
  saveProgress(item,'001',123.5,1430);const persisted=JSON.parse(map.get('palma.progress'));
  globalThis.localStorage={getItem:k=>k==='palma.progress'?JSON.stringify(persisted):map.get(k)||null,setItem:(k,v)=>map.set(k,v)};
  assert.equal(progressFor({...item,url:'https://changed.example/new.mp4'},'001').time,123.5);
  assert.equal(toggleFavorite(item),true);assert.equal(favorite(item),true);assert.equal(toggleFavorite(item),false);
});
