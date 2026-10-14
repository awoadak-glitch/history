export function matchGroups(value) {
  const data=value?.data ?? value;
  if (Array.isArray(data?.leagues)) return data.leagues;
  const matches=Array.isArray(data)?data:data?.matches||data?.items||[];
  return matches.length?[{league_name:'المباريات',matches}]:[];
}

export function matchChannel(value) {
  // MatchChannel.id identifies the association, not the playable Channel.
  return {...value,id:value.channel_id ?? (value.is_own_channel ? null : value.id),name:value.channel_name||value.name,logo:value.channel_logo||value.logo};
}

export function people(value) {
  return (Array.isArray(value)?value:[]).filter(x=>x&&x.id).sort((a,b)=>(a.order_index||0)-(b.order_index||0));
}

export function artwork(value) {
  const values=Array.isArray(value)?value:[...(value?.backdrops||[]),...(value?.posters||[])];
  return values.map(x=>typeof x==='string'?x:x.url||x.file_path||x.image).filter(Boolean);
}
