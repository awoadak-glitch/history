// MainActivity's ScrollableBottomBar, with Host.integrate's Source World entry at index 2.
export const mainNavigation = [
  ['home','الرئيسية','home'], ['series','المسلسلات','series'],
  ['world','عالم المصادر','world'], ['movies','الأفلام','movie'],
  ['channels','القنوات','channel'], ['matches','المباريات','matches'],
  ['anime','أنمي','anime'], ['wrestling','المصارعة','wrestling'],
  ['favorites','المفضلة','heart'], ['search','بحث','search'], ['settings','الإعدادات','settings'],
];
// res/layout/layout_drawer.xml. Additional web library tools live on their own pages.
export const drawerNavigation = [
  ['home','الرئيسية','home'], ['series','المسلسلات','series'],
  ['movies','الأفلام','movie'], ['anime','الأنمي','anime'],
  ['channels','القنوات','channel'], ['matches','مباريات اليوم','matches'],
  ['wrestling','مصارعة حرة','wrestling'], ['search','البحث','search'],
  ['favorites','المفضلة','heart'], ['request','طلب مسلسل/فيلم','download'],
  ['settings','الإعدادات','settings'], ['share','مشاركة التطبيق','share'], ['telegram','تليجرام','telegram'],
];
export const worldNavigation = [
  ['home','الرئيسية','back'], ['world','الأنمي','anime'],
  ['world?tab=series','المسلسلات','series'], ['world?tab=movies','الأفلام','movie'],
  ['world?tab=channels','القنوات','channel'],
];
export function navigationState(view, params=new URLSearchParams()) {
  const world=view==='world'||params.get('area')==='world'||(params.get('area')!=='main'&&(['witcher','drama'].includes(params.get('provider'))||params.get('source')==='witcher'||view==='news'));
  if(!world)return {world:false,active:['detail','servers'].includes(view)?(params.get('provider')==='witcher'?'anime':{movie:'movies',channel:'channels',series:'series',anime:'anime',wrestling:'wrestling'}[params.get('kind')]||view):view,tabs:mainNavigation};
  const kind=params.get('tab')||(params.get('provider')==='drama'?{series:'series',movie:'movies',channel:'channels'}[params.get('kind')]:'witcher');
  return {world:true,active:kind&&kind!=='witcher'?'world?tab='+kind:'world',tabs:worldNavigation};
}
