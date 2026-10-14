import {app,BrowserWindow,Menu,protocol,session,shell,dialog} from 'electron';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {APP_ORIGIN,createAppHandler} from './bridge.mjs';
import catalog from './web/api/catalog.js';
import resolve from './web/api/resolve.js';
import media from './web/api/media.js';
import {fetchPublic,unticket} from './web/server/http.js';
import {createDownloadManager} from './download-manager.mjs';
import {allowAppPermission} from './permissions.mjs';

const root=fileURLToPath(new URL('./web/public/',import.meta.url));
const selfTest=process.argv.includes('--self-test');
protocol.registerSchemesAsPrivileged([{scheme:'palma',privileges:{standard:true,secure:true,supportFetchAPI:true,corsEnabled:true,stream:true}}]);
app.setAppUserModelId('com.palma.vision.desktop');
if(!selfTest&&!app.requestSingleInstanceLock()){app.quit();}else{
  let window;
  app.on('second-instance',()=>{if(window){if(window.isMinimized())window.restore();window.focus();}});
  app.whenReady().then(async()=>{
    session.defaultSession.setPermissionRequestHandler((contents,permission,callback,details)=>callback(contents===window?.webContents&&allowAppPermission(permission,details.requestingUrl||contents.getURL(),details.isMainFrame)));
    session.defaultSession.setPermissionCheckHandler((contents,permission,origin,details)=>contents===window?.webContents&&allowAppPermission(permission,details.requestingUrl||origin,details.isMainFrame));
    const handler=createAppHandler({root,handlers:{'/api/catalog':catalog,'/api/resolve':resolve,'/api/media':media}});
    let testHandler,downloads;
    protocol.handle('palma',request=>{
      if(new URL(request.url).pathname==='/api/desktop/downloads')return downloads?.handle(request)||Response.json({error:'جاري بدء التطبيق.'},{status:503});
      return testHandler?.(request)||handler(request);
    });
    window=new BrowserWindow({width:1440,height:940,minWidth:900,minHeight:640,show:false,backgroundColor:'#0c1422',title:'PALMA VISION',icon:path.join(root,'assets/palma-logo.png'),autoHideMenuBar:true,webPreferences:{nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true}});
    const external=url=>{try{const u=new URL(url);if(u.protocol==='https:'&&!u.username&&!u.password)shell.openExternal(u.href).catch(()=>{});}catch{}};
    window.webContents.setWindowOpenHandler(({url})=>{
      if(url.startsWith(APP_ORIGIN+'/api/media?')&&new URL(url).searchParams.get('download')==='1')window.webContents.downloadURL(url);
      else external(url);
      return {action:'deny'};
    });
    window.webContents.on('will-navigate',(event,url)=>{if(!url.startsWith(APP_ORIGIN+'/')){event.preventDefault();external(url);}});
    window.webContents.on('will-attach-webview',event=>event.preventDefault());
    const executable=process.platform==='win32'?'ffmpeg.exe':'ffmpeg';
    const ffmpeg=app.isPackaged?path.join(process.resourcesPath,'ffmpeg',executable):fileURLToPath(new URL('../node_modules/ffmpeg-static/'+executable,import.meta.url));
    downloads=await createDownloadManager({app,window,dialog,shell,resolve,unticket,fetchResource:fetchPublic,ffmpeg});
    let quitting=false;
    window.on('close',event=>{if(downloads.active()&&!quitting){event.preventDefault();app.quit();}});
    app.on('before-quit',async event=>{
      if(quitting)return;event.preventDefault();
      if(downloads.active()){
        const answer=await dialog.showMessageBox(window,{type:'question',title:'تنزيلات جارية',message:'توجد تنزيلات جارية. هل تريد إلغاءها وإغلاق بالما؟',buttons:['متابعة التنزيل','إلغاء التنزيلات والخروج'],defaultId:0,cancelId:0});
        if(answer.response===0)return;
      }
      quitting=true;await downloads.shutdown();app.quit();
    });
    session.defaultSession.on('will-download',(_event,item,contents)=>{
      if(contents!==window.webContents){item.cancel();return;}
      item.on('updated',()=>{const total=item.getTotalBytes();window.setProgressBar(total>0?item.getReceivedBytes()/total:2);});
      item.once('done',(_event,state)=>{window.setProgressBar(-1);if(state==='interrupted')dialog.showErrorBox('تعذر التنزيل','انقطع اتصال المصدر. جهّز الرابط من جديد وأعد المحاولة.');});
    });
    Menu.setApplicationMenu(Menu.buildFromTemplate([
      {label:'بالما',submenu:[{label:'الرئيسية',click:()=>window.loadURL(APP_ORIGIN+'/#/home')},{type:'separator'},{label:'إغلاق',role:'quit'}]},
      {label:'عرض',submenu:[{label:'تحديث',role:'reload'},{label:'ملء الشاشة',role:'togglefullscreen'},{label:'تكبير',role:'zoomin'},{label:'تصغير',role:'zoomout'},{label:'الحجم الأصلي',role:'resetzoom'}]}
    ]));
    if(selfTest){
      window.webContents.setBackgroundThrottling(false);
      try{const {runSelfTest}=await import('./self-test.mjs');await runSelfTest({window,handler,ffmpeg,fetchResource:fetchPublic,unticket,setTestHandler:value=>{testHandler=value;}});app.exit(0);}
      catch(error){console.error('Desktop self-test failed:',error.message);app.exit(1);}
      return;
    }
    window.once('ready-to-show',()=>window.show());
    await window.loadURL(APP_ORIGIN+'/#/home');
  }).catch(error=>{dialog.showErrorBox('تعذر فتح بالما',error.message);app.quit();});
  app.on('window-all-closed',()=>app.quit());
}
