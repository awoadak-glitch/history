import {esc,icon,link,card,api,toggleFavorite,favorite,toast,modal} from './lib.js';
import {animeInfo,animeFacts,animeCharacters,episodeCountdown,animeImage} from './anime-details.js';

const count=value=>Number(value).toLocaleString('ar');
const photo=(url,alt,cls='')=>animeImage(url)?`<img src="${esc(animeImage(url))}" alt="${esc(alt)}" class="${cls}" loading="lazy" referrerpolicy="no-referrer">`:`<span class="anime-avatar-placeholder ${cls}" aria-hidden="true">${icon('people')}</span>`;
const dateAt=seconds=>new Intl.DateTimeFormat('ar',{weekday:'long',year:'numeric',month:'long',day:'numeric',hour:'numeric',minute:'2-digit',timeZoneName:'short'}).format(new Date(seconds*1000));

function scheduleMarkup(info){
  const state=episodeCountdown(info);
  return `<section class="anime-airing" data-airing-state="${state.state}" aria-label="موعد الحلقة القادمة"><div class="anime-airing-heading">${icon('clock')}<div><small>متابعة العرض</small><h2 data-airing-label>${esc(state.label)}</h2></div>${info.nextAt?'<span class="badge">وقت تقريبي</span>':''}</div><div class="anime-countdown" ${state.state==='scheduled'?'':'hidden'} role="timer" aria-label="الوقت المتبقي للحلقة">${['يوم','ساعة','دقيقة','ثانية'].map((label,i)=>`<div><strong data-countdown-part="${i}">${String(state.parts[i]||0).padStart(2,'0')}</strong><span>${label}</span></div>`).join('')}</div><p class="anime-airing-note">${info.nextAt?`<time datetime="${new Date(info.nextAt*1000).toISOString()}">${esc(dateAt(info.nextAt))}</time> · حسب توقيت جهازك. قد يتغير الموعد أو يتأخر توفر الترجمة.`:esc(info.finished?'يمكنك مشاهدة الحلقات المتاحة.':info.showTime?'يوم العرض المسجل: '+info.showTime:'سيظهر العد التنازلي عندما يعلن المصدر موعدًا.')}</p></section>`;
}

function scoresMarkup(info){
  const scores=[];
  if(info.rating)scores.push(`<div class="anime-score"><span>تقييم المتابعين</span><strong>${icon('star')} ${info.rating.toFixed(2)}<small>/ 10</small></strong>${info.ratingCount!==null?`<small>${count(info.ratingCount)} تقييم</small>`:''}</div>`);
  if(info.malScore||info.malRank)scores.push(`<div class="anime-score"><span>MyAnimeList</span>${info.malScore?`<strong>${icon('star')} ${info.malScore.toFixed(2)}<small>/ 10</small></strong>`:''}<small>${info.malRank?'الترتيب العالمي #'+count(info.malRank):''}${info.malUsers!==null?' · '+count(info.malUsers)+' تقييم':''}</small>${/^\d+$/.test(info.malId)?`<a href="https://myanimelist.net/anime/${info.malId}" target="_blank" rel="noopener noreferrer">صفحة العمل ${icon('external')}</a>`:''}</div>`);
  if(info.favorites!==null)scores.push(`<div class="anime-score"><span>في قوائم المتابعين</span><strong>${icon('heart')} ${count(info.favorites)}</strong><small>عدد الإضافات إلى المفضلة في المصدر</small></div>`);
  return scores.length?`<div class="anime-scores">${scores.join('')}</div>`:'';
}

