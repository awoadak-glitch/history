package awr.witcher;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import java.lang.reflect.*;
import java.util.*;

/** Invokes original extractor bytecode without booting Drama's application or config validator. */
public final class Legacy {
    public static final class Stream {
        public final String url,quality,cookie;
        Stream(String u,String q,String c){url=u;quality=q;cookie=c;}
    }
    public interface Callback {void done(List<Stream> streams);void failed();}
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
    public static boolean resolve(Activity activity,String url,Callback callback){
        String[] p=provider(url);if(p==null)return false;
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
                    if(streams.isEmpty())callback.failed();else callback.done(streams);
                }
                return null;
            });
            Class<?> extractor=Class.forName("awr.legacy."+p[1],true,loader);
            if(p[3].equals("1"))extractor.getMethod(p[2],String.class,listener,Context.class).invoke(null,url,bridge,activity);
            else extractor.getMethod(p[2],String.class,listener).invoke(null,url,bridge);
        }catch(ReflectiveOperationException|LinkageError|RuntimeException error){callback.failed();}
        return true;
    }
}
