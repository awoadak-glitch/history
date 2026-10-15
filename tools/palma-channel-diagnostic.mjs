import {catalogue} from '../palma-web/api/catalog.js';
import {playbackSource} from '../palma-web/server/source.js';
import {publicURL} from '../palma-web/server/http.js';
import {mediaHeadersForURL} from '../palma-web/server/source-session.js';

// Print only routing metadata. Never persist signed paths, query values or cookies.
const describe=raw=>{const u=new URL(raw);return {protocol:u.protocol,host:u.hostname,port:u.port||'default',userinfo:!!(u.username||u.password)};};
const channel=(await catalogue('palma','api/channels/show.php',{id:'63'})).data;
for(const [index,source] of channel.streams.entries()) {
  const playback=playbackSource(source,{provider:'palma'});
  let url=playback.url,headers=playback.headers;
  const report={channel:63,index,quality:source.quality,hops:[]};
  for(let hop=0;hop<4;hop++) {
    const item={...describe(url)};report.hops.push(item);
    try{await publicURL(url);}catch(error){item.rejected=error.code||'NETWORK_ERROR';break;}
    headers=mediaHeadersForURL(url,headers);
    try {
      const response=await fetch(url,{headers:Object.fromEntries(Object.entries(headers)),redirect:'manual',signal:AbortSignal.timeout(20000)});
      Object.assign(item,{status:response.status,content_type:response.headers.get('content-type'),source_session:!!headers.Cookie});
      const location=response.headers.get('location');await response.body?.cancel();
      if(![301,302,303,307,308].includes(response.status)||!location)break;
      url=new URL(location,url).href;
    }catch(error){item.error=error.name;break;}
  }
  console.log(JSON.stringify(report));
}
