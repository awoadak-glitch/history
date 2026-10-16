import {nearest} from './tv-focus.js';
import {closePlayer} from './playback.js';
import {modal,esc} from './lib.js';
document.documentElement.dataset.tv='true';
const $=s=>document.querySelector(s),focusable='a[href],button,input:not([type="file"]),select,summary,[tabindex="0"],label[for="import-library"]';
const memory=new Map();let route=location.hash,idle,previousRoute,scheduled=false;
const visible=el=>!el.disabled&&!el.closest('[hidden],[inert]')&&el.getClientRects().length&&getComputedStyle(el).visibility!=='hidden';
const scope=()=>$('.tv-picker[open]')||$('#dialog[open]')||$('#player-dialog[open]')||document;
const choices=()=>[...scope().querySelectorAll(focusable)].filter(visible);
const identity=el=>el.id?{id:el.id}:el.getAttribute('href')?{href:el.getAttribute('href')}:{action:el.dataset.action,text:el.textContent};
function focus(el){if(!el)return;el.focus({preventScroll:true});el.scrollIntoView({block:'nearest',inline:'nearest',behavior:'auto'});}
function remember(){const el=document.activeElement;if(el&&el!==document.body&&el.closest('#main,#sidebar,#header'))memory.set(route,identity(el));}
function initial(){if(document.activeElement!==document.body&&document.activeElement?.isConnected&&visible(document.activeElement)&&scope().contains(document.activeElement))return;const saved=memory.get(location.hash),all=choices();focus(all.find(el=>saved&&(saved.id?el.id===saved.id:saved.href?el.getAttribute('href')===saved.href:el.dataset.action===saved.action&&el.textContent===saved.text))||all.find(el=>el.matches('#main a,#main button'))||all.find(el=>el.matches('.nav-item.active'))||all[0]);}
function reveal(){const wrap=$('.player-wrap');if(!wrap)return;wrap.classList.remove('controls-hidden');clearTimeout(idle);idle=setTimeout(()=>{if(!$('.player-settings[open]')&&!$('.tv-picker[open]')&&!$('#dialog[open]')&&!$('#player-dialog video')?.paused){wrap.focus();wrap.classList.add('controls-hidden');}},5000);}
function picker(select){
  if(select.disabled)return;const d=document.createElement('dialog');d.className='dialog tv-picker';d.innerHTML=`<h2>${esc(select.getAttribute('aria-label')||select.closest('label')?.childNodes[0]?.textContent||'اختر')}</h2><div class="tv-options">${[...select.options].map((o,i)=>`<button class="btn ${o.selected?'primary':'outline'}" data-index="${i}" ${o.disabled?'disabled':''}>${o.selected?'✓ ':''}${esc(o.textContent)}</button>`).join('')}</div>`;document.body.append(d);d.showModal();d.addEventListener('click',e=>{const b=e.target.closest('button[data-index]');if(!b)return;select.selectedIndex=+b.dataset.index;select.dispatchEvent(new Event('change',{bubbles:true}));d.close();});d.addEventListener('close',()=>{d.remove();focus(select);reveal();},{once:true});focus(d.querySelector('.primary')||d.querySelector('button'));
}
function back(){
  const pick=$('.tv-picker[open]');if(pick){pick.close();return;}
  const dialog=$('#dialog[open]');if(dialog){dialog.close();return;}
  if($('#player-dialog[open]')){const settings=$('.player-settings[open]');if(settings){settings.open=false;focus(settings.querySelector('summary'));return;}closePlayer();initial();return;}
  if(location.hash&&location.hash!=='#/home'){if(previousRoute)history.back();else location.hash='#/home';return;}
  modal('إغلاق بالما؟','<p>هل تريد العودة إلى شاشة التلفاز؟</p>',[{text:'متابعة التصفح',run:d=>d.close()},{text:'إغلاق التطبيق',run:()=>window.PalmaTVNative?.exit()}]);
}
function mediaKey(key){const video=$('#player-dialog[open] video');if(!video)return;if(key===85)$('#play-toggle')?.click();if(key===126)video.play().catch(()=>{});if(key===127)video.pause();if(key===90)$('#seek-forward')?.click();if(key===89)$('#seek-back')?.click();reveal();}
function keydown(e){
  const key=e.key;if(['Escape','BrowserBack','GoBack'].includes(key)){e.preventDefault();e.stopImmediatePropagation();back();return;}
  if(!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Enter',' ','MediaPlayPause'].includes(key))return;
  const active=document.activeElement,typing=active?.matches('textarea,input:not([type="range"]):not([type="button"])');
  if(typing&&['ArrowLeft','ArrowRight',' '].includes(key))return;
  e.preventDefault();e.stopImmediatePropagation();
  const playing=$('#player-dialog[open]')&&scope()===$('#player-dialog');if(playing)reveal();
  if(playing&&(active?.matches('.player-wrap,video')||active===document.body)){
    if(key==='ArrowLeft'||key==='ArrowRight'){(key==='ArrowRight'?$('#seek-forward'):$('#seek-back'))?.click();return;}
    if(['Enter',' ','MediaPlayPause'].includes(key)){if(!e.repeat)$('#play-toggle')?.click();return;}
    focus($('#play-toggle'));return;
  }
  if(key==='Enter'||key===' '){if(e.repeat)return;if(typing){active.focus();window.PalmaTVNative?.keyboard();}else if(active?.tagName==='SELECT')picker(active);else if(active?.tagName==='SUMMARY')active.parentElement.open=!active.parentElement.open;else active?.click();return;}
  if(active?.matches('input[type="range"]')&&['ArrowLeft','ArrowRight'].includes(key)){
    const amount=active.id==='player-seek'?10:Number(active.step)||1;active.value=String(Math.max(Number(active.min),Math.min(Number(active.max),Number(active.value)+(key==='ArrowRight'?amount:-amount))));active.dispatchEvent(new Event('input',{bubbles:true}));active.dispatchEvent(new Event('change',{bubbles:true}));return;
  }
  const all=choices(),rect=active?.getBoundingClientRect();if(!rect||!all.includes(active)){initial();return;}const next=nearest(rect,all.filter(el=>el!==active).map(el=>({value:el,rect:el.getBoundingClientRect()})),key);focus(next);
}
document.addEventListener('keydown',keydown,true);
document.addEventListener('focusin',e=>{document.querySelector('.tv-focused')?.classList.remove('tv-focused');e.target.classList.add('tv-focused');remember();if(e.target.closest('#player-dialog'))reveal();});
window.addEventListener('hashchange',()=>{previousRoute=route;route=location.hash;setTimeout(initial,0);});
new MutationObserver(()=>{if(!scheduled){scheduled=true;requestAnimationFrame(()=>{scheduled=false;initial();});}}).observe(document.body,{childList:true,subtree:true});
const hint=document.createElement('div');hint.className='tv-remote-hint';hint.textContent='الأسهم للتنقل  •  OK للاختيار  •  رجوع للعودة';document.body.append(hint);
window.PalmaTV={back,mediaKey,focus,initial,picker};setTimeout(initial,200);

// Reassert native immersive mode when entering or leaving the video surface.
new MutationObserver(()=>window.PalmaTVNative?.immersive()).observe($('#player-dialog'),{attributes:true,attributeFilter:['open']});
