package awr.witcher;

import android.app.Activity;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dynamic fallback for `embed` providers. Drama World can let an embed page execute its player JS;
 * a static HTML parser cannot see media URLs created later by scripts/iframes. This resolver loads
 * only the selected public provider page in an off-screen WebView, watches its media requests and
 * returns the clean URL/cookie/referer to our MX handoff. It never displays or clicks ads.
 */
final class EmbedResolver {
    private static final long TIMEOUT_MS=22000;
    private EmbedResolver(){}

    private static final class Candidate {
        final String url,referer,userAgent;final int score;
        Candidate(String u,String r,String a,int s){url=u;referer=r;userAgent=a;score=s;}
    }

    static void resolve(Activity activity,String pageUrl,Map<String,String> initial,Legacy.Callback callback){
        activity.runOnUiThread(()->start(activity,pageUrl,initial,callback));
    }

    private static void start(Activity a,String pageUrl,Map<String,String> initial,Legacy.Callback callback){
        if(a.isFinishing()||a.isDestroyed()){callback.failed();return;}
        final Handler main=new Handler(Looper.getMainLooper());final AtomicBoolean done=new AtomicBoolean();
        final ArrayList<Candidate> candidates=new ArrayList<>();final WebView web=new WebView(a);
        final Runnable[] timeout=new Runnable[1],settle=new Runnable[1];

        Runnable cleanup=()->{
            try{ViewParent p=web.getParent();if(p instanceof ViewGroup)((ViewGroup)p).removeView(web);}catch(Exception ignored){}
            try{web.stopLoading();web.loadUrl("about:blank");web.destroy();}catch(Exception ignored){}
        };
        Runnable finish=()->{
            if(!done.compareAndSet(false,true))return;main.removeCallbacks(timeout[0]);main.removeCallbacks(settle[0]);
            Candidate best=null;synchronized(candidates){for(Candidate c:candidates)if(best==null||c.score>best.score)best=c;}
            cleanup.run();
            if(best==null){callback.failed();return;}
            String cookie="";try{cookie=CookieManager.getInstance().getCookie(best.url);}catch(Exception ignored){}
            ArrayList<Legacy.Stream> one=new ArrayList<>();one.add(new Legacy.Stream(best.url,"تلقائي",cookie,best.referer,best.userAgent));callback.done(one,false);
        };
        settle[0]=finish;
        timeout[0]=finish;

        final java.util.function.BiConsumer<String,Map<String,String>> capture=(raw,requestHeaders)->{
            Candidate c=candidate(raw,requestHeaders,0);if(c==null)return;
            boolean added=false;synchronized(candidates){boolean exists=false;for(Candidate old:candidates)if(old.url.equals(c.url)){exists=true;break;}if(!exists){candidates.add(c);added=true;}}
            if(added)main.post(()->{main.removeCallbacks(settle[0]);main.postDelayed(settle[0],c.score>=120?650:1400);});
        };

        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(value(initial,"User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/127.0 Mobile Safari/537.36"));
        try{if(Build.VERSION.SDK_INT>=26)s.setSafeBrowsingEnabled(true);}catch(Exception ignored){}
        web.setAlpha(0.01f);web.setTranslationX(-10000f);web.setTranslationY(-10000f);
        CookieManager cm=CookieManager.getInstance();cm.setAcceptCookie(true);if(Build.VERSION.SDK_INT>=21)cm.setAcceptThirdPartyCookies(web,true);
        String suppliedCookie=value(initial,"Cookie","");if(!suppliedCookie.isEmpty())try{for(String part:suppliedCookie.split(";"))cm.setCookie(pageUrl,part.trim());cm.flush();}catch(Exception ignored){}

        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                try{capture.accept(request.getUrl().toString(),request.getRequestHeaders());}catch(Exception ignored){}return null;
            }
            @Override public void onLoadResource(WebView view,String url){capture.accept(url,Collections.emptyMap());}
            @Override public void onPageFinished(WebView view,String url){scan(view,capture);main.postDelayed(()->scan(view,capture),900);main.postDelayed(()->scan(view,capture),2500);}
            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError err){
                if(Build.VERSION.SDK_INT>=23&&req.isForMainFrame()&&candidates.isEmpty())main.postDelayed(finish,500);
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        a.addContentView(web,new ViewGroup.LayoutParams(2,2));
        main.postDelayed(timeout[0],TIMEOUT_MS);
        Map<String,String> extra=new HashMap<>();for(Map.Entry<String,String> e:initial.entrySet())if(!"Cookie".equalsIgnoreCase(e.getKey()))extra.put(e.getKey(),e.getValue());
        try{web.loadUrl(pageUrl,extra);}catch(Exception e){finish.run();}
    }

    private static void scan(WebView web,java.util.function.BiConsumer<String,Map<String,String>> capture){
        if(Build.VERSION.SDK_INT<19)return;
        String js="(function(){var u=[];function a(x){if(x&&typeof x==='string'&&u.indexOf(x)<0)u.push(x)};try{document.querySelectorAll('video,source').forEach(function(e){a(e.currentSrc);a(e.src)});}catch(e){}try{var v=document.querySelector('video');if(v){v.muted=true;var p=v.play();if(p&&p.catch)p.catch(function(){})}}catch(e){}try{if(window.jwplayer){var j=window.jwplayer();var i=j.getPlaylistItem&&j.getPlaylistItem();if(i){a(i.file);(i.sources||[]).forEach(function(s){a(s.file)})}}}catch(e){}return u})()";
        try{web.evaluateJavascript(js,value->{try{JSONArray arr=new JSONArray(value);for(int i=0;i<arr.length();i++){String u=arr.optString(i);Candidate c=candidate(u,Collections.emptyMap(),40);if(c!=null)capture.accept(c.url,Collections.emptyMap());}}catch(Exception ignored){}});}catch(Exception ignored){}
    }

    private static Candidate candidate(String raw,Map<String,String> headers,int bonus){
        if(raw==null)return null;String url=raw.trim();if(!(url.startsWith("http://")||url.startsWith("https://")))return null;
        String l=url.toLowerCase(Locale.ROOT);if(adLike(l))return null;String path="";try{path=Uri.parse(url).getPath();}catch(Exception ignored){}if(path==null)path="";String p=path.toLowerCase(Locale.ROOT);
        int score=bonus;
        if(p.endsWith(".m3u8")||l.contains(".m3u8?")){score+=160;}
        else if(p.endsWith(".mpd")||l.contains(".mpd?")){score+=150;}
        else if(p.endsWith(".mp4")||p.endsWith(".mkv")||p.endsWith(".webm")||p.endsWith(".mov")){score+=120;}
        else {
            String accept=value(headers,"Accept","").toLowerCase(Locale.ROOT);String dest=value(headers,"Sec-Fetch-Dest","").toLowerCase(Locale.ROOT);
            if(accept.contains("mpegurl")||accept.contains("application/dash")||accept.contains("video/"))score+=95;
            if(l.matches(".*(?:master|playlist|manifest|hls)(?:[/?._=&-]|$).*")||dest.equals("video"))score+=55;
        }
        if(score<80)return null;
        String ref=value(headers,"Referer","");String ua=value(headers,"User-Agent","");return new Candidate(url,ref,ua,score);
    }
    private static boolean adLike(String l){return l.contains("doubleclick")||l.contains("googlesyndication")||l.contains("googleadservices")||l.contains("adservice")||l.contains("adserver")||l.contains("/vast")||l.contains("vast.")||l.contains("/ads/");}
    private static String value(Map<String,String> map,String key,String fallback){if(map!=null)for(Map.Entry<String,String> e:map.entrySet())if(key.equalsIgnoreCase(e.getKey()))return e.getValue()==null?fallback:e.getValue();return fallback;}
}
