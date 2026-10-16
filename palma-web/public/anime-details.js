// AWR's anime_info, character relations and characters_list are different
// records. Normalize them here without guessing dates or missing performers.
const text=value=>typeof value==='string'?value:typeof value==='number'?String(value):'';
const name=value=>typeof value==='object'&&value?text(value.ar||value.full||value.name||value.en||value.native):text(value);
const number=value=>value!==null&&value!==undefined&&value!==''&&Number.isFinite(Number(value))?Number(value):null;
const array=value=>Array.isArray(value)?value:[];
export function animeImage(value){
  const raw=typeof value==='object'&&value?value.large||value.medium||value.url||value.image_url||value.jpg?.image_url:value;
  try{const url=new URL(raw);return ['https:','http:'].includes(url.protocol)&&!url.username&&!url.password?url.href:'';}catch{return '';}
}
const picture=raw=>[raw.main_picture,raw.picture,raw.image,raw.poster,raw.images].map(animeImage).find(Boolean)||'';
const count=value=>{const n=number(value);return n!==null&&n>=0?n:null;};
const score=value=>{const n=number(value);return n!==null&&n>0&&n<=10?n:null;};
const names=value=>[...new Set((Array.isArray(value)?value:[value]).map(name).filter(Boolean))];

export function animeInfo(raw={}){
  const d=raw.details||{},state=name(d.state||raw.status||d.status),finished=/مكتمل|منتهي|finished|completed/i.test(state);
  let airing=number(raw.nextEpTimeInSec??raw.nextAiringEpisode?.airingAt);
  if(airing!==null&&airing>1e12)airing/=1000;
  if(airing!==null&&(airing<=0||!Number.isFinite(new Date(airing*1000).getTime())))airing=null;
  const video=text(raw.youtube_video_id||raw.trailer?.youtube_video_id||raw.trailer?.video_id);
  const youtube=/^[A-Za-z0-9_-]{11}$/.test(video)?video:'';
  return {
    title:name(raw.name)||text(raw.title)||'أنمي',english:name(d.english_title||raw.english_name||d.english_name),
    aliases:names(raw.other_names),season:name(d.season||raw.season),status:state,finished,
    type:name(raw.type),episodes:text(d.eps_num||raw.episodes_count||raw.episode_count),duration:count(raw.duration),
    source:name(d.source||raw.source),studios:names(d.studio||raw.studios),age:name(d.age||raw.age_rating),
    start:text(d.start_date),end:text(d.end_date),year:text(d.year||raw.year),showTime:name(d.show_time||raw.show_time||raw.broadcast_note),
    rating:score(raw.rating?.rate??raw.average_rate??raw.rate),ratingCount:count(raw.rating?.totalRatingsCount),
    malScore:score(d.mal_mean),malRank:count(d.mal_rank),malUsers:count(d.mal_num_scoring_users),malId:text(raw.mal_id||d.mal_id),
    favorites:count(raw.statictes?.fav_count),nextAt:finished?null:airing,
    nextNumber:count(raw.next_episode_number??raw.nextAiringEpisode?.episode),youtube,
  };
}

export function episodeCountdown(info,now=Date.now()){
  if(info.finished)return {state:'finished',label:'اكتمل عرض هذا الأنمي',remaining:0,parts:[]};
  if(!info.nextAt)return {state:'unknown',label:'لم يحدد المصدر موعد الحلقة القادمة',remaining:0,parts:[]};
  const remaining=Math.max(0,Math.ceil(info.nextAt-now/1000));
  if(!remaining)return {state:'elapsed',label:'انتهى الموعد المعلن؛ ننتظر تحديث المصدر',remaining:0,parts:[]};
  return {state:'scheduled',label:info.nextNumber?`الحلقة ${info.nextNumber} خلال`:'الحلقة القادمة خلال',remaining,
    parts:[Math.floor(remaining/86400),Math.floor(remaining%86400/3600),Math.floor(remaining%3600/60),remaining%60]};
}

export function animeCharacters(records){
  const seen=new Set();
  return array(records).map(raw=>{
    const info=raw.character_info||raw.character||{},value={...info,...raw},data=value.data||{};
    const id=text(raw.character_id||info.id||info.objectID||raw.id||raw.objectID),title=name(raw.name||info.name||raw.character_name);
    const role=text(raw.role||raw.character_role||info.role),main=/main|رئيس/i.test(role);
    const voices=array(value.voices||value.voice_actors||value.voiceActors||value.seiyuu||data.voices).map(v=>{
      const person=v.person||v.voice_actor||v;
      return {id:text(person.id||person.mal_id),name:name(person.name),image:picture(person),language:name(v.language||person.language)};
    }).filter(v=>v.name).sort((a,b)=>Number(b.language==='Japanese')-Number(a.language==='Japanese'));
    return {id,name:title||name(data.name),image:picture(raw)||picture(info)||picture(data),role:main?'شخصية رئيسية':/support|مساعد/i.test(role)?'شخصية مساعدة':role,main,
      likes:count(value.fav_count??value.favorites??value.likes??value.statictes?.fav_count??data.favorites),description:name(value.description||value.about||value.bio||data.about),voices};
  }).filter(c=>{const key=c.id||c.name;if(!key||!c.name||seen.has(key))return false;seen.add(key);return true;}).sort((a,b)=>Number(b.main)-Number(a.main));
}

export function animeFacts(info){
  return [['النوع',info.type],['عدد الحلقات',info.episodes],['مدة الحلقة',info.duration?`${info.duration} دقيقة`:''],['الحالة',info.status],
    ['موسم العرض',info.season],['الفئة العمرية',info.age],['المصدر الأصلي',info.source],['الاستوديو',info.studios.join('، ')],
    ['بداية العرض',info.start||info.year],['نهاية العرض',info.end],['موعد العرض الأسبوعي',info.showTime],['العنوان الإنجليزي',info.english]]
    .filter(([,value])=>value);
}
