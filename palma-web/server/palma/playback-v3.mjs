import {SourceError} from '../http.js';
import {PalmaV3Error} from './v3.mjs';

const unwrap=value=>value?.data&&!Array.isArray(value.data)?value.data:value;
const array=value=>Array.isArray(value)?value:[];
const restricted=value=>value!=null&&!['0',0,'false',false,'no'].includes(value);
export function assertPlaybackAllowed(value) {
  if(value?.update_required)throw new SourceError('مصدر المشاهدة يتطلب تحديث طريقة الاتصال. لا يمكن استخدام روابط الإصدار القديم.',503,'PALMA_UPDATE_REQUIRED');
  if(restricted(value?.premium))throw new SourceError('هذا السيرفر يحتاج حسابًا لدى المصدر.',403,'PREMIUM_SOURCE');
  for(const name of ['tdm_guard','tamper_guard','adblock_guard']) {
    const guard=value?.[name];
    if(guard?.action&&!['allow','none'].includes(guard.action))throw new SourceError('يتطلب المصدر تحققًا من الجهاز أو تطبيق التشغيل قبل المشاهدة.',403,'PALMA_PLAYER_VERIFICATION_REQUIRED');
  }
}

// V3 content types and routes come from the supplied 1.1.6 client. In particular,
// anime is an episode, while anime_movie is a film; they are not interchangeable.
export function playbackRoute({kind,id,episode,matchId}) {
  if(!/^\d+$/.test(String(id))||(episode&&!/^\d+$/.test(String(episode))))throw new SourceError('العمل غير صالح.',400);
  const cid=Number(episode||id);
  if(!Number.isSafeInteger(cid)||cid<=0)throw new SourceError('العمل غير صالح.',400);
  if(kind==='match-video') {
    const match=Number(matchId);
    if(!/^\d+$/.test(String(matchId))||!Number.isSafeInteger(match)||match<=0)throw new SourceError('المباراة غير صالحة.',400);
    return {route:'api/v3/matches/event-videos.php',type:'match_video',cid:match,videoId:cid};
  }
  if(episode&&kind!=='anime')return {route:'api/v3/episodes/show.php',type:'episode',cid};
  const types={movie:['movies','movie'],channel:['channels','channel'],anime:[episode?'anime/episodes':'anime',episode?'anime':'anime_movie'],wrestling:['wrestling','wrestling']};
  if(!types[kind])throw new SourceError('مسار المشاهدة الجديد غير متاح لهذا النوع بعد.',503,'PALMA_V3_ROUTE_UNSUPPORTED');
  const [folder,type]=types[kind];return {route:`api/v3/${folder}/show.php`,type,cid};
}

export function createV3Playback(client) {
  return async selection=>{
    try {
      const {index=0,download=false}=selection;
      if(!Number.isInteger(index)||index<0||index>300)throw new SourceError('اختيار السيرفر غير صالح.',400);
      const {route,type,cid,videoId}=playbackRoute(selection);
      const response=await client.get(route,videoId?{match_id:cid,page:1,limit:100}:{id:cid});
      const detail=unwrap(response);
      assertPlaybackAllowed(detail);
      const servers=videoId?array(response.data).filter(video=>Number(video.id)===videoId):array(download?(detail.download_links||detail.downloadLinks):(detail.watch_links||detail.watchLinks||detail.streams||detail.sources));
      const selected=servers[index];
      if(!selected)throw new SourceError('السيرفر غير متاح حاليًا.',404);
      assertPlaybackAllowed(selected);
      // A valid V3-issued link may already be present. Otherwise resolve the
      // selected link ID; an ID is never treated as a playable URL.
      if([selected.deep_link,selected.url,selected.stream_url,selected.video_url,selected.download_url,selected.link].some(link=>typeof link==='string'&&link.trim()))return {source:selected,servers};
      const lid=Number(selected.id);
      if(!Number.isSafeInteger(lid)||lid<=0)throw new SourceError('أعاد المصدر معرّف سيرفر غير صالح.',502,'PALMA_V3_LINK_INVALID');
      const resolved=unwrap(await client.resolve({t:type,cid,k:download?'download':'watch',lid}));
      assertPlaybackAllowed(resolved);
      if(!resolved?.deep_link&&!resolved?.url)throw new SourceError('لم يُرجع المصدر رابط مشاهدة.',502,'PALMA_V3_LINK_MISSING');
      return {source:{...selected,...resolved},servers};
    }catch(error) {
      if(error instanceof PalmaV3Error)throw new SourceError(error.message,error.status,error.code);
      throw error;
    }
  };
}
