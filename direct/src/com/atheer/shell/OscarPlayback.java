package com.atheer.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import awr.witcher.Media;
import awr.witcher.StreamCodec;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.WeakHashMap;

/** Replaces only Oscar watch-row clicks; download rows retain their original behavior. */
public final class OscarPlayback {
    private static final String TAG="PalmaMx";
    private static final Map<View,View.OnClickListener> HOOKS=new WeakHashMap<>();
    private static boolean hiddenReady;
    private OscarPlayback() {}

    static void scan(Activity activity) {
        if(activity.isFinishing()||activity.isDestroyed())return;
        enableViewReflection();
        scan(activity,activity.getWindow().getDecorView());
    }

    private static void scan(Activity activity,View view) {
        if(view.hasOnClickListeners())hook(activity,view);
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)scan(activity,group.getChildAt(i));
        }
    }

    private static void hook(Activity activity,View view) {
        try {
            View.OnClickListener current=listenerOf(view);
            if(current==null||current instanceof MxClick||HOOKS.get(view)==current)return;
            Target target=Target.from(current);
            if(target==null)return;
            MxClick replacement=new MxClick(activity,current,target);
            HOOKS.put(view,replacement);
            view.setOnClickListener(replacement);
        } catch(ReflectiveOperationException|RuntimeException error) {
            Log.w(TAG,"Unable to inspect click listener",error);
        }
    }

    private static void enableViewReflection() {
        if(hiddenReady)return;
        hiddenReady=true;
        try {
            Class<?> pass=Class.forName("org.lsposed.hiddenapibypass.LSPass");
            Method add=pass.getMethod("addHiddenApiExemptions",String[].class);
            add.invoke(null,(Object)new String[]{"Landroid/view/View;","Landroid/view/View$ListenerInfo;"});
        } catch(ReflectiveOperationException|LinkageError error) {
            Log.w(TAG,"Hidden API exemption unavailable",error);
        }
    }

    private static View.OnClickListener listenerOf(View view) throws ReflectiveOperationException {
        Field infoField=View.class.getDeclaredField("mListenerInfo");
        infoField.setAccessible(true);
        Object info=infoField.get(view);
        if(info==null)return null;
        Field click=info.getClass().getDeclaredField("mOnClickListener");
        click.setAccessible(true);
        Object value=click.get(info);
        return value instanceof View.OnClickListener?(View.OnClickListener)value:null;
    }

    private static final class MxClick implements View.OnClickListener {
        final Activity activity;
        final View.OnClickListener original;
        final Target target;
        MxClick(Activity activity,View.OnClickListener original,Target target){this.activity=activity;this.original=original;this.target=target;}
        @Override public void onClick(View view){if(!target.open(activity))original.onClick(view);}
    }

    private static final class Target {
        final Object model;
        final String kind;
        Target(Object model,String kind){this.model=model;this.kind=kind;}

        static Target from(View.OnClickListener listener) throws ReflectiveOperationException {
            for(Class<?> owner=listener.getClass();owner!=null&&owner!=Object.class;owner=owner.getSuperclass()) {
                for(Field field:owner.getDeclaredFields()) {
                    if(Modifier.isStatic(field.getModifiers())||field.getType().isPrimitive())continue;
                    field.setAccessible(true);Object value=field.get(listener);if(value==null)continue;
                    String name=value.getClass().getName();
                    if("com.drama.mp4.data.model.WatchLink".equals(name))return new Target(value,"watch");
                    if("com.drama.mp4.data.model.ChannelStream".equals(name))return new Target(value,"channel");
                    if("com.drama.mp4.data.model.MovieLink".equals(name)&&isMovieWatch(listener))return new Target(value,"movie");
                }
            }
            return null;
        }

        static boolean isMovieWatch(View.OnClickListener listener) {
            if(!"ij2".equals(listener.getClass().getName()))return false;
            try{Field mode=listener.getClass().getDeclaredField("o");mode.setAccessible(true);return mode.getInt(listener)!=0;}
            catch(ReflectiveOperationException error){return false;}
        }

        boolean open(Activity activity) {
            try {
                String url,type,title;
                if("channel".equals(kind)) {
                    url=firstWeb(call("getStreamUrl"),call("getDeepLink"));
                    type=call("getStreamType");
                } else {
                    url=firstWeb(call("getUrl"),call("getDeepLink"));
                    type=call("getType");
                }
                title=join(call("getServerName"),"movie".equals(kind)?call("getQualityLabel"):call("getQuality"));
                if(url==null) {
                    alert(activity,"تعذر استخراج رابط صالح لهذا السيرفر. جرّب سيرفراً آخر.");
                    return true;
                }
                Media.openExternal(activity,url,type,title.isEmpty()?"PALMA":title);
                return true;
            } catch(ReflectiveOperationException|RuntimeException error) {
                Log.e(TAG,"MX handoff failed",error);
                alert(activity,"تعذر تجهيز هذا السيرفر لمشغل MX.");
                return true;
            }
        }

        String call(String name) throws ReflectiveOperationException {
            try{Object value=model.getClass().getMethod(name).invoke(model);return value==null?"":String.valueOf(value).trim();}
            catch(NoSuchMethodException missing){return "";}
        }
    }

    private static String join(String a,String b){
        if(a==null)a="";if(b==null)b="";a=a.trim();b=b.trim();
        return a.isEmpty()?b:(b.isEmpty()?a:a+" • "+b);
    }

    /** Prefer Oscar's raw URL over its player-specific deep link, then unwrap common deep-link forms. */
    private static String firstWeb(String raw,String deep) {
        for(String candidate:new String[]{raw,deep}) {
            String resolved=web(candidate);
            if(resolved!=null)return resolved;
        }
        return null;
    }

    private static String web(String value) {
        if(value==null)return null;String clean=value.trim();if(clean.isEmpty())return null;
        if(clean.startsWith("http://")||clean.startsWith("https://"))return clean;
        try{return StreamCodec.forExternalPlayer(clean);}catch(RuntimeException ignored){}
        try {
            Uri uri=Uri.parse(clean);
            for(String key:new String[]{"url","link","video","stream","src"}) {
                String nested=uri.getQueryParameter(key);String result=webDecoded(nested);if(result!=null)return result;
            }
        } catch(RuntimeException ignored) {}
        String decoded=webDecoded(clean);if(decoded!=null)return decoded;
        int http=clean.indexOf("http");return http<0?null:webDecoded(clean.substring(http));
    }

    private static String webDecoded(String value) {
        if(value==null||value.isEmpty())return null;
        String decoded=Uri.decode(value).trim();
        if(decoded.startsWith("http://")||decoded.startsWith("https://"))return decoded;
        for(int flags:new int[]{Base64.DEFAULT,Base64.URL_SAFE|Base64.NO_WRAP})try {
            String plain=new String(Base64.decode(decoded,flags),StandardCharsets.UTF_8).trim();
            if(plain.startsWith("http://")||plain.startsWith("https://"))return plain;
        } catch(IllegalArgumentException ignored) {}
        return null;
    }

    private static void alert(Activity activity,String message) {
        if(!activity.isFinishing()&&!activity.isDestroyed())new AlertDialog.Builder(activity).setMessage(message).setPositiveButton("حسناً",null).show();
    }
}
