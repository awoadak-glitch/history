import {APP_ORIGIN} from './bridge.mjs';
export function createFullscreenHandler(window){
  return async request=>{
    const url=new URL(request.url);
    const respond=(body,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'no-store'}});
    if(url.protocol!=='palma:'||url.host!=='app'||url.username||url.password||(request.initiatorOrigin&&request.initiatorOrigin!==APP_ORIGIN))return respond({error:'Forbidden'},403);
    if(request.method==='GET')return respond({fullscreen:window.isFullScreen()});
    if(request.method!=='POST')return respond({error:'Method not allowed'},405);
    try{
      if(!request.headers.get('content-type')?.startsWith('application/json'))return respond({error:'Invalid input'},400);
      const body=await request.text();if(body.length>100)return respond({error:'Invalid input'},400);
      const value=JSON.parse(body).fullscreen;if(typeof value!=='boolean')return respond({error:'Invalid input'},400);
      window.setFullScreen(value);
      return respond({fullscreen:window.isFullScreen()});
    }catch{return respond({error:'Invalid input'},400);}
  };
}
