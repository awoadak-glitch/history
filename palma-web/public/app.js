import {icon,esc,first,arabic,list,data,safeURL,card,keyOf,playbackKey,link,detailLink,api,readStore,writeStore,favorite,toggleFavorite,progressFor,timeText,toast,modal} from './lib.js';
import {resolveServer} from './player.js';
import {PLAYER_OPTIONS} from './external-player.js';
import {renderDesktopDownloads} from './desktop-downloads.js';
import {mainNavigation,drawerNavigation,navigationState} from './navigation.js';
import {sectionKind,orderedSections,sectionQuery} from './palma-content.js';
import {matchGroups,matchChannel,people,artwork} from './content-details.js';
import {animeDetailMarkup,bindAnimeDetail} from './anime-view.js';

const main=document.getElementById('main'),seeds=new Map();
let controller=new AbortController(),generation=0,heroTimer,searchTimer,actions=new Map(),actionId=0,activeView='home';
const mainTabs=mainNavigation;
const extraTabs=[['collections','السلاسل','folder'],['actors','الممثلين','people'],['history','سجل المشاهدة','clock'],['downloads','التنزيلات','download'],['player-setup','مشغّل الكمبيوتر','play'],['notifications','الإشعارات','bell'],['request','طلب مسلسل/فيلم','download']];
const labels=Object.fromEntries([...mainTabs,...extraTabs].map(([v,t])=>[v,t]));
const nativeKinds={series:'series',movies:'movie',anime:'anime',channels:'channel',wrestling:'wrestling',collections:'collection',actors:'actor'};
const nativeRoutes={series:'api/series/',movies:'api/movies/',anime:'api/anime/',channels:'api/channels/',wrestling:'api/wrestling/',collections:'api/movies/collections.php',actors:'api/actors/'};
const detailRoutes={series:'api/series/show.php',episode:'api/episodes/show.php',movie:'api/movies/show.php',anime:'api/anime/show.php',channel:'api/channels/show.php',wrestling:'api/wrestling/show.php',actor:'api/actors/show.php',collection:'api/movies/collection.php',match:'api/matches/show.php'};
const filterRoutes={series:'api/series/filters.php',movies:'api/movies/filters.php',anime:'api/anime/filters.php',wrestling:'api/wrestling/categories.php'};
const animeSections=[['recent','أحدث الحلقات','series_date_created'],['popular','الأكثر شهرة هذا الموسم','series_fav_count_desc'],['bestMal','أفضل الأنميات عالمياً','best_mal_ranked'],['animations','الأنميشن الأكثر مشاهدة','most_watched_animations'],['latest','آخر الأعمال المضافة','series_date_created']];
function action(fn){const id=String(++actionId);actions.set(id,fn);return id;}
function button(text,fn,cls='btn',ico=''){return `<button class="${cls}" data-action="${action(fn)}">${ico?icon(ico):''}${esc(text)}</button>`;}
function heading(title,subtitle='',trailing=''){return `<div class="page-heading"><div><h1>${esc(title)}</h1>${subtitle?`<p>${esc(subtitle)}</p>`:''}</div>${trailing?`<div class="trailing">${trailing}</div>`:''}</div>`;}
function empty(text='لا توجد نتائج متاحة حالياً.'){return `<div class="state"><div class="state-icon">${icon('folder')}</div><p>${esc(text)}</p></div>`;}
function errorState(error,retry,extras=false){return `<div class="state"><div class="state-icon">${icon('error')}</div><h3>تعذر تحميل المحتوى</h3><p>${esc(error.message||error)}</p>${button('إعادة المحاولة',retry,'btn primary','refresh')}${extras?`<a class="btn outline" href="${link('world')}">${icon('world')} عالم المصادر</a>`:''}</div>`;}
function skeleton(){return `<div class="skeleton hero"></div><div class="rail">${Array.from({length:7},()=>'<div class="skeleton card"></div>').join('')}</div>`;}
function storeSeed(item){if(item){if(item.provider==='witcher'&&activeView==='anime')item.area='main';seeds.set(keyOf(item),item);if(seeds.size>1200)seeds.delete(seeds.keys().next().value);}}
function badgeText(item){if(item.provider==='witcher')return item.type||'أنمي';return ({series:'مسلسل',episode:'حلقة',movie:'فيلم',anime:'أنمي',channel:'قناة',wrestling:'مصارعة',actor:'ممثل',collection:'سلسلة أفلام'})[item.kind]||item.content_type||'';}
function img(raw,alt='',cls='',lazy=true){const url=safeURL(raw);return url?`<img src="${esc(url)}" alt="${esc(alt)}" class="${cls}" ${lazy?'loading="lazy"':''} decoding="async" referrerpolicy="no-referrer">`:`<img src="/assets/palma-logo.png" alt="${esc(alt)}" class="${cls}" loading="lazy">`;}
function mediaCard(item){if(!item?.id)return '';storeSeed(item);const progress=progressFor(item,item.episodeId||'');return `<article class="media-card"><a href="${detailLink(item)}"><div class="card-image">${img(item.poster,item.title)}${item.rating?`<span class="rating">★ ${esc(Number(item.rating)?Number(item.rating).toFixed(1):item.rating)}</span>`:''}<span class="card-type">${esc(badgeText(item))}</span><div class="play-overlay">${icon('play')}</div></div><h3 class="card-title" dir="auto">${esc(item.title)}</h3><div class="card-meta"><span>${esc(item.year||'')}</span><span>${esc(item.episodeName||item.status||'')}</span></div>${progress?.duration?`<div class="progress"><span style="width:${Math.min(100,Math.max(0,progress.time/progress.duration*100))}%"></span></div>`:''}</a></article>`;}
function rail(title,items,seeAll=''){const rendered=items.map(mediaCard).join('');if(!rendered)return '';return `<section class="section"><div class="section-head"><h2>${esc(title)}</h2>${seeAll?`<a href="${seeAll}">عرض الكل ${icon('forward')}</a>`:''}</div><div class="rail">${rendered}</div></section>`;}
function grid(items){return items.length?`<div class="grid">${items.map(mediaCard).join('')}</div>`:empty();}
function matchCard(m,detail=false){
  const home=m.home_team||m.homeTeam||{},away=m.away_team||m.awayTeam||{},scheduled=['NS','TBD','PST'].includes(m.status);
  const inner=`<div class="team">${img(home.logo||m.home_team_logo,'')}<strong>${esc(home.name||m.home_team_name)}</strong></div><div><small>${esc(m.league_name||m.league?.name||'')}</small><div class="match-score">${scheduled?'VS':esc(m.home_goals??m.home_score??'—')+' — '+esc(m.away_goals??m.away_score??'—')}</div><div class="match-date">${esc(scheduled?m.match_time:m.status)}</div></div><div class="team">${img(away.logo||m.away_team_logo,'')}<strong>${esc(away.name||m.away_team_name)}</strong></div>`;
  return detail===true?`<section class="match-card match-hero">${inner}</section>`:`<a class="match-card" href="${link('match',{id:m.id})}">${inner}</a>`;
}
function promoSection(section){
  const title=first(section,'title_ar','title_en'),type=section.promo_action_type,value=section.promo_action_value;
  if(type==='url')return '';
  const href=type==='url'?safeURL(value):type==='all_series_category'?link('series',{category:value}):link(({anime_home:'anime',wrestling:'wrestling',all_movies:'movies',all_series:'series',channels:'channels',matches:'matches'})[type]||'home');
  return `<section class="section native-promo"><a href="${esc(href||link('home'))}" ${type==='url'?'target="_blank" rel="noopener noreferrer"':''}>${section.promo_bg_image?img(section.promo_bg_image,'','promo-background'):''}${section.promo_char_image?img(section.promo_char_image,'','promo-character'):''}<div class="promo-copy"><h2>${esc(title)}</h2>${section.promo_description?`<p>${esc(section.promo_description)}</p>`:''}<span class="btn primary">${esc(section.promo_button_text||'تصفح الأعمال')} ${icon('forward')}</span></div></a></section>`;
}
function spotlightSection(title,items,kind,hideTitle=false){
  const cards=items.map(raw=>{const item=card(raw,'palma',kind);if(!item?.id)return '';storeSeed(item);return `<a class="spotlight-card" href="${detailLink(item)}">${img(item.cover,item.title)}<div class="spotlight-caption">${raw.logo_image?img(raw.logo_image,item.title,'spotlight-logo'):''}${!Number(raw.hide_title)?`<h3 dir="auto">${esc(item.title)}</h3>`:''}<span>${icon('play')} التفاصيل والمشاهدة</span></div></a>`;}).join('');
  return cards?`<section class="section">${hideTitle?'':`<div class="section-head"><h2>${esc(title)}</h2></div>`}<div class="spotlight-rail">${cards}</div></section>`:'';
}
function tags(item){const names=(item.tags||[]).map(x=>typeof x==='string'?x:first(x,'name_ar','name','title')).filter(Boolean);return `<div class="chips">${names.slice(0,6).map(t=>`<span class="badge">${esc(t)}</span>`).join('')}</div>`;}
function hero(items){items=items.filter(Boolean);if(!items.length)return '';items.forEach(storeSeed);const item=items[0];return `<section class="hero" data-hero><img class="hero-cover" src="${esc(safeURL(item.cover)||safeURL(item.poster)||'/assets/palma-logo.png')}" alt="${esc(item.title)}" referrerpolicy="no-referrer"><div class="hero-content"><img class="hero-poster" src="${esc(safeURL(item.poster)||'/assets/palma-logo.png')}" alt="" referrerpolicy="no-referrer"><div class="hero-info"><span class="hero-tag">${esc(badgeText(item))} · PALMA VISION</span><h1 dir="auto">${esc(item.title)}</h1>${tags(item)}<div class="card-meta">${item.rating?'★ '+esc(item.rating)+' · ':''}${esc(item.year)}</div><a class="btn primary hero-open" href="${detailLink(item)}">${icon('play')} التفاصيل والمشاهدة</a></div></div><div class="hero-dots">${items.map((_,i)=>`<button class="${i===0?'active':''}" aria-label="عرض ${i+1}" data-hero-index="${i}"></button>`).join('')}</div><button class="hero-nav prev" aria-label="السابق">${icon('back')}</button><button class="hero-nav next" aria-label="التالي">${icon('forward')}</button></section>`;}
function bindHero(items){items=items.filter(Boolean);clearInterval(heroTimer);const el=document.querySelector('[data-hero]');if(!el||items.length<2)return;let index=0;const swap=i=>{index=(i+items.length)%items.length;const item=items[index];el.querySelector('.hero-cover').src=safeURL(item.cover)||safeURL(item.poster)||'/assets/palma-logo.png';el.querySelector('.hero-cover').alt=item.title;el.querySelector('.hero-poster').src=safeURL(item.poster)||'/assets/palma-logo.png';el.querySelector('.hero-info').innerHTML=`<span class="hero-tag">${esc(badgeText(item))} · PALMA VISION</span><h1 dir="auto">${esc(item.title)}</h1>${tags(item)}<div class="card-meta">${item.rating?'★ '+esc(item.rating)+' · ':''}${esc(item.year)}</div><a class="btn primary" href="${detailLink(item)}">${icon('play')} التفاصيل والمشاهدة</a>`;el.querySelectorAll('[data-hero-index]').forEach((b,n)=>b.classList.toggle('active',n===index));};el.querySelectorAll('[data-hero-index]').forEach(b=>b.onclick=()=>swap(+b.dataset.heroIndex));el.querySelector('.prev').onclick=()=>swap(index-1);el.querySelector('.next').onclick=()=>swap(index+1);if(readStore('auto-banner',true)&&!matchMedia('(prefers-reduced-motion:reduce)').matches)heroTimer=setInterval(()=>{if(!document.hidden&&!document.querySelector('dialog[open]'))swap(index+1);},7000);}
function worldTabs(){return '';}
function animeNavigation(mainArea=false){
  const view=mainArea?'anime':'world', area=mainArea?'main':'world';
  return `<nav class="source-shortcuts" aria-label="قوائم الأنمي">${[['series_name_asc','كل الأنمي'],['series_date_created','أحدث الأعمال'],['series_fav_count_desc','الأكثر شهرة'],['best_mal_ranked','الأفضل عالمياً'],['all_animation','الأنميشن']].map(([index,title])=>`<a class="chip" href="${link(view,{index})}">${title}</a>`).join('')}<a class="chip" href="${link('favorites',{area,source:'witcher'})}">المفضلة</a><a class="chip" href="${link('history',{area,source:'witcher'})}">السجل</a><a class="chip" href="${link('news',{area})}">الأخبار</a></nav>`;
}
function shell(view){
  const p=new URLSearchParams(location.hash.split('?')[1]),nav=navigationState(view,p),world=nav.world;
  document.body.dataset.area=world?'world':'main';document.body.dataset.view=view;
  const title=world?(p.get('tab')==='series'?'المسلسلات':p.get('tab')==='movies'?'الأفلام':p.get('tab')==='channels'?'القنوات':view==='world'?'عالم الأنمي':labels[view]||'الأنمي'):view==='home'?'':labels[view]||'بالما';
  const searchHref=link('search',{source:world?'witcher':'all',area:world?'world':'main'});
  document.getElementById('header').innerHTML=`${world?`<a class="icon-btn" href="${link('home')}" aria-label="العودة إلى بالما">${icon('back')}</a>`:`<button class="icon-btn menu-button" id="open-menu" aria-label="القائمة" aria-controls="sidebar" aria-expanded="false">${icon('menu')}</button>`}<strong class="appbar-title">${esc(title)}</strong><div class="top-actions">${world||view!=='home'?`<a class="icon-btn" href="${searchHref}" aria-label="البحث">${icon('search')}</a>`:`<a class="icon-btn" href="${link('notifications')}" aria-label="الإشعارات">${icon('bell')}</a><a class="icon-btn" href="${link('favorites')}" aria-label="المفضلة">${icon('heart')}</a><a class="icon-btn" href="${link('settings')}" aria-label="الإعدادات">${icon('settings')}</a>`}</div>`;
  document.getElementById('sidebar').innerHTML=`<a class="drawer-logo" href="#/home"><img src="/assets/palma-logo.png" alt="PALMA VISION"></a><div class="nav-divider"></div><nav>${drawerNavigation.map(([v,t,i],index)=>`${[8,11].includes(index)?'<div class="nav-divider"></div>':''}${v==='share'?button(t,shareApp,'nav-item',i):`<a class="nav-item ${nav.active===v?'active':''}" href="${link(v)}">${icon(i)}<span>${t}</span></a>`}`).join('')}</nav><button class="drawer-close icon-btn" aria-label="إغلاق القائمة">${icon('close')}</button>`;
  const bottom=document.getElementById('bottom-nav');bottom.classList.toggle('world-bottom',world);
  bottom.setAttribute('aria-label',world?'تبويبات عالم المصادر':'تبويبات بالما الرئيسية');
  bottom.innerHTML=nav.tabs.map(([v,t,i])=>`<a href="#/${v}" class="${nav.active===v?'active':''}" ${nav.active===v?'aria-current="page"':''}>${icon(i)}<span>${t}</span></a>`).join('');
  requestAnimationFrame(()=>bottom.querySelector('[aria-current]')?.scrollIntoView({block:'nearest',inline:'nearest'}));
  document.getElementById('open-menu')?.addEventListener('click',()=>drawer(true));
  document.querySelector('.drawer-close').onclick=()=>drawer(false);
}
function drawer(open){const sidebar=document.getElementById('sidebar');sidebar.classList.toggle('open',open);sidebar.inert=!open;document.getElementById('drawer-shade').hidden=!open;document.getElementById('open-menu')?.setAttribute('aria-expanded',String(open));document.body.classList.toggle('drawer-open',open);if(open)sidebar.querySelector('.nav-item')?.focus();}
async function shareApp(){const url=location.origin+'/';if(navigator.share){try{await navigator.share({title:'PALMA VISION',url});}catch{}}else{try{await navigator.clipboard.writeText(url);toast('تم نسخ رابط بالما');}catch{modal('مشاركة التطبيق',`<input class="share-url" value="${esc(url)}" readonly aria-label="رابط بالما">`);}}drawer(false);}
document.addEventListener('keydown',e=>{if(e.key==='Escape')drawer(false);});
document.getElementById('drawer-shade').onclick=()=>drawer(false);
document.addEventListener('click',e=>{const node=e.target.closest('[data-action]');if(node){const fn=actions.get(node.dataset.action);if(fn)Promise.resolve(fn(e)).catch(err=>{if(err.name!=='AbortError')toast(err.message);});}if(e.target.closest('.nav-item,.bottom-nav a,.drawer-brand'))drawer(false);});
document.addEventListener('error',e=>{if(e.target.tagName==='IMG'&&!e.target.src.endsWith('/assets/palma-logo.png')){e.target.src='/assets/palma-logo.png';e.target.style.objectFit='contain';}},true);
function active(token){return token===generation;}
function page(html){main.innerHTML=`<div class="page">${html}</div>`;}

