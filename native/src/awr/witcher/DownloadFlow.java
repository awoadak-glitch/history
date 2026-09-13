package awr.witcher;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import java.util.*;

/** Download handoff kept separate from playback. Uses the already-resolved media URL. */
final class DownloadFlow {
    static final String TDM="com.tdm.manager";
    private static final String[] IDM={"idm.internet.download.manager.plus","idm.internet.download.manager","idm.internet.download.manager.adm.lite","com.dv.adm"};
    private DownloadFlow(){}

    static void open(Activity a,String url,Map<String,String> headers,String title,boolean segmented){
        String direct=segmented?"تحميل مباشر بواسطة التطبيق (غير متاح لـ HLS)":"تحميل مباشر بواسطة التطبيق";
        String[] choices={"تحميل سريع ⚡ TDM",direct,"تحميل خارجي بواسطة 1DM"};
        new AlertDialog.Builder(a).setTitle("خيارات التنزيل!").setItems(choices,(d,which)->{
            if(which==0){if(!start(a,tdmIntent(url,headers,title,segmented)))missingTdm(a);return;}
            if(which==1){if(segmented){alert(a,"هذا الرابط بث HLS متجزئ. استخدم TDM أو 1DM حتى يتم تنزيله كاملاً.");return;}internal(a,url,headers,title);return;}
            if(!startIdm(a,url,headers,title,segmented))missingIdm(a);
        }).setNegativeButton("إلغاء",null).show();
    }

    static Intent tdmIntent(String url,Map<String,String> headers,String title,boolean segmented){
        Intent i=new Intent(Intent.ACTION_VIEW).setPackage(TDM).setDataAndType(Uri.parse(url),segmented?"application/x-mpegURL":"video/*");
        addCommon(i,headers,title);i.putExtra("secure_uri",true);return i;
    }

    private static Intent idmIntent(String packageName,String url,Map<String,String> headers,String title,boolean segmented){
        Intent i=new Intent(Intent.ACTION_VIEW).setPackage(packageName).setDataAndType(Uri.parse(url),segmented?"application/x-mpegURL":"video/*");
        addCommon(i,headers,title);return i;
    }

    private static void addCommon(Intent i,Map<String,String> headers,String title){
        String referer=headers.get("Referer"),cookie=headers.get("Cookie"),ua=headers.get("User-Agent");
        if(referer!=null&&!referer.isEmpty())i.putExtra("Referer",referer);
        if(ua!=null&&!ua.isEmpty())i.putExtra("user_agent",ua);
        if(cookie!=null&&!cookie.isEmpty()){
            i.putExtra("Cookie",cookie);i.putExtra("Cookies",cookie);i.putExtra("cookie",cookie);i.putExtra("cookies",cookie);
        }
        ArrayList<String> flat=new ArrayList<>();for(Map.Entry<String,String> e:headers.entrySet()){flat.add(e.getKey());flat.add(e.getValue());}
        i.putExtra("headers",flat.toArray(new String[0]));i.putExtra("title",title);i.putExtra("com.android.extra.filename",safe(title)+extension(i.getDataString()));
    }

    private static boolean startIdm(Activity a,String url,Map<String,String> headers,String title,boolean segmented){
        for(String p:IDM)if(start(a,idmIntent(p,url,headers,title,segmented)))return true;return false;
    }
    private static boolean start(Activity a,Intent i){try{a.startActivity(i);return true;}catch(ActivityNotFoundException e){return false;}catch(Exception e){return false;}}

    private static void internal(Activity a,String url,Map<String,String> h,String title){
        if(Build.VERSION.SDK_INT<=28&&Build.VERSION.SDK_INT>=23&&a.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            a.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},831);Toast.makeText(a,"بعد منح الإذن، اضغط سيرفر التنزيل مجدداً.",Toast.LENGTH_LONG).show();return;
        }
        try{
            String filename=safe(title)+"-"+System.currentTimeMillis()+extension(url);
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url)).setTitle(title).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,filename);
            for(Map.Entry<String,String> entry:h.entrySet())request.addRequestHeader(entry.getKey(),entry.getValue());
            ((DownloadManager)a.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);Toast.makeText(a,"بدأ التنزيل",Toast.LENGTH_SHORT).show();
        }catch(Exception e){alert(a,"تعذر بدء التنزيل. تحقق من الإذن والمساحة المتاحة.");}
    }

    private static String safe(String title){String s=title==null?"video":title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();if(s.length()>110)s=s.substring(0,110);return s.isEmpty()?"video":s;}
    private static String extension(String url){
        String path=Uri.parse(url==null?"":url).getPath();String lower=path==null?"":path.toLowerCase(Locale.ROOT);
        for(String ext:new String[]{".mkv",".webm",".mov",".mp4",".ts",".m3u8"})if(lower.endsWith(ext))return ext;return ".mp4";
    }
    private static void missingTdm(Activity a){
        new AlertDialog.Builder(a).setMessage("تطبيق TDM غير مثبت.").setNegativeButton("إلغاء",null).setPositiveButton("فتح صفحة TDM",(d,w)->start(a,new Intent(Intent.ACTION_VIEW,Uri.parse("https://upd.traidmod.com/go?id="+TDM)))).show();
    }
    private static void missingIdm(Activity a){
        new AlertDialog.Builder(a).setMessage("لم يتم العثور على 1DM أو ADM.").setNegativeButton("إلغاء",null).setPositiveButton("فتح 1DM",(d,w)->{
            if(!start(a,new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=idm.internet.download.manager"))))start(a,new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=idm.internet.download.manager")));
        }).show();
    }
    private static void alert(Activity a,String message){if(!a.isFinishing()&&!a.isDestroyed())new AlertDialog.Builder(a).setMessage(message).setPositiveButton("حسناً",null).show();}
}
