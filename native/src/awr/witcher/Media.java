package awr.witcher;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import org.json.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** MX handoff always receives the actual stream and request headers. */
public final class Media {
    public static final String MX="com.mxtech.videoplayer.ad";
    private static final String UA="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private Media(){}
    public static void open(Activity activity,JSONObject source,String title,boolean download){
        if(!Api.publicAccess(source.optString("premium"))){alert(activity,"هذا المصدر يحتاج إلى حساب أو اشتراك.");return;}
        final String url;
        try{url=StreamCodec.unwrap(source.optString("url"),source.optBoolean("is_encoded"));}
        catch(Exception e){alert(activity,"تعذر قراءة رابط هذا السيرفر.");return;}
        Map<String,String> headers=headers(source,url);String type=source.optString("type").toLowerCase(Locale.ROOT);
        String path=Uri.parse(url).getPath();String lower=(path==null?"":path).toLowerCase(Locale.ROOT);
        boolean direct=lower.endsWith(".m3u8")||lower.endsWith(".mp4")||lower.endsWith(".mkv")||lower.endsWith(".mpd")||type.equals("mp4")||type.equals("m3u8")||type.equals("mkv")||type.equals("mpd");
        if(!direct){
            alert(activity,"هذا السيرفر يحتاج إلى مستخرج الروابط الخاص بعالم الدراما. لم يكتمل ربط إعداداته في هذه النسخة بعد. جرّب سيرفراً مباشراً.");return;
        }
        boolean hls=lower.endsWith(".m3u8")||type.equals("m3u8");
        if(hls)qualities(activity,url,headers,title,download);else launch(activity,url,headers,title,download,false);
    }
    public static Map<String,String> headers(JSONObject source,String url){
        Map<String,String> h=new LinkedHashMap<>();h.put("User-Agent",UA);
        JSONObject supplied=source.optJSONObject("headers");if(supplied!=null){Iterator<String> keys=supplied.keys();while(keys.hasNext()){String key=keys.next();String value=supplied.optString(key);if(!key.contains("\r")&&!key.contains("\n")&&!value.contains("\r")&&!value.contains("\n"))h.put(key,value);}}
        String host=source.optString("host",source.optString("size"));
        if(host.startsWith("http://")||host.startsWith("https://")){h.put("Referer",host);try{URL u=new URL(host);h.put("Origin",u.getProtocol()+"://"+u.getAuthority());}catch(Exception ignored){}}
        String cookie=source.optString("cookie");if(!cookie.isEmpty()&&!cookie.contains("\n")&&!cookie.contains("\r"))h.put("Cookie",cookie);
        return h;
    }
    public static Intent mxIntent(String url,Map<String,String> headers,String title){
        StreamCodec.validate(url);Intent intent=new Intent(Intent.ACTION_VIEW).setPackage(MX).setDataAndType(Uri.parse(url),"video/*");
        ArrayList<String> flat=new ArrayList<>();for(Map.Entry<String,String> h:headers.entrySet()){flat.add(h.getKey());flat.add(h.getValue());}
        intent.putExtra("headers",flat.toArray(new String[0]));intent.putExtra("title",title);return intent;
    }
    private static void launch(Activity a,String url,Map<String,String> h,String title,boolean download,boolean segmented){
        if(a.isFinishing()||a.isDestroyed())return;
        if(download){download(a,url,h,title,segmented);return;}
        try{a.startActivity(mxIntent(url,h,title));}
        catch(ActivityNotFoundException e){new AlertDialog.Builder(a).setMessage("ثبّت MX Player لفتح هذا الفيديو.").setNegativeButton("إلغاء",null).setPositiveButton("فتح صفحة MX Player",(d,w)->{try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+MX)));}catch(ActivityNotFoundException ignored){a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id="+MX)));}}).show();}
        catch(Exception e){alert(a,"تعذر فتح الفيديو في MX Player.");}
    }
    private static void qualities(Activity a,String url,Map<String,String> h,String title,boolean download){
        ProgressDialog wait=new ProgressDialog(a);wait.setMessage("جاري تجهيز الجودات…");wait.setCancelable(true);wait.show();final boolean[] canceled={false};wait.setOnCancelListener(d->canceled[0]=true);
        Api.IO.execute(()->{
            ArrayList<String> names=new ArrayList<>(),urls=new ArrayList<>();names.add("تلقائي");urls.add(url);
            try{
                String body=Api.read(url,h,1024*1024);String[] lines=body.split("\\r?\\n");
                for(int i=0;i<lines.length-1;i++)if(lines[i].startsWith("#EXT-X-STREAM-INF:")){
                    String descriptor=lines[i];
                    // Separate audio renditions need the master playlist; avoid silent playback.
                    if(descriptor.contains("AUDIO="))continue;
                    int j=i+1;while(j<lines.length&&lines[j].trim().isEmpty())j++;
                    if(j>=lines.length||lines[j].startsWith("#"))continue;
                    String label="جودة "+names.size();Matcher resolution=Pattern.compile("RESOLUTION=\\d+x(\\d+)").matcher(descriptor);if(resolution.find())label=resolution.group(1)+"p";
                    String resolved=new URL(new URL(url),lines[j].trim()).toString();StreamCodec.validate(resolved);names.add(label);urls.add(resolved);
                }
            }catch(Exception ignored){/* Original master remains playable; MX can choose adaptively. */}
            a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed())return;wait.dismiss();if(canceled[0])return;if(urls.size()==1){launch(a,url,h,title,download,true);return;}new AlertDialog.Builder(a).setTitle("اختيار الجودة").setItems(names.toArray(new String[0]),(d,i)->launch(a,urls.get(i),h,title,download,true)).show();});
        });
    }
    private static void download(Activity a,String url,Map<String,String> h,String title,boolean segmented){
        if(segmented){
            for(String packageName:new String[]{"idm.internet.download.manager.plus","idm.internet.download.manager","com.dv.adm"}){
                Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url),"video/*").setPackage(packageName).putExtra("title",title);
                ArrayList<String> flat=new ArrayList<>();for(Map.Entry<String,String> e:h.entrySet()){flat.add(e.getKey());flat.add(e.getValue());}intent.putExtra("headers",flat.toArray(new String[0]));intent.putExtra("Cookie",h.get("Cookie"));intent.putExtra("Referer",h.get("Referer"));
                try{a.startActivity(intent);return;}catch(ActivityNotFoundException ignored){}
            }
            alert(a,"هذا الرابط بث متجزئ. ثبّت 1DM أو ADM لتنزيله كاملاً.");return;
        }
        if(Build.VERSION.SDK_INT<=28&&Build.VERSION.SDK_INT>=23&&a.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},831);Toast.makeText(a,"بعد منح الإذن، اضغط سيرفر التنزيل مجدداً.",Toast.LENGTH_LONG).show();return;}
        try{
            String path=Uri.parse(url).getLastPathSegment();String ext=path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".mkv")?".mkv":".mp4";
            String safe=title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");if(safe.length()>110)safe=safe.substring(0,110);if(safe.isEmpty())safe="video";
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url)).setTitle(title).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,safe+"-"+System.currentTimeMillis()+ext);
            for(Map.Entry<String,String> entry:h.entrySet())request.addRequestHeader(entry.getKey(),entry.getValue());
            ((DownloadManager)a.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);Toast.makeText(a,"بدأ التنزيل",Toast.LENGTH_SHORT).show();
        }catch(Exception e){alert(a,"تعذر بدء التنزيل. تحقق من الإذن والمساحة المتاحة.");}
    }
    private static void alert(Activity a,String message){if(!a.isFinishing()&&!a.isDestroyed())new AlertDialog.Builder(a).setMessage(message).setPositiveButton("حسناً",null).show();}
}
