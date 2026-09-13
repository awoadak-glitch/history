package awr.witcher;

import org.json.*;
import java.net.URI;
import java.util.*;

/** Normalizes only playback media; captions, trailers and installer URLs remain distinct. */
final class HitvPlayback {
    private HitvPlayback(){}
    static final class Caption {
        final String url,label,language,mime;final boolean selected;
        Caption(String u,String l,String lang,String m,boolean d){url=u;label=l;language=lang;mime=m;selected=d;}
    }
    static ArrayList<HitvExperience.Source> sources(Object data){
        LinkedHashMap<String,HitvExperience.Source> out=new LinkedHashMap<>();
        collect(data,0,out,new ArrayList<Caption>(),new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER));
        return new ArrayList<>(out.values());
    }
    private static void collect(Object value,int depth,LinkedHashMap<String,HitvExperience.Source> out,ArrayList<Caption> inherited,Map<String,String> inheritedHeaders){
        if(value==null||value==JSONObject.NULL||depth>10||out.size()>=100)return;
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)collect(a.opt(i),depth+1,out,inherited,inheritedHeaders);return;}
        if(value instanceof String){String u=(String)value;if(media(u))add(out,u,"تلقائي","",inherited,inheritedHeaders);return;}
        if(!(value instanceof JSONObject))return;JSONObject o=(JSONObject)value;
        if("previewInfo".equals(o.optString("source"))||"downloadUrls".equals(o.optString("source")))return;
        ArrayList<Caption> captions=new ArrayList<>(inherited);readCaptions(o,captions);
        Map<String,String> headers=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);headers.putAll(inheritedHeaders);
        JSONObject explicit=o.optJSONObject("headers");if(explicit!=null){Iterator<String> keys=explicit.keys();while(keys.hasNext()){String k=keys.next(),v=explicit.optString(k);if(validHeader(k,v))headers.put(k,v);}}
        String url=first(o,"url","playUrl","mediaUrl","file","videoUrl");
        if(media(url))add(out,url,first(o,"label","definition","quality","resolution","clarity"),first(o,"type","format","mimeType"),captions,headers);
        for(String key:new String[]{"data","sources","playList","streams","variants","qualities","list","videoList"}){
            Object child=o.opt(key);if(child instanceof JSONObject||child instanceof JSONArray)collect(child,depth+1,out,captions,headers);
        }
    }
    private static void add(Map<String,HitvExperience.Source> out,String url,String label,String type,ArrayList<Caption> captions,Map<String,String> headers){
        if(out.containsKey(url))return;HitvExperience.Source s=new HitvExperience.Source(url,label.isEmpty()?"تلقائي":label,type);s.captions.addAll(captions);s.headers.putAll(headers);out.put(url,s);
    }
    private static void readCaptions(JSONObject o,ArrayList<Caption> out){
        Set<String> seen=new HashSet<>();for(Caption c:out)seen.add(c.url);
        for(String key:new String[]{"subtitles","textTrack","subtitleList"}){
            JSONArray list=o.optJSONArray(key);if(list==null)continue;
            for(int i=0;i<list.length();i++){
                JSONObject c=list.optJSONObject(i);if(c==null)continue;String url=first(c,"url","src");if(!http(url)||!seen.add(url))continue;
                String lang=first(c,"lang","languageCode","language"),label=first(c,"label","name","language");String mime=first(c,"mimeType","type");
                if(!mime.contains("/")){String p=URI.create(url).getPath().toLowerCase(Locale.ROOT);mime=p.endsWith(".srt")?"application/x-subrip":p.endsWith(".ass")||p.endsWith(".ssa")?"text/x-ssa":"text/vtt";}
                out.add(new Caption(url,label.isEmpty()?lang:label,lang.isEmpty()?"und":lang,mime,c.optBoolean("default")));
            }
        }
    }
    static boolean media(String url){
        if(!http(url))return false;String path=URI.create(url).getPath();if(path==null)return true;
        return !path.toLowerCase(Locale.ROOT).matches(".*\\.(?:jpg|jpeg|png|webp|gif|vtt|srt|ass|ssa|json|html?|apk|apks|aab|exe|msi|dmg|zip)$");
    }
    static boolean http(String url){try{URI u=URI.create(url);return ("https".equals(u.getScheme())||"http".equals(u.getScheme()))&&u.getHost()!=null&&u.getUserInfo()==null;}catch(Exception e){return false;}}
    private static boolean validHeader(String k,String v){return k.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")&&!v.contains("\r")&&!v.contains("\n");}
    private static String first(JSONObject o,String... keys){return HitvExperience.first(o,keys);}
}