async function home(token){
  page(skeleton());
  let native;
  try{native=data(await api('palma','api/v2/home.php',{},controller.signal));}catch(e){if(e.name==='AbortError')return;native={error:e};}
  if(!active(token))return;
  if(native.error){
    const cached=readStore('home-cache',null);
    if(cached?.data){native=cached.data;page(`<div class="offline-note">آخر محتوى محفوظ · تعذر تحديث المصدر الآن</div>`);}else{
      page(`<section class="native-home-state">${errorState(native.error,()=>navigate())}</section>`);return;
    }
  }else writeStore('home-cache',{data:native,updated:Date.now()});
  const heroes=list(native.banners).map(x=>card(x,'palma',x.item_type||'series'));
  page(hero(heroes)+`<div id="native-sections"></div>`);bindHero(heroes);
  const container=document.getElementById('native-sections');
  for(const section of orderedSections(list(native.sections))){
    if(!active(token))return;
    const title=first(section,'title_ar','title_en','title')||'أحدث الأعمال',type=first(section,'section_type'),kind=sectionKind(type);
    if(type.startsWith('promo_banner')){container.insertAdjacentHTML('beforeend',promoSection(section));continue;}
    if(type==='today_matches'){container.insertAdjacentHTML('beforeend',`<section class="section"><div class="section-head"><h2>${esc(title)}</h2><a href="${link('matches')}">عرض الكل</a></div><div class="home-matches">${list(section.items).map(m=>matchCard(m)).join('')}</div></section>`);continue;}
    const view=Object.keys(nativeKinds).find(k=>nativeKinds[k]===kind)||'series';
    const items=list(section.items||section.movieItems||section.seriesItems||section.animeItems||section.channelItems||section.actorItems||section.wrestlingItems);
    const more=Number(section.hide_see_all)?'':link(view,sectionQuery(section));
    if(type.endsWith('_spotlight'))container.insertAdjacentHTML('beforeend',spotlightSection(title,items,kind,Number(section.hide_title)));
    else if(active(token))container.insertAdjacentHTML('beforeend',rail(Number(section.hide_title)?'':title,items.map(x=>card(x,'palma',kind)),more));
  }
  if(active(token)&&!container.children.length)container.innerHTML=empty('لا توجد أقسام معروضة حاليًا.');
}

