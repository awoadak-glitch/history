import test from 'node:test';
import assert from 'node:assert/strict';
import {createFullscreenHandler} from '../src/fullscreen.mjs';
test('native fullscreen enters and exits without renderer Node access; foreign requests cannot change the window',async()=>{
 let value=false;const handler=createFullscreenHandler({isFullScreen:()=>value,setFullScreen:v=>{value=v;}});
 for(const fullscreen of [true,false]){const r=await handler(new Request('palma://app/api/desktop/fullscreen',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({fullscreen})}));assert.equal(r.status,200);assert.equal((await r.json()).fullscreen,fullscreen);assert.equal(value,fullscreen);}
 const foreign=new Request('palma://app/api/desktop/fullscreen',{method:'POST',headers:{'Content-Type':'application/json'},body:'{"fullscreen":true}'});foreign.initiatorOrigin='https://foreign.example';assert.equal((await handler(foreign)).status,403);assert.equal(value,false);
 assert.equal((await handler(new Request('palma://other/api/desktop/fullscreen'))).status,403);
 assert.equal((await handler(new Request('palma://app/api/desktop/fullscreen',{method:'POST',headers:{'Content-Type':'application/json'},body:'{"fullscreen":"true"}'}))).status,400);
});
