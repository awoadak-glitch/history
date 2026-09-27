package com.atheer.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import awr.witcher.Media;
import awr.witcher.StreamCodec;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
                String raw,deep,type,title;
                if("channel".equals(kind)) {
                    raw=call("getStreamUrl");
                    deep=call("getDeepLink");
                    type=call("getStreamType");
                } else {
                    raw=call("getUrl");
                    deep=call("getDeepLink");
                    type=call("getType");
                }
                title=join(call("getServerName"),"movie".equals(kind)?call("getQualityLabel"):call("getQuality"));
                if(raw.isEmpty()&&deep.isEmpty()) {
                    alert(activity,"تعذر استخراج رابط صالح لهذا السيرفر. جرّب سيرفراً آخر.");
                    return true;
                }
                // Oscar's own click path deliberately prefers deep_link. Keep both values here:
                // OscarResolver can unwrap a player deep link, validate the raw fallback, carry the
                // required Referer/Cookie headers, and only then hand a real media URL to MX.
                Media.openExternal(activity,raw,deep,type,title.isEmpty()?"PALMA":title);
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

    private static void alert(Activity activity,String message) {
        if(!activity.isFinishing()&&!activity.isDestroyed())new AlertDialog.Builder(activity).setMessage(message).setPositiveButton("حسناً",null).show();
    }
}