async function world(p,token){
  const tab=p.get('tab')||'witcher',index=p.get('index'),mainArea=activeView==='anime';
  page((tab==='witcher'?animeNavigation(mainArea):'')+`<div id="world-body">${skeleton()}</div>`);
  const body=document.getElementById('world-body');
  if(tab!=='witcher'){await dramaCatalog(tab,body,token);return;}
  if(index){await witcherCatalogue(index,body,token);return;}
  try{
    const j=await api('witcher','home',{},controller.signal);if(!active(token))return;
    const heroes=list(j.hero).map(x=>card(x,'witcher','anime'));
    body.innerHTML=hero(heroes)+animeSections.map(([key,title,index])=>rail(title,list(j[key]).map(x=>card(x,'witcher','anime')),link(mainArea?'anime':'world',{index}))).join('')+newsHTML(list(j.news));bindHero(heroes);
  }catch(e){if(active(token)&&e.name!=='AbortError')body.innerHTML=errorState(e,()=>navigate(),false);}
}
async function witcherCatalogue(index,body,token){
  const titles={series_name_asc:'كل الأنمي',series_date_created:'آخر الأعمال المضافة',best_mal_ranked:'أفضل الأنميات عالمياً',series_fav_count_desc:'الأكثر شهرة',most_watched_animations:'الأنميشن الأكثر مشاهدة',all_animation:'الأنميشن'};
  body.innerHTML=heading(titles[index]||'الأنمي')+'<div id="catalog-results"></div><div id="catalog-more"></div>';
  let current=0;const fetchPage=async()=>{
    const target=document.getElementById('catalog-results'),more=document.getElementById('catalog-more');more.innerHTML='<span class="spinner"></span>';
    try{const j=await api('witcher','search',{q:'',index,page:current},controller.signal);if(!active(token))return;const items=list(j.items||j.hits).map(x=>card(x,'witcher','anime'));if(!current)target.innerHTML=grid(items);else target.querySelector('.grid')?.insertAdjacentHTML('beforeend',items.map(mediaCard).join(''));current++;const hasMore=j.nbPages?current<j.nbPages:items.length>=20;more.innerHTML=hasMore?button('تحميل المزيد',fetchPage,'btn outline load-more'):'';}catch(e){if(active(token)&&e.name!=='AbortError')more.innerHTML=errorState(e,fetchPage,false);}
  };await fetchPage();
}
async function nativeCatalog(view,p,token){
  let current=1,requestVersion=0;const query={limit:'30',sort_by:p.get('sort_by')||'created'};for(const [k,v]of p)if(!['provider','kind','id','area'].includes(k))query[k]=v;
  page(heading(labels[view])+`<div id="filters" class="filters"></div><div id="catalog-results">${skeleton()}</div><div id="catalog-more"></div>`);
  const load=async(reset=false)=>{
    const version=++requestVersion;
    if(reset){current=1;document.getElementById('catalog-results').innerHTML=skeleton();}
    const target=document.getElementById('catalog-results'),more=document.getElementById('catalog-more');
    try{const j=await api('palma',nativeRoutes[view],{...query,page:current},controller.signal);if(!active(token)||version!==requestVersion)return;const items=list(j).map(x=>card(x,'palma',nativeKinds[view]));if(current===1)target.innerHTML=grid(items);else target.querySelector('.grid')?.insertAdjacentHTML('beforeend',items.map(mediaCard).join(''));current++;const meta=j.pagination||j.meta||{},has=meta.total_pages?current<=meta.total_pages:meta.has_more??items.length>=30;more.innerHTML=has?button('تحميل المزيد',()=>load(),'btn outline load-more'):'';}catch(e){if(active(token)&&version===requestVersion&&e.name!=='AbortError'){target.innerHTML=errorState(e,()=>load(true));more.innerHTML='';}}
  };
  const filters=document.getElementById('filters');
  filters.innerHTML=`<select id="sort-select" aria-label="الترتيب">${[['created','الأحدث'],['rating','الأعلى تقييمًا'],['views','الأكثر مشاهدة'],['title','الاسم'],['year','السنة']].map(([v,t])=>`<option value="${v}" ${query.sort_by===v?'selected':''}>${t}</option>`).join('')}</select><input id="catalog-search" type="search" placeholder="بحث في ${esc(labels[view])}" aria-label="البحث في القسم">`;
  document.getElementById('sort-select').onchange=e=>{query.sort_by=e.target.value;load(true);};
  document.getElementById('catalog-search').addEventListener('input',e=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>{query.search=e.target.value.trim();load(true);},420);});
  const loading=load();if(!filterRoutes[view]){await loading;return;}
  const filterLabels=[['countries','البلد'],['genres','النوع'],['years','السنة'],['content_types','المحتوى'],['statuses','الحالة'],['age_ratings','التصنيف العمري']];
  for(const [name,label]of filterLabels){const select=document.createElement('select');select.dataset.filter=name;select.setAttribute('aria-label',label);select.innerHTML=`<option value="">${label}</option>`;filters.append(select);}
  await loading;if(!active(token))return;
  try{const options=data(await api('palma',filterRoutes[view],{},controller.signal));if(!active(token))return;const pairs=[['categories','category','التصنيفات'],['countries','country','الدول'],['genres','genre_id','الأنواع'],['years','year','السنة'],['statuses','status','الحالة'],['languages','language','اللغة'],['content_types','content_type','المحتوى'],['age_ratings','age_rating','التصنيف العمري']];for(const [key,param,label]of pairs){const values=list(options?.[key]);if(!values.length)continue;const select=filters.querySelector(`[data-filter="${key}"]`)||document.createElement('select');select.setAttribute('aria-label',label);select.innerHTML=`<option value="">جميع ${label}</option>`+values.map(x=>`<option value="${esc(typeof x==='object'?first(x,'id','value','code'):x)}">${esc(typeof x==='object'?first(x,'name_ar','name','label','title','value'):x)}</option>`).join('');select.onchange=e=>{query[param]=e.target.value;load(true);};filters.append(select);}}catch{}
}
async function dramaCatalog(tab,body,token){
  const kind={series:'series',movies:'movie',channels:'channel'}[tab]||'series';let current=0;
  body.innerHTML=heading({series:'المسلسلات',movies:'الأفلام',channels:'القنوات'}[tab])+`<div class="filters"><select id="drama-sort" aria-label="الترتيب"><option value="created">الأحدث</option><option value="rating">الأعلى تقييمًا</option><option value="views">الأكثر مشاهدة</option><option value="title">الاسم</option></select></div><div id="drama-results"></div><div id="drama-more"></div>`;
  let order='created';const load=async(reset=false)=>{
    if(reset)current=0;const target=document.getElementById('drama-results'),more=document.getElementById('drama-more');if(!current)target.innerHTML=skeleton();
    const route=kind==='channel'?`channel/by/filtres/0/0/${current}/`:`${kind==='series'?'serie':'movie'}/by/filtres/0/${order}/${current}/`;
    try{const j=await api('drama',route,{},controller.signal);if(!active(token))return;const items=list(j).map(x=>card(x,'drama',kind));if(!current)target.innerHTML=grid(items);else target.querySelector('.grid')?.insertAdjacentHTML('beforeend',items.map(mediaCard).join(''));current++;more.innerHTML=items.length?button('تحميل المزيد',()=>load(),'btn outline load-more'):'';}catch(e){if(active(token)&&e.name!=='AbortError')target.innerHTML=errorState(e,()=>load(true),false);}
  };document.getElementById('drama-sort').onchange=e=>{order=e.target.value;load(true);};await load();
}

