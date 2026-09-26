package com.atheer.shell;
import android.app.*;import android.content.*;import android.view.*;import java.util.*;import java.lang.reflect.*;
/** Adds the source tab without modifying any protected host lifecycle method. */
public final class Host {
    static final int SOURCES=0x6a000001;
    private static final Map<Activity,ViewTreeObserver.OnGlobalLayoutListener> observers=new WeakHashMap<>();
    private static final Map<View,Object> wrappers=new WeakHashMap<>();
    public static void attach(Activity a){
        if(!observers.containsKey(a)){
            ViewTreeObserver.OnGlobalLayoutListener observer=()->integrate(a);
            observers.put(a,observer);a.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(observer);
        }
        a.getWindow().getDecorView().post(()->integrate(a));
    }
    public static void detach(Activity a){ViewTreeObserver.OnGlobalLayoutListener o=observers.remove(a);if(o!=null)a.getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(o);}
    private static void integrate(Activity a){
        if(a.isFinishing())return;
        try {
            int id=a.getResources().getIdentifier("bottomNav","id",a.getPackageName());View bar=a.findViewById(id);if(bar==null)return;
            Object adapter=bar.getClass().getField("u").get(bar);
            List<?> old=(List<?>)adapter.getClass().getField("g").get(adapter);if(old.isEmpty())return;
            Class<?> itemClass=old.get(0).getClass();boolean found=false;
            ArrayList<Object> items=new ArrayList<>();
            for(Object item:old){
                int itemId=itemClass.getField("a").getInt(item);
                String title=(String)itemClass.getField("c").get(item);
                if(itemId==SOURCES){found=true;items.add(item);}
                else if(!"عالم المصادر".equals(title)&&!"HiTV".equalsIgnoreCase(title))items.add(item);
            }
            if(!found){
                int icon=a.getResources().getIdentifier("ic_anime","drawable",a.getPackageName());
                Object source=itemClass.getConstructor(int.class,int.class,String.class,String.class,boolean.class).newInstance(SOURCES,icon,"عالم المصادر","atheer_sources",false);
                items.add(Math.min(2,items.size()),source);
            }
            Object callback=bar.getClass().getMethod("getOnItemSelected").invoke(bar);
            if(callback!=wrappers.get(bar)){
                Class<?> type=bar.getClass().getMethod("getOnItemSelected").getReturnType();
                Object wrapped=Proxy.newProxyInstance(bar.getClass().getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class){if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("equals"))return proxy==args[0];return "AtheerTabs";}
                    if(args!=null&&args.length==1&&itemClass.isInstance(args[0])&&itemClass.getField("a").getInt(args[0])==SOURCES){ModuleRuntime.open(a);return false;}
                    return callback==null?false:method.invoke(callback,args);
                });
                wrappers.put(bar,wrapped);bar.getClass().getMethod("setOnItemSelected",type).invoke(bar,wrapped);
            }
            if(!found||items.size()!=old.size())bar.getClass().getMethod("setItems",List.class).invoke(bar,items);
        }catch(ReflectiveOperationException|RuntimeException e){android.util.Log.e("Atheer","Source tab integration",e);}
    }
}
