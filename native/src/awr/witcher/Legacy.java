package awr.witcher;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Invokes original extractor bytecode without booting Drama's application or config validator. */
public final class Legacy {
    public static final class Stream {
        public final String url,quality,cookie;
        Stream(String u,String q,String c){url=u;quality=q;cookie=c;}
    }
    public interface Callback {void done(List<Stream> streams,boolean showQualities);void failed();}
    private Legacy(){}
    private static String[] provider(String url){
        String host=Uri.parse(url).getHost();if(host==null)return null;host=host.toLowerCase(Locale.ROOT);
        String exact=null;
        if(host.equals("ok.ru")||host.endsWith(".ok.ru"))exact="okru";
        else if(host.equals("vk.ru")||host.endsWith(".vk.ru")||host.equals("vk.com")||host.endsWith(".vk.com"))exact="vk";
        String normalized=host.replaceAll("[^a-z0-9]","");
        for(String[] p:Providers.TABLE){String key=p[0].toLowerCase(Locale.ROOT);if(p[0].equals(exact)||(key.length()>=4&&normalized.contains(key)))return p;}
        return null;
    }

    /**
     * The supplied Drama APK uses original Q0/S0 extractors, but several of them depend on
     * dynamic server configuration that is not present in the host app. Do not let such an
     * extractor block a provider page that can be resolved directly. Run both paths together and
     * accept the first valid result. This keeps original quality/cookie output when it works, while
     * avoiding the all-servers-unavailable regression caused by waiting on Q0/S0 exclusively.
     */
    public static boolean resolve(Activity activity,String url,Map<String,String> headers,Callback callback){
        final String clean=StreamCodec.sourceUrl(url);final boolean hasOriginal=provider(clean)!=null;
        final int attempts=hasOriginal?2:1;AtomicInteger failed=new AtomicInteger();AtomicBoolean delivered=new AtomicBoolean();
        Callback guarded=new Callback(){
            public void done(List<Stream> streams,boolean showQualities){
                if(streams==null||streams.isEmpty()){failed();return;}
                if(delivered.compareAndSet(false,true))callback.done(streams,showQualities);
            }
            public void failed(){if(failed.incrementAndGet()>=attempts&&delivered.compareAndSet(false,true))callback.failed();}
        };
        resolvePage(activity,clean,headers,guarded);
        if(hasOriginal)activity.runOnUiThread(()->invokeOriginal(activity,clean,guarded));
        return true;
    }

    private static void resolvePage(Activity activity,String clean,Map<String,String> headers,Callback callback){
        Api.IO.execute(()->{
            try{
                Api.Response response=Api.readResponse(clean,headers,2*1024*1024);String body=response.text.trim();
                if(body.startsWith("\ufeff"))body=body.substring(1).trim();
                // Some quick-play endpoints redirect to or directly return an HLS master without
                // a .m3u8 pathname. Treat the successful response URL itself as the final stream.
                if(body.startsWith("#EXTM3U")){
                    ArrayList<Stream> one=new ArrayList<>();one.add(new Stream(response.url,"تلقائي",null));
                    activity.runOnUiThread(()->callback.done(one,false));return;
                }
                PageStreams.Result parsed=PageStreams.parse(response.text,response.url);
                if(!parsed.streams.isEmpty()){activity.runOnUiThread(()->callback.done(parsed.streams,parsed.choice));return;}
            }catch(Exception ignored){}
            activity.runOnUiThread(callback::failed);
        });
    }

    private static void invokeOriginal(Activity activity,String url,Callback callback){
        String[] p=provider(url);if(p==null){callback.failed();return;}
        try{
            ClassLoader loader=Legacy.class.getClassLoader();
            Class.forName("awr.legacy.a1.a",true,loader).getMethod("c",Context.class).invoke(null,activity.getApplicationContext());
            Class<?> listener=Class.forName("awr.legacy.Q0.b$b",true,loader);
            Object bridge=Proxy.newProxyInstance(loader,new Class<?>[]{listener},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class){if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("equals"))return proxy==args[0];return "WitcherExtractorCallback";}
                if(method.getName().equals("a")){callback.failed();return null;}
                if(method.getName().equals("b")){
                    ArrayList<Stream> streams=new ArrayList<>();
                    try{for(Object item:(List<?>)args[0]){
                        String resolved=(String)item.getClass().getMethod("e").invoke(item);StreamCodec.validate(resolved);
                        String quality=(String)item.getClass().getMethod("d").invoke(item),cookie=(String)item.getClass().getMethod("c").invoke(item);
                        streams.add(new Stream(resolved,quality,cookie));
                    }}catch(Exception e){callback.failed();return null;}
                    if(streams.isEmpty())callback.failed();else callback.done(streams,args.length>1&&Boolean.TRUE.equals(args[1]));
                }
                return null;
            });
            Class<?> extractor=Class.forName("awr.legacy."+p[1],true,loader);
            if(p[3].equals("1"))extractor.getMethod(p[2],String.class,listener,Context.class).invoke(null,url,bridge,activity);
            else extractor.getMethod(p[2],String.class,listener).invoke(null,url,bridge);
        }catch(ReflectiveOperationException|LinkageError|RuntimeException error){callback.failed();}
    }
}
