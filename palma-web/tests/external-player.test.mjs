import test from 'node:test';
import assert from 'node:assert/strict';
import {mxPlayerLink,awrPlayerLink,playbackIdentity,playbackTarget} from '../public/external-player.js';
const item={provider:'witcher',kind:'anime',id:'Naruto',title:'ناروتو'};

test('integrated player is the default on every device, explicit external choice wins',()=>{
  assert.equal(playbackTarget('auto','Mozilla Android 15'),'browser');
  assert.equal(playbackTarget('auto','Mozilla Windows NT 10.0'),'browser');
  assert.equal(playbackTarget('auto','iPhone'),'browser');
  assert.equal(playbackTarget('browser','Android'),'browser');
  assert.equal(playbackTarget('awr','Windows'),'awr');
  assert.equal(playbackTarget('mx','Android'),'mx');
});
test('Android intent preserves signed query, targets MX, and supplies Play Store fallback',()=>{
  const link=mxPlayerLink({url:'https://cdn.example.com/video.mp4?token=a%2Bb&expires=9'},item,'001');
  assert.match(link,/intent:\/\/cdn.example.com\/video.mp4\?token=a%2Bb&expires=9#Intent;scheme=https;/);
  assert.match(link,/package=com.mxtech.videoplayer.ad;/);
  assert.match(link,/S.browser_fallback_url=https%3A%2F%2Fplay.google.com/);
  assert.ok(link.includes(encodeURIComponent('ناروتو · الحلقة 1')));
  assert.match(mxPlayerLink({url:'https://example.com/a'},item,'','pro'),/package=com.mxtech.videoplayer.pro;/);
});
test('AWR protocol round-trips Arabic and exact URL with episode identity across servers',()=>{
  const url='https://cdn.example.com/v.mp4?token=a%2Fb%3D&n=2';
  const link=awrPlayerLink({url,headers:{Referer:'https://example.com/','User-Agent':'PALMA',Cookie:'never-export'}},item,'001');
  const payload=JSON.parse(Buffer.from(new URL(link).searchParams.get('data'),'base64url').toString('utf8'));
  assert.equal(payload.source,url);assert.equal(payload.v,1);assert.equal(payload.title,'ناروتو · الحلقة 1');
  assert.equal(payload.historyId,playbackIdentity(item,'001'));assert.equal(payload.referer,'https://example.com/');
  assert.equal(payload.Cookie,undefined);
  assert.notEqual(playbackIdentity(item,'001'),playbackIdentity(item,'002'));
  assert.throws(()=>awrPlayerLink({url:'file:///etc/passwd'},item));
  assert.throws(()=>mxPlayerLink({url:'https://user:secret@example.com/a'},item));
});