async function detail(p,token){
  const provider=p.get('provider')||'palma',kind=p.get('kind')||'series',id=p.get('id')||'';
  if(!id){page(empty('لا يوجد عمل محدد.'));return;}
  let seed=seeds.get(`${provider}:${kind}:${id}`);page(skeleton());
  try{
    let j;
    if(provider==='witcher')j=await api(provider,`anime/${encodeURIComponent(id)}`,{},controller.signal);
    else if(provider==='drama')j=await api(provider,`${kind==='channel'?'channel':'movie'}/by/${id}/`,{},controller.signal);
    else j=await api(provider,detailRoutes[kind]||detailRoutes.series,{id},controller.signal);
    if(!active(token))return;
    if(provider==='palma'&&kind==='episode') {
      const episode=data(j), seriesId=episode.series_id;
      if(!seriesId)throw Error('تعذر تحديد مسلسل هذه الحلقة.');
      const item=card({id:seriesId,title_ar:episode.series_title,poster:episode.series_poster,banner:episode.series_banner},'palma','series');
      storeSeed(item);
      page(heading(item.title,`الموسم ${episode.season_number||1} · الحلقة ${episode.episode_number||''}`,`<a class="btn outline" href="${detailLink(item)}">${icon('back')} كل الحلقات</a>`)+`<div id="detail-content"></div>`);
      await loadServers(item,String(episode.id),token,episode);return;
    }
    const raw=data(j)||{},item=card({...seed,...raw,id,area:p.get('area')||seed?.area},provider,kind);storeSeed(item);renderDetail(item,raw,token);
  }catch(e){if(active(token)&&e.name!=='AbortError')page(heading(seed?.title||'التفاصيل')+errorState(e,()=>navigate()));}
}
function castSection(values,title='الممثلون'){
  const cast=people(values);if(!cast.length)return '';
  return `<section class="section cast-section"><div class="section-head"><h2>${esc(title)}</h2></div><div class="cast-rail">${cast.map(actor=>`<a class="cast-card" href="${detailLink({provider:'palma',kind:'actor',id:actor.id})}" data-actor-id="${esc(actor.id)}">${img(actor.photo||actor.profile_path,actor.name)}<strong dir="auto">${esc(actor.name)}</strong><small dir="auto">${esc(actor.role_name||'')}</small></a>`).join('')}</div></section>`;
}
function extraDetails(raw){
  const images=artwork(raw.images).filter(safeURL).slice(0,30);
  const ratings=raw.ratings||{};
  const scores=[['TMDB',ratings.tmdb,10],['IMDb',ratings.imdb,10],['Rotten Tomatoes',ratings.rt,100],['Metacritic',ratings.metacritic,100]].filter(([,v])=>v!=null&&v!=='');
  const crew=list(raw.crew),roles={director:'إخراج',writer:'كتابة',producer:'إنتاج'};
  return castSection(raw.actors||raw.cast)+(crew.length?`<section class="section"><div class="section-head"><h2>صنّاع العمل</h2></div><div class="crew-grid">${crew.map(x=>`<div class="crew-card">${img(x.photo,x.name)}<div><strong>${esc(x.name)}</strong><small>${esc(roles[x.role]||x.role)}</small></div></div>`).join('')}</div></section>`:'')+(scores.length?`<section class="section"><div class="section-head"><h2>التقييمات</h2></div><div class="rating-grid">${scores.map(([label,value,max])=>`<div><strong>${esc(value)}<small> / ${max}</small></strong><span>${label}</span></div>`).join('')}</div></section>`:'')+(raw.awards?.awards_text?`<p class="info-panel" dir="auto">${esc(raw.awards.awards_text)}</p>`:'')+(images.length?`<section class="section"><div class="section-head"><h2>صور العمل</h2></div><div class="artwork-rail">${images.map((url,index)=>`<button data-action="${action(()=>{const d=modal('صور العمل',img(url,'صورة من العمل','gallery-full',false));d.classList.add('gallery-dialog');d.addEventListener('close',()=>d.classList.remove('gallery-dialog'),{once:true});})}" aria-label="عرض الصورة ${index+1}">${img(url,'صورة من العمل')}</button>`).join('')}</div></section>`:'');
}
function renderDetail(item,raw,token){
  if(item.provider==='witcher'&&item.kind==='anime'){
    page(animeDetailMarkup(item,raw));document.title=`${item.title} | PALMA VISION`;
    bindAnimeDetail(main.querySelector('.anime-detail-page'),item,raw,{signal:controller.signal,loadEpisodes:()=>loadSeasons(item,raw,token),renderRail:rail});return;
  }
  const favoriteAction=button(favorite(item)?'في المفضلة':'إضافة للمفضلة',()=>{const added=toggleFavorite(item);toast(added?'أضيف للمفضلة':'أزيل من المفضلة');renderDetail(item,raw,token);},'btn outline','heart');
  const viewEpisodes=['series','anime'].includes(item.kind)&&item.provider!=='drama'||item.kind==='series';
  const meta=[['النوع',first(raw,'type','anime_type','content_type')||badgeText(item)],['سنة العرض',item.year],['الحالة',first(raw,'status','state')||first(raw.details,'state')],[item.kind==='movie'?'المدة':'مدة الحلقة',first(raw,'duration','episode_duration','runtime')],['عدد الحلقات',first(raw,'episodes_count','episode_count')||first(raw.details,'eps_num')],['التقييم',Number.isFinite(Number(item.rating))&&item.rating?Number(item.rating).toFixed(1):item.rating],['الدولة',first(raw,'country_name','country')],['الفئة العمرية',first(raw,'age_rating')||first(raw.details,'age')],['تاريخ الميلاد',raw.birthdate],['الجنسية',raw.nationality],['تاريخ الوفاة',raw.deathday],['موعد العرض',raw.broadcast_note],['اللغة',list(raw.spoken_languages).map(x=>typeof x==='string'?x:x.name||x.english_name).filter(Boolean).join('، ')],['شركات الإنتاج',list(raw.production_companies).map(x=>x.name).filter(Boolean).join('، ')]].filter(x=>x[1]);
  page(`<a class="chip" href="${link(item.provider==='palma'?'home':item.area==='main'?'anime':'world')}">${icon('back')} رجوع</a><div style="height:17px"></div><div class="detail-cover">${img(item.cover||item.poster,'','',false)}</div><div class="detail-info">${img(item.poster,item.title,'detail-poster',false)}<div class="detail-data"><span class="badge gold">${esc(badgeText(item))}</span><h1 dir="auto">${esc(item.title)}</h1>${tags(item)}<div class="actions">${viewEpisodes?button('الحلقات والمشاهدة',async()=>{await loadSeasons(item,raw,token);document.getElementById('detail-content')?.scrollIntoView({behavior:'smooth',block:'start'});},'btn primary','play'):['movie','channel','wrestling'].includes(item.kind)?button('المشاهدة والسيرفرات',async()=>{await loadServers(item,'',token);document.getElementById('detail-content')?.scrollIntoView({behavior:'smooth',block:'start'});},'btn primary','play'):''}${favoriteAction}</div></div></div>${meta.length?`<div class="metadata-grid">${meta.map(([k,v])=>`<div><small>${esc(k)}</small><strong dir="auto">${esc(v)}</strong></div>`).join('')}</div>`:''}${item.story?`<h2>${item.kind==='actor'?'نبذة':'القصة'}</h2><p class="story" dir="auto">${esc(item.story)}</p>`:''}<div class="detail-tags">${tags(item)}</div><div id="trailer"></div>${extraDetails(raw)}<div id="detail-content"></div><div id="related"></div>`);
  main.querySelector('.page').classList.toggle('actor-page',item.kind==='actor');
  const trailer=first(raw,'trailer_url','trailer')||(list(raw.videos).find(v=>v.site==='YouTube'&&v.video_key)?'https://www.youtube.com/watch?v='+encodeURIComponent(list(raw.videos).find(v=>v.site==='YouTube'&&v.video_key).video_key):'')||(raw.youtube_video_id?`https://www.youtube.com/watch?v=${encodeURIComponent(raw.youtube_video_id)}`:'');
  if(safeURL(trailer))document.getElementById('trailer').innerHTML=`<a class="btn outline" href="${esc(safeURL(trailer))}" target="_blank" rel="noopener noreferrer">${icon('play')} العرض الدعائي</a>`;
  const related=list(raw.recommendations||raw.similar||raw.related||raw.related_channels).map(x=>card(x,item.provider,item.kind));document.getElementById('related').innerHTML=rail('قد يعجبك أيضًا',related);
  if(item.kind==='collection'){const items=list(raw.movies||raw.items).map(x=>card(x,'palma','movie'));document.getElementById('detail-content').innerHTML=rail('أفلام السلسلة',items);}
  if(item.kind==='actor'){document.getElementById('detail-content').innerHTML=rail('الأفلام',list(raw.movies).map(x=>card(x,'palma','movie')))+rail('المسلسلات',list(raw.series).map(x=>card(x,'palma','series')))||(empty('لا توجد أعمال مرتبطة بهذا الممثل حاليًا.'));}
  if(viewEpisodes)loadSeasons(item,raw,token);else if(['movie','channel','wrestling'].includes(item.kind))loadServers(item,'',token);
}
async function loadSeasons(item,raw,token){
  const el=document.getElementById('detail-content');if(!el)return;el.innerHTML='<div class="state"><span class="spinner"></span></div>';
  if(item.provider==='witcher'){try{const j=await api('witcher',`anime/${encodeURIComponent(item.id)}/episodes`,{},controller.signal);if(active(token))renderEpisodes(item,list(j),el,token);}catch(e){if(active(token)&&e.name!=='AbortError')el.innerHTML=errorState(e,()=>loadSeasons(item,raw,token),false);}return;}
  try{
    let seasons=list(raw.seasons);
    if(!seasons.length){const j=await api(item.provider,item.provider==='drama'?`season/by/serie/${item.id}/`:'api/seasons/',item.provider==='palma'?{series_id:item.id}:{},controller.signal);seasons=list(j);}
    if(!active(token))return;if(!seasons.length){el.innerHTML=empty('لا توجد مواسم أو حلقات متاحة حاليًا.');return;}
    el.innerHTML='<section class="section"><h2>المواسم والحلقات</h2><div class="season-tabs" id="seasons"></div><div id="episodes"></div></section>';
    const holder=document.getElementById('episodes'),tabs=document.getElementById('seasons');
    const load=async(season)=>{holder.innerHTML='<div class="state"><span class="spinner"></span></div>';tabs.querySelectorAll('button').forEach(b=>b.classList.toggle('active',b.dataset.season===String(season.id)));try{let episodes=list(season.episodes);if(!episodes.length&&item.provider==='palma')episodes=list(await api('palma',item.kind==='anime'?'api/anime/episodes/':'api/episodes/',{season_id:season.id,per_page:500},controller.signal));if(active(token))renderEpisodes(item,episodes,holder,token);}catch(e){if(active(token)&&e.name!=='AbortError')holder.innerHTML=errorState(e,()=>load(season),false);}};
    seasons.forEach((s,i)=>{const b=document.createElement('button');b.className='chip';b.dataset.season=s.id;b.textContent=first(s,'name','title')||`الموسم ${s.season_number||i+1}`;b.onclick=()=>load(s);tabs.append(b);});await load(seasons[0]);
  }catch(e){if(active(token)&&e.name!=='AbortError')el.innerHTML=errorState(e,()=>loadSeasons(item,raw,token),false);}
}
function renderEpisodes(item,episodes,el,token){
  if(!episodes.length){el.innerHTML=empty('لا توجد حلقات متاحة حاليًا.');return;}
  const render=(filter='')=>episodes.filter(e=>!filter||JSON.stringify([e.name,e.title,e.episode_number,e.doc_id]).toLowerCase().includes(filter.toLowerCase())).map((e,i)=>{
    const id=first(e,'doc_id','episode_id','id')||String(i+1),number=e.episode_number||parseInt(id)||i+1,progress=progressFor(item,id);
    const fn=action(()=>{location.hash=link('servers',{provider:item.provider,kind:item.kind,id:item.id,episode:id,title:item.title,area:item.area});});
    return `<button class="episode-card" data-action="${fn}">${img(first(e,'thumb_uri','thumbnail','image')||item.cover,'')}<div class="episode-label"><div class="episode-title">${esc(first(e,'name','title')||'الحلقة '+number)}</div><small>${esc(first(e,'title_translated','air_date'))}${e.filler?' · فلر':''}</small>${progress?.time?`<small class="gold">متابعة من ${timeText(progress.time)}</small>`:''}${progress?.duration?`<div class="progress"><span style="width:${Math.min(100,progress.time/progress.duration*100)}%"></span></div>`:''}</div>${icon('play')}</button>`;
  }).join('');
  el.innerHTML=`<section class="section"><div class="section-head"><h2>الحلقات</h2><span class="badge">${episodes.length} حلقة</span></div><div class="episode-toolbar"><input type="search" placeholder="ابحث برقم الحلقة أو اسمها" aria-label="البحث في الحلقات"></div><div class="episodes-grid">${render()}</div></section>`;
  el.querySelector('input').oninput=e=>{if(active(token))el.querySelector('.episodes-grid').innerHTML=render(e.target.value);};
}
async function serversPage(p,token){
  const provider=p.get('provider')||'palma',kind=p.get('kind')||'series',id=p.get('id'),episode=p.get('episode')||'';
  const item=seeds.get(`${provider}:${kind}:${id}`)||{id,kind,provider,title:p.get('title')||'المشاهدة',area:p.get('area')};
  page(heading(item.title,episode?'الحلقة '+(parseInt(episode)||episode):'السيرفرات',`<a class="btn outline" href="${detailLink(item)}">${icon('back')} رجوع</a>`)+`<div id="detail-content"></div>`);await loadServers(item,episode,token);
}
async function loadServers(item,episode,token,prepared){
  const el=document.getElementById('detail-content');if(!el)return;el.innerHTML='<div class="state"><span class="spinner"></span></div>';
  try{
    let watch=[],downloads=[];
    if(item.provider==='witcher'){const j=await api('witcher',`anime/${encodeURIComponent(item.id)}/episodes/${encodeURIComponent(episode)}/servers`,{},controller.signal);watch=list(j).map((x,i)=>({...x,_index:i}));downloads=watch;}
    else if(item.provider==='drama'){const j=await api('drama',item.kind==='channel'?`channel/by/${item.id}/`:`${episode?'episode':'movie'}/source/by/${episode||item.id}/`,{},controller.signal);const items=list(item.kind==='channel'?data(j)?.sources:j).map((x,i)=>({...x,_index:i}));watch=items.filter(x=>!x.kind||['both','play'].includes(x.kind));downloads=items.filter(x=>['both','download'].includes(x.kind));}
    else if(episode&&item.kind!=='anime'){
      const raw=prepared||data(await api('palma','api/episodes/show.php',{id:episode},controller.signal));
      item={...item,episodeNumber:raw.episode_number};
      watch=list(raw.watch_links).map((x,i)=>({...x,_index:i}));downloads=list(raw.download_links).map((x,i)=>({...x,_index:i}));
    }else{
      const raw=data(await api('palma',episode?'api/anime/episodes/show.php':detailRoutes[item.kind],{id:episode||item.id},controller.signal));
      watch=list(raw.watch_links||raw.watchLinks||raw.streams||raw.sources).map((x,i)=>({...x,_index:i}));downloads=list(raw.download_links||raw.downloadLinks).map((x,i)=>({...x,_index:i}));
    }
    if(!active(token))return;
    const draw=(values,download=false)=>{
      const sorted=[...values].sort((a,b)=>Number(b.is_abr||0)-Number(a.is_abr||0)||(parseInt(b.quality)||0)-(parseInt(a.quality)||0));let last='';return sorted.map(s=>{
        const quality=s.quality||'متعدد',group=quality!==last?`<h3 class="gold" style="margin-top:22px">${esc(quality)}</h3>`:'';last=quality;
        return group+`<div class="server-row"><span class="server-number">${String(s._index+1).padStart(2,'0')}</span><div class="server-name"><strong>${esc(first(s,'server_name','name','title')||'سيرفر '+(s._index+1))}</strong><small>${esc([quality,first(s,'size','file_size','stream_type')].filter(Boolean).join(' · '))}</small></div>${button(download?'تنزيل':'مشاهدة',()=>resolveServer(item,episode,s._index,download,controller.signal),'btn primary',download?'download':'play')}${!download&&item.provider==='witcher'?button('',()=>resolveServer(item,episode,s._index,true,controller.signal),'icon-btn','download'):''}</div>`;
      }).join('');
    };
    el.innerHTML=`<section class="section"><div class="section-head"><h2>سيرفرات المشاهدة</h2></div>${watch.length?draw(watch):empty('لا توجد سيرفرات مشاهدة متاحة حاليًا.')}</section>${downloads.length&&item.provider!=='witcher'?`<section class="section"><div class="section-head"><h2>سيرفرات التنزيل</h2></div>${draw(downloads,true)}</section>`:''}`;
  }catch(e){if(active(token)&&e.name!=='AbortError')el.innerHTML=errorState(e,()=>loadServers(item,episode,token),false);}
}

