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
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Native HiTV catalogue/playback client.
 *
 * This mirrors the server-side adapter already validated in the user's HITV-WEB project:
 * per-request 16-character AES key, RSA PKCS#1 encrypted aesKey header, MD5 signature,
 * AES-ECB response decryption and the public web-api.hitvpro.com routes.
 */
final class HitvApi {
    static final String BASE="https://web-api.hitvpro.com";
    static final ExecutorService IO=Executors.newFixedThreadPool(4);
    private static final String PUBLIC_KEY_DER="MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCEZTSwZTZdBfQAm8oVroRdy6K7VVB0/7Jojx7e3UPa7NjjZ2A/mydH7QI9aHr3dNEJDrNLyZ/nEK6XzV+XTG3t3GVqsEjVOVO26K/VcDVwk/dFJs8bmK3AaIBPQATJQDztgSjg5H7u92JnyXvtj0XB0IH+GM2ui6sB69Y5T0LPAwIDAQAB";
    private static final SecureRandom RNG=new SecureRandom();
    private HitvApi(){}

    interface Callback { void done(Object value,String error); }

    static void get(String path,JSONObject params,Callback callback){ request(path,params,false,callback); }
    static void post(String path,JSONObject params,Callback callback){ request(path,params,true,callback); }

    private static void request(String path,JSONObject params,boolean post,Callback callback){
        final JSONObject safe=params==null?new JSONObject():params;
        IO.execute(()->{
            Object value=null;String error=null;HttpURLConnection c=null;
            try{
                if(path==null||!path.startsWith("/")||path.contains(".."))throw new IOException("Invalid HiTV route");
                String uuid=randomHex16();String current=Long.toString(System.currentTimeMillis());
                String flattened=flatten(safe);String payload=(current+flattened).replace('+','-').replace('/','_');
                String encrypted=encryptAes(payload,uuid);String sign=md5(encrypted);String aesKey=encryptRsa(uuid);
                URL url=new URL(BASE+(post?path:path+query(safe)));
                c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(true);
                c.setRequestMethod(post?"POST":"GET");c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
                c.setRequestProperty("lang","en");c.setRequestProperty("currentTime",current);c.setRequestProperty("sign",sign);c.setRequestProperty("aesKey",aesKey);
                c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/127.0 Mobile Safari/537.36");
                if(post){byte[] body=safe.toString().getBytes(StandardCharsets.UTF_8);c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
                int code=c.getResponseCode();InputStream raw=code>=200&&code<300?c.getInputStream():c.getErrorStream();String text=read(raw,6*1024*1024);
                if(code<200||code>=300)throw new IOException("HiTV HTTP "+code);
                JSONObject envelope=new JSONObject(text);String apiCode=envelope.optString("code");
                if(!apiCode.isEmpty()&&!"00000".equals(apiCode))throw new IOException(envelope.optString("msg",envelope.optString("message","HiTV rejected request")));
                Object data=envelope.opt("data");
                if(data==null||data==JSONObject.NULL)value=JSONObject.NULL;
                else if(data instanceof String){
                    String plain;
                    try{plain=decryptAes((String)data,uuid);}catch(Exception ignored){plain=(String)data;}
                    value=parseAny(plain);
                } else value=data;
            }catch(Exception e){error="تعذر الاتصال بمصدر HiTV"+(e.getMessage()==null?"":" · "+shortMessage(e.getMessage()));}
            finally{if(c!=null)c.disconnect();}
            Object result=value;String failure=error;android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());main.post(()->callback.done(result,failure));
        });
    }

    private static String shortMessage(String s){String t=s.replace('\n',' ').replace('\r',' ').trim();return t.length()>90?t.substring(0,90):t;}
    private static Object parseAny(String text){
        String s=text==null?"":text.trim();if(s.isEmpty())return "";
        try{if(s.charAt(0)=='{')return new JSONObject(s);if(s.charAt(0)=='[')return new JSONArray(s);}catch(JSONException ignored){}
        return s;
    }
    private static String read(InputStream in,int max)throws IOException{
        if(in==null)return "";ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;
        try(InputStream src=in){while((n=src.read(b))!=-1){out.write(b,0,n);if(out.size()>max)throw new IOException("HiTV response too large");}}
        return out.toString("UTF-8");
    }
    private static String query(JSONObject params)throws Exception{
        ArrayList<String> parts=new ArrayList<>();Iterator<String> it=params.keys();while(it.hasNext()){
            String k=it.next();Object v=params.opt(k);if(v==null||v==JSONObject.NULL||v instanceof JSONObject||v instanceof JSONArray)continue;
            parts.add(URLEncoder.encode(k,"UTF-8")+"="+URLEncoder.encode(String.valueOf(v),"UTF-8"));
        }return parts.isEmpty()?"":"?"+android.text.TextUtils.join("&",parts);
    }
    /** Same primitive/nested flattening order as HITV-WEB/src/lib/hitv-api.ts. */
    private static String flatten(JSONObject params)throws Exception{
        ArrayList<String> values=new ArrayList<>();Iterator<String> keys=params.keys();while(keys.hasNext()){
            String key=keys.next();Object value=params.opt(key);if(value==null||value==JSONObject.NULL)continue;
            if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++){Object entry=a.opt(i);if(entry instanceof JSONObject)values.add(key+"="+flatten((JSONObject)entry));else if(entry!=null&&entry!=JSONObject.NULL)values.add(key+"["+i+"]="+entry);}}
            else if(value instanceof JSONObject)values.add(key+"="+flatten((JSONObject)value));
            else values.add(key+"="+value);
        }
        Collections.sort(values,Collections.reverseOrder());StringBuilder joined=new StringBuilder();for(String pair:values){int at=pair.indexOf('=');joined.append(at<0?pair:pair.substring(at+1));}
        return Base64.encodeToString(joined.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
    }
    private static String randomHex16(){byte[] b=new byte[8];RNG.nextBytes(b);StringBuilder s=new StringBuilder(16);for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
    private static String encryptAes(String value,String key)throws Exception{
        Cipher c=Cipher.getInstance("AES/ECB/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES"));
        return Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    private static String decryptAes(String value,String key)throws Exception{
        Cipher c=Cipher.getInstance("AES/ECB/PKCS5Padding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES"));
        return new String(c.doFinal(Base64.decode(value,Base64.DEFAULT)),StandardCharsets.UTF_8);
    }
    private static String md5(String value)throws Exception{
        byte[] d=MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder(32);for(byte b:d)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    private static String encryptRsa(String uuid)throws Exception{
        byte[] der=Base64.decode(PUBLIC_KEY_DER,Base64.DEFAULT);PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        Cipher c=Cipher.getInstance("RSA/ECB/PKCS1Padding");c.init(Cipher.ENCRYPT_MODE,key);return Base64.encodeToString(c.doFinal(uuid.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }

    static JSONObject params(Object... pairs){JSONObject o=new JSONObject();for(int i=0;i+1<pairs.length;i+=2)try{o.put(String.valueOf(pairs[i]),pairs[i+1]);}catch(JSONException ignored){}return o;}
}
