// Owner explicitly approved the original bounded diagnostic on 2026-10-23.
// After being informed why the earlier compatibility probe was blocked, the
// owner explicitly requested TDM identity emulation for their Windows port.
// --tdm-compatibility is that separate, opt-in experiment. Its player state
// emulates the reference APK; it is NOT a Windows package-manager measurement.
// Registration defaults to Windows. --android-protocol explicitly compares the
// reference software-key contract; neither mode supplies hardware attestation.
// Test-only: not included in the packaged app. Credentials remain in memory.
import assert from 'node:assert/strict';
import {createCipheriv,createHash,createHmac,randomBytes} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {getServers} from 'node:dns';
import {networkInterfaces} from 'node:os';
import {PalmaV3Client,canonicalRequest} from '../../palma-web/server/palma/v3.mjs';
import {DEFAULT_BASE,APP_PROFILE,PalmaClient} from '../../palma-web/server/palma/client.mjs';
import {SOURCE_PROFILE,sourceApplicationProof} from '../../palma-web/server/palma/registration-profile.mjs';
import {requestHeaders} from '../../palma-web/server/palma/iron.mjs';
import {assertPlaybackAllowed} from '../../palma-web/server/palma/playback-v3.mjs';
import {playbackSource} from '../../palma-web/server/source.js';
import {fetchPublic,readLimited} from '../../palma-web/server/http.js';
import {parseHLS} from '../../palma-web/server/media.js';
import {assertPlaybackMedia} from '../../palma-web/server/media-rejection.mjs';
import {tdmMediaHeaders} from '../../palma-web/server/tdm-media-headers.mjs';

