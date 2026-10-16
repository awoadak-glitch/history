import test from 'node:test';
import assert from 'node:assert/strict';
import {animeInfo,animeFacts,animeCharacters,episodeCountdown,animeImage} from '../public/anime-details.js';
import {animeDetailMarkup} from '../public/anime-view.js';
import {card} from '../public/lib.js';
import {validateRoute} from '../api/catalog.js';

const raw={id:'Naruto',name:'Naruto',type:'مسلسل',duration:23,mal_id:'20',other_names:['narotu','naroto'],
 rating:{rate:9.49936678032148,totalRatingsCount:10215},trailer:{youtube_video_id:'-G9BqkgZXRA'},
 details:{state:'مكتمل',eps_num:'220 حلقة',season:'خريف عام 2002',studio:['Studio Pierrot'],source:'مانجا',age:'+13',start_date:'2002-10-03',end_date:'2007-02-08',english_title:'Naruto',mal_mean:8.03,mal_rank:724,mal_num_scoring_users:2174424}};

test('AWR nested anime information preserves separate scores, studios, dates and trailer',()=>{
 const info=animeInfo(raw);
 assert.equal(info.rating,raw.rating.rate);assert.equal(info.ratingCount,10215);assert.equal(info.malScore,8.03);assert.equal(info.malRank,724);
 assert.deepEqual(info.studios,['Studio Pierrot']);assert.equal(info.episodes,'220 حلقة');assert.equal(info.youtube,'-G9BqkgZXRA');
 assert.ok(animeFacts(info).some(([label,value])=>label==='مدة الحلقة'&&value==='23 دقيقة'));
 assert.ok(animeFacts(info).some(([label,value])=>label==='نهاية العرض'&&value==='2007-02-08'));
 assert.equal(card(raw,'witcher','anime').rating,String(raw.rating.rate));
});

test('nextEpTimeInSec is an absolute timestamp with a ticking countdown and explicit expired state',()=>{
 const now=Date.parse('2026-10-15T12:00:00Z'),seconds=2*86400+3*3600+4*60+5;
 const info=animeInfo({nextEpTimeInSec:now/1000+seconds,next_episode_number:8,details:{state:'مستمر'}});
 assert.deepEqual(episodeCountdown(info,now).parts,[2,3,4,5]);assert.match(episodeCountdown(info,now).label,/8/);
 assert.deepEqual(episodeCountdown(info,now+1000).parts,[2,3,4,4]);
 assert.equal(episodeCountdown(info,now+seconds*1000).state,'elapsed');
 assert.equal(episodeCountdown(info,now+(seconds+100)*1000).remaining,0);
 assert.equal(animeInfo({nextEpTimeInSec:info.nextAt*1000}).nextAt,info.nextAt);
});

test('completed, missing and invalid schedules never invent a future release date',()=>{
 assert.equal(episodeCountdown(animeInfo({...raw,nextEpTimeInSec:2000000000})).state,'finished');
 for(const value of [null,'',0,'invalid',Infinity])assert.equal(episodeCountdown(animeInfo({nextEpTimeInSec:value})).state,'unknown');
 assert.equal(episodeCountdown(animeInfo({show_time:'الأحد',details:{state:'مستمر'}})).state,'unknown');
});

test('character relations retain portraits, main/support roles and only supplied voice actors',()=>{
 const records=[{id:'2',name:'Supporting',role:'Supporting',main_picture:'https://images.example/support.jpg'},
  {id:'relation',character_id:'17',role:'Main',character_info:{id:'17',name:'Naruto Uzumaki',main_picture:'https://images.example/naruto.jpg',favorites:101,
   data:{about:'Character biography supplied by AWR',voices:[{person:{name:'Junko Takeuchi',images:{jpg:{image_url:'https://images.example/voice.jpg'}}},language:'Japanese'}]}}},
  {id:'2',name:'Duplicate'},{}];
 const characters=animeCharacters(records);
 assert.equal(characters.length,2);assert.equal(characters[0].id,'17');assert.equal(characters[0].name,'Naruto Uzumaki');
 assert.equal(characters[0].main,true);assert.equal(characters[0].image,'https://images.example/naruto.jpg');
 assert.equal(characters[0].voices[0].name,'Junko Takeuchi');assert.equal(characters[0].voices[0].image,'https://images.example/voice.jpg');
 assert.equal(characters[0].description,'Character biography supplied by AWR');
 assert.equal(animeCharacters([{id:'17',name:'Naruto',voices:records[1].character_info.data.voices}])[0].voices[0].language,'Japanese');
 assert.equal(characters[1].role,'شخصية مساعدة');assert.equal(characters[1].voices.length,0);
});

test('anime extras stay inside the catalogue boundary and external text cannot become markup',()=>{
 assert.equal(validateRoute('witcher','anime/Naruto/extras'),true);
 for(const route of ['anime/Naruto/extras/admin','anime/%2e%2e/extras','characters/private'])assert.equal(validateRoute('witcher',route),false);
 assert.equal(animeImage('javascript:alert(1)'),'');assert.equal(animeImage('https://user:password@images.example/a.jpg'),'');
 const item=card({...raw,name:'<img src=x onerror=alert(1)>',poster:'javascript:alert(1)',story:'<script>bad()</script>'},'witcher','anime');
 const html=animeDetailMarkup(item,raw);
 assert.ok(!html.includes('<script>'));assert.ok(html.includes('&lt;img'));assert.ok(!html.includes('src="javascript:'));
 assert.ok(html.includes('anime-tab-characters'));assert.ok(html.includes('aria-controls="anime-panel-episodes"'));
});