export function animeDetailMarkup(item,raw){
  const info=animeInfo(raw),facts=animeFacts(info),tags=Array.isArray(item.tags)?item.tags:[];
  return `<article class="anime-detail-page"><section class="anime-detail-hero">${photo(item.cover||item.poster,'','anime-detail-backdrop')}<div class="anime-hero-shade"></div><a class="anime-back chip" href="${link(item.area==='main'?'anime':'world')}">${icon('back')} عالم الأنمي</a><div class="anime-identity">${photo(item.poster,item.title,'anime-detail-poster')}<div class="anime-title-block"><span class="anime-eyebrow">PALMA · ANIME</span><div class="chips">${[info.type,info.status,info.season||info.year].filter(Boolean).map(v=>`<span class="badge">${esc(v)}</span>`).join('')}</div><h1 dir="auto">${esc(item.title)}</h1>${info.english&&info.english!==item.title?`<p class="anime-english" dir="auto">${esc(info.english)}</p>`:''}<div class="anime-genres">${tags.map(v=>typeof v==='string'?v:v.name).filter(Boolean).map(v=>`<span>${esc(v)}</span>`).join('')}</div><div class="actions"><button class="btn primary" data-anime-watch>${icon('play')} الحلقات والمشاهدة</button><button class="btn outline" data-anime-favorite aria-pressed="${favorite(item)}">${icon('heart')}<span>${favorite(item)?'في المفضلة':'إضافة للمفضلة'}</span></button></div></div></div></section>
  <div class="anime-detail-body">${scheduleMarkup(info)}
  <div class="anime-detail-tabs" role="tablist" aria-label="تفاصيل الأنمي">${[['overview','التفاصيل'],['episodes','الحلقات'],['characters','الشخصيات'],['related','أعمال مرتبطة']].map(([id,label],i)=>`<button id="anime-tab-${id}" role="tab" aria-controls="anime-panel-${id}" aria-selected="${!i}" tabindex="${i?-1:0}" data-anime-tab="${id}">${label}${id==='characters'?'<span data-characters-count></span>':''}</button>`).join('')}</div>
  <section id="anime-panel-overview" role="tabpanel" aria-labelledby="anime-tab-overview" tabindex="0">${scoresMarkup(info)}<div class="anime-overview-grid"><section class="anime-story-panel"><span class="anime-section-kicker">عن العمل</span><h2>القصة</h2><p class="story" dir="auto">${esc(item.story||'لم يضف المصدر قصة لهذا الأنمي بعد.')}</p>${info.aliases.length?`<details class="anime-aliases"><summary>أسماء أخرى</summary><p dir="auto">${info.aliases.map(esc).join(' · ')}</p></details>`:''}${info.youtube?`<a class="anime-trailer" href="https://www.youtube.com/watch?v=${info.youtube}" target="_blank" rel="noopener noreferrer"><img src="https://img.youtube.com/vi/${info.youtube}/hqdefault.jpg" alt="" loading="lazy"><span>${icon('play')} العرض الدعائي ${icon('external')}</span></a>`:''}</section><section class="anime-facts-panel"><h2>معلومات الأنمي</h2><dl>${facts.map(([label,value])=>`<div><dt>${esc(label)}</dt><dd dir="auto">${esc(value)}</dd></div>`).join('')}</dl></section></div><section class="section"><div class="section-head"><h2>شخصيات العمل</h2><button class="chip" data-show-characters>عرض الشخصيات ${icon('forward')}</button></div><div data-character-preview><p class="muted">جارٍ تحميل الشخصيات…</p></div></section></section>
  <section id="anime-panel-episodes" role="tabpanel" aria-labelledby="anime-tab-episodes" tabindex="0" hidden><div id="detail-content"></div></section>
  <section id="anime-panel-characters" role="tabpanel" aria-labelledby="anime-tab-characters" tabindex="0" hidden><div data-character-list aria-live="polite"><div class="state"><span class="spinner"></span><p>جارٍ تحميل الشخصيات…</p></div></div></section>
  <section id="anime-panel-related" role="tabpanel" aria-labelledby="anime-tab-related" tabindex="0" hidden><div data-anime-related><p class="muted">جارٍ تحميل الأعمال المرتبطة…</p></div></section></div></article>`;
}

