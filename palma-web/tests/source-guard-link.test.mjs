import test from 'node:test';
import assert from 'node:assert/strict';
import {playbackSource} from '../server/source.js';
import {createResolver} from '../api/resolve.js';

// The supplied 1.1.6 APK handles this control URI in h32.L/j44.K/g32.K
// before opening any player. The URL below is a synthetic regression fixture.
const fallback='https://media.example/replacement.mp4';
const guard='oscartv-guard://tdm?a=install&s=missing';

test('a source guard deep link cannot fall through to a playable-looking HTTP field',()=>{
  for(const deep_link of [guard, encodeURIComponent(guard),
    encodeURIComponent(' '+guard+' '),
    Buffer.from(guard).toString('base64url'),
    'tdmvideo://play?'+new URLSearchParams({url:guard})]) {
    assert.throws(()=>playbackSource({deep_link,url:fallback},{provider:'palma'}),
      error=>error.status===403&&error.code==='PALMA_PLAYER_VERIFICATION_REQUIRED');
  }
});

test('source guard responses stop actual watch and download preparation before fetching media',async()=>{
  const kinds=[['tdm','PALMA_PLAYER_VERIFICATION_REQUIRED'],
    ['tamper','PALMA_APP_VERIFICATION_REQUIRED'],
    ['adblock','PALMA_ADBLOCK_VERIFICATION_REQUIRED']];
  for(const [kind,code] of kinds)for(const download of ['0','1']) {
    const source={deep_link:`oscartv-guard://${kind}?a=message&s=blocked`,url:fallback};
    const resolve=createResolver({
      getCatalogue:async()=>({data:{watch_links:[source],download_links:[source]}}),
      getMedia:()=>assert.fail('explicit source rejection must not fetch a fallback video'),
    });
    const res={setHeader(){},end(text){this.body=JSON.parse(text);}};
    await resolve({method:'GET',url:`/api/resolve?provider=palma&kind=movie&id=12&download=${download}`},res);
    assert.equal(res.statusCode,403);assert.equal(res.body.code,code);
  }
});

test('unknown or silent guard actions are control responses rather than playable URLs',()=>{
  for(const deep_link of ['oscartv-guard://tdm?a=no_response',
    'oscartv-guard://future?a=new_action', 'oscartv-guard://constructor',
    'oscartv-guard://__proto__', 'oscartv-guard:malformed']) {
    assert.throws(()=>playbackSource({deep_link,url:fallback},{provider:'palma'}),
      error=>error.status===403&&error.code==='PALMA_PLAYER_VERIFICATION_REQUIRED');
  }
  assert.equal(playbackSource({url:fallback}).url,fallback);
});
