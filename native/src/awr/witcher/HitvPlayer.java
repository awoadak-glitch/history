package awr.witcher;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Internal host ExoPlayer adapter. The protected HiTV VideoPlayer has not been transplanted. */
final class HitvPlayer {
    private static final String EXO="com.google.android.exoplayer2.";
    private HitvPlayer(){}
    static void play(Activity activity,String title,HitvExperience.Source source){
        if(!HitvPlayback.media(source.url)){alert(activity,"رابط تشغيل HiTV غير صالح.");return;}
        new Session(activity,title,source).show();
    }
    static final class Session implements Application.ActivityLifecycleCallbacks {
        final Activity activity;final String title;final HitvExperience.Source source;
        Dialog dialog;Object player;Class<?> playerApi;boolean released,registered;ProgressBar loading;TextView error;
        Session(Activity a,String t,HitvExperience.Source s){activity=a;title=t;source=s;}
        void show(){
            if(activity.isFinishing()||activity.isDestroyed())return;
            dialog=new Dialog(activity,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            FrameLayout root=new FrameLayout(activity);root.setBackgroundColor(Color.BLACK);root.setKeepScreenOn(true);
            try{
                playerApi=Class.forName(EXO+"Player");Class<?> viewClass=Class.forName(EXO+"ui.PlayerView");
                View view=(View)viewClass.getConstructor(Context.class).newInstance(activity);root.addView(view,new FrameLayout.LayoutParams(-1,-1));
                player=buildPlayer(activity,source.headers);viewClass.getMethod("setPlayer",playerApi).invoke(view,player);
                loading=new ProgressBar(activity);root.addView(loading,new FrameLayout.LayoutParams(Ui.dp(activity,42),Ui.dp(activity,42),Gravity.CENTER));
                error=button("",v->retry());error.setVisibility(View.GONE);FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);ep.setMargins(Ui.dp(activity,28),0,Ui.dp(activity,28),0);root.addView(error,ep);
                Class<?> listener=Class.forName(EXO+"Player$Listener");
                Object events=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class){if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("equals"))return proxy==args[0];return "HiTVPlayerListener";}
                    if(!released&&"onPlaybackStateChanged".equals(method.getName()))loading.setVisibility(((Integer)args[0])==2?View.VISIBLE:View.GONE);
                    if(!released&&"onPlayerError".equals(method.getName())){loading.setVisibility(View.GONE);error.setText("تعذر تشغيل هذا المصدر. اضغط لإعادة المحاولة");error.setVisibility(View.VISIBLE);}
                    return null;
                });
                call("addListener",new Class<?>[]{listener},events);
                call("setMediaItem",new Class<?>[]{Class.forName(EXO+"MediaItem")},mediaItem(source));
                call("prepare",new Class<?>[0]);call("setPlayWhenReady",new Class<?>[]{boolean.class},true);
                LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(Ui.dp(activity,12),0,Ui.dp(activity,6),0);bar.setBackgroundColor(0x88000000);
                TextView name=button(title==null?"HiTV":title,v->{});name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);bar.addView(name,new LinearLayout.LayoutParams(0,Ui.dp(activity,54),1));
                bar.addView(button("السرعة",v->speed()),new LinearLayout.LayoutParams(Ui.dp(activity,70),Ui.dp(activity,54)));
                if(!source.captions.isEmpty())bar.addView(button("الترجمة",v->captions()),new LinearLayout.LayoutParams(Ui.dp(activity,74),Ui.dp(activity,54)));
                TextView close=button("×",v->dialog.dismiss());close.setTextSize(30);close.setContentDescription("إغلاق المشغل");bar.addView(close,new LinearLayout.LayoutParams(Ui.dp(activity,46),Ui.dp(activity,54)));
                root.addView(bar,new FrameLayout.LayoutParams(-1,Ui.dp(activity,54),Gravity.TOP));
                dialog.setContentView(root);dialog.setOnDismissListener(d->release());dialog.show();
                Window w=dialog.getWindow();if(w!=null){w.setLayout(-1,-1);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
                activity.getApplication().registerActivityLifecycleCallbacks(this);registered=true;
            }catch(ReflectiveOperationException|RuntimeException|LinkageError e){release();dialog.dismiss();alert(activity,"تعذر تهيئة المشغل الداخلي لهذا الجهاز.");}
        }
        Object call(String name,Class<?>[] types,Object... args)throws ReflectiveOperationException{return playerApi.getMethod(name,types).invoke(player,args);}
        TextView button(String text,View.OnClickListener action){TextView t=Ui.text(activity,text,14,true);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);t.setPadding(Ui.dp(activity,6),Ui.dp(activity,8),Ui.dp(activity,6),Ui.dp(activity,8));t.setOnClickListener(action);return t;}
        void retry(){if(released)return;try{error.setVisibility(View.GONE);loading.setVisibility(View.VISIBLE);call("prepare",new Class<?>[0]);call("setPlayWhenReady",new Class<?>[]{boolean.class},true);}catch(Exception e){error.setVisibility(View.VISIBLE);}}
        void speed(){String[] labels={"0.5×","0.75×","1×","1.25×","1.5×","2×"};float[] values={.5f,.75f,1f,1.25f,1.5f,2f};new AlertDialog.Builder(activity).setTitle("سرعة التشغيل").setItems(labels,(d,i)->{if(released)return;try{call("setPlaybackSpeed",new Class<?>[]{float.class},values[i]);}catch(Exception e){alert(activity,"تعذر تغيير السرعة.");}}).show();}
        void captions(){ArrayList<String> labels=new ArrayList<>();labels.add("إيقاف الترجمة");for(HitvPlayback.Caption c:source.captions)labels.add(c.label);new AlertDialog.Builder(activity).setTitle("الترجمة").setItems(labels.toArray(new String[0]),(d,i)->{
            if(released)return;try{
                Class<?> parameters=Class.forName(EXO+"trackselection.TrackSelectionParameters"),builder=Class.forName(EXO+"trackselection.TrackSelectionParameters$Builder");
                Object current=call("getTrackSelectionParameters",new Class<?>[0]);Object b=parameters.getMethod("buildUpon").invoke(current);
                builder.getMethod("setTrackTypeDisabled",int.class,boolean.class).invoke(b,3,i==0);
                if(i>0)builder.getMethod("setPreferredTextLanguage",String.class).invoke(b,source.captions.get(i-1).language);
                call("setTrackSelectionParameters",new Class<?>[]{parameters},builder.getMethod("build").invoke(b));
            }catch(Exception e){alert(activity,"تعذر اختيار هذه الترجمة.");}
        }).show();}
        void release(){if(released)return;released=true;if(player!=null)try{call("release",new Class<?>[0]);}catch(Exception ignored){}player=null;if(registered){activity.getApplication().unregisterActivityLifecycleCallbacks(this);registered=false;}}
        public void onActivityStopped(Activity a){if(a==activity){if(dialog!=null)dialog.dismiss();release();}}
        public void onActivityDestroyed(Activity a){if(a==activity){if(dialog!=null)dialog.dismiss();release();}}
        public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}
    }
    static Object buildPlayer(Context context,Map<String,String> headers)throws ReflectiveOperationException{
        Class<?> builder=Class.forName(EXO+"ExoPlayer$Builder");Object b=builder.getConstructor(Context.class).newInstance(context);
        Class<?> http=Class.forName(EXO+"upstream.DefaultHttpDataSource$Factory");Object factory=http.getConstructor().newInstance();http.getMethod("setDefaultRequestProperties",Map.class).invoke(factory,headers);
        Class<?> mediaFactory=Class.forName(EXO+"source.DefaultMediaSourceFactory");Object mf=mediaFactory.getConstructor(Class.forName(EXO+"upstream.DataSource$Factory")).newInstance(factory);
        builder.getMethod("setMediaSourceFactory",Class.forName(EXO+"source.MediaSource$Factory")).invoke(b,mf);
        Class<?> audio=Class.forName(EXO+"audio.AudioAttributes");builder.getMethod("setAudioAttributes",audio,boolean.class).invoke(b,audio.getField("DEFAULT").get(null),true);
        builder.getMethod("setHandleAudioBecomingNoisy",boolean.class).invoke(b,true);
        return builder.getMethod("build").invoke(b);
    }
    static Object mediaItem(HitvExperience.Source source)throws ReflectiveOperationException{
        Class<?> builder=Class.forName(EXO+"MediaItem$Builder");Object b=builder.getConstructor().newInstance();builder.getMethod("setUri",String.class).invoke(b,source.url);
        String mime=mime(source.type,source.url);if(mime!=null)builder.getMethod("setMimeType",String.class).invoke(b,mime);
        if(!source.captions.isEmpty()){
            Class<?> subtitle=Class.forName(EXO+"MediaItem$SubtitleConfiguration$Builder");ArrayList<Object> list=new ArrayList<>();
            for(HitvPlayback.Caption c:source.captions){Object sub=subtitle.getConstructor(Uri.class).newInstance(Uri.parse(c.url));subtitle.getMethod("setMimeType",String.class).invoke(sub,c.mime);subtitle.getMethod("setLanguage",String.class).invoke(sub,c.language);subtitle.getMethod("setLabel",String.class).invoke(sub,c.label);subtitle.getMethod("setSelectionFlags",int.class).invoke(sub,c.selected?1:0);list.add(subtitle.getMethod("build").invoke(sub));}
            builder.getMethod("setSubtitleConfigurations",List.class).invoke(b,list);
        }
        return builder.getMethod("build").invoke(b);
    }
    static String mime(String type,String url){String t=type==null?"":type.toLowerCase(Locale.ROOT),u=url.toLowerCase(Locale.ROOT);if(t.contains("m3u8")||t.contains("hls")||t.contains("mpegurl")||u.contains(".m3u8"))return "application/x-mpegURL";if(t.contains("mpd")||t.contains("dash")||u.contains(".mpd"))return "application/dash+xml";if(t.contains("mp4")||u.contains(".mp4"))return "video/mp4";return null;}
    private static void alert(Activity a,String message){if(!a.isFinishing()&&!a.isDestroyed())new AlertDialog.Builder(a).setMessage(message).setPositiveButton("حسناً",null).show();}
}
