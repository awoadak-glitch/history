package awr.witcher;

import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** Native HiTV client. Uses the complete public web endpoint family discovered in the user's HiTV project. */
final class HitvApi {
    static final String BASE="https://web-api.hitvpro.com";
    static final ExecutorService IO=Executors.newFixedThreadPool(8);
    private static final String PUBLIC_KEY_DER="MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCEZTSwZTZdBfQAm8oVroRdy6K7VVB0/7Jojx7e3UPa7NjjZ2A/mydH7QI9aHr3dNEJDrNLyZ/nEK6XzV+XTG3t3GVqsEjVOVO26K/VcDVwk/dFJs8bmK3AaIBPQATJQDztgSjg5H7u92JnyXvtj0XB0IH+GM2ui6sB69Y5T0LPAwIDAQAB";
    private static final SecureRandom RNG=new SecureRandom();
    private HitvApi(){}

    interface Callback {void done(Object value,String error);}
    private static final class Req {final String name,path;final JSONObject params;final boolean post;Req(String n,String p,JSONObject q,boolean x){name=n;path=p;params=q;post=x;}}

    static void get(String path,JSONObject params,Callback callback){
        if("/cms/web/hitv/movieDrama/getPlayInfo".equals(path)&&params!=null&&params.has("id")){
            playback(params.optString("id"),params.optInt("category",1),Math.max(1,params.optInt("seriesNo",1)),callback);return;
        }
        request(path,params,false,callback);
    }
    static void post(String path,JSONObject params,Callback callback){request(path,params,true,callback);}

    /** Pull every useful HiTV catalogue surface, not only album/page. */
    static void home(int type,Callback callback){
        ArrayList<Req> r=new ArrayList<>();
        r.add(new Req("firstPage","/cms/web/hitv/homePage/firstPage",params(),false));
        r.add(new Req("albums","/cms/web/hitv/homePage/album/page",params("sequence",0,"type",type),false));
        r.add(new Req("banners","/cms/web/pc/homePage/banners",params("type",type),false));
        r.add(new Req("singleAlbums","/cms/web/pc/homePage/singleAlbums",params("type",type),false));
        r.add(new Req("movies","/cms/web/hitv/movieDrama/moviePage",params(),false));
        r.add(new Req("dramas","/cms/web/hitv/movieDrama/dramaPage",params(),false));
        r.add(new Req("leaderboard","/cms/web/hitv/search/leaderboard",params(),false));
        r.add(new Req("desktopLeaderboard","/cms/web/pc/search/searchLeaderboard",params("type",type),false));
        aggregate(r,callback);
    }

    /** Search keyword + filtered search + suggestions + ranking, then merge results in the UI. */
    static void searchAll(String query,Callback callback){
        String q=query==null?"":query.trim();ArrayList<Req> r=new ArrayList<>();
        r.add(new Req("keyword","/cms/web/hitv/movieDrama/searchWithKeyWord",params("size",50,"searchKeyWord",q),true));
        r.add(new Req("filtered","/cms/web/hitv/search/search",params("page",0,"size",60,"searchKeyWord",q),true));
        r.add(new Req("suggestions","/cms/web/pc/search/searchLenovo",params("size",20,"searchKeyWord",q),true));
        r.add(new Req("leaderboard","/cms/web/hitv/search/leaderboard",params(),false));
        aggregate(r,callback);
    }

    static void detail(String id,int category,Callback callback){request("/cms/web/hitv/movieDrama/detail",params("id",id,"category",category),false,callback);}

    /** Merge all media variants available from the HiTV web family into one quality/source list. */
    static void playback(String id,int category,int episode,Callback callback){
        ArrayList<Req> r=new ArrayList<>();JSONObject p=params("id",id,"category",category,"seriesNo",episode);
        r.add(new Req("playInfo","/cms/web/hitv/movieDrama/getPlayInfo",p,false));
        r.add(new Req("downloadUrls","/cms/web/pc/download/urls",p,false));
        r.add(new Req("previewInfo","/cms/web/pc/movieDrama/previewInfo",p,true));
        aggregate(r,callback);
    }

    private static void aggregate(List<Req> reqs,Callback callback){
        if(reqs.isEmpty()){callback.done(new JSONArray(),null);return;}JSONArray merged=new JSONArray();AtomicInteger left=new AtomicInteger(reqs.size());AtomicInteger ok=new AtomicInteger();String[] lastError={null};Object lock=new Object();
        for(Req r:reqs){request(r.path,r.params,r.post,(value,error)->{
            synchronized(lock){if(error==null&&value!=null&&value!=JSONObject.NULL){JSONObject wrap=new JSONObject();try{wrap.put("source",r.name);wrap.put("data",value);merged.put(wrap);ok.incrementAndGet();}catch(JSONException ignored){}}else if(error!=null)lastError[0]=error;}
            if(left.decrementAndGet()==0)callback.done(merged,ok.get()>0?null:(lastError[0]==null?"تعذر الوصول إلى مصادر HiTV حالياً.":lastError[0]));
        });}
    }

