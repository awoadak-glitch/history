package com.atheer.shell;

import android.app.Activity;
import android.view.View;
import android.view.ViewTreeObserver;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds one Source World destination while leaving Oscar's protected activity bytecode untouched. */
public final class Host {
    static final int SOURCES=0x6a000001;
    private static final Map<Activity,ViewTreeObserver.OnGlobalLayoutListener> OBSERVERS=new WeakHashMap<>();
    private static final Map<View,Object> WRAPPERS=new WeakHashMap<>();
    private Host() {}

    public static void attach(Activity activity) {
        if(!OBSERVERS.containsKey(activity)) {
            ViewTreeObserver.OnGlobalLayoutListener observer=()->integrate(activity);
            OBSERVERS.put(activity,observer);
            activity.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(observer);
        }
        activity.getWindow().getDecorView().post(()->integrate(activity));
    }

    public static void detach(Activity activity) {
        ViewTreeObserver.OnGlobalLayoutListener observer=OBSERVERS.remove(activity);
        if(observer!=null)activity.getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(observer);
    }

    private static void integrate(Activity activity) {
        if(activity.isFinishing())return;
        try {
            int id=activity.getResources().getIdentifier("bottomNav","id",activity.getPackageName());
            View bar=activity.findViewById(id);
            if(bar==null)return;
            Object adapter=bar.getClass().getField("u").get(bar);
            List<?> old=(List<?>)adapter.getClass().getField("g").get(adapter);
            if(old.isEmpty())return;
            Class<?> itemClass=old.get(0).getClass();
            boolean found=false;
            ArrayList<Object> items=new ArrayList<>();
            for(Object item:old) {
                int itemId=itemClass.getField("a").getInt(item);
                String title=(String)itemClass.getField("c").get(item);
                if(itemId==SOURCES) { found=true; items.add(item); }
                else if(!"عالم المصادر".equals(title)&&!"HiTV".equalsIgnoreCase(title))items.add(item);
            }
            if(!found) {
                int icon=activity.getResources().getIdentifier("ic_anime","drawable",activity.getPackageName());
                Object source=itemClass.getConstructor(int.class,int.class,String.class,String.class,boolean.class)
                        .newInstance(SOURCES,icon,"عالم المصادر","direct_sources",false);
                items.add(Math.min(2,items.size()),source);
            }
            Method getSelected=bar.getClass().getMethod("getOnItemSelected");
            Object callback=getSelected.invoke(bar);
            if(callback!=WRAPPERS.get(bar)) {
                Class<?> callbackType=getSelected.getReturnType();
                Object wrapped=Proxy.newProxyInstance(bar.getClass().getClassLoader(),new Class<?>[]{callbackType},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class) {
                        if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);
                        if("equals".equals(method.getName()))return proxy==args[0];
                        return "DirectSourceTabs";
                    }
                    if(args!=null&&args.length==1&&itemClass.isInstance(args[0])&&itemClass.getField("a").getInt(args[0])==SOURCES) {
                        DirectSources.open(activity);
                        return false;
                    }
                    return callback==null?false:method.invoke(callback,args);
                });
                WRAPPERS.put(bar,wrapped);
                bar.getClass().getMethod("setOnItemSelected",callbackType).invoke(bar,wrapped);
            }
            if(!found||items.size()!=old.size())bar.getClass().getMethod("setItems",List.class).invoke(bar,items);
        } catch(ReflectiveOperationException|RuntimeException error) {
            android.util.Log.e("DirectSources","Source tab integration",error);
        }
    }
}
