import {cp,mkdir,readFile,rm,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {prepareVendors} from '../../palma-web/scripts/vendor.mjs';
await prepareVendors();
const root=fileURLToPath(new URL('../',import.meta.url)),out=path.join(root,'build/runtime');
await rm(out,{recursive:true,force:true});await mkdir(out,{recursive:true});
await cp(path.join(root,'runtime'),out,{recursive:true});
for(const name of ['public','api','server'])await cp(path.join(root,'../palma-web',name),path.join(out,'web',name),{recursive:true,filter:p=>!p.split(path.sep).includes('generated')});
await cp(path.join(root,'public'),path.join(out,'web/public'),{recursive:true});
await cp(path.join(root,'licenses'),path.join(out,'licenses'),{recursive:true});
await cp(path.join(root,'../palma-windows/src/download-core.mjs'),path.join(out,'download-core.mjs'));
await writeFile(path.join(out,'package.json'),JSON.stringify({type:'module',version:'1.0.0'}));
async function patch(name,changes){const file=path.join(out,'web/public',name);let source=await readFile(file,'utf8');for(const [before,after]of changes){if(source.split(before).length!==2)throw Error(`TV overlay drift in ${name}: ${before.slice(0,60)}`);source=source.replace(before,after);}await writeFile(file,source);}
await patch('index.html',[
  ['data-theme="dark"','data-theme="dark" data-tv="true"'],
  ['<script type="module" src="/app.js"></script>','<link rel="stylesheet" href="/tv.css"><script type="module" src="/tv.js"></script><script type="module" src="/app.js"></script>'],
  ['<title>PALMA VISION | بالما</title>','<title>PALMA TV | بالما</title>'],
]);
await patch('player.js',[
  ["import {startDesktopDownload}","import {startTVDownload} from './tv-downloads.js';\nimport {startDesktopDownload}"],
  ["if(download&&location.protocol==='palma:')return startDesktopDownload(item,episode,index);","if(download)return startTVDownload(item,episode,index);"],
  ["const target=location.protocol==='palma:'?'browser':playbackTarget(readStore('playback-target','auto'),navigator.userAgent);","const target='browser';"],
]);
await patch('app.js',[
  ["import {renderDesktopDownloads}","import {renderTVDownloads} from './tv-downloads.js';\nimport {renderDesktopDownloads}"],
  ["function drawer(open){","function drawer(open){document.getElementById('sidebar').inert=false;return;"],
  ["if(view==='downloads'&&location.protocol==='palma:'){renderDesktopDownloads", "if(view==='downloads'){renderTVDownloads"],
  ["if(location.protocol==='palma:'){","if(true){"],
  ["PALMA VISION · Windows","PALMA TV · Android TV / Google TV · 1.0.0"],
  ['حفظ موضع الفيديو داخل المتصفح. المشغّل الخارجي يحفظ تقدّمك داخله.','يحفظ بالما موضع الفيديو على هذا التلفاز لاستكمال المشاهدة.'],
  ['محفوظان على هذا المتصفح، ولا تتم مزامنتهما مع حساب Android','محفوظان على هذا التلفاز. يمكنك تصدير نسخة احتياطية أو استعادتها.'],
  ['استعادة بيانات الويب','استعادة المفضلة والسجل'],
  ['استيراد نسخة المفضلة وسجل المشاهدة التي صدّرتها من الموقع','استيراد نسخة المفضلة وسجل المشاهدة التي صدّرتها من بالما'],
  ['هل تريد مسح هذه البيانات المحفوظة على هذا المتصفح؟','هل تريد مسح هذه البيانات المحفوظة على هذا التلفاز؟'],
  ['اختر ملف بيانات بالما ويب.','اختر ملف بيانات بالما الذي صدّرته من الإعدادات.'],
  ["readStore('auto-banner',true)","false"],
  ['<label class="btn outline" for="import-library">','<label class="btn outline" for="import-library" tabindex="0" role="button">'],
  ["function exportLibrary(){","function exportLibrary(){if(window.PalmaTVNative){window.PalmaTVNative.exportLibrary(JSON.stringify({app:'PALMA VISION WEB',version:1,favorites:readStore('favorites',[]),progress:readStore('progress',{})}));return;}"],
]);
let navigation=await readFile(path.join(out,'web/public/navigation.js'),'utf8');
navigation=navigation.replace(/export const drawerNavigation = \[[\s\S]*?\];/,"export const drawerNavigation = [...mainNavigation.filter(x=>x[0]!=='settings'),['history','متابعة المشاهدة','clock'],['downloads','التنزيلات','download'],['collections','السلاسل','folder'],['actors','الممثلون','people'],['settings','الإعدادات','settings']];");
await writeFile(path.join(out,'web/public/navigation.js'),navigation);
await patch('playback.js',[["Space تشغيل / إيقاف · ضغط مطوّل 2× · الأسهم للتقديم والصوت · F ملء الشاشة","OK تشغيل / إيقاف · يمين / يسار ±10 ثوان · أعلى / أسفل لإظهار التحكم · رجوع للإغلاق"]]);
console.log('Prepared PALMA TV runtime and remote interface.');
