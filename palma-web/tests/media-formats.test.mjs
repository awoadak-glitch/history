import test from 'node:test';
import assert from 'node:assert/strict';
import {detectMedia,inspectMedia} from '../server/media-format.js';
import {playbackSource} from '../server/source.js';
import {matchGroups,matchChannel,people,artwork} from '../public/content-details.js';
import {card} from '../public/lib.js';
import {rewriteDASH,dashTemplate,expandDashTicket} from '../server/dash.js';
import {unticket} from '../server/http.js';
const bytes=text=>new TextEncoder().encode(text);

test('live channels are detected from bytes even when the CDN returns a CSS filename and MIME',()=>{
 assert.equal(detectMedia(bytes('#EXTM3U\n#EXT-X-TARGETDURATION:6'),'text/css','https://cdn.example/1427.css'),'hls');
 const ts=new Uint8Array(564);ts[0]=ts[188]=ts[376]=0x47;
 assert.equal(detectMedia(ts,'text/css','https://cdn.example/segment.css'),'mpegts');
 assert.equal(detectMedia(bytes('FLV\x01\x05'),'application/octet-stream'),'flv');
 assert.equal(detectMedia(bytes('\x00\x00\x00\x18ftypisom'),'application/octet-stream'),'video');
 assert.equal(detectMedia(bytes('<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"></MPD>'),'text/plain'),'dash');
 assert.equal(detectMedia(bytes('<html>Site unavailable</html>'),'video/mp4','https://cdn.example/movie.mp4'),'page');
});
test('inspection preserves bytes and HTTP range metadata across the sniff boundary',async()=>{
 const chunks=[bytes('FLV'),new Uint8Array(2048).fill(42)];
 const source=new Response(new ReadableStream({start(c){chunks.forEach(x=>c.enqueue(x));c.close();}}),{status:206,headers:{'Content-Range':'bytes 0-2050/100000'}});
 const result=await inspectMedia(source,'https://cdn.example/stream');assert.equal(result.type,'flv');assert.equal(result.response.status,206);assert.equal(result.response.headers.get('content-range'),'bytes 0-2050/100000');assert.deepEqual(new Uint8Array(await result.response.arrayBuffer()),new Uint8Array([...chunks[0],...chunks[1]]));
});
test('fresh catalogue URLs and native fallback preserve identity without session cookies',()=>{
 const url='https://cdn.example/live/42.css',deep='tdmvideo://play?'+new URLSearchParams({url,ua:'OSCARTV2021',cookies:'must-not-forward'});
 const result=playbackSource({stream_url:url,deep_link:deep,headers:{Authorization:'private',Cookie:'private',Referer:'https://source.example/'}});
 assert.equal(result.url,url);assert.deepEqual(result.headers,{Referer:'https://source.example/'});
 assert.equal(playbackSource({deep_link:'tdmvideo://play?'+new URLSearchParams({url,ua:'public-player'})}).headers['User-Agent'],'public-player');
 for(const key of ['url','video_url','stream_url','download_url','link'])assert.equal(playbackSource({[key]:url}).url,url);
});
test('match leagues and actual channel IDs survive API association records',()=>{
 const group={league_name:'League',matches:[{id:22}]};assert.deepEqual(matchGroups({data:{leagues:[group]}}),[group]);
 assert.equal(matchChannel({id:2644,channel_id:83,channel_name:'Sports'}).id,83);
 assert.equal(matchChannel({id:2644,is_own_channel:true}).id,null);
});
test('cast preserves actor identity, portrait, roles and separate films/series',()=>{
 const actors=people([{id:2,name:'Actor',photo:'/uploads/actors/a.webp',role_name:'Role',order_index:2},{id:1,order_index:0}]);
 assert.equal(actors[0].id,1);const actor=card(actors[1],'palma','actor');assert.equal(actor.kind,'actor');assert.equal(actor.id,'2');assert.match(actor.poster,/uploads\/actors\/a.webp$/);
 assert.deepEqual(artwork([{file_path:'https://images.example/backdrop.jpg'}]),['https://images.example/backdrop.jpg']);
});
test('DASH segment templates are signed, preserve nested BaseURL and constrain substitutions',()=>{
 const xml='<MPD><BaseURL>media/</BaseURL><Period><AdaptationSet><Representation id="video"><BaseURL>1080/</BaseURL><SegmentTemplate initialization="init-$RepresentationID$.mp4" media="chunk-$Number%05d$.m4s"/></Representation></AdaptationSet></Period></MPD>';
 const result=rewriteDASH(xml,'https://cdn.example/show/manifest.mpd');
 const attribute=/media="([^"]+)"/.exec(result)[1].replaceAll('&amp;','&');const url=new URL(attribute,'https://app.example');
 const payload=unticket(url.searchParams.get('t'));url.searchParams.set('v0','00042');assert.equal(expandDashTicket(payload,url.searchParams),'https://cdn.example/show/media/1080/chunk-00042.m4s');
 url.searchParams.set('v0','../../secret');assert.throws(()=>expandDashTicket(payload,url.searchParams));
 assert.throws(()=>rewriteDASH('<!DOCTYPE MPD><MPD/>','https://cdn.example/file.mpd'));
});
