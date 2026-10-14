import test from 'node:test';
import assert from 'node:assert/strict';
import {card,detailLink} from '../public/lib.js';
import {sectionKind,orderedSections,sectionQuery} from '../public/palma-content.js';
import {normalizeImages} from '../server/palma/images.mjs';
import {PalmaClient} from '../server/palma/client.mjs';

test('latest-episode cards keep episode identity until detail resolves the parent series',()=>{
  const episode=card({id:43000,season_id:2900,series_id:1600,series_title:'مسلسل تجريبي',episode_number:2,series_poster:'/uploads/test.webp'},'palma',sectionKind('latest_episodes'));
  assert.equal(episode.id,'43000');
  assert.equal(episode.kind,'episode');
  assert.equal(episode.title,'مسلسل تجريبي');
  assert.match(detailLink(episode),/kind=episode&id=43000/);
  assert.equal(episode.poster,'https://api.ossccaarr111.online/uploads/test.webp');
});

test('native section ordering and category links preserve the original home contract',()=>{
  const sections=orderedSections([{display_order:9,section_type:'promo_banner_section'},{display_order:3,section_type:'movies_spotlight'}]);
  assert.equal(sections[0].section_type,'movies_spotlight');
  assert.deepEqual(sectionQuery({sort_by:'newest',content_category_filter:4,content_filters:{language:'en',arbitrary:'ignored'}}),{sort_by:'newest',category:'4',language:'en'});
  const spotlight=card({item_id:4000,item_type:'movie',title_ar:'فيلم',bg_image:'/uploads/spotlight/bg.webp'},'palma','movie');
  assert.equal(spotlight.id,'4000');assert.match(spotlight.cover,/\/uploads\/spotlight\/bg.webp$/);
});

test('poster normalization traverses nested home data and never rewrites playback URLs',()=>{
  const input={data:{sections:[{items:[{poster:'/uploads/a.webp',url:'https://cdn.example/video?token=unchanged',home_team:{logo:'https://teams.example/1.png'}}]}]}};
  const result=normalizeImages(input,'https://images.example/');
  assert.equal(result.data.sections[0].items[0].poster,'https://images.example/uploads/a.webp');
  assert.equal(result.data.sections[0].items[0].url,input.data.sections[0].items[0].url);
  assert.equal(result.data.sections[0].items[0].home_team.logo,'https://teams.example/1.png');
  assert.equal(input.data.sections[0].items[0].poster,'/uploads/a.webp');
  assert.equal(normalizeImages({poster:'/tmdb-poster.jpg'}).poster,'https://image.tmdb.org/t/p/w780/tmdb-poster.jpg');
  assert.equal(normalizeImages({poster:'https://admin.dramaramadan.net/uploads/a.webp'},'https://images.example/').poster,'https://images.example/uploads/a.webp');
});

test('independent client synchronizes once and signs API paths while preserving query parameters',async()=>{
  const calls=[];
  const client=new PalmaClient({fetchImpl:async(url,options)=>{
    calls.push({url:new URL(url),options});
    return new Response(JSON.stringify({status:'success',data:[]}),{headers:{Date:new Date(Date.now()+90000).toUTCString()}});
  }});
  await client.get('api/series/',{search:'اختبار',page:2});
  await client.get('api/seasons/',{series_id:1});
  assert.equal(calls.filter(c=>c.url.pathname==='/api/ping.php').length,1);
  assert.equal(calls[1].url.searchParams.get('app_version'),'17');
  assert.equal(calls[1].url.searchParams.get('search'),'اختبار');
  assert.match(calls[1].options.headers['X-Iron-Sig'],/^[a-f0-9]{64}$/);
  assert.equal(calls[1].options.redirect,'error');
  assert.ok(Number(calls[1].options.headers['X-Iron-Ts'])>Date.now()/1000+85);
  await assert.rejects(client.get('api/../secret'),/Invalid/);
});
