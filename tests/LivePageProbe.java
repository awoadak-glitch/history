package awr.witcher;
import java.nio.file.*;
import java.net.URL;
/** Optional read-only local snapshot check. Prints no signed stream URLs. */
public final class LivePageProbe {
    public static void main(String[] args)throws Exception {
        for(String id:args){
            String text=new String(Files.readAllBytes(Paths.get("build/live/provider-"+id+".html")),"UTF-8");
            PageStreams.Result r=PageStreams.parse(text,"https://provider.example.org/");
            System.out.println("page="+id+" streams="+r.streams.size()+" choice="+r.choice);
            for(Legacy.Stream s:r.streams)System.out.println("  quality="+s.quality+" media_host="+new URL(s.url).getHost());
            if(r.streams.isEmpty())throw new IllegalStateException("No streams in page "+id);
        }
    }
}
