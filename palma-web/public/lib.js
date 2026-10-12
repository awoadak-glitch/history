import {nativeIcons} from './native-icons.js';
const paths={
  home:'M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z',
  series:'M3 6h18v14H3z M7 3h10 M7 10l6 4-6 4z',movie:'M3 4h18v16H3z M3 9h18 M7 4l4 5 M14 4l4 5',
  anime:'M5 7l2-4 5 3 5-3 2 4v9l-7 5-7-5Z M8 11h2 M14 11h2 M9 16h6',
  channel:'M3 7h18v13H3z M8 3l4 4 4-4 M7 11h7 M7 15h5 M18 11v4',
  matches:'M8 3h8v5c0 4-2 6-4 6s-4-2-4-6Z M8 5H4v3c0 2 2 3 4 3 M16 5h4v3c0 2-2 3-4 3 M12 14v5 M8 21h8',
  heart:'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z',
  search:'M21 21l-6-6 M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0',
  settings:'M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1Z M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  menu:'M4 6h16 M4 12h16 M4 18h16',back:'m9 5 7 7-7 7',forward:'m15 5-7 7 7 7',
  play:'m8 4 12 8-12 8Z',download:'M12 3v12 m-5-5 5 5 5-5 M4 16v5h16v-5',
  clock:'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M12 7v5l3 2',
  world:'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M3 12h18 M12 3c-5 6-5 12 0 18 5-6 5-12 0-18',
  bell:'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4',
  close:'M6 6l12 12 M6 18 18 6',refresh:'M20 10a8 8 0 1 0 0 5 M20 4v6h-6',
  error:'M12 3 2 21h20Z M12 9v5 M12 17v1',star:'m12 3 3 6 6 .8-4.5 4.5L18 21l-6-3-6 3 1.5-6.7L3 9.8 9 9Z',
  folder:'M3 5h7l2 3h9v12H3Z',people:'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-3a5 5 0 0 1 5-5h6a5 5 0 0 1 5 5v3',
  wrestling:'M3 8l4-4 5 5 5-5 4 4-5 5 5 4-4 4-5-5-5 5-4-4 5-4Z',
  copy:'M8 8h13v13H8z M16 8V3H3v13h5',external:'M14 3h7v7 M21 3l-10 10 M10 3H3v18h18v-7',
  fullscreen:'M3 9V3h6 M15 3h6v6 M21 15v6h-6 M9 21H3v-6',trash:'M4 6h16 M9 3h6 M6 6l1 15h10l1-15 M10 10v7 M14 10v7',
  news:'M3 4h18v17H3z M7 8h4v4H7z M14 8h3 M14 12h3 M7 16h10'
};
export const icon=(name)=>nativeIcons[name]?`<svg class="icon native-icon" viewBox="${nativeIcons[name].viewBox}" aria-hidden="true">${nativeIcons[name].paths}</svg>`:`<svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[name]||paths.movie}"/></svg>`;
export const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const str=value=>typeof value==='string'?value:typeof value==='number'?String(value):'';
export const first=(o,...keys)=>keys.map(k=>str(o?.[k])).find(Boolean)||'';
export function arabic(value){if(typeof value==='string')return value;if(value&&typeof value==='object')return first(value,'ar','arabic','name','en');return str(value);}
export function list(value){if(Array.isArray(value))return value;if(!value)return [];for(const k of ['items','data','results','sources','links','episodes','seasons','posters','channels','hits'])if(Array.isArray(value[k]))return value[k];if(value.data&&typeof value.data==='object')return list(value.data);return [];}
export function data(value){return value?.data&&!Array.isArray(value.data)?value.data:value;}
export function safeURL(value){const s=str(value).trim();if(!s)return '';try{const u=new URL(s,location.origin);return ['https:','http:'].includes(u.protocol)&&!u.username&&!u.password?u.href:'';}catch{return '';}}
function image(value){if(typeof value==='object'&&value)return first(value,'large','medium','url');return str(value);}
export function card(raw,provider='palma',kind='series'){
  const d=raw?.details||{};
  if(!raw)return null;
  const type=first(raw,'item_type','_kind')||kind;
  kind=({movies:'movie',channels:'channel',actor:'actor',wrestling_show:'wrestling',animes:'anime',series:'series'})[type]||type;
  const id=first(raw,'item_id','animeId','objectID','series_id','id','doc_id');
  let title=arabic(raw.title_ar)||arabic(raw.name)||first(raw,'title','title_en','original_title')||arabic(d.name)||'عمل';
  let poster=image(raw.poster)||image(raw.poster_uri)||image(raw.aniList_poster)||image(raw.logo)||image(raw.image)||image(d.poster);
  let cover=image(raw.cover)||image(raw.cover_uri)||image(raw.banner)||image(raw.thumb)||image(raw.thumb_uri)||poster;
  if(poster && !/^(https?:|data:)/.test(poster))poster=new URL(poster,'https://dramaramadan.net/').href;
  if(cover && !/^(https?:|data:)/.test(cover))cover=new URL(cover,'https://dramaramadan.net/').href;
  if(!poster && provider==='palma'&&id&&kind==='series')poster=`https://dramaramadan.net/img/poster${id}.webp`;
  return {...raw,id,title,poster,cover,provider,kind,year:first(raw,'release_year','year')||first(d,'year'),rating:first(raw,'rating','rate'),tags:raw.tags||raw.genres||[],story:arabic(raw.story)||arabic(d.story)||first(raw,'description','story_en')};
}
export const keyOf=item=>`${item.provider}:${item.kind}:${item.id}`;
export const playbackKey=(item,episode='')=>`${keyOf(item)}:${episode||'movie'}`;
export function link(view,params={}){const q=new URLSearchParams();for(const [k,v]of Object.entries(params))if(v!==undefined&&v!==null&&v!=='')q.set(k,String(v));return `#/${view}${q.size?'?'+q:''}`;}
export const detailLink=item=>link('detail',{provider:item.provider,kind:item.kind,id:item.id,area:item.area});
export async function api(provider,route,query={},signal){const p=new URLSearchParams({provider,route,...query});const r=await fetch(`/api/catalog?${p}`,{signal});let j;try{j=await r.json();}catch{throw Error('تعذر قراءة المصدر. أعد المحاولة.');}if(!r.ok||j?.error){const e=Error(j?.error||'تعذر الاتصال بالمصدر.');e.code=j?.code;e.status=r.status;throw e;}return j;}
export function readStore(name,fallback){try{return JSON.parse(localStorage.getItem('palma.'+name))??fallback;}catch{return fallback;}}
export function writeStore(name,value){try{localStorage.setItem('palma.'+name,JSON.stringify(value));return true;}catch{return false;}}
export function favorite(item){return readStore('favorites',[]).some(x=>keyOf(x)===keyOf(item));}
export function toggleFavorite(item){let values=readStore('favorites',[]);const had=favorite(item);values=values.filter(x=>keyOf(x)!==keyOf(item));if(!had)values.unshift({...item,story:undefined,raw:undefined});writeStore('favorites',values.slice(0,250));return !had;}
export function saveProgress(item,episode,time,duration){if(!Number.isFinite(time)||time<0)return;const key=playbackKey(item,episode),values=readStore('progress',{});values[key]={time,duration,updated:Date.now(),item:{...item,story:undefined,raw:undefined},episode};const keys=Object.keys(values).sort((a,b)=>values[b].updated-values[a].updated);for(const k of keys.slice(250))delete values[k];writeStore('progress',values);}
export function progressFor(item,episode=''){return readStore('progress',{})[playbackKey(item,episode)];}
export function timeText(s){const n=Math.max(0,Math.floor(s||0));return `${Math.floor(n/60)}:${String(n%60).padStart(2,'0')}`;}
let toastTimer;
export function toast(text){const e=document.getElementById('toast');e.textContent=text;e.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>e.classList.remove('show'),3000);}
export function modal(title,body,actions=[]){const d=document.getElementById('dialog');d.innerHTML=`<div class="dialog-header"><h3>${esc(title)}</h3><button class="icon-btn" data-close aria-label="إغلاق">${icon('close')}</button></div>${body}<div class="dialog-actions"></div>`;d.querySelector('[data-close]').onclick=()=>d.close();for(const a of actions){const b=document.createElement('button');b.className='btn '+(a.primary?'primary':'');b.textContent=a.text;b.onclick=()=>a.run(d);d.querySelector('.dialog-actions').append(b);}if(!d.open)d.showModal();return d;}
