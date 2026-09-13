package awr.witcher;

import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Read-only Drama World routes; no identity emulation or validation replay. */
public final class Api {
    public interface Callback { void result(Object value, String error); }
    public static final ExecutorService IO=Executors.newFixedThreadPool(3);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Map<String,Entry> CACHE=new LinkedHashMap<String,Entry>(32,0.75f,true){
        protected boolean removeEldestEntry(Map.Entry<String,Api.Entry> e){return size()>36;}
    };
    private static final class Entry { final String text; final long time=System.currentTimeMillis(); Entry(String t){text=t;} }
    private Api(){}
    public static String list(int tab,int category,String order,int page){
        return list(tab,category,0,order,page);
    }
    public static String list(int tab,int category,int country,String order,int page){
        if(tab==3)return "channel/by/filtres/"+category+"/"+country+"/"+page+"/";
        return (tab==1?"serie":"movie")+"/by/filtres/"+category+"/"+Uri.encode(order)+"/"+page+"/";
    }
    public static String search(String query,int page){return "search/"+Uri.encode(query)+"/"+page+"/";}
    public static String detail(boolean channel,int id){return (channel?"channel":"movie")+"/by/"+id+"/";}
    public static String sources(boolean episode,int id){return (episode?"episode":"movie")+"/source/by/"+id+"/";}
    public static void get(String route,Callback callback){
        IO.execute(()->{
            Object value=null;String error=null;
            try{
                String raw=null;
                synchronized(CACHE){Entry e=CACHE.get(route);if(e!=null && System.currentTimeMillis()-e.time<90000)raw=e.text;}
                if(raw==null){
                    raw=read(ApiConfig.BASE+route+ApiConfig.SUFFIX,Collections.emptyMap(),8*1024*1024);
                    decode(raw);synchronized(CACHE){CACHE.put(route,new Entry(raw));}
                }
                value=decode(raw);
            }catch(SocketTimeoutException e){error="انتهت مهلة الاتصال. حاول مرة أخرى.";}
            catch(HttpError e){error=e.code==403?"رفض مصدر المحتوى الاتصال (403). حاول من اتصال آخر أو أعد المحاولة لاحقاً.":"تعذر جلب المحتوى ("+e.code+").";}
            catch(Exception e){error="تعذر الاتصال بمصدر المحتوى أو قراءة رده. أعد المحاولة.";}
            final Object v=value;final String err=error;MAIN.post(()->callback.result(v,err));
        });
    }
    public static final class HttpError extends IOException {public final int code;HttpError(int n){super("HTTP "+n);code=n;}}
    public static final class Response {public final String text,url;Response(String t,String u){text=t;url=u;}}
    public static String read(String url,Map<String,String> headers,int limit)throws IOException{
        return readResponse(url,headers,limit).text;
    }
    public static Response readResponse(String url,Map<String,String> headers,int limit)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("User-Agent","okhttp/4.12.0");
        for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
        try{
            int status=c.getResponseCode();if(status<200||status>=300)throw new HttpError(status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] block=new byte[16384];int count;
                while((count=in.read(block))!=-1){bytes.write(block,0,count);if(bytes.size()>limit)throw new IOException("Response too large");}
                return new Response(new String(bytes.toByteArray(),StandardCharsets.UTF_8),c.getURL().toString());
            }
        }finally{c.disconnect();}
    }
    public static Object decode(String raw)throws JSONException{
        String s=raw.trim();if(s.startsWith("\ufeff"))s=s.substring(1);
        if(s.startsWith("[")||s.startsWith("{"))return new JSONTokener(s).nextValue();
        int start=s.indexOf("W3s");
        if(start>=0){
            try{return new JSONArray(new String(Base64.decode(s.substring(start),Base64.DEFAULT),StandardCharsets.UTF_8));}
            catch(IllegalArgumentException e){throw new JSONException("Invalid server envelope");}
        }
        throw new JSONException("Not a content response");
    }

    /**
     * Source arrays are decorated with the same human-readable server names used by Drama World.
     * A "both" source is split into equivalent play/download views so each tab can keep its own
     * original label without changing the URL, order, access flag, or extractor metadata.
     */
    public static JSONArray array(Object value){
        if(!(value instanceof JSONArray))return new JSONArray();
        JSONArray input=(JSONArray)value;if(input.length()==0)return input;
        JSONObject first=input.optJSONObject(0);if(first==null||!first.has("url")||!first.has("kind"))return input;
        JSONArray out=new JSONArray();int play=0,download=0;
        for(int i=0;i<input.length();i++){
            JSONObject source=input.optJSONObject(i);if(source==null)continue;String kind=source.optString("kind");
            try{
                if("both".equals(kind)){
                    JSONObject p=new JSONObject(source.toString());p.put("kind","play");p.put("title",sourceTitle(p,++play,false));out.put(p);
                    JSONObject d=new JSONObject(source.toString());d.put("kind","download");d.put("title",sourceTitle(d,++download,true));out.put(d);
                }else if("download".equals(kind)){
                    JSONObject d=new JSONObject(source.toString());d.put("title",sourceTitle(d,++download,true));out.put(d);
                }else{
                    JSONObject p=new JSONObject(source.toString());p.put("title",sourceTitle(p,++play,false));out.put(p);
                }
            }catch(JSONException e){out.put(source);}
        }
        return out;
    }
    private static String sourceTitle(JSONObject source,int number,boolean download){
        String type=source.optString("type").toLowerCase(Locale.ROOT);
        if(download){
            if("webm".equals(type))return "سيرفر تحميل "+number+" : جودة متعددة";
            if("m3u8".equals(type))return "سيرفر تحميل "+number;
            if("mov".equals(type))return "سيرفر تحميل "+number+" : جودة HD";
            if("mp4".equals(type))return "سيرفر تحميل مباشر";
            if("mkv".equals(type))return "سيرفر تحميل ملف مضغوط";
            String original=source.optString("title");return original.isEmpty()?"سيرفر تحميل "+number:original;
        }
        if("embed".equals(type))return "تشغيل سيرفر "+number;
        if("webm".equals(type))return "سيرفر "+number+" : جودة متعددة";
        if("m3u8".equals(type))return "سيرفر "+number+" : تشغيل سريع";
        if("mov".equals(type))return "سيرفر "+number+" : جودة HD";
        if("mp4".equals(type)||"mkv".equals(type))return "تشغيل سيرفر مباشر";
        if("youtube".equals(type))return "تشغيل سيرفر YT";
        String original=source.optString("title");return original.isEmpty()?"سيرفر "+number:original;
    }
    public static JSONObject object(Object value){return value instanceof JSONObject?(JSONObject)value:new JSONObject();}
    public static boolean channel(JSONObject o){return "channel".equals(o.optString("type"))||o.optBoolean("_channel");}
    public static boolean series(JSONObject o){return "serie".equals(o.optString("type"))||"series".equals(o.optString("type"));}
    /** Drama World only treats access flag 2 or 3 as restricted. 0/1/empty are public. */
    public static boolean publicAccess(String value){String v=value==null?"":value.trim();return !"2".equals(v)&&!"3".equals(v);}
    public static String label(JSONObject o){return o.optString("title",o.optString("name",""));}
}
