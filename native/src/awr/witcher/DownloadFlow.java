package awr.witcher;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import java.util.*;

/** Download handoff kept separate from playback. Receives the already-resolved R0/a media URL. */
final class DownloadFlow {
    static final String TDM="com.tdm.manager";
    private static final String[] IDM={"idm.internet.download.manager","idm.internet.download.manager.plus","idm.internet.download.manager.adm.lite"};
    private static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";
    private DownloadFlow(){}

    static void open(Activity a,String url,Map<String,String> headers,String title,boolean segmented){
        String direct=segmented?"تحميل مباشر بواسطة التطبيق (غير متاح لـ HLS)":"تحميل مباشر بواسطة التطبيق";
        String[] choices={"تحميل سريع ⚡ TDM",direct,"تحميل خارجي بواسطة 1DM"};
        new AlertDialog.Builder(a).setTitle("خيارات التنزيل!").setItems(choices,(d,which)->{
            if(which==0){if(!start(a,tdmIntent(url,headers,title)))missingTdm(a);return;}
            if(which==1){if(segmented){alert(a,"هذا الرابط بث HLS متجزئ. استخدم TDM أو 1DM.");return;}internal(a,url,headers,title);return;}
            if(!startIdm(a,url,headers,title))missingIdm(a);
        }).setNegativeButton("إلغاء",null).show();
    }

    /**
     * Matches EpisodesActivity.o1 in the supplied Drama World V4.2f APK.
     * TDM is deliberately given application/x-mpegURL even for the resolved file URL, a secure URI,
     * the original referer, all cookie spellings, and a .ts filename for HLS instead of .m3u8.
     */
    static Intent tdmIntent(String url,Map<String,String> headers,String title){
        Intent i=new Intent(Intent.ACTION_VIEW).setPackage(TDM).setDataAndType(Uri.parse(url),"application/x-mpegURL");
        String referer=headers.get("Referer"),cookie=headers.get("Cookie");
        if(referer!=null&&!referer.isEmpty())i.putExtra("Referer",referer);
        i.putExtra("secure_uri",true);
        i.putExtra("com.android.extra.filename",safe(title)+"."+originalExtension(url));
        if(cookie!=null&&!cookie.isEmpty()){
            i.putExtra("Cookie",cookie);i.putExtra("Cookies",cookie);i.putExtra("cookie",cookie);i.putExtra("cookies",cookie);
        }
        return i;
    }

    /** Matches EpisodesActivity.p1 extras used by 1DM/IDM. */
    private static Intent idmIntent(String packageName,String url,Map<String,String> headers,String title){
        Intent i=new Intent(Intent.ACTION_VIEW).setPackage(packageName).setDataAndType(Uri.parse(url),"application/x-mpegURL");
        String referer=headers.get("Referer"),cookie=headers.get("Cookie"),ua=headers.get("User-Agent");
        i.putExtra("extra_useragent",ua==null||ua.isEmpty()?UA:ua);
        i.putExtra("extra_headers",new String[]{"Accept","*/*"});
        i.putExtra("hide_browser_option",true);i.putExtra("secure_uri",true);
        if(referer!=null&&!referer.isEmpty())i.putExtra("extra_referer",referer);
        i.putExtra("extra_filename",safe(title)+"."+originalExtension(url));
        if(cookie!=null&&!cookie.isEmpty())i.putExtra("extra_cookies",cookie);
        return i;
    }

    private static boolean startIdm(Activity a,String url,Map<String,String> headers,String title){
        for(String p:IDM)if(start(a,idmIntent(p,url,headers,title)))return true;return false;
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
    /** Same extension decision used by EpisodesActivity.o1/p1: HLS downloads are named .ts. */
    private static String originalExtension(String url){
        String u=url==null?"":url.toLowerCase(Locale.ROOT);String path=Uri.parse(u).getPath();String p=path==null?u:path;
        if(p.endsWith(".mkv"))return "mkv";if(p.endsWith(".rmvb"))return "rmvb";if(u.contains(".m3u8"))return "ts";return "mp4";
    }
    private static String extension(String url){
        String path=Uri.parse(url==null?"":url).getPath();String lower=path==null?"":path.toLowerCase(Locale.ROOT);
        for(String ext:new String[]{".mkv",".webm",".mov",".mp4",".ts"})if(lower.endsWith(ext))return ext;return ".mp4";
    }
    private static void missingTdm(Activity a){
        new AlertDialog.Builder(a).setMessage("تطبيق TDM غير مثبت.").setNegativeButton("إلغاء",null).setPositiveButton("فتح صفحة TDM",(d,w)->start(a,new Intent(Intent.ACTION_VIEW,Uri.parse("https://upd.traidmod.com/go?id="+TDM)))).show();
    }
    private static void missingIdm(Activity a){
        new AlertDialog.Builder(a).setMessage("لم يتم العثور على 1DM.").setNegativeButton("إلغاء",null).setPositiveButton("فتح 1DM",(d,w)->{
            if(!start(a,new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=idm.internet.download.manager"))))start(a,new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=idm.internet.download.manager")));
        }).show();
    }
    private static void alert(Activity a,String message){if(!a.isFinishing()&&!a.isDestroyed())new AlertDialog.Builder(a).setMessage(message).setPositiveButton("حسناً",null).show();}
}
