import {createHash,createHmac,createPrivateKey,createPublicKey,generateKeyPairSync,randomBytes,randomUUID,sign,createDecipheriv} from 'node:crypto';

const sha=value=>createHash('sha256').update(value).digest('hex');
const hex=value=>typeof value==='string'&&/^[a-f0-9]{32,256}$/i.test(value)&&value.length%2===0;
const registrationRoute='api/v3/devices/register.php';

export class PalmaV3Error extends Error {
  constructor(message,status=502,code='PALMA_V3_ERROR') {super(message);this.name='PalmaV3Error';this.status=status;this.code=code;}
}

export function canonicalRequest(method,url,timestamp,nonce,body='') {
  return [method.toUpperCase(),url.pathname,sha(url.search.slice(1)),timestamp,nonce,sha(body)].join('\n');
}

// The store belongs to one installation and one HTTPS API origin. It must protect
// its contents at rest. Device private keys and session secrets are never shared.
export class PalmaV3Client {
  constructor({base,store,fetchImpl=globalThis.fetch,version='2.2.7',sourceVersion=version,timeout=25000,provideRegistrationProof}={}) {
    this.base=new URL(base);
    if(this.base.protocol!=='https:'||this.base.username||this.base.password||this.base.port)throw new TypeError('Expected an HTTPS API origin');
    this.base=new URL(this.base.origin+'/');
    if(!store?.load||!store?.save)throw new TypeError('Protected device store required');
    this.store=store;this.fetch=fetchImpl;this.version=version;this.sourceVersion=sourceVersion;this.timeout=timeout;
    this.provideRegistrationProof=provideRegistrationProof;this.clockOffset=0;
    this.initPromise=null;this.registrationPromise=null;this.state=null;
    this.registrationFailure=null;this.retryAt=0;
  }

  async identity() {
    if(!this.initPromise)this.initPromise=(async()=>{
      let state=await this.store.load();
      if(state==null) {
        const pair=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
        state={v:1,origin:this.base.origin,deviceId:'palma-windows-'+randomUUID(),privateKey:pair.privateKey.export({type:'pkcs8',format:'pem'})};
        await this.store.save(state);
      }
      try {
        if(state.v!==1||state.origin!==this.base.origin||!/^palma-windows-[a-f0-9-]{36}$/.test(state.deviceId))throw Error();
        const key=createPrivateKey(state.privateKey);
        if(key.asymmetricKeyType!=='ec'||key.asymmetricKeyDetails?.namedCurve!=='prime256v1')throw Error();
        const publicKey=createPublicKey(key),keyId=sha(publicKey.export({type:'spki',format:'der'}));
        if(state.keyId&&state.keyId!==keyId)throw Error();
        if(state.deviceSecret&&!hex(state.deviceSecret))throw Error();
        this.state={...state,keyId};this.privateKey=key;this.publicKey=publicKey;
      } catch {throw new PalmaV3Error('تعذر فتح هوية المشاهدة المحفوظة لهذا الجهاز.',503,'PALMA_V3_IDENTITY_INVALID');}
      return this.state;
    })();
    await this.initPromise;return this.state;
  }

  clock(response) {
    const server=response.headers.get('x-server-time');
    const seconds=server&&/^\d{9,12}$/.test(server)?Number(server):Date.parse(response.headers.get('date'))/1000;
    if(Number.isFinite(seconds))this.clockOffset=Math.floor(seconds)-Math.floor(Date.now()/1000);
  }

  async register() {
    await this.identity();
    if(this.state.deviceSecret)return;
    if(this.registrationFailure&&Date.now()<this.retryAt)throw this.registrationFailure;
    if(!this.registrationPromise)this.registrationPromise=(async()=>{
      try {
        const challenge=await this.send('api/v3/devices/challenge.php',{body:{device_id:this.state.deviceId},signed:false});
        // Application identity and hardware attestation are separate fields in
        // the source protocol. Software keys do not send a hardware chain.
        const proof=await this.provideRegistrationProof?.({origin:this.base.origin,deviceId:this.state.deviceId,publicKey:this.publicKey.export({type:'spki',format:'pem'}),challenge:challenge.challenge});
        const body={device_id:this.state.deviceId,public_key:this.publicKey.export({type:'spki',format:'pem'}),platform:'windows',app_version:this.sourceVersion};
        if(proof) {
          if(typeof proof.attest==='string')body.attest=proof.attest;
          if(Array.isArray(proof.attest_chain)&&proof.attest_chain.every(v=>typeof v==='string'))body.attest_chain=proof.attest_chain;
          if(proof.challenge===challenge.challenge&&typeof proof.challenge==='string')body.challenge=proof.challenge;
        }
        const result=await this.send(registrationRoute,{body,signed:false});
        if(result.status!=='success'||result.key_id!==this.state.keyId||!hex(result.device_secret))throw new PalmaV3Error('أعاد المصدر بيانات تسجيل جهاز غير صالحة.',502,'PALMA_V3_REGISTRATION_INVALID');
        const next={...this.state,deviceSecret:result.device_secret};
        if(hex(result.hot_token)&&Number.isSafeInteger(result.hot_exp)) {next.hotToken=result.hot_token;next.hotExpiry=result.hot_exp;}
        await this.store.save(next);this.state=next;this.registrationFailure=null;
      } catch(error) {this.registrationFailure=error;this.retryAt=Date.now()+60000;throw error;}
    })().finally(()=>{this.registrationPromise=null;});
    return this.registrationPromise;
  }

