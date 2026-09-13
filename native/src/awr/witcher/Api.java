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
        if(tab==3)return "channel/by/filtres/"+category+"/0/"+page+"/";
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
                    // Never cache challenge pages or malformed envelopes.
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
    public static JSONArray array(Object value){return value instanceof JSONArray?(JSONArray)value:new JSONArray();}
    public static JSONObject object(Object value){return value instanceof JSONObject?(JSONObject)value:new JSONObject();}
    public static boolean channel(JSONObject o){return "channel".equals(o.optString("type"))||o.optBoolean("_channel");}
    public static boolean series(JSONObject o){return "serie".equals(o.optString("type"))||"series".equals(o.optString("type"));}
    public static boolean publicAccess(String value){return value==null||value.isEmpty()||"1".equals(value);}
    public static String label(JSONObject o){return o.optString("title",o.optString("name",""));}
}
