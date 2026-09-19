package awr.witcher;

import org.json.*;
import java.util.*;

/** Typed contexts prevent actors, banner IDs and metadata from becoming films. */
final class OscarCatalog {
    static final class Item {
        final String kind,id,title,poster,banner,story,subtitle;final JSONObject raw;
        Item(String k,JSONObject o){kind=k;raw=o;id=text(o,"id");
            String name=text(o,"title_ar","title","title_en","name","series_title","anime_title");
            if("match".equals(k)){JSONObject h=o.optJSONObject("home_team"),a=o.optJSONObject("away_team");name=text(h,"name_ar","name")+"  ×  "+text(a,"name_ar","name");}
            if(name.isEmpty())name=k.contains("episode")?"الحلقة "+text(o,"episode_number"):"بدون عنوان";
            title=name;poster=image(text(o,"poster","thumbnail","logo","series_poster","anime_poster","league_logo"));banner=image(text(o,"banner","cover","series_banner","anime_banner","poster"));story=text(o,"story","description");
            subtitle=join(text(o,"release_year","match_date"),text(o,"country_name","status"),text(o,"quality","qualities","match_time"));
        }
        String key(){return kind+":"+id;}
    }
    static final class Page {final List<Item> items;final int page,totalPages;Page(JSONObject o,String kind,int requested){items=items(o.optJSONArray("data"),kind);JSONObject p=o.optJSONObject("pagination");page=p==null?requested:p.optInt("page",requested);totalPages=p==null?page:Math.max(page,p.optInt("total_pages",page));}boolean more(){return page<totalPages;}}
    static final class Section {final String title,kind;final List<Item> items;final Map<String,String> filters=new LinkedHashMap<>();final boolean more;Section(JSONObject o){String type=text(o,"section_type");kind=sectionKind(type);title=text(o,"title_ar","title_en");items=items(o.optJSONArray("items"),kind);more=!o.optBoolean("hide_see_all")&&!kind.isEmpty()&&!kind.contains("episode");JSONObject f=o.optJSONObject("content_filters");if(f!=null)for(String key:new String[]{"year","country","genre_id","language","content_type","age_rating","collection_id","quality_source","featured","awards"})put(filters,key,text(f,key));if(f!=null)put(filters,"category",text(f,"category_id"));put(filters,"sort_by",text(o,"sort_by"));put(filters,"content_type",text(o,"content_category_filter"));}}
    static String text(JSONObject o,String... keys){if(o==null)return "";for(String key:keys){Object v=o.opt(key);if(v!=null&&v!=JSONObject.NULL&&!(v instanceof JSONObject)&&!(v instanceof JSONArray)){String s=String.valueOf(v).trim();if(!s.isEmpty()&&!s.equals("null"))return s;}}return "";}
    static void put(Map<String,String> m,String k,String v){if(v!=null&&!v.isEmpty())m.put(k,v);}
    static String join(String... values){StringBuilder b=new StringBuilder();for(String s:values)if(s!=null&&!s.isEmpty()){if(b.length()>0)b.append(" • ");b.append(s);}return b.toString();}
    static List<Item> items(JSONArray a,String kind){List<Item> list=new ArrayList<>();if(a==null||kind.isEmpty())return list;Set<String> ids=new HashSet<>();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||text(o,"id").isEmpty())continue;Item item=new Item(kind,o);if(ids.add(item.key()))list.add(item);}return list;}
    static String sectionKind(String type){if(type.startsWith("anime_"))return type.contains("episode")?"anime_episode":"anime";if(type.contains("wrestling"))return "wrestling";if(type.startsWith("movies"))return "movie";if(type.equals("latest_episodes"))return "episode";if(type.startsWith("channels"))return type.contains("country")||type.contains("category")?"":"channel";if(type.contains("matches"))return "match";if(type.startsWith("series")||type.equals("featured")||type.equals("latest")||type.equals("top_rated")||type.equals("most_viewed"))return "series";return "";}
    static List<Item> banners(JSONObject data){List<Item> list=new ArrayList<>();JSONArray a=data.optJSONArray("banners");if(a==null)return list;for(int i=0;i<a.length();i++){JSONObject b=a.optJSONObject(i);if(b==null)continue;String kind=text(b,"item_type"),id=text(b,"item_id","series_id");if(!Arrays.asList("movie","series","anime","wrestling","channel").contains(kind))kind="series";if(id.isEmpty())continue;try{JSONObject copy=new JSONObject(b.toString());copy.put("id",id);list.add(new Item(kind,copy));}catch(JSONException ignored){}}return list;}
    static String image(String value){if(value.isEmpty())return "";if(value.startsWith("https://admin.dramaramadan.net/")||value.startsWith("http://admin.dramaramadan.net/"))return OscarApi.BASE+value.substring(value.indexOf('/',value.indexOf("://")+3)+1);if(value.startsWith("http://")||value.startsWith("https://"))return value;String p=value.startsWith("/")?value.substring(1):value;for(String prefix:new String[]{"uploads/","storage/","api/","static/","img/"})if(p.startsWith(prefix))return OscarApi.BASE+p;return "https://image.tmdb.org/t/p/w780/"+p;}
    static String listRoute(String kind){switch(kind){case "movie":return "api/movies/";case "series":return "api/series/";case "anime":return "api/anime/";case "channel":return "api/channels/";case "match":return "api/matches/";case "wrestling":return "api/wrestling/";case "episode":return "api/episodes/";case "anime_episode":return "api/anime/episodes/";default:throw new IllegalArgumentException("Unknown catalogue "+kind);}}
    static String detailRoute(String kind){return listRoute(kind)+"show.php";}
    static Map<String,String> query(String kind,String search,int page,Map<String,String> filters){Map<String,String> q=new LinkedHashMap<>(filters);q.put("page",String.valueOf(page));q.put(kind.contains("episode")?"per_page":"limit","24");put(q,kind.contains("episode")?"q":"search",search);if(kind.equals("series")||kind.equals("movie"))q.put("app_version","14");return q;}
    static List<Item> matches(JSONObject data){List<Item> result=new ArrayList<>();JSONArray leagues=data.optJSONArray("leagues");if(leagues!=null)for(int i=0;i<leagues.length();i++){JSONObject league=leagues.optJSONObject(i);if(league!=null)result.addAll(items(league.optJSONArray("matches"),"match"));}return result;}
}
