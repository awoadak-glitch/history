package awr.witcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONTokener;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last-resort resolver for Oscar/TDM watch pages whose final media URL is created by JavaScript.
 * The WebView remains inside PALMA; it only observes the page's own media request and then hands
 * that HTTP URL, cookies and request headers to MX Player.
 */
final class OscarBrowserResolver {
    private static final String UA="Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0 Mobile Safari/537.36";
    private static final Pattern HTTP=Pattern.compile("https?:(?:\\\\/|/){2}[^\\s\\\"'<>]+",Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA=Pattern.compile("(?:\\.(?:m3u8|mpd|mp4|m4v|mkv|mov|webm|ts)(?:[?#&]|$)|(?:[?&](?:url|file|src|stream|hls|video)=[^&]*(?:m3u8|mpd|mp4|m4v|mkv|mov|webm))|/(?:hls|dash|manifest|playlist)(?:/|[?.]))",Pattern.CASE_INSENSITIVE);
    private static final String SCRIPT="(function(){var o=[],r=/(?:\\.(?:m3u8|mpd|mp4|m4v|mkv|mov|webm|ts)(?:[?#&]|$)|\\/(?:hls|dash|manifest|playlist)(?:\\/|[?.]))/i,a=function(v,f){if(typeof v!=='string'||!/^https?:/i.test(v)||(!f&&!r.test(v)))return;v=(f?'!':'')+v;if(o.indexOf(v)<0)o.push(v);};try{document.querySelectorAll('video,source').forEach(function(e){a(e.currentSrc,true);a(e.src,true);});}catch(e){}try{performance.getEntriesByType('resource').forEach(function(e){a(e.name,e.initiatorType==='video');});}catch(e){}try{var p=window.jwplayer&&window.jwplayer();if(p){var i=p.getPlaylistItem&&p.getPlaylistItem();if(i){a(i.file,true);(i.sources||[]).forEach(function(s){a(s.file||s.src,true);});}}}catch(e){}return JSON.stringify(o);})()";

    private OscarBrowserResolver() {}

    static void open(Activity activity,String raw,String deep,String type,String title,String previousIssue) {
        List<Target> targets=targets(raw,deep);
        if(targets.isEmpty()){
            fallback(activity,raw,deep,previousIssue);
            return;
        }
        new Session(activity,targets,type,title,raw,deep,previousIssue).start();
    }

    private static final class Target {
        final String url;
        final Map<String,String> headers;
        Target(String url,Map<String,String> headers){this.url=url;this.headers=new LinkedHashMap<>(headers);}
    }

    private static final class Session {
        final Activity activity;
        final List<Target> targets;
        final String declared,title,raw,deep,previousIssue;
        final Handler main=new Handler(Looper.getMainLooper());
        final AtomicBoolean done=new AtomicBoolean();
        final ProgressDialog wait;
        WebView web;
        int index;
        String page="";

        Session(Activity activity,List<Target> targets,String declared,String title,String raw,String deep,String previousIssue){
            this.activity=activity;this.targets=targets;this.declared=declared==null?"":declared;
            this.title=title;this.raw=raw;this.deep=deep;this.previousIssue=previousIssue;
            wait=new ProgressDialog(activity);wait.setMessage("جاري استخراج رابط الفيديو من المشغل الأصلي لـ MX…");wait.setCancelable(true);
        }

        void start(){
            if(activity.isFinishing()||activity.isDestroyed())return;
            wait.setOnCancelListener(dialog->finish(false));wait.show();
            web=new WebView(activity);web.setAlpha(0.01f);
            ViewGroup root=(ViewGroup)activity.getWindow().getDecorView();root.addView(web,new ViewGroup.LayoutParams(2,2));
            WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);settings.setUserAgentString(UA);
            if(Build.VERSION.SDK_INT>=21)settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager cookies=CookieManager.getInstance();cookies.setAcceptCookie(true);
            if(Build.VERSION.SDK_INT>=21)cookies.setAcceptThirdPartyCookies(web,true);
            web.setWebChromeClient(new WebChromeClient());
            web.setWebViewClient(new WebViewClient(){
                @Override public void onPageStarted(WebView view,String url,Bitmap icon){page=url==null?"":url;observe(url,null,false);}
                @Override public void onPageFinished(WebView view,String url){page=url==null?page:url;inspect();}
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){String url=request.getUrl().toString();if(observe(url,request.getRequestHeaders(),false))return true;return false;}
                @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return observe(url,null,false);}
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){observe(request.getUrl().toString(),request.getRequestHeaders(),false);return null;}
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,String url){observe(url,null,false);return null;}
            });
            main.postDelayed(()->{if(!done.get())next();},16000);
            main.postDelayed(()->{if(done.compareAndSet(false,true)){cleanup();fallback(activity,raw,deep,previousIssue);}},52000);
            load();
        }

        void load(){
            if(done.get()||index>=targets.size())return;
            Target target=targets.get(index);page=target.url;seedCookies(target);
            web.loadUrl(target.url,target.headers);inspectLater();
        }

        void next(){
            if(done.get())return;
            index++;
            if(index<targets.size()){web.stopLoading();load();main.postDelayed(()->{if(!done.get())next();},16000);}
        }

        void inspectLater(){main.postDelayed(new Runnable(){public void run(){if(done.get())return;inspect();main.postDelayed(this,850);}},700);}

        void inspect(){
            if(done.get()||web==null)return;
            web.evaluateJavascript(SCRIPT,value->{
                if(done.get()||value==null||"null".equals(value))return;
                try{
                    Object first=new JSONTokener(value).nextValue();String json=first instanceof String?(String)first:value;
                    JSONArray urls=new JSONArray(json);
                    for(int i=0;i<urls.length();i++){
                        String found=urls.optString(i);boolean trusted=found.startsWith("!");
                        if(trusted)found=found.substring(1);
                        if(observe(found,null,trusted))break;
                    }
                }catch(Exception ignored){}
            });
        }

        boolean observe(String url,Map<String,String> requestHeaders,boolean fromPlayer){
            if(done.get()||!http(url))return false;
            String accept=header(requestHeaders,"Accept");boolean mediaRequest=accept!=null&&accept.toLowerCase(Locale.ROOT).contains("video");
            if(!fromPlayer&&!mediaRequest&&!MEDIA.matcher(url).find())return false;
            if(same(url,page)&&!MEDIA.matcher(url).find())return false;
            Map<String,String> headers=new LinkedHashMap<>(targets.get(Math.min(index,targets.size()-1)).headers);
            copyHeader(requestHeaders,headers,"Referer");copyHeader(requestHeaders,headers,"Origin");copyHeader(requestHeaders,headers,"User-Agent");copyHeader(requestHeaders,headers,"Authorization");
            if(!headers.containsKey("Referer")&&http(page))headers.put("Referer",page);
            String cookie="";try{cookie=CookieManager.getInstance().getCookie(url);}catch(RuntimeException ignored){}
            if(cookie!=null&&!cookie.trim().isEmpty())headers.put("Cookie",cookie);
            final Map<String,String> ready=headers;final String media=url;
            main.post(()->{
                if(!done.compareAndSet(false,true))return;
                cleanup();Media.launchResolved(activity,media,ready,title,segmented(media,declared));
            });
            return true;
        }

        void seedCookies(Target target){
            String cookie=target.headers.get("Cookie");if(cookie==null||cookie.isEmpty())return;
            try{for(String part:cookie.split(";"))CookieManager.getInstance().setCookie(target.url,part.trim());if(Build.VERSION.SDK_INT>=21)CookieManager.getInstance().flush();}catch(RuntimeException ignored){}
        }

        void finish(boolean showFallback){if(!done.compareAndSet(false,true))return;cleanup();if(showFallback)fallback(activity,raw,deep,previousIssue);}

        void cleanup(){
            if(wait.isShowing())wait.dismiss();
            if(web!=null){try{web.stopLoading();ViewGroup parent=(ViewGroup)web.getParent();if(parent!=null)parent.removeView(web);web.destroy();}catch(RuntimeException ignored){}web=null;}
        }
    }

    private static List<Target> targets(String raw,String deep){
        List<Target> out=new ArrayList<>();Set<String> seen=new LinkedHashSet<>();Map<String,String> base=headers(raw,deep);
        collect(deep,base,out,seen,0);collect(raw,base,out,seen,0);return out;
    }

    private static void collect(String value,Map<String,String> headers,List<Target> out,Set<String> seen,int depth){
        if(value==null||depth>4||out.size()>=8)return;String clean=value.trim();if(clean.isEmpty())return;
        for(String form:forms(clean)){
            Matcher matcher=HTTP.matcher(form);while(matcher.find()){String url=unescape(matcher.group());if(http(url)&&seen.add(url))out.add(new Target(url,headers));}
            if(http(form)&&seen.add(form))out.add(new Target(form,headers));
            try{Uri uri=Uri.parse(form);for(String key:uri.getQueryParameterNames())if(linkKey(key))for(String nested:uri.getQueryParameters(key))collect(nested,headers,out,seen,depth+1);}catch(RuntimeException ignored){}
        }
    }

    private static List<String> forms(String value){
        LinkedHashSet<String> out=new LinkedHashSet<>();String current=value;
        for(int i=0;i<3;i++){if(!out.add(current))break;String decoded=decode(current);if(decoded.equals(current))break;current=decoded;}
        try{out.add(StreamCodec.forExternalPlayer(value));}catch(RuntimeException ignored){}
        if(value.length()>12&&value.matches("[A-Za-z0-9_+/=\\-]+"))for(int flags:new int[]{Base64.DEFAULT,Base64.URL_SAFE|Base64.NO_WRAP})try{String decoded=new String(Base64.decode(value,flags),StandardCharsets.UTF_8).trim();if(decoded.contains("://"))out.add(decoded);}catch(IllegalArgumentException ignored){}
        return new ArrayList<>(out);
    }

    private static Map<String,String> headers(String raw,String deep){
        Map<String,String> out=new LinkedHashMap<>();out.put("User-Agent",UA);out.put("Accept","*/*");out.put("X-Requested-With","com.drama.mp4");
        hints(deep,out);hints(raw,out);return out;
    }

    private static void hints(String value,Map<String,String> out){
        if(value==null)return;try{Uri uri=Uri.parse(value);for(String key:uri.getQueryParameterNames()){
            String low=key.toLowerCase(Locale.ROOT).replace('_','-');String val=uri.getQueryParameter(key);if(val==null||val.contains("\r")||val.contains("\n"))continue;
            if("referer".equals(low)||"referrer".equals(low))out.put("Referer",val);else if("origin".equals(low))out.put("Origin",val);else if("cookie".equals(low))out.put("Cookie",val);else if("user-agent".equals(low)||"ua".equals(low))out.put("User-Agent",val);
        }}catch(RuntimeException ignored){}
    }

    private static void fallback(Activity activity,String raw,String deep,String previousIssue){
        if(activity.isFinishing()||activity.isDestroyed())return;String original=deep!=null&&!deep.trim().isEmpty()?deep.trim():raw==null?"":raw.trim();
        String message="هذا السيرفر يستخدم رابطاً خاصاً بالمشغل الأصلي ولم يكشف رابط HTTP يمكن لـ MX تشغيله.";
        if(previousIssue!=null&&!previousIssue.trim().isEmpty())message+="\n"+previousIssue;
        AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setMessage(message).setNegativeButton("إغلاق",null);
        if(!original.isEmpty())dialog.setPositiveButton("تشغيل بالمشغل الأصلي",(d,w)->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(original)));}catch(ActivityNotFoundException e){alert(activity,"المشغل الأصلي غير مثبت.");}catch(RuntimeException e){alert(activity,"تعذر فتح رابط المشغل الأصلي.");}});
        dialog.show();
    }

    private static boolean segmented(String url,String declared){String low=(url+" "+declared).toLowerCase(Locale.ROOT);return low.contains("m3u8")||low.contains("mpegurl")||low.contains(".mpd")||low.contains("dash");}
    private static boolean linkKey(String key){String low=key==null?"":key.toLowerCase(Locale.ROOT).replace("s.","");return low.equals("url")||low.equals("link")||low.equals("video")||low.equals("stream")||low.equals("src")||low.equals("source")||low.equals("file")||low.equals("play")||low.equals("playlist")||low.equals("hls")||low.equals("mp4")||low.equals("uri")||low.equals("data")||low.contains("playurl")||low.contains("streamurl")||low.contains("video-url")||low.equals("browser-fallback-url");}
    private static String decode(String value){try{return URLDecoder.decode(value.replace("+","%2B"),"UTF-8");}catch(Exception ignored){return value;}}
    private static String unescape(String value){return value.replace("\\/","/").replace("&amp;","&");}
    private static boolean http(String value){try{URI uri=new URI(value);String scheme=uri.getScheme();return uri.getHost()!=null&&uri.getUserInfo()==null&&("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme));}catch(Exception ignored){return false;}}
    private static boolean same(String a,String b){return a!=null&&b!=null&&a.equals(b);}
    private static String header(Map<String,String> headers,String name){if(headers==null)return null;for(Map.Entry<String,String> item:headers.entrySet())if(item.getKey().equalsIgnoreCase(name))return item.getValue();return null;}
    private static void copyHeader(Map<String,String> from,Map<String,String> to,String name){String value=header(from,name);if(value!=null&&!value.isEmpty()&&!value.contains("\r")&&!value.contains("\n"))to.put(name,value);}
    private static void alert(Activity activity,String message){if(!activity.isFinishing()&&!activity.isDestroyed())new AlertDialog.Builder(activity).setMessage(message).setPositiveButton("حسناً",null).show();}
}
