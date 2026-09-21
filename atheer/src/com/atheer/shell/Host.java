package com.atheer.shell;
import android.app.*;import android.content.*;import android.view.*;import java.util.*;import java.lang.reflect.*;
public final class Host {
    static final int SOURCES=0x6a000001;
    public static void attach(Activity activity){activity.getWindow().getDecorView().post(()->{
        try {
            int id=activity.getResources().getIdentifier("bottomNav","id",activity.getPackageName());
            View bar=activity.findViewById(id);if(bar==null)return;
            Object adapter=bar.getClass().getField("u").get(bar);
            List<?> old=(List<?>)adapter.getClass().getField("g").get(adapter);
            if(old.isEmpty())return;
            for(Object item:old)if(item.getClass().getField("a").getInt(item)==SOURCES)return;
            Class<?> itemClass=old.get(0).getClass();
            int icon=activity.getResources().getIdentifier("ic_anime","drawable",activity.getPackageName());
            Object source=itemClass.getConstructor(int.class,int.class,String.class,String.class,boolean.class).newInstance(SOURCES,icon,"عالم المصادر","atheer_sources",false);
            ArrayList<Object> items=new ArrayList<>(old);items.add(Math.min(2,items.size()),source);
            bar.getClass().getMethod("setItems",List.class).invoke(bar,items);
            Object callback=bar.getClass().getMethod("getOnItemSelected").invoke(bar);
            Class<?> callbackType=bar.getClass().getMethod("getOnItemSelected").getReturnType();
            Object wrapper=Proxy.newProxyInstance(bar.getClass().getClassLoader(),new Class<?>[]{callbackType},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class){if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("equals"))return proxy==args[0];return "AtheerTabs";}
                if(args!=null&&args.length==1&&itemClass.isInstance(args[0])&&itemClass.getField("a").getInt(args[0])==SOURCES){ModuleRuntime.open(activity);return false;}
                return method.invoke(callback,args);
            });
            bar.getClass().getMethod("setOnItemSelected",callbackType).invoke(bar,wrapper);
        }catch(Exception e){android.util.Log.e("Atheer","Cannot add Source World tab",e);}
    });}
    /** Replaces only the app-update call; never substitutes content or integrity results. */
    public static Object updatesDisabled(){
        try {ClassLoader cl=Host.class.getClassLoader();Object update=cl.loadClass("com.drama.mp4.data.model.AppUpdate").getConstructor().newInstance();
            Object body=cl.loadClass("com.drama.mp4.data.api.SingleResponse").getConstructor(String.class,String.class,Object.class).newInstance("success",null,update);
            return cl.loadClass("retrofit2.Response").getMethod("success",Object.class).invoke(null,body);
        }catch(Exception e){throw new IllegalStateException("App-update policy",e);}
    }
}
