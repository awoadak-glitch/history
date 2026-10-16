import {fileURLToPath} from 'node:url';
import {createTVServer} from './main.mjs';
// Android may canonicalize /data/user/0 to /data/data. An explicit entry module
// must not compare argv[1] to import.meta.url before starting the local service.
const [, ,rawPort,token,storage,mode]=process.argv;
if(!/^\d+$/.test(rawPort)||!token||token.length!==64||!storage)throw Error('Invalid local runtime configuration');
const root=fileURLToPath(new URL('./web/public',import.meta.url));
createTVServer({port:Number(rawPort),token,storage,publicRoot:root,test:mode==='test'}).listen(Number(rawPort),'127.0.0.1',()=>console.log('PALMA TV runtime ready'));
