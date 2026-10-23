// Owner explicitly approved this bounded X-TE diagnostic on 2026-10-23.
// Test-only: not included in the packaged app. It never claims Android or an
// installed/signed TDM package. Credentials stay in this process's memory.
import assert from 'node:assert/strict';
import {createCipheriv,createHash,createHmac,randomBytes} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {PalmaV3Client} from '../../palma-web/server/palma/v3.mjs';
import {DEFAULT_BASE} from '../../palma-web/server/palma/client.mjs';
import {SOURCE_PROFILE,sourceApplicationProof} from '../../palma-web/server/palma/registration-profile.mjs';
import {assertPlaybackAllowed} from '../../palma-web/server/palma/playback-v3.mjs';
import {playbackSource} from '../../palma-web/server/source.js';
import {fetchPublic,readLimited} from '../../palma-web/server/http.js';
import {parseHLS} from '../../palma-web/server/media.js';
import {assertPlaybackMedia} from '../../palma-web/server/media-rejection.mjs';

const evidence=Object.freeze({p:'com.tdm.manager',i:0,pf:'windows',av:Number(SOURCE_PROFILE.versionCode),h:'',e:0,v:0});
function encodeEvidence(secret,keyId,nonce,timestamp,iv=randomBytes(12)) {
  const key=createHmac('sha256',Buffer.from(secret,'hex')).update(`oscar-te-v1|${keyId}|${nonce}|${timestamp}`).digest();
  const cipher=createCipheriv('aes-256-gcm',key,iv);
  cipher.setAAD(Buffer.from(keyId));
  const data=Buffer.concat([cipher.update(JSON.stringify(evidence),'utf8'),cipher.final()]);
  return Buffer.concat([iv,data,cipher.getAuthTag()]).toString('base64url');
}

// Fixed result independently generated with Python cryptography AESGCM.
assert.equal(encodeEvidence('11'.repeat(32),'22'.repeat(32),'test-nonce','1720000000',Buffer.from(Array.from({length:12},(_,i)=>i))),
  'AAECAwQFBgcICQoLRe82mim59GCPjQKJgcDXie1GLncRDxEkBJ1Vv_wdouU5KF-iTd4VYOKFcT3F2uoJGvfKVNxF8H4aNDpiqu0uuh8jSFprQ5lyM1adAP09fn9xYpEqtTgj');
if(process.argv.includes('--self-test')) {
  console.log('X-TE format matches the independent AES-GCM vector. No network requests made.');
}else{
  assert.equal(process.platform,'win32','This probe must report an actual Windows host.');
  assert.equal(process.env.PALMA_APPROVED_PLAYER_PROBE,'1','Explicit diagnostic opt-in required.');
  let state=null;
  const report={time:new Date().toISOString(),platform:process.platform,evidence,calls:[],guards:[],media:[],candidatePlayable:false};
  const safe=value=>typeof value==='string'&&/^[a-zA-Z0-9_-]{1,100}$/.test(value)?value:null;
  const client=new PalmaV3Client({base:DEFAULT_BASE,version:'2.2.7',sourceVersion:SOURCE_PROFILE.versionName,
    store:{load:async()=>state,save:async value=>{state=value;}},provideRegistrationProof:sourceApplicationProof,
    fetchImpl:async(url,options)=>{
      const headers={...options.headers};
      if(headers['X-Sig-V3']){
        assert.ok(state.deviceSecret);
        headers['X-TE']=encodeEvidence(state.deviceSecret,headers['X-Key-Id'],headers['X-V3-Nonce'],headers['X-V3-Ts']);
      }
      const response=await fetch(url,{...options,headers});
      report.calls.push({route:url.pathname,status:response.status,evidenceSent:!!headers['X-TE'],reason:safe(response.headers.get('x-v3-reason')),edgeRequestId:safe(response.headers.get('cf-ray'))});
      return response;
    }
  });
  const inspectGuards=(stage,value)=>{
    for(const name of ['tdm_guard','tamper_guard','adblock_guard']){
      const guard=value?.[name];
      if(guard)report.guards.push({stage,name,action:safe(guard.action),reason:safe(guard.reason),code:safe(guard.code)});
    }
    assertPlaybackAllowed(value);
  };
  const unwrap=value=>value?.data&&!Array.isArray(value.data)?value.data:value;
  try{
    const id=4655;
    const detailResponse=await client.get('api/v3/movies/show.php',{id});
    inspectGuards('detail-envelope',detailResponse);
    const detail=unwrap(detailResponse);inspectGuards('detail',detail);
    const selected=detail.watch_links?.[0];assert.ok(selected);inspectGuards('link',selected);
    report.selection={kind:'movie',id,index:0,linkId:selected.id};
    const resolvedResponse=await client.resolve({t:'movie',cid:id,k:'watch',lid:selected.id});
    inspectGuards('resolve-envelope',resolvedResponse);
    const source={...selected,...unwrap(resolvedResponse)};inspectGuards('resolve',source);
    const media=playbackSource(source,{provider:'palma'});
    let target=media.url;
    for(let depth=0;depth<3;depth++){
      const {response,url}=await fetchPublic(target,{headers:media.headers});
      const item={status:response.status,edgeRequestId:safe(response.headers.get('cf-ray'))};report.media.push(item);
      if(!response.ok){await response.body?.cancel();throw Error('Media request was not accepted');}
      const text=await readLimited(response,2*1024*1024);
      item.hls=text.startsWith('#EXTM3U');
      item.playlistSha256=createHash('sha256').update(text.replaceAll('\r\n','\n').trim()).digest('hex');
      assertPlaybackMedia(url,text);
      assert.ok(item.hls,'Unexpected media format');
      const variants=parseHLS(text,url);item.variantCount=variants.length;
      if(variants.length){target=variants[0].url;continue;}
      item.duration=[...text.matchAll(/^#EXTINF:([\d.]+)/gm)].reduce((sum,match)=>sum+Number(match[1]),0);
      report.candidatePlayable=item.duration>300;break;
    }
  }catch(error){report.error={code:safe(error.code)||'DIAGNOSTIC_FAILED',status:error.status||null,message:error.code?error.message:'Diagnostic did not establish requested-content playback.'};}
  const directory=path.resolve('probe-output');await mkdir(directory,{recursive:true});
  await writeFile(path.join(directory,'windows-player-evidence.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify(report,null,2));
}
