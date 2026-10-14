// Native home response semantics, shared by rendering and regression checks.
export function sectionKind(type='') {
  if (/latest.*episodes/.test(type)) return 'episode';
  if (/movie/.test(type)) return 'movie';
  if (/anime/.test(type)) return 'anime';
  if (/channel/.test(type)) return 'channel';
  if (/actor/.test(type)) return 'actor';
  if (/wrestling/.test(type)) return 'wrestling';
  return 'series';
}

export function orderedSections(sections=[]) {
  return [...sections].sort((a,b)=>(Number(a.display_order)||0)-(Number(b.display_order)||0));
}

export function sectionQuery(section) {
  const query={sort_by:section.sort_by||'newest'};
  if (Number(section.content_category_filter)>0) query.category=String(section.content_category_filter);
  for (const [key,value] of Object.entries(section.content_filters||{})) {
    if (['genre_id','language','country','year','content_type','age_rating','category','status','primary_category_id'].includes(key) && value != null && value!=='') query[key]=String(value);
  }
  return query;
}
