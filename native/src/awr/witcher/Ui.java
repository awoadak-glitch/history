package awr.witcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;

/** Native widgets follow the host application's existing theme resources. */
public final class Ui {
    private Ui() {}
    public static final int ACCENT = 0xffeec60a;
    private static final ExecutorService IMAGES = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(18 * 1024 * 1024) {
        protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    public static int dp(Context c, float n) { return Math.round(c.getResources().getDisplayMetrics().density * n); }
    public static int color(Context c, String name, int fallback) {
        int id = c.getResources().getIdentifier(name, "color", c.getPackageName());
        return id == 0 ? fallback : c.getResources().getColor(id);
    }
    public static int bg(Context c) { return color(c, "windowBackground", 0xff161617); }
    public static int surface(Context c) { return color(c, "colorPrimary", 0xff28282c); }
    public static int textColor(Context c) { return color(c, "textColor", Color.WHITE); }
    public static int muted(Context c) { return color(c, "textColor2", 0xffbfbfbf); }
    public static GradientDrawable rounded(Context c, int color, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius)); return d;
    }
    public static void clickable(View v, int color, float radius) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30eec60a), rounded(v.getContext(), color, radius), rounded(v.getContext(), Color.WHITE, radius)));
    }
    public static TextView text(Context c, String s, int size, boolean bold) {
        TextView v = new TextView(c); v.setText(s); v.setTextSize(size); v.setTextColor(textColor(c));
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        Typeface face=null;if(android.os.Build.VERSION.SDK_INT>=26){int id=c.getResources().getIdentifier("montserrat","font",c.getPackageName());if(id!=0)try{face=c.getResources().getFont(id);}catch(Exception ignored){}}
        v.setTypeface(face==null?Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL):Typeface.create(face,bold?Typeface.BOLD:Typeface.NORMAL));
        v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); return v;
    }
    public static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return v;
    }
    public static LinearLayout row(Context c) {
        LinearLayout v = new LinearLayout(c); v.setGravity(Gravity.CENTER_VERTICAL); v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return v;
    }
    public static TextView button(Context c, String s, View.OnClickListener action) {
        TextView v = text(c, s, 15, true); v.setGravity(Gravity.CENTER); v.setMinHeight(dp(c,48));
        v.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10)); clickable(v,surface(c),12); v.setOnClickListener(action); return v;
    }
    public static ImageView image(Context c, String url, int radius) {
        ImageView v = new ImageView(c); v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setBackground(rounded(c,surface(c),radius)); v.setClipToOutline(true); load(v,url); return v;
    }
    public static void load(ImageView view, String url) {
        view.setTag(url); if(url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
        Bitmap cached = CACHE.get(url); if(cached != null){ view.setImageBitmap(cached); return; }
        WeakReference<ImageView> ref = new WeakReference<>(view);
        IMAGES.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL target=new URL(url);URLConnection opened=HitvNet.open(target);connection=(HttpURLConnection)opened;connection.setConnectTimeout(12000);connection.setReadTimeout(16000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/127.0 Mobile Safari/537.36");
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                try(InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[16384]; int n;
                    while((n=in.read(buffer))!=-1){data.write(buffer,0,n);if(data.size()>10*1024*1024)throw new IOException("Image too large");}
                }
                byte[] bytes=data.toByteArray(); BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);o.inSampleSize=1;
                while(o.outWidth/o.inSampleSize>1200 || o.outHeight/o.inSampleSize>1600)o.inSampleSize*=2;
                o.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
                if(bitmap==null)return;CACHE.put(url,bitmap);
                MAIN.post(() -> { ImageView v=ref.get();if(v!=null && url.equals(v.getTag()))v.setImageBitmap(bitmap); });
            } catch(Exception ignored) { /* Leave themed placeholder; never block content loading. */ }
            finally { if(connection!=null)connection.disconnect(); }
        });
    }
    public static final class Icon extends View {
        private final String name; private int color; private final Paint p=new Paint(3);
        public Icon(Context c,String name,int color){super(c);this.name=name;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        public void tint(int value){color=value;invalidate();}
        protected void onDraw(Canvas c){
            super.onDraw(c);float size=Math.min(getWidth()-getPaddingLeft()-getPaddingRight(),getHeight()-getPaddingTop()-getPaddingBottom());
            c.save();c.translate((getWidth()-size)/2f,(getHeight()-size)/2f);c.scale(size/24f,size/24f);
            p.setColor(color);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
            Path path=new Path();
            switch(name){
                case "search":c.drawCircle(10,10,6,p);c.drawLine(15,15,21,21,p);break;
                case "back":path.moveTo(9,4);path.lineTo(17,12);path.lineTo(9,20);c.drawPath(path,p);break;
                case "anime":c.drawRoundRect(3,3,21,21,6,6,p);c.drawLine(8,9,8,12,p);c.drawLine(16,9,16,12,p);c.drawArc(8,11,16,17,15,150,false,p);break;
                case "series":c.drawRoundRect(3,6,21,20,3,3,p);c.drawLine(8,2,12,6,p);c.drawLine(16,2,12,6,p);c.drawLine(7,16,17,16,p);break;
                case "movies":c.drawRoundRect(3,7,21,21,2,2,p);c.drawLine(3,11,21,11,p);c.drawLine(3,6,20,2,p);c.drawLine(7,5,10,9,p);c.drawLine(14,3,17,7,p);break;
                case "channels":c.drawCircle(12,12,2,p);c.drawArc(6,6,18,18,-55,110,false,p);c.drawArc(6,6,18,18,125,110,false,p);c.drawArc(2,2,22,22,-55,110,false,p);c.drawArc(2,2,22,22,125,110,false,p);break;
                case "hitv":c.drawRoundRect(3,5,21,19,3,3,p);path.moveTo(10,9);path.lineTo(16,12);path.lineTo(10,15);path.close();p.setStyle(Paint.Style.FILL);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);c.drawLine(8,2,12,5,p);c.drawLine(16,2,12,5,p);break;
                case "download":c.drawLine(12,3,12,16,p);path.moveTo(7,11);path.lineTo(12,16);path.lineTo(17,11);c.drawPath(path,p);c.drawLine(5,21,19,21,p);break;
                default:p.setStyle(Paint.Style.FILL);path.moveTo(7,4);path.lineTo(21,12);path.lineTo(7,20);path.close();c.drawPath(path,p);
            } c.restore();
        }
    }
}