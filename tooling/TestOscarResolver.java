package awr.witcher;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Device-free local HTTP regression harness for the Oscar-to-MX resolver. */
public final class TestOscarResolver {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String enc(String value)throws Exception{return URLEncoder.encode(value,"UTF-8").replace("+","%20");}
    private static void send(HttpExchange exchange,int status,String type,String body)throws IOException{
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);if(type!=null)exchange.getResponseHeaders().set("Content-Type",type);
        if("HEAD".equals(exchange.getRequestMethod()))exchange.sendResponseHeaders(status,-1);
        else{exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);}exchange.close();
    }
    public static void main(String[] args)throws Exception{
        deepFirst();cookieRedirect();hls();rawFallback();tdmAttachmentRedirect();
        System.out.println("{\"oscar_resolver_tests\":5,\"passed\":true}");
    }
    private static void deepFirst()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger rawHits=new AtomicInteger();
        String base="http://127.0.0.1:"+server.getAddress().getPort(),referer=base+"/watch/2";
        server.createContext("/deep",exchange->{if(!referer.equals(exchange.getRequestHeaders().getFirst("Referer"))){exchange.sendResponseHeaders(403,-1);exchange.close();return;}send(exchange,200,"video/mp4","");});
        server.createContext("/raw.mp4",exchange->{rawHits.incrementAndGet();send(exchange,200,"video/mp4","");});server.start();
        try{String deep="tdm://play?url="+enc(base+"/deep")+"&referer="+enc(referer);List<OscarResolver.Stream> streams=OscarResolver.resolve(base+"/raw.mp4",deep,"mp4");check(streams.size()==1,"deep count");check((base+"/deep").equals(streams.get(0).url),"deep priority");check(referer.equals(streams.get(0).headers.get("Referer")),"deep referer");check(rawHits.get()==0,"raw used before deep");}
        finally{server.stop(0);}
    }
    private static void cookieRedirect()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);String base="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/page",exchange->send(exchange,200,"text/html","<video><source src='/jump' label='720p'></video>"));
        server.createContext("/jump",exchange->{exchange.getResponseHeaders().add("Set-Cookie","session=fixture; Path=/; HttpOnly");exchange.getResponseHeaders().add("Location","/stream");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/stream",exchange->{String cookie=exchange.getRequestHeaders().getFirst("Cookie"),ref=exchange.getRequestHeaders().getFirst("Referer");if(cookie==null||!cookie.contains("session=fixture")||!(base+"/page").equals(ref)){exchange.sendResponseHeaders(403,-1);exchange.close();return;}send(exchange,200,"video/mp4","");});server.start();
        try{List<OscarResolver.Stream> streams=OscarResolver.resolve(base+"/page","","embed");check(streams.size()==1,"redirect count");check((base+"/stream").equals(streams.get(0).url),"redirect target");check("720p".equals(streams.get(0).label),"quality label");check(streams.get(0).headers.get("Cookie").contains("session=fixture"),"cookie missing");check((base+"/page").equals(streams.get(0).headers.get("Referer")),"page referer missing");}
        finally{server.stop(0);}
    }
    private static void hls()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);String base="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/playlist",exchange->send(exchange,200,"application/vnd.apple.mpegurl","#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720\n720.m3u8?token=a%2Bb\n#EXT-X-STREAM-INF:BANDWIDTH=2300000,RESOLUTION=1920x1080\n1080.m3u8\n"));server.start();
        try{List<OscarResolver.Stream> streams=OscarResolver.resolve(base+"/playlist","","embed");check(streams.size()==3,"HLS qualities");check("720p".equals(streams.get(1).label),"HLS label");check((base+"/720.m3u8?token=a%2Bb").equals(streams.get(1).url),"HLS signed URL");check(streams.get(1).segmented(),"HLS MIME");}
        finally{server.stop(0);}
    }
    private static void rawFallback()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);String base="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/page",exchange->send(exchange,200,"text/html","<script>player.setup({file:'"+base+"/movie.mp4'});</script>"));server.createContext("/movie.mp4",exchange->send(exchange,200,"video/mp4",""));server.start();
        try{List<OscarResolver.Stream> streams=OscarResolver.resolve(base+"/page","tdm://opaque-token","embed");check((base+"/movie.mp4").equals(streams.get(0).url),"raw fallback");}
        finally{server.stop(0);}
    }
    private static void tdmAttachmentRedirect()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);String base="http://127.0.0.1:"+server.getAddress().getPort();
        String target=base+"/opaque-download?filename=episode-8";
        server.createContext("/tdm-wrapper",exchange->{exchange.getResponseHeaders().add("Location",target);exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/opaque-download",exchange->send(exchange,200,"application/octet-stream","not-read-by-the-resolver"));server.start();
        try{List<OscarResolver.Stream> streams=OscarResolver.resolve(base+"/tdm-wrapper","","embed");check(streams.size()==1,"TDM redirect count");check(target.equals(streams.get(0).url),"TDM attachment redirect");}
        finally{server.stop(0);}
    }
}
