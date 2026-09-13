package awr.witcher;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Resolve/decode inside the app; MX always receives the final clean media URI plus headers. */
public final class Media {
    public static final String MX="com.mxtech.videoplayer.ad";
    // Same browser identity used by Drama World's original extractor family.
    private static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";
    private static final Map<Activity,Runnable> PENDING=new WeakHashMap<>();
    private Media(){}
    public static void cancelPending(Activity activity){Runnable cancel=PENDING.remove(activity);if(cancel!=null)cancel.run();}

    public static void open(Activity activity,JSONObject source,String title,boolean download){
        if(!Api.publicAccess(source.optString("premium"))){alert(activity,"هذا المصدر يحتاج إلى حساب أو اشتراك.");return;}
        final String url;
        try{url=StreamCodec.sourceUrl(StreamCodec.unwrap(source.optString("url"),source.optBoolean("is_encoded")));}
        catch(Exception e){alert(activity,"تعذر فك رابط هذا السيرفر.");return;}
        Map<String,String> headers=headers(source,url);String type=source.optString("type").toLowerCase(Locale.ROOT);
        String path=Uri.parse(url).getPath();String lower=(path==null?"":path).toLowerCase(Locale.ROOT);
        if(needsExtractionForSource(source,url,type,download)){extract(activity,url,headers,title,download,"webm".equals(type));return;}
        boolean hls=lower.endsWith(".m3u8");
        if(hls)qualities(activity,url,headers,title,download);else launch(activity,url,headers,title,download,lower.endsWith(".mpd")||type.equals("mpd"));
    }

    /** Mirrors the original MovieActivity routing before its DW-player-only P1 transport wrapping. */
    static boolean needsExtractionForSource(JSONObject source,String url,String type,boolean download){
        boolean channel=source.optBoolean("_channel");
        if(channel)return needsExtraction(url,type,download,true);
        // external chooses browser versus the app in the original; false does NOT mean
        // a final media URI. Internal MOV/WEBM/M3U8 pages still need extraction.
        return needsExtraction(url,type,download,false);
    }

    static boolean needsExtraction(String url,String type,boolean download,boolean channel){
        String normalized=type==null?"":type.toLowerCase(Locale.ROOT);
        if(channel)return !(normalized.equals("m3u8")||normalized.equals("mp4")||normalized.equals("mkv")||normalized.equals("mpd"));
        if(download){
            if(normalized.equals("mp4")||normalized.equals("mpd"))return false;
            return true;
        }
        // Exact original playback behavior: mov/webm and embed are extractor pages even when
        // their URL happens to end with a media-looking suffix. m3u8 is direct only when the
        // supplied URL itself ends in .m3u8; otherwise it is a provider page that must resolve.
        if(normalized.equals("mov")||normalized.equals("webm")||normalized.equals("embed"))return true;
        if(normalized.equals("m3u8")){String path=Uri.parse(url).getPath();return path==null||!path.toLowerCase(Locale.ROOT).endsWith(".m3u8");}
        if(normalized.equals("mp4")||normalized.equals("mkv")||normalized.equals("mpd"))return false;
        return true;
    }

