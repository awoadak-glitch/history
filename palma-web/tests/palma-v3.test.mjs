import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash,createHmac,createCipheriv,createPublicKey,verify,randomBytes} from 'node:crypto';
import {PalmaV3Client} from '../server/palma/v3.mjs';

const base='https://source.example/';
const digest=value=>createHash('sha256').update(value).digest('hex');
const memory=()=>{let value=null;return {load:async()=>structuredClone(value),save:async next=>{value=structuredClone(next);}};};

function source({deny=false,badTime=false,corrupt=false}={}) {
  const requests=[];let publicKey,keyId,seenTime=false;const secret=randomBytes(32);
  const encrypted=(payload,headers)=>{
    const key=createHmac('sha256',secret).update(['oscar-resp-v1',keyId,headers['X-V3-Nonce'],headers['X-V3-Ts']].join('|')).digest();
    const iv=randomBytes(12),aad=Buffer.from(keyId),cipher=createCipheriv('aes-256-gcm',key,iv);
    cipher.setAAD(aad);
    const ciphertext=Buffer.concat([cipher.update(JSON.stringify(payload)),cipher.final()]);
    const tag=cipher.getAuthTag();if(corrupt)tag[0]^=1;
    return Response.json({iv:iv.toString('base64'),aad:aad.toString('base64'),ciphertext:ciphertext.toString('base64'),tag:tag.toString('base64')},{headers:{'X-Content-Encrypted':'AES-256-GCM'}});
  };
  const fetchImpl=async(url,options)=>{
    requests.push({url,options});assert.equal(options.redirect,'error');assert.equal(url.origin,new URL(base).origin);
    const body=options.body?JSON.parse(options.body):null;
    if(url.pathname.endsWith('/challenge.php'))return Response.json({status:'success',challenge:'c291cmNlLWNoYWxsZW5nZQ=='});
    if(url.pathname.endsWith('/register.php')) {
      assert.equal(body.platform,'windows');assert.equal(body.attest,undefined);assert.equal(body.attest_chain,undefined);
      publicKey=createPublicKey(body.public_key);keyId=digest(publicKey.export({type:'spki',format:'der'}));
      if(deny)return Response.json({status:'error',message:'attestation failed'},{status:403});
      return Response.json({status:'success',key_id:keyId,device_secret:secret.toString('hex')});
    }
    const headers=options.headers;
    // Independent verifier: tampering with method, query, or body must fail.
    const signed=[options.method,url.pathname,digest(url.search.slice(1)),headers['X-V3-Ts'],headers['X-V3-Nonce'],digest(options.body||'')].join('\n');
    assert.equal(headers['X-Key-Id'],keyId);assert.equal(Buffer.from(headers['X-V3-Nonce'],'base64url').length,32);
    const signature=Buffer.from(headers['X-Sig-V3'].slice('ecdsa='.length),'base64');
    assert.equal(verify('sha256',Buffer.from(signed),publicKey,signature),true);
    assert.equal(verify('sha256',Buffer.from(signed+'changed'),publicKey,signature),false);
    if(badTime&&!seenTime){seenTime=true;return Response.json({status:'error'},{status:403,headers:{'X-V3-Reason':'bad_ts','X-Server-Time':String(Math.floor(Date.now()/1000)+90)}});}
    if(badTime)assert.ok(Number(headers['X-V3-Ts'])>Date.now()/1000+80);
    if(url.pathname.endsWith('/token.php'))return encrypted({status:'success',hot_token:randomBytes(32).toString('hex'),hot_exp:Math.floor(Date.now()/1000)+300},headers);
    return encrypted({status:'success',data:{deep_link:'tdmvideo://play?url=https%3A%2F%2Fcdn.mediaoscar.com%2Fsample.m3u8'}},headers);
  };
  return {fetchImpl,requests};
}

test('V3 registers one per-installation Windows key, signs requests, and decrypts replies',async()=>{
  const store=memory(),server=source(),client=new PalmaV3Client({base,store,fetchImpl:server.fetchImpl});
  const [one,two]=await Promise.all([client.get('api/v3/movies/show.php',{id:12,q:'عنوان + &'}),client.resolve({t:'movie',cid:12,k:'watch',lid:81})]);
  assert.equal(one.status,'success');assert.equal(two.status,'success');
  assert.equal(server.requests.filter(r=>r.url.pathname.endsWith('register.php')).length,1);
  await client.refreshHotToken();assert.ok((await store.load()).hotToken);
  const previous=await store.load(),next=new PalmaV3Client({base,store,fetchImpl:server.fetchImpl});
  await next.get('api/v3/movies/show.php',{id:12});
  assert.equal((await store.load()).deviceId,previous.deviceId);
  assert.equal(server.requests.filter(r=>r.url.pathname.endsWith('register.php')).length,1);
});

test('clock error retries once with a fresh nonce and server time',async()=>{
  const server=source({badTime:true}),client=new PalmaV3Client({base,store:memory(),fetchImpl:server.fetchImpl});
  await client.get('api/v3/movies/show.php',{id:12});
  const signed=server.requests.filter(r=>r.options.headers['X-V3-Nonce']);
  assert.equal(signed.length,2);assert.notEqual(signed[0].options.headers['X-V3-Nonce'],signed[1].options.headers['X-V3-Nonce']);
});

test('refused registration never resolves playback, rotates identity, or retries every click',async()=>{
  const server=source({deny:true}),store=memory(),client=new PalmaV3Client({base,store,fetchImpl:server.fetchImpl});
  await assert.rejects(client.resolve({t:'movie',cid:12,k:'watch',lid:81}),{code:'PALMA_V3_DEVICE_REJECTED'});
  const before=await store.load();
  await assert.rejects(client.get('api/v3/movies/show.php',{id:12}),{code:'PALMA_V3_DEVICE_REJECTED'});
  assert.equal(server.requests.length,2);assert.deepEqual(await store.load(),before);
});

test('modified encrypted response is rejected instead of exposing a video URL',async()=>{
  const server=source({corrupt:true}),client=new PalmaV3Client({base,store:memory(),fetchImpl:server.fetchImpl});
  await assert.rejects(client.get('api/v3/movies/show.php',{id:12}),{code:'PALMA_V3_DECRYPT_FAILED'});
});

test('stored credentials cannot migrate to a different API origin',async()=>{
  const server=source(),store=memory(),client=new PalmaV3Client({base,store,fetchImpl:server.fetchImpl});
  await client.get('api/v3/movies/show.php',{id:12});
  const next=new PalmaV3Client({base:'https://different.example',store,fetchImpl:()=>assert.fail('must not transmit credentials')});
  await assert.rejects(next.get('api/v3/movies/show.php',{id:12}),{code:'PALMA_V3_IDENTITY_INVALID'});
});
