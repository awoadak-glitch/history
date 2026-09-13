package awr.witcher;

import android.util.Base64;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;

/** Native HiTV client with secure DNS fallback for networks that poison/block the HiTV hostname. */
final class HitvApi {
    static final String BASE="https://web-api.hitvpro.com";
    static final String HOST="web-api.hitvpro.com";
    static final ExecutorService IO=Executors.newFixedThreadPool(7);
    private static final String TAG="AWR-HiTV";
    private static final String PUBLIC_KEY_DER="MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCEZTSwZTZdBfQAm8oVroRdy6K7VVB0/7Jojx7e3UPa7NjjZ2A/mydH7QI9aHr3dNEJDrNLyZ/nEK6XzV+XTG3t3GVqsEjVOVO26K/VcDVwk/dFJs8bmK3AaIBPQATJQDztgSjg5H7u92JnyXvtj0XB0IH+GM2ui6sB69Y5T0LPAwIDAQAB";
    private static final SecureRandom RNG=new SecureRandom();
    private static volatile ArrayList<String> dohIps=new ArrayList<>();
    private static volatile long dohExpiry;
    private HitvApi(){}

    interface Callback { void done(Object value,String error); }
    static void get(String path,JSONObject params,Callback callback){request(path,params,false,callback);}
    static void post(String path,JSONObject params,Callback callback){request(path,params,true,callback);}