async function search(p,token){
  let source=p.get('source')||'all',query=p.get('q')||'',type='series',pageIndex=0,searchVersion=0;
  page(heading('البحث','ابحث في أقسام بالما وعالم المصادر')+`<form id="search-form" class="search-large"><input name="q" value="${esc(query)}" placeholder="اكتب اسم العمل…" aria-label="اسم العمل" type="search"><button class="btn primary" type="submit">${icon('search')} بحث</button></form><div class="search-tabs chips" id="search-sources"></div><div id="search-results"></div><div id="search-more"></div>`);
  const sources=[['all','كل المصادر'],['palma','بالما'],['witcher','أنمي ويتشر'],['drama','عالم الدراما']];
  const drawTabs=()=>{document.getElementById('search-sources').innerHTML=sources.map(([k,t])=>button(t,()=>{source=k;drawTabs();run(true);},'chip '+(source===k?'active':''))).join('');if(source==='palma')document.getElementById('search-sources').innerHTML+=`<select id="search-type" aria-label="نوع المحتوى">${['series','movies','anime','channels','wrestling','actors'].map(v=>`<option value="${v}" ${v===type?'selected':''}>${labels[v]}</option>`).join('')}</select>`;document.getElementById('search-type')?.addEventListener('change',e=>{type=e.target.value;run(true);});};
  const run=async(reset=false)=>{
    if(reset)pageIndex=0;const seq=++searchVersion,target=document.getElementById('search-results'),more=document.getElementById('search-more');if(!query.trim()){target.innerHTML=empty('اكتب اسم العمل للبحث.');more.innerHTML='';return;}
    if(!pageIndex)target.innerHTML='<div class="state"><span class="spinner"></span></div>';more.innerHTML='';
    const selected=source==='all'?['palma','witcher','drama']:[source];
    const results=await Promise.allSettled(selected.map(s=>s==='witcher'?api(s,'search',{q:query,index:'series_name_asc',page:pageIndex},controller.signal):s==='palma'&&type==='anime'?api('witcher','search',{q:query,index:'series_name_asc',page:pageIndex},controller.signal):s==='palma'?api(s,nativeRoutes[type],{search:query,page:pageIndex+1,limit:30},controller.signal):api(s,`search/${encodeURIComponent(query)}/${pageIndex}/`,{},controller.signal)));
    if(!active(token)||seq!==searchVersion)return;
    let cards=[],failures=[],hasMore=false;
    results.forEach((r,i)=>{if(r.status==='fulfilled'){const provider=selected[i]==='palma'&&type==='anime'?'witcher':selected[i];let values=provider==='drama'?[...list(r.value.posters),...list(r.value.channels)].filter(Boolean):list(r.value);if(!values.length)values=list(r.value);cards.push(...values.map(x=>card(x,provider,provider==='witcher'?'anime':nativeKinds[type]||'series')));hasMore ||= r.value.nbPages?pageIndex+1<r.value.nbPages:values.length>=30;}else if(r.reason.name!=='AbortError')failures.push(`${sources.find(x=>x[0]===selected[i])?.[1]}: ${r.reason.message}`);});
    if(pageIndex===0)target.innerHTML=(failures.length?`<div class="notice"><span>${failures.map(esc).join('<br>')}</span></div>`:'')+grid(cards);else target.querySelector('.grid')?.insertAdjacentHTML('beforeend',cards.map(mediaCard).join(''));
    pageIndex++;more.innerHTML=hasMore?button('تحميل المزيد',()=>run(),'btn outline load-more'):'';
  };
  drawTabs();document.getElementById('search-form').onsubmit=e=>{e.preventDefault();clearTimeout(searchTimer);query=new FormData(e.target).get('q');run(true);};
  document.querySelector('#search-form input').oninput=e=>{query=e.target.value;clearTimeout(searchTimer);searchTimer=setTimeout(()=>run(true),420);};await run(true);
}