function characterCards(characters){
  return `<div class="anime-character-grid">${characters.map(c=>`<button class="anime-character-card" data-character-id="${esc(c.id||c.name)}" aria-label="تفاصيل ${esc(c.name)}"><div class="anime-character-image">${photo(c.image,c.name)}<span class="anime-character-role ${c.main?'main-role':''}">${esc(c.role||'شخصية')}</span></div><strong dir="auto">${esc(c.name)}</strong>${c.likes!==null?`<small>${icon('heart')} ${count(c.likes)}</small>`:''}${c.voices.length?`<span class="anime-voice-name">${esc(c.voices[0].name)}</span>`:''}</button>`).join('')}</div>`;
}

function characterDialog(character){
  const voices=character.voices;
  const languages={Japanese:'اليابانية',English:'الإنجليزية',Arabic:'العربية',French:'الفرنسية',German:'الألمانية',Spanish:'الإسبانية',Italian:'الإيطالية',Portuguese:'البرتغالية',Korean:'الكورية'};
  const dialog=modal(character.name,`<div class="anime-character-profile">${photo(character.image,character.name)}<div><span class="badge gold">${esc(character.role||'شخصية')}</span>${character.likes!==null?`<p>${count(character.likes)} إضافة للمفضلة</p>`:''}<p>مؤدو الأصوات بحسب لغة الأداء المسجلة في المصدر.</p></div></div>${voices.length?`<section class="anime-voices"><h3>مؤدو الأصوات</h3><div class="anime-voice-grid">${voices.map(v=>`<div>${photo(v.image,v.name)}<span><strong dir="auto">${esc(v.name)}</strong><small>${esc(languages[v.language]||v.language)}</small></span></div>`).join('')}</div></section>`:'<p class="anime-profile-note">لم يوفّر المصدر بيانات مؤدي الصوت لهذه الشخصية.</p>'}${character.description?`<details class="anime-character-bio"><summary>نبذة عن الشخصية · قد تتضمن أحداثًا من القصة</summary><p class="story" dir="auto">${esc(character.description)}</p></details>`:''}`);
  dialog.classList.add('anime-character-dialog');dialog.addEventListener('close',()=>dialog.classList.remove('anime-character-dialog'),{once:true});
}