const evidence=Object.freeze({p:'com.tdm.manager',i:0,pf:'windows',av:Number(SOURCE_PROFILE.versionCode),h:'',e:0,v:0});
const compatibilityEvidence=Object.freeze({p:'com.tdm.manager',i:1,pf:'phone',av:Number(SOURCE_PROFILE.versionCode),h:'d0576253f7a9f0e5b4c1d1c40a75eb7d3794aafc2dfe0bb49f0ae9917b38a94e',e:0,v:58});
function encodeEvidence(secret,keyId,nonce,timestamp,iv=randomBytes(12),payload=evidence,label='te') {
  const key=createHmac('sha256',Buffer.from(secret,'hex')).update(`oscar-${label}-v1|${keyId}|${nonce}|${timestamp}`).digest();
  const cipher=createCipheriv('aes-256-gcm',key,iv);
  cipher.setAAD(Buffer.from(keyId));
  const data=Buffer.concat([cipher.update(JSON.stringify(payload),'utf8'),cipher.final()]);
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
  const compatibility=process.argv.includes('--tdm-compatibility');
  if(compatibility)assert.equal(process.env.PALMA_OWNER_APPROVED_COMPATIBILITY_TEST,'1','Owner-approved compatibility experiment required.');
  const androidProtocol=compatibility&&process.argv.includes('--android-protocol');
  const sourceNetworkChecks=compatibility&&process.argv.includes('--source-network-checks');
  const referenceAppEvidence=compatibility&&process.argv.includes('--reference-app-evidence');
  const anonymousSession=compatibility&&process.argv.includes('--anonymous-session');
  const payload=compatibility?compatibilityEvidence:evidence;
  let state=null,networkEvidence=null,applicationEvidence=null,bearer=null;
  const mode=sourceNetworkChecks?'owner-approved-tdm-with-network-checks':androidProtocol?'owner-approved-android-protocol':compatibility?'owner-approved-tdm-compatibility':'measured-windows-tdm-absent';
  const report={time:new Date().toISOString(),platform:process.platform,registrationPlatform:androidProtocol?'android':'windows',mode,evidence:payload,stage:'setup',calls:[],guards:[],media:[],candidatePlayable:false};
  const safe=value=>typeof value==='string'&&/^[a-zA-Z0-9_-]{1,100}$/.test(value)?value:null;
  const client=new PalmaV3Client({base:DEFAULT_BASE,version:'2.2.7',sourceVersion:SOURCE_PROFILE.versionName,
    store:{load:async()=>state,save:async value=>{state=value;}},provideRegistrationProof:sourceApplicationProof,
    fetchImpl:async(url,options)=>{
      const headers={...options.headers};
      if(bearer)headers.Authorization='Bearer '+bearer;
      // Test the reference client's software-key registration contract. This
      // remains a Windows process, not an Android hardware-attestation claim.
      if(androidProtocol&&url.pathname.endsWith('/devices/register.php')){
        const body=JSON.parse(options.body);body.platform='android';
        options={...options,body:JSON.stringify(body)};
      }
      if(headers['X-Sig-V3']){
        assert.ok(state.deviceSecret);
        headers['X-TE']=encodeEvidence(state.deviceSecret,headers['X-Key-Id'],headers['X-V3-Nonce'],headers['X-V3-Ts'],randomBytes(12),payload);
        if(networkEvidence)headers['X-AB']=encodeEvidence(state.deviceSecret,headers['X-Key-Id'],headers['X-V3-Nonce'],headers['X-V3-Ts'],randomBytes(12),networkEvidence,'ab');
        if(applicationEvidence)headers['X-TG']=encodeEvidence(state.deviceSecret,headers['X-Key-Id'],headers['X-V3-Nonce'],headers['X-V3-Ts'],randomBytes(12),applicationEvidence,'tg');
      }
      const response=await fetch(url,{...options,headers});
      report.calls.push({route:url.pathname,status:response.status,evidenceSent:!!headers['X-TE'],networkEvidenceSent:!!headers['X-AB'],applicationEvidenceSent:!!headers['X-TG'],reason:safe(response.headers.get('x-v3-reason')),edgeRequestId:safe(response.headers.get('cf-ray'))});
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
    if(sourceNetworkChecks){
      report.stage='bootstrap';
      const legacy=new PalmaClient({base:DEFAULT_BASE,profile:{...APP_PROFILE,version_code:SOURCE_PROFILE.versionCode},fetchImpl:async(url,options)=>{
        const response=await fetch(url,options);
        report.calls.push({route:url.pathname,status:response.status,evidenceSent:false,networkEvidenceSent:false});
        return response;
      }});
      // ApiService.getBootstrap's Retrofit annotations: version_code, platform,
      // optional v3. app_version/lang are not this endpoint's contract.
      const bootstrap=unwrap((await legacy.get('api/app/bootstrap.php',{version_code:SOURCE_PROFILE.versionCode,platform:'phone'})).body);
      const policy=bootstrap.v3guard?.adblock;assert.equal(policy?.enabled,true);
      if(referenceAppEvidence){
        assert.equal(bootstrap.v3guard?.tamper?.enabled,true);
        // Explicit compatibility simulation, not Android runtime attestation.
        // Tests the supplied reference app's empty modification-detection lists.
        applicationEvidence={pk:[],cl:[],hk:0,as:[],pf:'phone',av:Number(SOURCE_PROFILE.versionCode)};
        report.applicationEvidence={mode:'reference-app-compatibility-simulation',androidRuntimeMeasured:false};
      }
      // Only the observed empty test-domain policy is implemented here. A
      // changed policy must be ported, never reported as a successful check.
      assert.equal((policy.test_domains||[]).length,0,'Network test-domain policy changed');
      report.stage='registration';await client.register();
      if(anonymousSession){
        report.stage='anonymous-session';
        const url=new URL('api/auth/anonymous.php',DEFAULT_BASE);
        const body=JSON.stringify({device_id:state.deviceId,platform:androidProtocol?'android':'windows',app_version:SOURCE_PROFILE.versionName});
        const ts=String(Math.floor(Date.now()/1000)+client.clockOffset),nonce=randomBytes(16).toString('base64url');
        assert.ok(state.hotToken);
        const headers={...requestHeaders(APP_PROFILE,url.pathname,ts),'Content-Type':'application/json',Accept:'application/json','User-Agent':'okhttp/4.12.0','X-Device-Id':state.deviceId,'X-Key-Id':state.keyId,'X-D-Ts':ts,'X-D-Nonce':nonce,'X-HT-Exp':String(state.hotExpiry),'X-App-Ver':SOURCE_PROFILE.versionCode,'X-D-Sig':createHmac('sha256',Buffer.from(state.hotToken,'hex')).update(canonicalRequest('POST',url,ts,nonce,body)).digest('hex')};
        const response=await fetch(url,{method:'POST',body,headers,redirect:'error',signal:AbortSignal.timeout(25000)});
        const data=unwrap(JSON.parse(await readLimited(response,65536)));bearer=data.access_token||data.token;
        report.anonymousSession={status:response.status,tokenReceived:typeof bearer==='string'&&bearer.length>0};
        assert.equal(response.status,200);assert.ok(report.anonymousSession.tokenReceived);
      }
      report.stage='network-beacon';
      const beacon=new URL(policy.beacon_url,DEFAULT_BASE);assert.equal(beacon.origin,new URL(DEFAULT_BASE).origin);
      const nonce=randomBytes(8).toString('hex');beacon.searchParams.set('kid',state.keyId);beacon.searchParams.set('n',nonce);
      const response=await fetch(beacon,{headers:{'Cache-Control':'no-cache'},redirect:'error',signal:AbortSignal.timeout(3500)});
      const data=JSON.parse(await readLimited(response,65536));assert.equal(response.status,200);assert.match(data.t,/^[0-9a-f]{64}$/);
      const deniedDns=new Set((policy.dns_ips||[]).flatMap(value=>String(value).split(',')).map(value=>value.trim().toLowerCase()));
      const dnsBlocked=getServers().some(value=>deniedDns.has(value.toLowerCase()));
      const vpn=Object.entries(networkInterfaces()).some(([name,addresses])=>/\b(vpn|wireguard|tun|tap)\b/i.test(name)&&addresses?.some(address=>!address.internal));
      // Dalvik j8.a reads boolean fields but calls JSONObject.put(String, int).
      networkEvidence={inet:1,cb:dnsBlocked?1:0,bt:data.t,bn:nonce,vpn:vpn?1:0,reason:dnsBlocked?'dns_ip':'',pf:'phone',av:Number(SOURCE_PROFILE.versionCode)};
      report.networkMeasurement={beaconStatus:response.status,beaconTokenReceived:true,dnsBlocked,vpnInterfaceDetected:vpn,testDomainCount:0,limitations:['Windows DNS/interface measurement; Android private-DNS setting unavailable']};
      report.stage='network-guard';
      const checked=unwrap(await client.get('api/v3/guard/adblock_check.php'));
      report.networkGuard={action:safe(checked.action),state:safe(checked.state)};
      assertPlaybackAllowed({adblock_guard:checked});
      if(referenceAppEvidence){
        report.stage='application-guard';
        const checked=unwrap(await client.get('api/v3/guard/tamper_check.php'));
        report.applicationGuard={action:safe(checked.action),state:safe(checked.state)};
        assertPlaybackAllowed({tamper_guard:checked});
      }
    }
    const id=Number(process.argv.find(arg=>arg.startsWith('--movie-id='))?.split('=')[1]||4655);
    assert.ok([4655,4528].includes(id),'Use only the two already selected diagnostic movie IDs');
    report.stage='detail';
    const detailResponse=await client.get('api/v3/movies/show.php',{id});
    inspectGuards('detail-envelope',detailResponse);
    const detail=unwrap(detailResponse);inspectGuards('detail',detail);
    const selected=detail.watch_links?.[0];assert.ok(selected);inspectGuards('link',selected);
    report.selection={kind:'movie',id,index:0,linkId:selected.id};
    let source=selected;
    if(selected.deep_link||selected.url){report.linkPath='ready-link';}
    else{
      report.stage='resolve';report.linkPath='resolve';
      const resolvedResponse=await client.resolve({t:'movie',cid:id,k:'watch',lid:selected.id});
      inspectGuards('resolve-envelope',resolvedResponse);
      source={...selected,...unwrap(resolvedResponse)};inspectGuards('resolve',source);
    }
    const media=playbackSource(source,{provider:'palma'});
    const deep=new URL(source.deep_link),rawCookies=deep.searchParams.get('cookies')||'';
    const ot=rawCookies.split(';').map(part=>part.trim()).find(part=>part.startsWith('ot='));
    const mediaAddress=new URL(media.url),storageIndex=mediaAddress.pathname.indexOf('/b2/');
    report.transport={deepLinkParameters:[...deep.searchParams.keys()],cookieNames:rawCookies.split(';').filter(Boolean).map(part=>part.trim().split('=')[0]),cookieBound:!!media.headers.Cookie,cookiePreserved:!!ot&&media.headers.Cookie===ot,mediaHeaderNames:Object.keys(media.headers),userAgent:media.headers['User-Agent'],mediaOrigin:mediaAddress.origin,sourceIssuedObject:storageIndex<0?null:mediaAddress.pathname.slice(storageIndex+4)};
    let target=media.url;
    report.stage='media';
    for(let depth=0;depth<3;depth++){
      const {response,url}=await fetchPublic(target,{headers:media.headers});
      const names=['content-type','content-length','cache-control','age','cf-cache-status','date','server','x-cache','x-reason','x-error','x-token-status','x-auth-status','x-bz-file-name'];
      const item={status:response.status,edgeRequestId:safe(response.headers.get('cf-ray')),redirected:url!==target,responseHeaders:Object.fromEntries(names.map(name=>[name,response.headers.get(name)]).filter(([,value])=>value!==null)),additionalHeaderNames:[...response.headers.keys()].filter(name=>!names.includes(name))};report.media.push(item);
      if(!response.ok){await response.body?.cancel();throw Error('Media request was not accepted');}
      const text=await readLimited(response,2*1024*1024);
      item.hls=text.startsWith('#EXTM3U');
      item.playlistSha256=createHash('sha256').update(text.replaceAll('\r\n','\n').trim()).digest('hex');
      try{assertPlaybackMedia(url,text);}catch(error){
        if(error.code==='SOURCE_SESSION_REQUIRED'&&process.argv.includes('--compare-media-transport')){
          report.transportComparisons=[];
          const origin=new URL(target).origin;
          assert.equal(origin,new URL(media.url).origin);
          const variants=[
            {name:'revalidate',headers:{'Cache-Control':'no-cache',Pragma:'no-cache'}},
            // TDM nd.f.I sets DataSpec FLAG_ALLOW_GZIP for CSS/JS transport.
            {name:'android-datasource-gzip',headers:{'Accept-Encoding':'gzip'}},
          ];
          for(const variant of variants){
            const headers={...tdmMediaHeaders(target,media.headers),...variant.headers};
            const reply=await fetch(target,{headers:Object.fromEntries(Object.entries(headers)),mode:'no-cors',redirect:'manual',signal:AbortSignal.timeout(25000)});
            const body=await readLimited(reply,2*1024*1024);
            const record={name:variant.name,status:reply.status,requestHeaderNames:Object.keys(headers),responseHeaders:Object.fromEntries(names.map(name=>[name,reply.headers.get(name)]).filter(([,value])=>value!==null)),hls:body.startsWith('#EXTM3U'),playlistSha256:createHash('sha256').update(body.replaceAll('\r\n','\n').trim()).digest('hex')};
            try{assertPlaybackMedia(target,body);record.knownNotice=false;}catch{record.knownNotice=true;}
            report.transportComparisons.push(record);
          }
        }
        throw error;
      }
      assert.ok(item.hls,'Unexpected media format');
      const variants=parseHLS(text,url);item.variantCount=variants.length;
      if(variants.length){target=variants[0].url;continue;}
      item.duration=[...text.matchAll(/^#EXTINF:([\d.]+)/gm)].reduce((sum,match)=>sum+Number(match[1]),0);
      report.candidatePlayable=item.duration>300;break;
    }
  }catch(error){report.error={code:safe(error.code)||'DIAGNOSTIC_FAILED',status:error.status||null,message:error.code?error.message:'Diagnostic did not establish requested-content playback.'};}
  const directory=path.resolve('probe-output');await mkdir(directory,{recursive:true});
  const suffix=anonymousSession?'-anonymous':process.argv.includes('--movie-id=4528')?'-movie-4528':'';
  await writeFile(path.join(directory,sourceNetworkChecks?`windows-source-network-checks${suffix}.json`:androidProtocol?'windows-android-protocol.json':compatibility?'windows-tdm-compatibility.json':'windows-player-evidence.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify(report,null,2));
}