    private static boolean validHeader(String key,String value){
        return key!=null&&key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")&&value!=null&&!value.contains("\r")&&!value.contains("\n");
    }
    public static Map<String,String> headers(JSONObject source,String url){
        Map<String,String> h=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);h.put("User-Agent",UA);
        JSONObject supplied=source.optJSONObject("headers");if(supplied!=null){Iterator<String> keys=supplied.keys();while(keys.hasNext()){String key=keys.next();String value=supplied.optString(key);if(validHeader(key,value))h.put(key,value);}}
        String host=source.optString("host",source.optString("size"));
        if(!(host.startsWith("http://")||host.startsWith("https://"))&&!source.optBoolean("_channel"))host=originalReferer(url);
        if((host.startsWith("http://")||host.startsWith("https://"))&&validHeader("Referer",host)&&!h.containsKey("Referer"))h.put("Referer",host);
        if(h.containsKey("Referer")&&!h.containsKey("Origin")){try{URL u=new URL(h.get("Referer"));h.put("Origin",u.getProtocol()+"://"+u.getAuthority());}catch(Exception ignored){}}
        String cookie=source.optString("cookie");if(!cookie.isEmpty()&&!cookie.contains("\n")&&!cookie.contains("\r"))h.put("Cookie",cookie);
        return h;
    }
    private static String originalReferer(String url){
        if(url.contains("stardima")||url.contains("dailymotion.com"))return "";
        try{if(url.contains("=http"))url=url.substring(url.lastIndexOf('=')+1);String host=Uri.parse(url).getHost();return host==null?"":"https://"+host+"/";}catch(Exception e){return "";}
    }

    private static void extract(Activity a,String url,Map<String,String> headers,String title,boolean download,boolean requestedQualities){
        cancelPending(a);
        ProgressDialog wait=new ProgressDialog(a);wait.setMessage(download?"جاري تجهيز رابط التنزيل…":"جاري تجهيز رابط السيرفر…");wait.setCancelable(true);wait.show();
        Handler timer=new Handler(Looper.getMainLooper());java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable timeout=()->{if(finished.compareAndSet(false,true)){PENDING.remove(a);wait.dismiss();alert(a,"انتهت مهلة تجهيز الرابط. جرّب سيرفراً آخر.");}};
        PENDING.put(a,()->{finished.set(true);timer.removeCallbacks(timeout);wait.dismiss();});
        timer.postDelayed(timeout,65000);wait.setOnCancelListener(d->cancelPending(a));
        boolean supported=Legacy.resolve(a,url,headers,new Legacy.Callback(){
            public void failed(){a.runOnUiThread(()->{if(!finished.compareAndSet(false,true))return;PENDING.remove(a);timer.removeCallbacks(timeout);wait.dismiss();alert(a,"هذا السيرفر غير متاح حالياً. جرّب سيرفراً آخر.");});}
            public void done(List<Legacy.Stream> streams,boolean showQualities){a.runOnUiThread(()->{
                if(!finished.compareAndSet(false,true))return;PENDING.remove(a);timer.removeCallbacks(timeout);wait.dismiss();if(a.isFinishing()||a.isDestroyed())return;
                ArrayList<Legacy.Stream> valid=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
                for(Legacy.Stream stream:streams){
                    try{
                        String finalUrl=StreamCodec.forExternalPlayer(stream.url);
                        valid.add(new Legacy.Stream(finalUrl,stream.quality,stream.cookie));
                        String q=stream.quality==null?"":stream.quality.trim();labels.add(q.isEmpty()?"جودة "+valid.size():q);
                    }catch(Exception ignored){}
                }
                if(valid.isEmpty()){alert(a,"تعذر فك الرابط النهائي لهذا السيرفر.");return;}
                android.content.DialogInterface.OnClickListener select=(d,i)->{
                    Legacy.Stream s=valid.get(i);Map<String,String> h=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);h.putAll(headers);
                    if(s.cookie!=null&&!s.cookie.isEmpty()&&validHeader("Cookie",s.cookie))h.put("Cookie",s.cookie);
                    final String finalUrl;try{finalUrl=StreamCodec.forExternalPlayer(s.url);}catch(Exception e){alert(a,"تعذر فك الرابط المختار.");return;}
                    String path=Uri.parse(finalUrl).getPath();String lower=path==null?"":path.toLowerCase(Locale.ROOT);
                    if(lower.endsWith(".m3u8"))qualities(a,finalUrl,h,title,download);else launch(a,finalUrl,h,title,download,lower.endsWith(".mpd"));
                };
                if(valid.size()==1&&!showQualities&&!requestedQualities)select.onClick(null,0);
                else new AlertDialog.Builder(a).setTitle(download?"اختر جودة التنزيل":"إختر جودة التشغيل!").setItems(labels.toArray(new String[0]),select).show();
            });}
        });
        if(!supported){cancelPending(a);alert(a,"هذا السيرفر غير متاح حالياً. جرّب سيرفراً آخر.");}
    }

    private static String mimeFor(String url){
        String path=Uri.parse(url).getPath();String lower=path==null?"":path.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".m3u8"))return "application/x-mpegURL";
        if(lower.endsWith(".mpd"))return "application/dash+xml";
        return "video/*";
    }
    public static Intent mxIntent(String url,Map<String,String> headers,String title){
        String finalUrl=StreamCodec.forExternalPlayer(url);
        Intent intent=new Intent(Intent.ACTION_VIEW).setPackage(MX).setDataAndType(Uri.parse(finalUrl),mimeFor(finalUrl));
        ArrayList<String> flat=new ArrayList<>();for(Map.Entry<String,String> h:headers.entrySet())if(validHeader(h.getKey(),h.getValue())){flat.add(h.getKey());flat.add(h.getValue());}
        intent.putExtra("headers",flat.toArray(new String[0]));intent.putExtra("title",title);return intent;
    }
    private static void launch(Activity a,String url,Map<String,String> h,String title,boolean download,boolean segmented){
        if(a.isFinishing()||a.isDestroyed())return;
        final String finalUrl;try{finalUrl=StreamCodec.forExternalPlayer(url);}catch(Exception e){alert(a,"تعذر فك الرابط النهائي.");return;}
        if(download){DownloadFlow.open(a,finalUrl,h,title,segmented);return;}
        try{a.startActivity(mxIntent(finalUrl,h,title));}
        catch(ActivityNotFoundException e){new AlertDialog.Builder(a).setMessage("ثبّت MX Player لفتح هذا الفيديو.").setNegativeButton("إلغاء",null).setPositiveButton("فتح صفحة MX Player",(d,w)->{try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+MX)));}catch(ActivityNotFoundException ignored){a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id="+MX)));}}).show();}
        catch(Exception e){alert(a,"تعذر فتح الفيديو في MX Player.");}
    }

    private static void qualities(Activity a,String url,Map<String,String> h,String title,boolean download){
        final String clean;try{clean=StreamCodec.forExternalPlayer(url);}catch(Exception e){alert(a,"تعذر فك رابط الجودة.");return;}
        cancelPending(a);ProgressDialog wait=new ProgressDialog(a);wait.setMessage(download?"جاري تجهيز الجودات للتنزيل…":"جاري تجهيز الجودات…");wait.setCancelable(true);wait.show();final boolean[] canceled={false};PENDING.put(a,()->{canceled[0]=true;wait.dismiss();});wait.setOnCancelListener(d->cancelPending(a));
        Api.IO.execute(()->{
            ArrayList<String> names=new ArrayList<>(),urls=new ArrayList<>();names.add("تلقائي");urls.add(clean);
            boolean valid=false;
            try{
                Api.Response response=Api.readResponse(clean,h,1024*1024);String body=response.text.trim();if(body.startsWith("\ufeff"))body=body.substring(1).trim();
                if(!body.startsWith("#EXTM3U"))throw new java.io.IOException("Not a playlist");valid=true;String[] lines=body.split("\\r?\\n");
                for(int i=0;i<lines.length-1;i++)if(lines[i].startsWith("#EXT-X-STREAM-INF:")){
                    String descriptor=lines[i];
                    if(descriptor.contains("AUDIO="))continue;
                    int j=i+1;while(j<lines.length&&lines[j].trim().isEmpty())j++;
                    if(j>=lines.length||lines[j].startsWith("#"))continue;
                    String label="جودة "+names.size();Matcher resolution=Pattern.compile("RESOLUTION=\\d+x(\\d+)").matcher(descriptor);if(resolution.find())label=resolution.group(1)+"p";
                    String resolved=new URL(new URL(response.url),lines[j].trim()).toString();resolved=StreamCodec.forExternalPlayer(resolved);names.add(label);urls.add(resolved);
                }
            }catch(Exception ignored){}
            final boolean playable=valid;
            a.runOnUiThread(()->{if(canceled[0]||a.isFinishing()||a.isDestroyed())return;PENDING.remove(a);wait.dismiss();if(!playable){alert(a,"لم يُرجع السيرفر رابط بث صالحاً. جرّب سيرفراً آخر.");return;}if(urls.size()==1){launch(a,clean,h,title,download,true);return;}new AlertDialog.Builder(a).setTitle(download?"اختر جودة التنزيل":"إختر جودة التشغيل!").setItems(names.toArray(new String[0]),(d,i)->launch(a,urls.get(i),h,title,download,true)).show();});
        });
    }
    private static void alert(Activity a,String message){if(!a.isFinishing()&&!a.isDestroyed())new AlertDialog.Builder(a).setMessage(message).setPositiveButton("حسناً",null).show();}
}