export function bindAnimeDetail(root,item,raw,{signal,loadEpisodes,renderRail}){
  const info=animeInfo(raw);let episodeLoad=null,characters=[],records=[],loadingExtras=false;
  const tabs=[...root.querySelectorAll('[data-anime-tab]')];
  function select(id,scroll=false){
    for(const tab of tabs){const chosen=tab.dataset.animeTab===id;tab.setAttribute('aria-selected',String(chosen));tab.tabIndex=chosen?0:-1;root.querySelector('#'+tab.getAttribute('aria-controls')).hidden=!chosen;}
    if(id==='episodes'&&!episodeLoad)episodeLoad=Promise.resolve(loadEpisodes());
    if(scroll)root.querySelector('.anime-detail-tabs').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'start'});
  }
  tabs.forEach((tab,index)=>{
    tab.onclick=()=>select(tab.dataset.animeTab);
    tab.onkeydown=event=>{let next;if(event.key==='ArrowLeft')next=(index+1)%tabs.length;else if(event.key==='ArrowRight')next=(index+tabs.length-1)%tabs.length;else if(event.key==='Home')next=0;else if(event.key==='End')next=tabs.length-1;else return;event.preventDefault();tabs[next].focus();select(tabs[next].dataset.animeTab);};
  });
  root.querySelector('[data-anime-watch]').onclick=()=>select('episodes',true);
  root.querySelector('[data-show-characters]').onclick=()=>select('characters',true);
  root.querySelector('[data-anime-favorite]').onclick=event=>{const added=toggleFavorite(item),button=event.currentTarget;button.setAttribute('aria-pressed',String(added));button.querySelector('span').textContent=added?'في المفضلة':'إضافة للمفضلة';toast(added?'أضيف للمفضلة':'أزيل من المفضلة');};
  const schedule=root.querySelector('.anime-airing');
  const tick=()=>{const state=episodeCountdown(info);schedule.dataset.airingState=state.state;schedule.querySelector('[data-airing-label]').textContent=state.label;schedule.querySelector('.anime-countdown').hidden=state.state!=='scheduled';schedule.querySelectorAll('[data-countdown-part]').forEach((element,index)=>{element.textContent=String(state.parts[index]||0).padStart(2,'0');});if(state.state!=='scheduled')clearInterval(timer);};
  let timer=info.nextAt&&!info.finished?setInterval(tick,1000):null;
  signal.addEventListener('abort',()=>clearInterval(timer),{once:true});
  root.addEventListener('click',event=>{const button=event.target.closest('[data-character-id]');if(button){const character=characters.find(c=>(c.id||c.name)===button.dataset.characterId);if(character)characterDialog(character);}},{signal});
  async function loadExtras(page=0){
    if(loadingExtras||signal.aborted)return;
    loadingExtras=true;
    const target=root.querySelector('[data-character-list]'),preview=root.querySelector('[data-character-preview]'),related=root.querySelector('[data-anime-related]');
    const previousButton=target.querySelector('[data-more-characters]');
    if(previousButton){previousButton.disabled=true;previousButton.textContent='جارٍ التحميل…';}
    try{
      const extras=await api('witcher',`anime/${encodeURIComponent(item.id)}/extras`,{page},signal);if(signal.aborted||!root.isConnected)return;
      records=page?[...records,...(extras.characters||[])]:extras.characters||[];
      characters=animeCharacters(records);root.dataset.charactersLoaded='true';root.querySelector('[data-characters-count]').textContent=String(extras.total||characters.length||'');
      const main=characters.filter(c=>c.main),support=characters.filter(c=>!c.main);
      target.innerHTML=characters.length?(main.length?`<h2>الشخصيات الرئيسية</h2>${characterCards(main)}`:'')+(support.length?`<h2>الشخصيات المساعدة</h2>${characterCards(support)}`:''):'<div class="state"><p>لم يضف المصدر شخصيات لهذا الأنمي بعد.</p></div>';
      if(Number.isInteger(extras.nextPage)&&extras.nextPage>page){
        target.insertAdjacentHTML('beforeend',`<button class="btn outline anime-more-characters" data-more-characters>عرض المزيد من الشخصيات · ${count(characters.length)} من ${count(extras.total)}</button>`);
        target.querySelector('[data-more-characters]').onclick=()=>loadExtras(extras.nextPage);
      }
      preview.innerHTML=characters.length?characterCards(characters.slice(0,6)):'<p class="muted">لم يضف المصدر شخصيات لهذا الأنمي بعد.</p>';
      if(page===0){const entries=Array.isArray(extras.related)?extras.related:[];
        related.innerHTML=renderRail('أعمال مرتبطة',entries.map(value=>card({...value,area:item.area},'witcher','anime'))) || '<div class="state"><p>لا توجد أعمال مرتبطة متاحة من المصدر حاليًا.</p></div>';}
    }catch(error){
      if(signal.aborted)return;
      if(page&&previousButton){previousButton.disabled=false;previousButton.textContent='تعذر تحميل المزيد · إعادة المحاولة';return;}
      target.innerHTML='<div class="state"><p>تعذر تحميل الشخصيات الآن.</p><button class="btn outline" data-retry-characters>إعادة المحاولة</button></div>';
      preview.innerHTML='<p class="muted">تعذر تحميل الشخصيات. افتح تبويب الشخصيات لإعادة المحاولة.</p>';related.innerHTML='<p class="muted">تعذر تحميل الأعمال المرتبطة الآن.</p>';
      target.querySelector('[data-retry-characters]').onclick=()=>loadExtras();
    }finally{loadingExtras=false;}
  }
  loadExtras();
}
