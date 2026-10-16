import {fileURLToPath} from 'node:url';
import dns from 'node:dns';
import net from 'node:net';
import {createTVServer} from './main.mjs';
// Android may canonicalize /data/user/0 to /data/data. An explicit entry module
// must not compare argv[1] to import.meta.url before starting the local service.
const [, ,rawPort,token,storage,mode]=process.argv;
if(!/^\d+$/.test(rawPort)||!token||token.length!==64||!storage)throw Error('Invalid local runtime configuration');
// Node 18 does not enable network-family fallback by default. TV and emulator
// networks may advertise IPv6 DNS records without a usable IPv6 route.
dns.setDefaultResultOrder('ipv4first');
net.setDefaultAutoSelectFamily(true);
if(mode==='test'){
  const fetch=globalThis.fetch;
  globalThis.fetch=async(...args)=>{try{return await fetch(...args);}catch(error){console.log('PALMA_TV_NETWORK '+JSON.stringify({name:error.name,code:error.cause?.code||error.code||'unknown'}));throw error;}};
}
const root=fileURLToPath(new URL('./web/public',import.meta.url));
createTVServer({port:Number(rawPort),token,storage,publicRoot:root,test:mode==='test'}).listen(Number(rawPort),'127.0.0.1',()=>console.log('PALMA TV runtime ready'));
