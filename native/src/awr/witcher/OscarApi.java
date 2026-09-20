package awr.witcher;

import android.os.Handler;
import android.os.Looper;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Oscar 1.1.4's catalogue contract with the owner's original native authentication. */
final class OscarApi {
    static final String BASE="https://ostvapp.cam/";
    static final ExecutorService IO=Executors.newFixedThreadPool(3);
    static final Handler MAIN=new Handler(Looper.getMainLooper());
    interface Callback { void done(JSONObject response,String error); }
    interface Transport { JSONObject get(String route,Map<String,String> query)throws Exception; }
    interface ConnectionFactory { HttpURLConnection open(URL url)throws IOException; }
    static Transport transport=OscarApi::request;
    static ConnectionFactory connections=url->(HttpURLConnection)url.openConnection();
    static volatile OscarAuth auth;
    static synchronized void initialize(android.content.Context context){if(auth==null)auth=new OscarAuth(context);}
    static void get(String route,Map<String,String> query,Callback callback){
        Map<String,String> copy=new LinkedHashMap<>(query);
        IO.execute(()->{JSONObject value=null;String error=null;try{value=transport.get(route,copy);}catch(Exception e){error=message(e);}final JSONObject result=value;final String failure=error;MAIN.post(()->callback.done(result,failure));});
    }
    static Map<String,String> params(String... pairs){Map<String,String> p=new LinkedHashMap<>();for(int i=0;i+1<pairs.length;i+=2)if(pairs[i+1]!=null&&!pairs[i+1].isEmpty())p.put(pairs[i],pairs[i+1]);return p;}
    static String url(String route,Map<String,String> query)throws IOException{
        if(!route.matches("api/[a-z0-9_/.-]+")||route.contains(".."))throw new IOException("مسار غير صالح");
        StringBuilder b=new StringBuilder(BASE).append(route);boolean first=true;
        for(Map.Entry<String,String> e:query.entrySet()){b.append(first?'?':'&');first=false;b.append(URLEncoder.encode(e.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(e.getValue(),"UTF-8"));}return b.toString();
    }
    static JSONObject request(String route,Map<String,String> query)throws Exception{
        URL target=new URL(url(route,query));
        OscarAuth signer=auth;
        for(int redirects=0;redirects<4;redirects++){
            if(!OscarAuth.accepts(target))throw new IOException("عنوان إعادة التوجيه لا يطابق مصدر المحتوى.");
            OscarAuth.Result signed=signer==null?OscarAuth.empty("IRON_CONTEXT"):signer.headers(target);
            HttpURLConnection c=connections.open(target);
            // Re-sign same-origin redirects; never forward authentication to another host.
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);c.setReadTimeout(18000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","AWR-World/1.0");
            for(Map.Entry<String,String> h:signed.headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
            try{int code=c.getResponseCode();if(signer!=null)signer.observeServerDate(c.getHeaderFieldDate("Date",-1));
                if(code==301||code==302||code==303||code==307||code==308){String location=c.getHeaderField("Location");if(location==null)throw new IOException("لم يرسل المصدر عنوان إعادة التوجيه.");target=new URL(target,location);continue;}
                if(code<200||code>=300)throw new IOException(code==401||code==403?"رفض المصدر الطلب (HTTP "+code+").\n"+signed.denialMessage():httpError(code));
                String body=read(c.getInputStream(),6*1024*1024);JSONObject data;
                try{data=new JSONObject(body);}catch(JSONException e){throw new IOException("أعاد المصدر صفحة غير صالحة. حاول لاحقاً.");}
                String issue=apiError(data);if(issue!=null)throw new IOException(issue);return data;
            }finally{c.disconnect();}
        }
        throw new IOException("تجاوز المصدر عدد إعادة التوجيه المسموح.");
    }
    static String read(InputStream in,int max)throws IOException{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){if(out.size()+n>max)throw new IOException("استجابة المصدر أكبر من الحد المسموح");out.write(b,0,n);}return out.toString("UTF-8");}}
    static String httpError(int code){if(code==401||code==403)return "المصدر لم يسمح بتحميل المحتوى حالياً (HTTP "+code+"). أعد المحاولة لاحقاً.";return "تعذر تحميل المحتوى من المصدر (HTTP "+code+").";}
    static String apiError(JSONObject o){String s=o.optString("status","");if(o.optBoolean("is_blocked")||"error".equalsIgnoreCase(s)||"failed".equalsIgnoreCase(s)||"false".equalsIgnoreCase(s))return OscarCatalog.text(o,"message","msg").isEmpty()?"المصدر لا يتيح هذا المحتوى حالياً.":OscarCatalog.text(o,"message","msg");return null;}
    static String message(Exception e){if(e instanceof SocketTimeoutException)return "انتهت مهلة الاتصال بالمصدر. أعد المحاولة.";if(e instanceof UnknownHostException)return "تعذر الوصول إلى عنوان المصدر. تحقق من الاتصال.";return e instanceof IOException&&e.getMessage()!=null?e.getMessage():"تعذر قراءة بيانات المصدر. أعد المحاولة.";}
}
