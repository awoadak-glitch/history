import {openPlayer,closePlayer} from '/player.js';
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(predicate,label,timeout=65000){const end=Date.now()+timeout;while(Date.now()<end){if(predicate())return;await wait(200);}throw Error(label);}
const check=values=>fetch('/__test/check',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});
try {
  await until(()=>document.querySelector('.media-card'),'Catalog did not render');
  const cases=await (await fetch('/__test/cases')).json();
  for(const item of cases){
    try {
    const query=new URLSearchParams({provider:'palma',kind:item.kind,id:item.id,index:item.index,target:'web'});
    if(item.episode)query.set('episode',item.episode);
    const response=await fetch('/api/resolve?'+query),playback=await response.json();
    if(!response.ok)throw Error(item.label+': '+response.status+' '+playback.code);
    openPlayer(playback,{...item,provider:'palma',title:item.label},item.episode||'');
    await until(()=>document.querySelector('#player-dialog[open] video'),item.label+' player missing');
    const video=document.querySelector('#player-dialog[open] video');video.muted=true;await video.play();
    await until(()=>{
      const error=document.querySelector('#player-dialog[open] .player-error:not([hidden]) p');
      if(error)throw Error(item.label+': '+error.textContent);
      return video.currentTime>3&&video.videoWidth>0&&video.getVideoPlaybackQuality().totalVideoFrames>0;
    },item.label+' did not decode');
    if(item.minDuration&&video.duration<item.minDuration)throw Error(item.label+' returned a short replacement');
    if(item.live&&Number.isFinite(video.duration))throw Error('Channel returned a recorded replacement');
    await check({step:item.label+' decoded',success:true,id:item.id,episode:item.episode,width:video.videoWidth,height:video.videoHeight,time:video.currentTime,duration:Number.isFinite(video.duration)?video.duration:'live',frames:video.getVideoPlaybackQuality().totalVideoFrames});
    if(item.kind==='series'){
      video.currentTime=90;await until(()=>video.currentTime>=90&&video.readyState>=2,'Episode seeking failed');
      await check({step:'episode seek',time:video.currentTime});
    }
    await fetch('/__test/shot?name='+item.label.toLowerCase().replaceAll(' ','-'));
    }catch(error){
      await check({step:item.label,success:false,error:error.message.replace(/https?:\/\/[^\s]+/g,'[source URL]')});
      await fetch('/__test/shot?name='+item.label.toLowerCase().replaceAll(' ','-')+'-failed');
    }finally{closePlayer();}
  }
  await fetch('/__test/result');
}catch(error){await fetch('/__test/failure?message='+encodeURIComponent(error.message));}