    private static void request(String path,JSONObject params,boolean post,Callback callback){
        final JSONObject safe=params==null?new JSONObject():params;
        IO.execute(()->{
            Object value=null;String error=null;HttpURLConnection c=null;
            try{
                if(path==null||!path.startsWith("/")||path.contains(".."))throw new IOException("bad route");
                String uuid=randomHex16(),current=Long.toString(System.currentTimeMillis());String payload=(current+flatten(safe)).replace('+','-').replace('/','_');String sign=md5(encryptAes(payload,uuid)),aesKey=encryptRsa(uuid);
                URL url=new URL(BASE+(post?path:path+query(safe)));URLConnection opened=HitvNet.open(url);if(!(opened instanceof HttpURLConnection))throw new IOException("unsupported transport");c=(HttpURLConnection)opened;
                c.setConnectTimeout(12000);c.setReadTimeout(28000);c.setInstanceFollowRedirects(true);c.setRequestMethod(post?"POST":"GET");c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");c.setRequestProperty("Accept-Language","ar,en;q=0.8");c.setRequestProperty("lang","ar");c.setRequestProperty("currentTime",current);c.setRequestProperty("sign",sign);c.setRequestProperty("aesKey",aesKey);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/127.0 Mobile Safari/537.36");
                if(post){byte[] body=safe.toString().getBytes(StandardCharsets.UTF_8);c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
                int code=c.getResponseCode();InputStream raw=code>=200&&code<300?c.getInputStream():c.getErrorStream();String text=read(raw,8*1024*1024);if(code<200||code>=300)throw new IOException("HTTP "+code);
                JSONObject envelope=new JSONObject(text);String apiCode=envelope.optString("code");if(!apiCode.isEmpty()&&!"00000".equals(apiCode))throw new IOException("API "+apiCode+" "+envelope.optString("msg",envelope.optString("message","")));
                Object data=envelope.opt("data");if(data==null||data==JSONObject.NULL)value=JSONObject.NULL;else if(data instanceof String){String plain;try{plain=decryptAes((String)data,uuid);}catch(Exception ignored){plain=(String)data;}value=parseAny(plain);}else value=data;
            }catch(Exception e){error="تعذر الوصول إلى مصادر HiTV. تم تجربة DNS العادي وDNS الآمن.";}finally{if(c!=null)c.disconnect();}
            Object result=value;String failure=error;new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.done(result,failure));
        });
    }

    private static Object parseAny(String text){String s=text==null?"":text.trim();if(s.isEmpty())return "";try{if(s.charAt(0)=='{')return new JSONObject(s);if(s.charAt(0)=='[')return new JSONArray(s);}catch(JSONException ignored){}return s;}
    private static String read(InputStream in,int max)throws IOException{if(in==null)return "";ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;try(InputStream src=in){while((n=src.read(b))!=-1){out.write(b,0,n);if(out.size()>max)throw new IOException("response too large");}}return out.toString("UTF-8");}
    private static String query(JSONObject params)throws Exception{ArrayList<String> parts=new ArrayList<>();Iterator<String> it=params.keys();while(it.hasNext()){String k=it.next();Object v=params.opt(k);if(v==null||v==JSONObject.NULL||v instanceof JSONObject||v instanceof JSONArray)continue;parts.add(URLEncoder.encode(k,"UTF-8")+"="+URLEncoder.encode(String.valueOf(v),"UTF-8"));}return parts.isEmpty()?"":"?"+android.text.TextUtils.join("&",parts);}
    private static String flatten(JSONObject params)throws Exception{ArrayList<String> values=new ArrayList<>();Iterator<String> keys=params.keys();while(keys.hasNext()){String key=keys.next();Object value=params.opt(key);if(value==null||value==JSONObject.NULL)continue;if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++){Object entry=a.opt(i);if(entry instanceof JSONObject)values.add(key+"="+flatten((JSONObject)entry));else if(entry!=null&&entry!=JSONObject.NULL)values.add(key+"["+i+"]="+entry);}}else if(value instanceof JSONObject)values.add(key+"="+flatten((JSONObject)value));else values.add(key+"="+value);}Collections.sort(values,Collections.reverseOrder());StringBuilder joined=new StringBuilder();for(String pair:values){int at=pair.indexOf('=');joined.append(at<0?pair:pair.substring(at+1));}return Base64.encodeToString(joined.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);}
    private static String randomHex16(){byte[] b=new byte[8];RNG.nextBytes(b);StringBuilder s=new StringBuilder(16);for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
    private static String encryptAes(String value,String key)throws Exception{Cipher c=Cipher.getInstance("AES/ECB/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES"));return Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
    private static String decryptAes(String value,String key)throws Exception{Cipher c=Cipher.getInstance("AES/ECB/PKCS5Padding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES"));return new String(c.doFinal(Base64.decode(value,Base64.DEFAULT)),StandardCharsets.UTF_8);}
    private static String md5(String value)throws Exception{byte[] d=MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder(32);for(byte b:d)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static String encryptRsa(String uuid)throws Exception{byte[] der=Base64.decode(PUBLIC_KEY_DER,Base64.DEFAULT);PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));Cipher c=Cipher.getInstance("RSA/ECB/PKCS1Padding");c.init(Cipher.ENCRYPT_MODE,key);return Base64.encodeToString(c.doFinal(uuid.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
    static JSONObject params(Object... pairs){JSONObject o=new JSONObject();for(int i=0;i+1<pairs.length;i+=2)try{o.put(String.valueOf(pairs[i]),pairs[i+1]);}catch(JSONException ignored){}return o;}
}