  async send(route,{query={},body,signed=true,retryClock=true}={}) {
    if(!/^api\/v3\/[a-z0-9_./-]+$/.test(route)||route.split('/').includes('..'))throw new TypeError('Invalid V3 route');
    const url=new URL(route,this.base);
    for(const [name,value] of Object.entries(query))if(value!=null)url.searchParams.set(name,String(value));
    const method=body===undefined?'GET':'POST',raw=body===undefined?'':JSON.stringify(body);
    const headers={'User-Agent':`PALMA-VISION/${this.version} (Windows)`,Accept:'application/json','X-Device-Id':this.state.deviceId};
    if(body!==undefined)headers['Content-Type']='application/json';
    let context;
    if(signed) {
      const ts=String(Math.floor(Date.now()/1000)+this.clockOffset),nonce=randomBytes(32).toString('base64url');
      context={ts,nonce,keyId:this.state.keyId};
      Object.assign(headers,{'X-Key-Id':context.keyId,'X-V3-Ts':ts,'X-V3-Nonce':nonce,'X-Encrypt-Response':'1','X-Sig-V3':'ecdsa='+sign('sha256',Buffer.from(canonicalRequest(method,url,ts,nonce,raw)),this.privateKey).toString('base64')});
    }
    const response=await this.fetch(url,{method,headers,body:body===undefined?undefined:raw,redirect:'error',signal:AbortSignal.timeout(this.timeout)});
    this.clock(response);
    const reason=response.headers.get('x-v3-reason');
    if(signed&&response.status===403&&reason==='bad_ts'&&retryClock) {
      await response.body?.cancel();return this.send(route,{query,body,signed,retryClock:false});
    }
    let parsed;
    try {
      const length=Number(response.headers.get('content-length'));
      if(length>4*1024*1024)throw Error('large');
      const reader=response.body?.getReader();const chunks=[];let size=0;
      if(!reader)throw Error('empty');
      while(true){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>4*1024*1024){await reader.cancel();throw Error('large');}chunks.push(Buffer.from(value));}
      parsed=JSON.parse(Buffer.concat(chunks).toString('utf8'));
    } catch {throw new PalmaV3Error('أعاد المصدر استجابة مشاهدة غير صالحة.',502,'PALMA_V3_INVALID_RESPONSE');}
    if(response.headers.get('x-content-encrypted')?.toUpperCase()==='AES-256-GCM') {
      try {
        if(!context||!this.state.deviceSecret)throw Error('context');
        const key=createHmac('sha256',Buffer.from(this.state.deviceSecret,'hex')).update(`oscar-resp-v1|${context.keyId}|${context.nonce}|${context.ts}`).digest();
        const iv=Buffer.from(parsed.iv,'base64'),tag=Buffer.from(parsed.tag,'base64');
        if(iv.length!==12||tag.length!==16)throw Error('invalid GCM parameters');
        const cipher=createDecipheriv('aes-256-gcm',key,iv);cipher.setAAD(Buffer.from(parsed.aad,'base64'));cipher.setAuthTag(tag);
        parsed=JSON.parse(Buffer.concat([cipher.update(Buffer.from(parsed.ciphertext,'base64')),cipher.final()]).toString('utf8'));
      } catch {throw new PalmaV3Error('تعذر التحقق من استجابة المشاهدة الآمنة.',502,'PALMA_V3_DECRYPT_FAILED');}
    }
    if(!response.ok||parsed?.status==='error') {
      if(route===registrationRoute&&(response.status===401||response.status===403))throw new PalmaV3Error('تعذر التحقق من هوية التطبيق لدى مصدر المشاهدة. لم تُنشأ جلسة تشغيل.',503,'PALMA_V3_DEVICE_REJECTED');
      if([401,403].includes(response.status))throw new PalmaV3Error('رفض مصدر المشاهدة جلسة هذا الجهاز. لا يمكن تجهيز رابط البث.',403,'PALMA_V3_SESSION_REJECTED');
      throw new PalmaV3Error('تعذر تجهيز رابط المشاهدة من المصدر.',response.status===404?404:502,'PALMA_V3_UPSTREAM_ERROR');
    }
    if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))throw new PalmaV3Error('بيانات المصدر غير صالحة.',502,'PALMA_V3_INVALID_RESPONSE');
    return parsed;
  }

  async request(route,options={}) {await this.register();return this.send(route,options);}
  get(route,query={}) {return this.request(route,{query});}
  resolve(body) {return this.request('api/v3/resolve.php',{body});}
  async refreshHotToken() {
    const result=await this.request('api/v3/devices/token.php');
    if(!hex(result.hot_token)||!Number.isSafeInteger(result.hot_exp))throw new PalmaV3Error('أعاد المصدر جلسة غير صالحة.',502,'PALMA_V3_TOKEN_INVALID');
    const next={...this.state,hotToken:result.hot_token,hotExpiry:result.hot_exp};
    await this.store.save(next);this.state=next;
  }
}
