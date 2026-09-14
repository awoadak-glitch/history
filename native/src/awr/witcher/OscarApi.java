package awr.witcher;

import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Oscar TV 1.1.4 public catalogue adapter, reconstructed from its Retrofit ApiService map. */
final class OscarApi {
    private static final String[] BASES={"https://ostvapp.cam/","https://admin.dramaramadan.net/"};
    private static final ExecutorService IO=Executors.newFixedThreadPool(4);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String UA="OscarTV/1.1.4 (Android; com.drama.mp4)";
    interface Callback { void done(Object value,String error); }
    private OscarApi(){}

    static void get(String path,Map<String,String> params,Callback cb){
        IO.execute(()->{
            Object value=null;String error=null;
            for(String base:BASES){
                HttpURLConnection c=null;
                try{
                    String u=base+path+(params==null||params.isEmpty()?"":"?"+query(params));
                    c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(14000);c.setReadTimeout(22000);c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("Accept","application/json, text/plain, */*");c.setRequestProperty("User-Agent",UA);
                    c.setRequestProperty("X-Requested-With","com.drama.mp4");c.setRequestProperty("Referer","https://oscartv.app/");c.setRequestProperty("Origin","https://oscartv.app");
                    int code=c.getResponseCode();String body=read(code>=200&&code<300?c.getInputStream():c.getErrorStream(),7*1024*1024);
                    if(code<200||code>=300){error="Oscar TV HTTP "+code;continue;}
                    value=parse(body);error=null;break;
                }catch(Exception e){error="تعذر الاتصال بمصدر Oscar TV"+(e.getMessage()==null?"":" · "+shortMsg(e.getMessage()));}
                finally{if(c!=null)c.disconnect();}
            }
            Object v=value;String e=error;MAIN.post(()->cb.done(v,e));
        });
    }
    static Map<String,String> params(Object... pairs){LinkedHashMap<String,String> m=new LinkedHashMap<>();for(int i=0;i+1<pairs.length;i+=2)if(pairs[i+1]!=null)m.put(String.valueOf(pairs[i]),String.valueOf(pairs[i+1]));return m;}
    static String absolute(String url){
        if(url==null)return "";String s=url.trim();if(s.startsWith("http://")||s.startsWith("https://"))return s;if(s.startsWith("//"))return "https:"+s;if(s.startsWith("/"))return "https://admin.dramaramadan.net"+s;return s;
    }
    private static Object parse(String body)throws Exception{String s=body==null?"":body.trim();if(s.startsWith("{"))return new JSONObject(s);if(s.startsWith("["))return new JSONArray(s);throw new IOException("Oscar response is not JSON");}
    private static String query(Map<String,String> p)throws Exception{ArrayList<String> a=new ArrayList<>();for(Map.Entry<String,String> e:p.entrySet())a.add(URLEncoder.encode(e.getKey(),"UTF-8")+"="+URLEncoder.encode(e.getValue(),"UTF-8"));return android.text.TextUtils.join("&",a);}
    private static String read(InputStream in,int max)throws Exception{if(in==null)return "";ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;try(InputStream x=in){while((n=x.read(b))!=-1){o.write(b,0,n);if(o.size()>max)throw new IOException("response too large");}}return new String(o.toByteArray(),StandardCharsets.UTF_8);}
    private static String shortMsg(String s){String t=s.replace('\n',' ').replace('\r',' ').trim();return t.length()>90?t.substring(0,90):t;}
}