function library(view){
  if(view==='downloads'&&location.protocol==='palma:'){renderDesktopDownloads(document.getElementById('main'),controller.signal);return;}
  const source=new URLSearchParams(location.hash.split('?')[1]).get('source');const libraryTabs=`<nav class="library-tabs chips"><a class="chip" href="${link('favorites',{source})}">المفضلة</a><a class="chip" href="${link('history',{source})}">سجل المشاهدة</a><a class="chip" href="${link('downloads',{source})}">التنزيلات</a></nav>`;
  if(view==='favorites'){const items=readStore('favorites',[]).filter(x=>!source||x.provider===source);page(heading('المفضلة')+libraryTabs+grid(items));return;}
  if(view==='history'){
    const entries=Object.values(readStore('progress',{})).filter(x=>!source||x.item?.provider===source).sort((a,b)=>b.updated-a.updated);
    page(heading('سجل المشاهدة','متابعة الحلقات من حيث توقفت',entries.length?button('مسح السجل',()=>confirmClear('progress',{}),'btn outline','trash'):'')+(entries.length?`<div class="episodes-grid">${entries.map(e=>{const item=e.item;storeSeed(item);return `<a class="episode-card" href="${e.episode?link('servers',{provider:item.provider,kind:item.kind,id:item.id,episode:e.episode,title:item.title,area:item.area}):detailLink(item)}">${img(item.poster,'')}<div class="episode-label"><strong>${esc(item.title)}</strong><small>${e.episode?'الحلقة '+esc(parseInt(e.episode)||e.episode)+' · ':''}متابعة من ${timeText(e.time)}</small>${e.duration?`<div class="progress"><span style="width:${Math.min(100,e.time/e.duration*100)}%"></span></div>`:''}</div>${icon('play')}</a>`;}).join('')}</div>`:empty('لم تبدأ مشاهدة أي عمل بعد.')));return;
  }
  const entries=readStore('downloads',[]);page(heading('التنزيلات','الروابط التي جهزتها للتنزيل')+(entries.length?entries.map(e=>`<div class="download-item">${icon('download')}<div class="download-title"><strong>${esc(e.title)}</strong><small>${e.episode?'الحلقة '+esc(e.episode)+' · ':''}${new Date(e.date).toLocaleDateString('ar')}</small></div>${button('تجهيز الرابط مجددًا',()=>resolveServer(e.item,e.episode,e.index,true,controller.signal),'btn outline')}</div>`).join(''):empty('تظهر هنا الروابط التي تجهزها للتنزيل. الملفات المنزلة تجدها في مجلد التنزيلات على جهازك.')));
}
function confirmClear(name,value){modal('مسح البيانات',`<p>هل تريد مسح هذه البيانات المحفوظة على هذا المتصفح؟</p>`,[{text:'إلغاء',run:d=>d.close()},{text:'مسح',primary:true,run:d=>{writeStore(name,value);d.close();navigate();}}]);}
function settings(){
  const theme=readStore('theme','dark'),resume=readStore('auto-resume',true),target=readStore('playback-target','auto');
  page(heading('الإعدادات')+`<div class="settings-list"><div class="setting">${icon('play')}<div class="setting-content"><h3>مشغّل المشاهدة</h3><p>المشغّل المدمج يعمل داخل الموقع، ويمكنك اختيار مشغّل خارجي.</p><select id="playback-target" aria-label="مشغّل المشاهدة">${PLAYER_OPTIONS.map(([id,title])=>`<option value="${id}" ${target===id?'selected':''}>${title}</option>`).join('')}</select><label for="mx-edition">نسخة MX Player على الهاتف</label><select id="mx-edition"><option value="ad">MX Player</option><option value="pro" ${readStore('mx-edition','ad')==='pro'?'selected':''}>MX Player Pro</option></select><a class="btn outline" href="#/player-setup">إعداد AWR MX للكمبيوتر</a></div></div><div class="setting"><div class="setting-content"><h3>مظهر التطبيق</h3><p>اختيارات المظهر الموجودة في بالما</p><div class="theme-options">${[['original','الواجهة الأصلية','#0a0a0a'],['white','الواجهة البيضاء','#ffffff'],['dark','مظلم بالما','#0c1422']].map(([k,t,c])=>button(t,()=>{writeStore('theme',k);applyTheme();settings();},'theme-option '+(k===theme?'active':'')).replace('>'+esc(t),`><span class="theme-dot" style="background:${c}"></span>${esc(t)}`)).join('')}</div></div></div><div class="setting">${icon('clock')}<div class="setting-content"><h3>متابعة المشاهدة</h3><p>حفظ موضع الفيديو داخل المتصفح. المشغّل الخارجي يحفظ تقدّمك داخله.</p></div>${button(resume?'مفعّل':'متوقف',()=>{writeStore('auto-resume',!resume);settings();},'btn '+(resume?'primary':'outline'))}</div><div class="setting">${icon('heart')}<div class="setting-content"><h3>المفضلة والسجل</h3><p>محفوظان على هذا المتصفح، ولا تتم مزامنتهما مع حساب Android</p></div>${button('تصدير',exportLibrary,'btn outline','download')}</div><div class="setting">${icon('folder')}<div class="setting-content"><h3>استعادة بيانات الويب</h3><p>استيراد نسخة المفضلة وسجل المشاهدة التي صدّرتها من الموقع</p></div><label class="btn outline" for="import-library">استيراد</label><input type="file" id="import-library" accept="application/json" hidden></div><div class="setting">${icon('world')}<div class="setting-content"><h3>حول بالما</h3><p>PALMA VISION · إصدار الويب 1.1.7</p></div><img src="/assets/palma-logo.png" alt="بالما" width="65"></div></div>`);
  document.getElementById('playback-target').onchange=e=>writeStore('playback-target',e.target.value);
  document.getElementById('mx-edition').onchange=e=>writeStore('mx-edition',e.target.value);
  if(location.protocol==='palma:'){
    const choice=document.getElementById('playback-target');
    choice.replaceChildren(new Option('مشغّل بالما المدمج','browser',true,true));
    choice.closest('.setting-content').querySelector('p').textContent='المشاهدة داخل بالما مع الجودة والسرعة وملء الشاشة واستكمال الفيديو.';
    document.querySelector('label[for="mx-edition"]')?.remove();
    document.getElementById('mx-edition').remove();
    document.querySelector('.settings-list a[href="#/player-setup"]')?.remove();
    const about=[...document.querySelectorAll('.settings-list .setting-content')].find(x=>x.querySelector('h3')?.textContent==='حول بالما');
    if(about)about.querySelector('p').textContent='PALMA VISION · Windows';
  }
  document.getElementById('import-library').onchange=async e=>{try{const file=e.target.files[0];if(!file||file.size>4*1024*1024)throw Error('ملف النسخة غير صالح.');const j=JSON.parse(await file.text());if(j.app!=='PALMA VISION WEB'||!Array.isArray(j.favorites)||!j.progress||typeof j.progress!=='object'||Array.isArray(j.progress))throw Error('اختر ملف بيانات بالما ويب.');const favorites=j.favorites.filter(x=>x&&['palma','witcher','drama'].includes(x.provider)&&typeof x.id==='string').slice(0,250);writeStore('favorites',favorites);const progress=Object.fromEntries(Object.entries(j.progress).filter(([k,v])=>v?.item&&Number.isFinite(v.time)&&typeof v.episode==='string').slice(0,250));writeStore('progress',progress);toast('تم استيراد البيانات.');}catch(err){toast(err.message);}};
}
function playerSetup(){
  page(heading('AWR MX للكمبيوتر','المشغّل المتصل ببالما')+`<section class="info-panel"><h2>ابدأ المشاهدة على Windows</h2><ol><li>فك ضغط حزمة AWR MX V7 بالكامل في مجلد ثابت.</li><li>افتح <bdi>AWR-MX-Desktop.exe</bdi> مرة واحدة لتفعيل الربط.</li><li>ارجع إلى بالما، اختر السيرفر، ثم اضغط «فتح AWR MX» ووافق على طلب المتصفح.</li></ol><p>تتضمن الحزمة محرك الفيديو. يُحفظ تقدّم كل حلقة داخل المشغّل حتى عند تغيير السيرفر أو إغلاق الموقع.</p><p>إذا نقلت مجلد المشغّل، افتح ملفه مرة أخرى لتحديث الربط.</p><a class="btn primary" href="/downloads/AWR-MX-Desktop-Windows-v7.zip" download>${icon('download')} تنزيل AWR MX V7 لويندوز</a><a class="btn outline" href="#/settings">اختيار مشغّل المشاهدة</a></section>`);
}
function exportLibrary(){const j={app:'PALMA VISION WEB',version:1,favorites:readStore('favorites',[]),progress:readStore('progress',{})};const url=URL.createObjectURL(new Blob([JSON.stringify(j,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='palma-vision-library.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function applyTheme(){document.documentElement.dataset.theme=readStore('theme','dark');}
function newsHTML(news){return news.length?`<section class="section"><div class="section-head"><h2>أخبار الأنمي</h2></div><div class="news-grid">${news.filter(n=>safeURL(n.url)).map(n=>`<a class="news-card" href="${esc(safeURL(n.url))}" target="_blank" rel="noopener noreferrer">${img(n.image,'')}<div><h3>${esc(n.title)}</h3><small>${n.date?esc(new Date(n.date).toLocaleDateString('ar')):''}</small></div></a>`).join('')}</div></section>`:'';}
async function news(token){page(heading('أخبار الأنمي')+skeleton());try{const j=await api('witcher','home',{},controller.signal);if(active(token))page(heading('أخبار الأنمي')+newsHTML(list(j.news)));}catch(e){if(active(token)&&e.name!=='AbortError')page(errorState(e,()=>navigate(),false));}}
async function matches(p,token){
  const date=p.get('date')||new Date().toISOString().slice(0,10);page(heading('مباريات اليوم')+`<div class="filters"><input type="date" id="match-date" value="${esc(date)}" aria-label="تاريخ المباريات"></div><div id="matches-body"><span class="spinner"></span></div>`);
  document.getElementById('match-date').onchange=e=>{location.hash=link('matches',{date:e.target.value});};
  try{const raw=await api('palma','api/matches/',{date},controller.signal);if(!active(token))return;const groups=matchGroups(raw);document.getElementById('matches-body').innerHTML=groups.length?groups.map(group=>`<section class="section"><div class="section-head"><h2>${esc(group.league_name||group.name||group.league?.name||'المباريات')}</h2></div>${list(group.matches).map(m=>matchCard({...m,league_name:m.league_name||group.league_name||group.name})).join('')}</section>`).join(''):empty('لا توجد مباريات في هذا التاريخ.');}catch(e){if(active(token)&&e.name!=='AbortError')document.getElementById('matches-body').innerHTML=errorState(e,()=>navigate());}
}
async function matchDetail(p,token){
  page(heading('تفاصيل المباراة')+skeleton());
  try{
    const id=p.get('id'),raw=data(await api('palma','api/matches/show.php',{id},controller.signal));if(!active(token))return;
    const title=[raw.home_team?.name||raw.home_team_name,raw.away_team?.name||raw.away_team_name].filter(Boolean).join(' × ');
    const meta=[['التاريخ',first(raw,'match_date','date')],['الوقت',raw.match_time],['الملعب',first(raw,'venue','stadium')],['الحكم',raw.referee]].filter(([,value])=>value);
    page(heading(title||'تفاصيل المباراة',raw.league_name||raw.league?.name||'',`<a class="btn outline" href="${link('matches',{date:raw.match_date})}">كل المباريات</a>`)+matchCard(raw,true)+`<div class="metadata-grid">${meta.map(([label,value])=>`<div><small>${esc(label)}</small>${esc(value)}</div>`).join('')}</div><div id="match-streams"></div><div id="match-videos"><span class="spinner"></span></div>`);
    const channels=list(raw.channels).map(matchChannel).filter(x=>x.id);
    document.getElementById('match-streams').innerHTML=channels.length?rail('القنوات الناقلة',channels.map(x=>card(x,'palma','channel'))):empty('لم يحدد المصدر قناة ناقلة لهذه المباراة بعد.');
    try{
      const videos=list(await api('palma','api/matches/event_videos.php',{match_id:id,page:1,limit:100},controller.signal));if(!active(token))return;
      document.getElementById('match-videos').innerHTML=videos.length?`<section class="section"><div class="section-head"><h2>الملخص والأهداف</h2></div><div class="highlight-grid">${videos.map(v=>`<button class="highlight-card" data-action="${action(()=>resolveServer({id:String(v.id),provider:'palma',kind:'match-video',matchId:id,title:v.title||title},'',0,false,controller.signal))}">${img(v.thumbnail,title)}<span>${icon('play')} ${esc(v.title||'مشاهدة الفيديو')}</span></button>`).join('')}</div></section>`:empty('لا توجد فيديوهات لهذه المباراة حاليًا.');
    }catch(e){if(active(token)&&e.name!=='AbortError')document.getElementById('match-videos').innerHTML=errorState(e,()=>navigate());}
  }catch(e){if(active(token)&&e.name!=='AbortError')page(errorState(e,()=>navigate()));}
}

function notifications(){page(heading('الإشعارات')+empty('إشعارات التطبيق غير متصلة بنسخة الويب حالياً.'));}
function telegramPage(){page(heading('تليجرام')+empty('لا يوجد رابط قناة مضاف لبالما حاليًا.'));}
function requestPage(){
  const draft=readStore('request-draft',{});
  page(heading('طلب مسلسل/فيلم')+`<form id="request-form" class="info-panel"><p>خدمة إرسال الطلبات غير متصلة حالياً. يمكنك تجهيز الطلب وحفظه على هذا الجهاز.</p><label>نوع العمل<select name="kind"><option value="series">مسلسل</option><option value="movie">فيلم</option></select></label><label>اسم العمل<input name="title" required maxlength="160" value="${esc(draft.title||'')}" placeholder="اسم المسلسل أو الفيلم"></label><label>ملاحظات<textarea name="notes" maxlength="2000" rows="4">${esc(draft.notes||'')}</textarea></label><button class="btn primary" type="submit">حفظ مسودة الطلب</button><span id="request-status" role="status"></span></form>`);
  const form=document.getElementById('request-form');form.elements.kind.value=draft.kind||'series';form.onsubmit=e=>{e.preventDefault();writeStore('request-draft',Object.fromEntries(new FormData(form)));document.getElementById('request-status').textContent='حُفظت المسودة على هذا الجهاز؛ لم يُرسل الطلب.';};
}

async function navigate(){
  controller.abort();controller=new AbortController();generation++;const token=generation;clearInterval(heroTimer);clearTimeout(searchTimer);actions=new Map();
  const raw=location.hash.replace(/^#\/?/,'')||'home',name=raw.split('?')[0],p=new URLSearchParams(raw.split('?')[1]);activeView=name;shell(name);drawer(false);window.scrollTo(0,0);document.title=`${labels[name]||'PALMA VISION'} | PALMA VISION`;
  try{
    if(name==='home')await home(token);
    else if(name==='anime')await world(p,token);
    else if(nativeRoutes[name])await nativeCatalog(name,p,token);
    else if(name==='world')await world(p,token);
    else if(name==='detail')await detail(p,token);
    else if(name==='servers')await serversPage(p,token);
    else if(name==='search')await search(p,token);
    else if(['favorites','history','downloads'].includes(name))library(name);
    else if(name==='settings')settings();
    else if(name==='player-setup')playerSetup();
    else if(name==='news')await news(token);
    else if(name==='notifications')await notifications(token);
    else if(name==='request')requestPage();
    else if(name==='telegram')telegramPage();
    else if(name==='matches')await matches(p,token);
    else if(name==='match')await matchDetail(p,token);
    else page(heading('الصفحة غير موجودة')+`<a class="btn primary" href="#/home">العودة للرئيسية</a>`);
  }catch(e){if(active(token)&&e.name!=='AbortError')page(errorState(e,()=>navigate()));}
}
applyTheme();window.addEventListener('hashchange',navigate);navigate();
