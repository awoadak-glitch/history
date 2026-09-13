package awr.witcher;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * HiTV playback surface. The supplied HiTV build contains ExoPlayer UI resources; Anime Witcher
 * already ships the legacy com.google.android.exoplayer2 runtime, so we reuse that player via
 * reflection instead of adding another media engine to the APK.
 */
final class HitvPlayer {
    private HitvPlayer(){}

    static void play(Activity activity,String title,String url,String type){
        if(url==null||!(url.startsWith("https://")||url.startsWith("http://"))){alert(activity,"رابط تشغيل HiTV غير صالح.");return;}
        final Dialog dialog=new Dialog(activity,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root=new FrameLayout(activity);root.setBackgroundColor(Color.BLACK);root.setKeepScreenOn(true);
        final Object[] playerBox=new Object[1];
        try{
            Class<?> playerViewClass=Class.forName("com.google.android.exoplayer2.ui.PlayerView");
            View playerView=(View)playerViewClass.getConstructor(Context.class).newInstance(activity);
            root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
            Object player=buildPlayer(activity);playerBox[0]=player;
            Class<?> playerInterface=Class.forName("com.google.android.exoplayer2.Player");
            playerViewClass.getMethod("setPlayer",playerInterface).invoke(playerView,player);
            try{playerViewClass.getMethod("setUseController",boolean.class).invoke(playerView,true);}catch(Exception ignored){}
            try{playerViewClass.getMethod("setControllerAutoShow",boolean.class).invoke(playerView,true);}catch(Exception ignored){}
            Object mediaItem=mediaItem(url,type);
            Class<?> mediaItemClass=Class.forName("com.google.android.exoplayer2.MediaItem");
            player.getClass().getMethod("setMediaItem",mediaItemClass).invoke(player,mediaItem);
            player.getClass().getMethod("prepare").invoke(player);
            try{player.getClass().getMethod("play").invoke(player);}catch(Exception e){try{player.getClass().getMethod("setPlayWhenReady",boolean.class).invoke(player,true);}catch(Exception ignored){}}
        }catch(Throwable error){
            dialog.dismiss();fallback(activity,title,url,type);return;
        }

        LinearLayout top=new LinearLayout(activity);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(Ui.dp(activity,14),Ui.dp(activity,8),Ui.dp(activity,8),Ui.dp(activity,8));top.setBackgroundColor(0x66000000);
        TextView name=new TextView(activity);name.setText(title==null?"HiTV":title);name.setTextColor(Color.WHITE);name.setTextSize(16);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        top.addView(name,new LinearLayout.LayoutParams(0,Ui.dp(activity,48),1));
        TextView close=new TextView(activity);close.setText("×");close.setTextColor(Color.WHITE);close.setTextSize(34);close.setGravity(Gravity.CENTER);close.setContentDescription("إغلاق المشغل");close.setOnClickListener(v->dialog.dismiss());top.addView(close,new LinearLayout.LayoutParams(Ui.dp(activity,52),Ui.dp(activity,52)));
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,Ui.dp(activity,64),Gravity.TOP);root.addView(top,tp);

        dialog.setContentView(root);dialog.setOnDismissListener(d->release(playerBox[0]));
        Window w=dialog.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}
        dialog.show();if(w!=null)w.setLayout(-1,-1);
    }

    private static Object buildPlayer(Context context)throws Exception{
        try{
            Class<?> builder=Class.forName("com.google.android.exoplayer2.ExoPlayer$Builder");Object b=builder.getConstructor(Context.class).newInstance(context);return builder.getMethod("build").invoke(b);
        }catch(Throwable first){
            Class<?> builder=Class.forName("com.google.android.exoplayer2.SimpleExoPlayer$Builder");Object b=builder.getConstructor(Context.class).newInstance(context);return builder.getMethod("build").invoke(b);
        }
    }
    private static Object mediaItem(String url,String type)throws Exception{
        Class<?> media=Class.forName("com.google.android.exoplayer2.MediaItem");String mime=mime(type,url);
        try{
            Class<?> builder=Class.forName("com.google.android.exoplayer2.MediaItem$Builder");Object b=builder.getConstructor().newInstance();
            try{builder.getMethod("setUri",String.class).invoke(b,url);}catch(Exception e){builder.getMethod("setUri",Uri.class).invoke(b,Uri.parse(url));}
            if(mime!=null)try{builder.getMethod("setMimeType",String.class).invoke(b,mime);}catch(Exception ignored){}
            return builder.getMethod("build").invoke(b);
        }catch(Throwable ignored){
            try{return media.getMethod("fromUri",String.class).invoke(null,url);}catch(Exception e){return media.getMethod("fromUri",Uri.class).invoke(null,Uri.parse(url));}
        }
    }
    private static String mime(String type,String url){String t=type==null?"":type.toLowerCase(Locale.ROOT),u=url.toLowerCase(Locale.ROOT);if(t.contains("m3u8")||t.contains("hls")||u.contains(".m3u8"))return "application/x-mpegURL";if(t.contains("mpd")||t.contains("dash")||u.contains(".mpd"))return "application/dash+xml";if(t.contains("mp4")||u.contains(".mp4"))return "video/mp4";return null;}
    private static void release(Object player){if(player==null)return;try{player.getClass().getMethod("release").invoke(player);}catch(Exception ignored){}}
    private static void fallback(Activity a,String title,String url,String type){
        try{boolean segmented="application/x-mpegURL".equals(mime(type,url));a.startActivity(Media.mxIntent(url,Collections.emptyMap(),title,segmented));}
        catch(Exception e){alert(a,"تعذر تشغيل هذا المصدر بمحرك HiTV أو MX Player.");}
    }
    private static void alert(Activity a,String message){if(!a.isFinishing()&&!a.isDestroyed())new AlertDialog.Builder(a).setMessage(message).setPositiveButton("حسناً",null).show();}
}