    private static void request(String path,JSONObject params,boolean post,Callback callback){
        final JSONObject safe=params==null?new JSONObject():params;
        IO.execute(()->{
            Object value=null;String error=null;
            try{
                if(path==null||!path.startsWith("/")||path.contains(".."))throw new IOException("bad route");
                try{value=attempt(BASE,null,path,safe,post);}catch(Exception direct){
                    Log.w(TAG,"Direct HiTV route failed; trying secure DNS",direct);
                    Exception last=direct;ArrayList<String> ips=resolveDoh();
                    for(String ip:ips){try{value=attempt("https://"+ip,HOST,path,safe,post);last=null;break;}catch(Exception e){last=e;Log.w(TAG,"HiTV IP fallback failed: "+ip,e);}}
                    if(last!=null)throw last;
                }
            }catch(Exception e){Log.w(TAG,"HiTV request unavailable",e);error="تعذر الوصول إلى خوادم HiTV حالياً. جرّب إعادة المحاولة أو بدّل الشبكة.";}
            Object result=value;String failure=error;new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.done(result,failure));
        });
    }

    private static Object attempt(String base,String hostOverride,String path,JSONObject params,boolean post)throws Exception{
        String uuid=randomHex16(),current=Long.toString(System.currentTimeMillis());String flattened=flatten(params);String payload=(current+flattened).replace('+','-').replace('/','_');
        String encrypted=encryptAes(payload,uuid),sign=md5(encrypted),aesKey=encryptRsa(uuid);String suffix=post?path:path+query(params);
        URL url=new URL(base+suffix);HttpURLConnection raw=(HttpURLConnection)url.openConnection();
        if(raw instanceof HttpsURLConnection&&hostOverride!=null){HttpsURLConnection https=(HttpsURLConnection)raw;https.setSSLSocketFactory(new SniFactory((SSLSocketFactory)SSLSocketFactory.getDefault(),hostOverride));HostnameVerifier verifier=HttpsURLConnection.getDefaultHostnameVerifier();https.setHostnameVerifier((ignored,session)->verifier.verify(hostOverride,session));}
        raw.setConnectTimeout(10000);raw.setReadTimeout(25000);raw.setInstanceFollowRedirects(true);raw.setRequestMethod(post?"POST":"GET");
        raw.setRequestProperty("Content-Type","application/json");raw.setRequestProperty("Accept","application/json");raw.setRequestProperty("Accept-Language","ar,en;q=0.8");raw.setRequestProperty("lang","ar");raw.setRequestProperty("currentTime",current);raw.setRequestProperty("sign",sign);raw.setRequestProperty("aesKey",aesKey);raw.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/127.0 Mobile Safari/537.36");if(hostOverride!=null)raw.setRequestProperty("Host",hostOverride);
        try{
            if(post){byte[] body=params.toString().getBytes(StandardCharsets.UTF_8);raw.setDoOutput(true);raw.setFixedLengthStreamingMode(body.length);try(OutputStream out=raw.getOutputStream()){out.write(body);}}
            int code=raw.getResponseCode();InputStream in=code>=200&&code<300?raw.getInputStream():raw.getErrorStream();String text=read(in,8*1024*1024);if(code<200||code>=300)throw new IOException("HTTP "+code);
            JSONObject envelope=new JSONObject(text);String apiCode=envelope.optString("code");if(!apiCode.isEmpty()&&!"00000".equals(apiCode))throw new IOException("API "+apiCode);
            Object data=envelope.opt("data");if(data==null||data==JSONObject.NULL)return JSONObject.NULL;if(!(data instanceof String))return data;
            String plain;try{plain=decryptAes((String)data,uuid);}catch(Exception ignored){plain=(String)data;}return parseAny(plain);
        }finally{raw.disconnect();}
    }

    /** Resolve through HTTPS only after normal DNS failed. No certificate checks are disabled. */
    private static ArrayList<String> resolveDoh(){
        long now=System.currentTimeMillis();if(now<dohExpiry&&!dohIps.isEmpty())return new ArrayList<>(dohIps);
        LinkedHashSet<String> found=new LinkedHashSet<>();String[] endpoints={
            "https://dns.google/resolve?name="+HOST+"&type=A",
            "https://cloudflare-dns.com/dns-query?name="+HOST+"&type=A"
        };
        for(String endpoint:endpoints){HttpsURLConnection c=null;try{c=(HttpsURLConnection)new URL(endpoint).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setRequestProperty("Accept","application/dns-json");c.setRequestProperty("User-Agent","Mozilla/5.0 (Android)");if(c.getResponseCode()!=200)continue;JSONObject root=new JSONObject(read(c.getInputStream(),256*1024));JSONArray answers=root.optJSONArray("Answer");if(answers==null)continue;for(int i=0;i<answers.length();i++){JSONObject a=answers.optJSONObject(i);if(a!=null&&a.optInt("type")==1){String ip=a.optString("data");if(ip.matches("\\d{1,3}(?:\\.\\d{1,3}){3}"))found.add(ip);}}}catch(Exception e){Log.w(TAG,"DoH provider failed",e);}finally{if(c!=null)c.disconnect();}if(!found.isEmpty())break;}
        dohIps=new ArrayList<>(found);dohExpiry=now+(found.isEmpty()?60_000L:20*60_000L);return new ArrayList<>(dohIps);
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

    private static final class SniFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;private final String hostname;SniFactory(SSLSocketFactory d,String h){delegate=d;hostname=h;}
        private Socket tune(Socket s){if(!(s instanceof SSLSocket))return s;SSLSocket ssl=(SSLSocket)s;try{ssl.getClass().getMethod("setHostname",String.class).invoke(ssl,hostname);}catch(Exception ignored){}try{SSLParameters p=ssl.getSSLParameters();Class<?> serverName=Class.forName("javax.net.ssl.SNIServerName");Class<?> sniHost=Class.forName("javax.net.ssl.SNIHostName");Object name=sniHost.getConstructor(String.class).newInstance(hostname);java.lang.reflect.Method m=SSLParameters.class.getMethod("setServerNames",List.class);m.invoke(p,Collections.singletonList(name));ssl.setSSLParameters(p);}catch(Exception ignored){}return ssl;}
        public String[] getDefaultCipherSuites(){return delegate.getDefaultCipherSuites();}public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}
        public Socket createSocket(Socket s,String h,int p,boolean a)throws IOException{return tune(delegate.createSocket(s,h,p,a));}public Socket createSocket(String h,int p)throws IOException{return tune(delegate.createSocket(h,p));}public Socket createSocket(String h,int p,InetAddress l,int lp)throws IOException{return tune(delegate.createSocket(h,p,l,lp));}public Socket createSocket(InetAddress h,int p)throws IOException{return tune(delegate.createSocket(h,p));}public Socket createSocket(InetAddress h,int p,InetAddress l,int lp)throws IOException{return tune(delegate.createSocket(h,p,l,lp));}
    }
}
