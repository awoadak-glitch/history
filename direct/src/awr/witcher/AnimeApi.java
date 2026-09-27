package awr.witcher;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.*;
import java.net.SocketTimeoutException;
import java.util.*;

/** Native client for the owner's Anime Witcher Firestore/Algolia gateway. */
public final class AnimeApi {
    public interface Callback { void result(JSONObject value,String error); }
    static final String BASE="https://awr-stream-web.vercel.app/api/";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Map<String,CacheEntry> CACHE=new LinkedHashMap<String,CacheEntry>(24,0.75f,true){
        protected boolean removeEldestEntry(Map.Entry<String,CacheEntry> item){return size()>28;}
    };
    private static final class CacheEntry {final String text;final long time=System.currentTimeMillis();CacheEntry(String value){text=value;}}
    private AnimeApi(){}

    public static String anime(String id){return "anime/"+segment(id);}
    public static String episodes(String id){return anime(id)+"/episodes";}
    public static String servers(String id,String episode){return episodes(id)+"/"+segment(episode)+"/servers";}
    public static String resolve(String id,String episode,int index){return servers(id,episode)+"?resolve="+index;}
    public static String search(String query,String index,int page){
        return "search?q="+Uri.encode(query)+"&index="+Uri.encode(index)+"&page="+Math.max(0,page);
    }
    public static String segment(String value){return Uri.encode(decoded(value));}
    static String decoded(String value){
        String out=value==null?"":value;
        for(int i=0;i<2&&out.matches(".*%[0-9A-Fa-f]{2}.*");i++)try{String next=Uri.decode(out);if(next.equals(out))break;out=next;}catch(Exception ignored){break;}
        return out;
    }

    public static void get(String path,Callback callback){request(path,false,callback);}
    public static void fresh(String path,Callback callback){request(path,true,callback);}
    private static void request(String path,boolean fresh,Callback callback){
        Api.IO.execute(()->{
            JSONObject value=null;String error=null;
            try{
                String raw=null;
                if(!fresh)synchronized(CACHE){CacheEntry e=CACHE.get(path);if(e!=null&&System.currentTimeMillis()-e.time<90000)raw=e.text;}
                if(raw==null){raw=Api.read(BASE+path,Collections.singletonMap("Accept","application/json"),8*1024*1024);if(!fresh)synchronized(CACHE){CACHE.put(path,new CacheEntry(raw));}}
                Object decoded=new JSONTokener(raw.trim()).nextValue();
                if(!(decoded instanceof JSONObject))throw new JSONException("Expected object");
                value=(JSONObject)decoded;
                String remote=value.optString("error");if(!remote.isEmpty())throw new IllegalStateException(remote);
            }catch(SocketTimeoutException e){error="انتهت مهلة اتصال Anime Witcher. حاول مرة أخرى.";}
            catch(Api.HttpError e){error="تعذر جلب محتوى الأنمي ("+e.code+").";}
            catch(Exception e){String message=e.getMessage();error=message!=null&&!message.isEmpty()&&message.length()<180?message:"تعذر قراءة مصدر Anime Witcher.";}
            final JSONObject output=value;final String problem=error;MAIN.post(()->callback.result(output,problem));
        });
    }

    public static JSONArray items(JSONObject value){return value==null?new JSONArray():array(value.opt("items"));}
    public static JSONArray array(Object value){return value instanceof JSONArray?(JSONArray)value:new JSONArray();}
    public static JSONObject card(JSONObject raw){
        if(raw==null)return new JSONObject();
        JSONObject out;try{out=new JSONObject(raw.toString());}catch(JSONException e){out=new JSONObject();}
        JSONObject details=raw.optJSONObject("details");
        String id=first(raw,"animeId","id","objectID","doc_id");
        String title=arabic(raw.opt("name"));if(title.isEmpty()&&details!=null)title=arabic(details.opt("name"));
        String image=first(raw,"poster","poster_uri","aniList_poster","thumb","thumb_uri","image");
        if(image.isEmpty()&&details!=null)image=first(details,"poster","poster_uri","image");
        String cover=first(raw,"cover","cover_uri");if(cover.isEmpty()&&details!=null)cover=first(details,"cover","cover_uri");
        if(cover.isEmpty())cover=image;
        put(out,"id",decoded(id));put(out,"title",title.isEmpty()?"عمل أنمي":title);put(out,"image",image);put(out,"cover",cover);put(out,"_anime",true);
        if(!out.has("year")&&details!=null)put(out,"year",first(details,"year","release_year","start_date"));
        return out;
    }
    public static JSONArray cards(JSONArray values){JSONArray out=new JSONArray();for(int i=0;i<values.length();i++){JSONObject item=values.optJSONObject(i);if(item!=null)out.put(card(item));}return out;}
    public static String episodeId(JSONObject episode,int fallback){return first(episode,"doc_id","id","order","episode_id").isEmpty()?String.valueOf(fallback):first(episode,"doc_id","id","order","episode_id");}
    public static int episodeNumber(JSONObject episode,int fallback){for(String key:new String[]{"order","doc_id","id","episode_id"}){Object value=episode.opt(key);if(value instanceof Number)return ((Number)value).intValue();try{String s=String.valueOf(value).replaceAll("[^0-9]","");if(!s.isEmpty())return Integer.parseInt(s);}catch(Exception ignored){}}return fallback;}
    public static String first(JSONObject object,String... keys){
        if(object==null)return "";for(String key:keys){Object value=object.opt(key);String text=string(value);if(!text.isEmpty()&&!"null".equalsIgnoreCase(text))return text;}return "";
    }
    public static String arabic(Object value){
        if(value instanceof String)return ((String)value).trim();
        if(value instanceof JSONObject){JSONObject o=(JSONObject)value;String result=first(o,"ar","arabic","name","en");if(!result.isEmpty())return result;Iterator<String> keys=o.keys();while(keys.hasNext()){String text=string(o.opt(keys.next()));if(!text.isEmpty())return text;}}
        return "";
    }
    public static String string(Object value){return value instanceof String?((String)value).trim():value instanceof Number||value instanceof Boolean?String.valueOf(value):"";}
    public static int qualityRank(String quality){String digits=quality==null?"":quality.replaceAll("[^0-9]","");try{return digits.isEmpty()?0:Integer.parseInt(digits);}catch(Exception e){return 0;}}
    public static String indexTitle(String index){
        switch(index){case "series_date_created":return "آخر الأعمال المضافة";case "series_fav_count_desc":return "الأكثر شهرة";case "best_mal_ranked":return "الأفضل عالمياً";case "most_watched_animations":case "all_animation":return "الأنميشن";case "series_name_asc":return "كل الأنمي";default:return "الأنمي";}
    }
    private static void put(JSONObject out,String key,Object value){try{out.put(key,value);}catch(JSONException ignored){}}
}
