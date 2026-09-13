package awr.witcher;

import org.json.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/**
 * HiTV network helper.
 *
 * Yemen/ISP DNS can return UnknownHost for *.hitvpro.com even while the service itself is
 * reachable. Resolve those hosts with the system resolver first, then Cloudflare DNS-over-HTTPS
 * through 1.1.1.1/1.0.0.1. HTTPS still uses the original hostname for SNI/certificate checks;
 * only the TCP destination is replaced with the resolved IP.
 */
final class HitvNet {
    private static final long TTL=10*60*1000L;
    private static final Map<String,Entry> CACHE=new ConcurrentHashMap<>();
    private static final SSLSocketFactory DEFAULT=(SSLSocketFactory)SSLSocketFactory.getDefault();
    private HitvNet(){}
    static final class Entry {final String ip;final long at;Entry(String i){ip=i;at=System.currentTimeMillis();}}

    static URLConnection open(URL url)throws IOException{
        String host=url.getHost();
        if(!"https".equalsIgnoreCase(url.getProtocol())||!hitvHost(host))return url.openConnection();
        String ip=resolve(host);
        if(ip==null||ip.isEmpty())return url.openConnection();
        HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
        c.setSSLSocketFactory(new DirectFactory(host,ip));
        c.setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());
        return c;
    }

    private static boolean hitvHost(String h){
        if(h==null)return false;h=h.toLowerCase(Locale.ROOT);
        return h.equals("hitvpro.com")||h.endsWith(".hitvpro.com")||h.equals("gohitv.com")||h.endsWith(".gohitv.com")||h.equals("hitv.vip")||h.endsWith(".hitv.vip")||h.equals("hitvweb.com")||h.endsWith(".hitvweb.com");
    }
    private static String resolve(String host){
        Entry cached=CACHE.get(host);if(cached!=null&&System.currentTimeMillis()-cached.at<TTL)return cached.ip;
        try{InetAddress[] all=InetAddress.getAllByName(host);for(InetAddress a:all){String ip=a.getHostAddress();if(ip!=null&&ip.indexOf(':')<0){CACHE.put(host,new Entry(ip));return ip;}}}catch(Exception ignored){}
        String ip=doh(host,"1.1.1.1");if(ip==null)ip=doh(host,"1.0.0.1");if(ip!=null)CACHE.put(host,new Entry(ip));return ip;
    }
    private static String doh(String host,String resolver){
        HttpsURLConnection c=null;
        try{
            URL u=new URL("https://"+resolver+"/dns-query?name="+URLEncoder.encode(host,"UTF-8")+"&type=A");
            c=(HttpsURLConnection)u.openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setRequestProperty("Accept","application/dns-json");c.setRequestProperty("User-Agent","Mozilla/5.0 (Android) HiTV");
            int code=c.getResponseCode();if(code<200||code>=300)return null;String text=read(c.getInputStream(),128*1024);JSONObject root=new JSONObject(text);JSONArray answer=root.optJSONArray("Answer");if(answer==null)return null;
            for(int i=0;i<answer.length();i++){JSONObject row=answer.optJSONObject(i);if(row==null||row.optInt("type")!=1)continue;String ip=row.optString("data");if(ip.matches("(?:\\d{1,3}\\.){3}\\d{1,3}"))return ip;}
        }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}return null;
    }
    private static String read(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;try(InputStream s=in){while((n=s.read(b))!=-1){out.write(b,0,n);if(out.size()>max)throw new IOException("response too large");}}return out.toString("UTF-8");}

    private static final class DirectFactory extends SSLSocketFactory {
        final String hostname,ip;DirectFactory(String h,String i){hostname=h;ip=i;}
        public String[] getDefaultCipherSuites(){return DEFAULT.getDefaultCipherSuites();}
        public String[] getSupportedCipherSuites(){return DEFAULT.getSupportedCipherSuites();}
        private Socket connect(int port,InetAddress local,int localPort)throws IOException{
            Socket raw=new Socket();if(local!=null)raw.bind(new InetSocketAddress(local,Math.max(0,localPort)));raw.connect(new InetSocketAddress(ip,port),12000);return DEFAULT.createSocket(raw,hostname,port,true);
        }
        public Socket createSocket(String host,int port)throws IOException{return connect(port,null,0);}
        public Socket createSocket(String host,int port,InetAddress local,int localPort)throws IOException{return connect(port,local,localPort);}
        public Socket createSocket(InetAddress host,int port)throws IOException{return connect(port,null,0);}
        public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort)throws IOException{return connect(port,local,localPort);}
        public Socket createSocket(Socket s,String host,int port,boolean autoClose)throws IOException{return DEFAULT.createSocket(s,hostname,port,autoClose);}
        public Socket createSocket()throws IOException{return DEFAULT.createSocket();}
    }
}
