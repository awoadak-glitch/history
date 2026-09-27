package awr.witcher;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Oscar-styled native Anime Witcher catalogue, episodes, sources, playback and local library. */
public final class AnimeActivity extends Activity {
    private final ArrayDeque<JSONObject> backStack=new ArrayDeque<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JSONObject page;
    private LinearLayout root,content;
    private ScrollView scroll;
    private int generation,searchGeneration;
    private Runnable searchTask;
    private interface Result {void show(JSONObject value);}
    private static JSONObject page(String kind){JSONObject o=new JSONObject();put(o,"kind",kind);return o;}
    private static void put(JSONObject o,String key,Object value){try{o.put(key,value);}catch(JSONException ignored){}}

    @Override protected void attachBaseContext(Context base){
        Configuration cfg=new Configuration(base.getResources().getConfiguration());
        cfg.uiMode=(cfg.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_YES;
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        if(state!=null)try{page=new JSONObject(state.getString("page","{}"));JSONArray list=new JSONArray(state.getString("back","[]"));for(int i=0;i<list.length();i++)backStack.addLast(list.getJSONObject(i));}catch(Exception ignored){}
        if(page==null||page.length()==0)page=page("home");render();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        if(scroll!=null)put(page,"scroll",scroll.getScrollY());state.putString("page",page.toString());JSONArray list=new JSONArray();for(JSONObject item:backStack)list.put(item);state.putString("back",list.toString());super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed(){if(backStack.isEmpty()){finish();return;}page=backStack.removeLast();render();}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);Media.cancelPending(this);super.onDestroy();}
    private void navigate(JSONObject next){
        if(scroll!=null)put(page,"scroll",scroll.getScrollY());if(backStack.size()>=14)backStack.removeFirst();backStack.addLast(page);page=next;
        View focus=getCurrentFocus();if(focus!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);render();
    }
    private Ui.Icon icon(String name){Ui.Icon v=new Ui.Icon(this,name,Ui.textColor(this));v.setPadding(Ui.dp(this,11),Ui.dp(this,11),Ui.dp(this,11),Ui.dp(this,11));Ui.clickable(v,0,24);return v;}
    private boolean topLevel(String kind){return kind.equals("home")||kind.equals("list")||kind.equals("search")||kind.equals("favorites")||kind.equals("history")||kind.equals("news");}
    private void render(){
        generation++;searchGeneration++;if(searchTask!=null)handler.removeCallbacks(searchTask);Media.cancelPending(this);
        String kind=page.optString("kind","home");getWindow().setStatusBarColor(Ui.bg(this));getWindow().setNavigationBarColor(Ui.surface(this));
        root=Ui.column(this);root.setBackgroundColor(Ui.bg(this));LinearLayout bar=Ui.row(this);bar.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);
        Ui.Icon back=icon("back");back.setContentDescription("رجوع");back.setOnClickListener(v->onBackPressed());bar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,50)));
        TextView title=Ui.text(this,page.optString("title",kind.equals("home")?"عالم الأنمي":"الأنمي"),21,true);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        Ui.Icon search=icon("search");search.setContentDescription("البحث في Anime Witcher");search.setOnClickListener(v->{JSONObject n=page("search");put(n,"title","بحث الأنمي");navigate(n);});bar.addView(search,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,50)));root.addView(bar,new LinearLayout.LayoutParams(-1,Ui.dp(this,58)));
        scroll=new ScrollView(this);scroll.setFillViewport(true);content=Ui.column(this);content.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),Ui.dp(this,28));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(topLevel(kind)){WitcherTabs tabs=new WitcherTabs(this);tabs.bind(1,this::switchTab);root.addView(tabs,new LinearLayout.LayoutParams(-1,Ui.dp(this,64)));}
        setContentView(root);
        switch(kind){case "list":showList();break;case "search":showSearch();break;case "detail":showDetail();break;case "episodes":showEpisodes();break;case "servers":showServers();break;case "favorites":showLibrary(false);break;case "history":showLibrary(true);break;case "news":showNews();break;default:showHome();}
    }
    private void switchTab(int tab){
        if(tab==0){finish();return;}if(tab==1){backStack.clear();page=page("home");render();return;}
        Intent intent=new Intent(this,DramaActivity.class).putExtra("tab",tab-1);startActivity(intent);finish();
    }
    private void loading(LinearLayout parent){ProgressBar b=new ProgressBar(this);b.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,34),Ui.dp(this,34));lp.gravity=Gravity.CENTER;lp.topMargin=lp.bottomMargin=Ui.dp(this,30);parent.addView(b,lp);}
    private void note(LinearLayout parent,String message){TextView t=Ui.text(this,message,15,false);t.setTextColor(Ui.muted(this));t.setGravity(Gravity.CENTER);t.setPadding(Ui.dp(this,12),Ui.dp(this,28),Ui.dp(this,12),Ui.dp(this,28));parent.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    private void heading(LinearLayout parent,String text){TextView t=Ui.text(this,text,20,true);t.setPadding(Ui.dp(this,4),Ui.dp(this,20),Ui.dp(this,4),Ui.dp(this,10));parent.addView(t);}
    private void load(String route,LinearLayout target,Result result){
        int token=generation;loading(target);AnimeApi.get(route,(value,error)->{if(token!=generation||isFinishing()||isDestroyed())return;target.removeAllViews();if(error!=null){note(target,error);target.addView(Ui.button(this,"إعادة المحاولة",v->{target.removeAllViews();load(route,target,result);}));}else{result.show(value);int y=page.optInt("scroll");if(y>0)scroll.post(()->scroll.scrollTo(0,y));}});
    }
    private TextView chip(String title,View.OnClickListener click){TextView button=Ui.button(this,title,click);button.setTextSize(13);button.setMinWidth(Ui.dp(this,94));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,Ui.dp(this,44));lp.setMargins(Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4));button.setLayoutParams(lp);return button;}
    private void navigation(){
        HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);horizontal.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);LinearLayout row=Ui.row(this);
        row.addView(chip("كل الأنمي",v->openList("series_name_asc","كل الأنمي")));row.addView(chip("أحدث الأعمال",v->openList("series_date_created","أحدث الأنمي")));row.addView(chip("الأكثر شهرة",v->openList("series_fav_count_desc","الأكثر شهرة")));row.addView(chip("الأفضل عالمياً",v->openList("best_mal_ranked","الأفضل عالمياً")));row.addView(chip("الأنميشن",v->openList("most_watched_animations","الأنميشن")));row.addView(chip("المفضلة",v->openLibrary(false)));row.addView(chip("السجل",v->openLibrary(true)));row.addView(chip("الأخبار",v->openNews()));horizontal.addView(row);content.addView(horizontal,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));
    }
    private void showHome(){
        navigation();LinearLayout body=Ui.column(this);content.addView(body);load("home",body,value->{
            JSONArray hero=AnimeApi.cards(AnimeApi.array(value.opt("hero")));if(hero.length()>0)body.addView(Cards.hero(this,hero,this::openDetail));
            JSONArray recent=AnimeApi.cards(AnimeApi.array(value.opt("recent")));rail(body,"أحدث الحلقات",recent);
            rail(body,"الأكثر شهرة هذا الموسم",AnimeApi.cards(AnimeApi.array(value.opt("popular"))));
            rail(body,"أفضل الأنميات عالمياً",AnimeApi.cards(AnimeApi.array(value.opt("bestMal"))));
            rail(body,"الأنميشن الأكثر مشاهدة",AnimeApi.cards(AnimeApi.array(value.opt("animations"))));
            rail(body,"آخر الأعمال المضافة",AnimeApi.cards(AnimeApi.array(value.opt("latest"))));
            JSONArray history=library(true);if(history.length()>0)rail(body,"متابعة المشاهدة",history);
            if(hero.length()+recent.length()==0)note(body,"تعذر جلب واجهة Anime Witcher حالياً.");
        });
    }
    private void rail(LinearLayout parent,String title,JSONArray items){Cards.rail(this,parent,title,items,this::openDetail);}
    private void openList(String index,String title){JSONObject n=page("list");put(n,"index",index);put(n,"title",title);navigate(n);}
    private void openLibrary(boolean history){JSONObject n=page(history?"history":"favorites");put(n,"title",history?"سجل المشاهدة":"المفضلة");navigate(n);}
    private void openNews(){JSONObject n=page("news");put(n,"title","أخبار الأنمي");navigate(n);}
    private void openDetail(JSONObject item){JSONObject n=page("detail");put(n,"item",AnimeApi.card(item));put(n,"title",AnimeApi.first(AnimeApi.card(item),"title"));navigate(n);}

    private void showList(){
        navigation();String index=page.optString("index","series");LinearLayout results=Ui.column(this);content.addView(results);listPage(results,index,"",page.optInt("lastPage",0));
    }
    private void listPage(LinearLayout target,String index,String query,int number){
        LinearLayout batch=Ui.column(this);target.addView(batch);int token=generation;loading(batch);AnimeApi.get(AnimeApi.search(query,index,number),(value,error)->{
            if(token!=generation||isFinishing()||isDestroyed())return;batch.removeAllViews();if(error!=null){note(batch,error);return;}JSONArray items=AnimeApi.cards(AnimeApi.items(value));if(items.length()==0){if(number==0)note(batch,"لا توجد أعمال في هذا القسم حالياً.");return;}Cards.grid(this,batch,items,false,this::openDetail);
            int pages=value.optInt("nbPages",number+1);if(number+1<pages){TextView more=Ui.button(this,"عرض المزيد",v->{put(page,"lastPage",number+1);batch.removeView(v);listPage(target,index,query,number+1);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(this,14);batch.addView(more,lp);}
        });
    }
    private void showSearch(){
        EditText field=new EditText(this);field.setSingleLine(true);field.setTextSize(16);field.setTextColor(Ui.textColor(this));field.setHintTextColor(Ui.muted(this));field.setHint("ابحث في جميع أعمال Anime Witcher");field.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),0);field.setBackground(Ui.rounded(this,Ui.surface(this),12));field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);content.addView(field,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));LinearLayout results=Ui.column(this);content.addView(results);
        String query=page.optString("query");field.setText(query);if(query.isEmpty())note(results,"اكتب اسم الأنمي للبحث.");else search(results,query,++searchGeneration);
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){if(searchTask!=null)handler.removeCallbacks(searchTask);String q=s.toString().trim();put(page,"query",q);results.removeAllViews();int token=++searchGeneration;if(q.isEmpty()){note(results,"اكتب اسم الأنمي للبحث.");return;}searchTask=()->search(results,q,token);handler.postDelayed(searchTask,420);}});
        field.setOnEditorActionListener((v,a,e)->{if(searchTask!=null)handler.removeCallbacks(searchTask);String q=field.getText().toString().trim();results.removeAllViews();int token=++searchGeneration;if(!q.isEmpty())search(results,q,token);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);return true;});
    }
    private void search(LinearLayout target,String query,int token){loading(target);AnimeApi.get(AnimeApi.search(query,"series",0),(value,error)->{if(token!=searchGeneration||isFinishing())return;target.removeAllViews();if(error!=null){note(target,error);return;}JSONArray items=AnimeApi.cards(AnimeApi.items(value));if(items.length()==0)note(target,"لم نعثر على نتائج لهذا الاسم.");else Cards.grid(this,target,items,false,this::openDetail);});}

    private void showDetail(){
        JSONObject seed=page.optJSONObject("item");if(seed==null){note(content,"تعذر فتح العمل.");return;}String id=seed.optString("id");load(AnimeApi.anime(id),content,value->{JSONObject item=AnimeApi.card(value);put(item,"raw",value);put(page,"item",item);renderDetail(item,value);});
    }
    private void renderDetail(JSONObject item,JSONObject raw){
        String cover=item.optString("cover",item.optString("image"));content.addView(Ui.image(this,cover,14),new LinearLayout.LayoutParams(-1,Ui.dp(this,235)));heading(content,item.optString("title"));
        LinearLayout actions=Ui.row(this);TextView episodes=Ui.button(this,"▶  الحلقات والمشاهدة",v->{JSONObject n=page("episodes");put(n,"item",item);put(n,"title",item.optString("title"));navigate(n);});episodes.setTextColor(Color.WHITE);Ui.clickable(episodes,Ui.ACCENT,12);actions.addView(episodes,new LinearLayout.LayoutParams(0,Ui.dp(this,52),2));
        boolean favorite=isFavorite(item.optString("id"));TextView fav=Ui.button(this,favorite?"★ في المفضلة":"☆ إضافة للمفضلة",v->{toggleFavorite(item);render();});LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,Ui.dp(this,52),1);fp.setMarginStart(Ui.dp(this,8));actions.addView(fav,fp);content.addView(actions);
        JSONObject details=raw.optJSONObject("details");ArrayList<String> meta=new ArrayList<>();for(String value:new String[]{AnimeApi.first(raw,"type"),details==null?"":AnimeApi.first(details,"state","status"),details==null?"":AnimeApi.first(details,"eps_num"),details==null?"":AnimeApi.first(details,"age"),AnimeApi.first(raw,"duration")})if(!value.isEmpty())meta.add(value);TextView info=Ui.text(this,TextUtils.join("  •  ",meta),13,false);info.setTextColor(Ui.muted(this));info.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,8));content.addView(info);
        JSONArray tags=raw.optJSONArray("tags");if(tags!=null&&tags.length()>0){ArrayList<String> names=new ArrayList<>();for(int i=0;i<tags.length();i++)names.add(tags.optString(i));TextView tag=Ui.text(this,TextUtils.join("  ·  ",names),13,false);tag.setTextColor(Ui.ACCENT);content.addView(tag);}
        String story=AnimeApi.arabic(raw.opt("story"));if(story.isEmpty()&&details!=null)story=AnimeApi.arabic(details.opt("story"));if(!story.isEmpty()){heading(content,"القصة");TextView text=Ui.text(this,story,16,false);text.setLineSpacing(Ui.dp(this,5),1);content.addView(text);}
        String youtube=AnimeApi.first(raw,"youtube_video_id");JSONObject trailer=raw.optJSONObject("trailer");if(youtube.isEmpty()&&trailer!=null)youtube=AnimeApi.first(trailer,"youtube_video_id","video_id","id");if(!youtube.isEmpty()){final String video=youtube;TextView button=Ui.button(this,"▶  العرض الدعائي",v->openExternal("https://www.youtube.com/watch?v="+Uri.encode(video)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(this,16);content.addView(button,lp);}
    }

    private void showEpisodes(){
        JSONObject anime=page.optJSONObject("item");if(anime==null)return;LinearLayout list=Ui.column(this);content.addView(list);load(AnimeApi.episodes(anime.optString("id")),list,value->{JSONArray input=AnimeApi.items(value);ArrayList<JSONObject> episodes=new ArrayList<>();for(int i=0;i<input.length();i++){JSONObject ep=input.optJSONObject(i);if(ep!=null)episodes.add(ep);}Collections.sort(episodes,(a,b)->Integer.compare(AnimeApi.episodeNumber(b,0),AnimeApi.episodeNumber(a,0)));if(episodes.isEmpty()){note(list,"لا توجد حلقات متاحة حالياً.");return;}for(int i=0;i<episodes.size();i++)episodeRow(list,anime,episodes.get(i),episodes.size()-i);});
    }
    private void episodeRow(LinearLayout parent,JSONObject anime,JSONObject episode,int fallback){
        String id=AnimeApi.episodeId(episode,fallback);int number=AnimeApi.episodeNumber(episode,fallback);String translated=AnimeApi.first(episode,"title_translated","title");String image=AnimeApi.first(episode,"thumb_uri","cover","image");if(image.isEmpty())image=anime.optString("cover",anime.optString("image"));
        LinearLayout row=Ui.row(this);row.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));Ui.clickable(row,Ui.surface(this),12);row.addView(Ui.image(this,image,8),new LinearLayout.LayoutParams(Ui.dp(this,104),Ui.dp(this,68)));LinearLayout labels=Ui.column(this);TextView title=Ui.text(this,"الحلقة "+number,16,true);labels.addView(title);if(!translated.isEmpty()&&!translated.equals("null")){TextView sub=Ui.text(this,translated,12,false);sub.setTextColor(Ui.muted(this));labels.addView(sub);}row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));row.addView(new Ui.Icon(this,"play",Ui.ACCENT),new LinearLayout.LayoutParams(Ui.dp(this,28),Ui.dp(this,28)));row.setOnClickListener(v->{JSONObject n=page("servers");put(n,"item",anime);put(n,"episode",episode);put(n,"episodeId",id);put(n,"episodeNumber",number);put(n,"title",anime.optString("title")+" • الحلقة "+number);navigate(n);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(this,9);parent.addView(row,lp);
    }

    private void showServers(){
        JSONObject anime=page.optJSONObject("item"),episode=page.optJSONObject("episode");if(anime==null||episode==null)return;String episodeId=page.optString("episodeId");LinearLayout list=Ui.column(this);content.addView(list);load(AnimeApi.servers(anime.optString("id"),episodeId),list,value->{JSONArray servers=AnimeApi.items(value);if(servers.length()==0){note(list,"لا توجد سيرفرات لهذه الحلقة حالياً.");return;}ArrayList<JSONObject> sorted=new ArrayList<>();for(int i=0;i<servers.length();i++){JSONObject s=servers.optJSONObject(i);if(s!=null){put(s,"_index",i);sorted.add(s);}}Collections.sort(sorted,(a,b)->Integer.compare(AnimeApi.qualityRank(b.optString("quality")),AnimeApi.qualityRank(a.optString("quality"))));String last="";for(JSONObject server:sorted){String quality=server.optString("quality","متعدد");if(!quality.equals(last)){heading(list,quality);last=quality;}serverRow(list,anime,episode,server);}});
    }
    private void serverRow(LinearLayout parent,JSONObject anime,JSONObject episode,JSONObject server){
        LinearLayout row=Ui.row(this);row.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));Ui.clickable(row,Ui.surface(this),11);TextView name=Ui.text(this,"سيرفر "+server.optString("name","متاح"),16,true);row.addView(name,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));TextView watch=Ui.button(this,"مشاهدة",v->resolve(anime,episode,server,false));watch.setTextColor(Color.WHITE);Ui.clickable(watch,Ui.ACCENT,10);row.addView(watch,new LinearLayout.LayoutParams(Ui.dp(this,96),Ui.dp(this,46)));Ui.Icon download=icon("download");download.setContentDescription("تنزيل");download.setOnClickListener(v->resolve(anime,episode,server,true));row.addView(download,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(this,8);parent.addView(row,lp);
    }
    private void resolve(JSONObject anime,JSONObject episode,JSONObject server,boolean download){
        ProgressDialog wait=new ProgressDialog(this);wait.setMessage(download?"جاري تجهيز رابط التنزيل…":"جاري تجهيز رابط المشاهدة…");wait.setCancelable(true);wait.show();String animeId=anime.optString("id"),episodeId=page.optString("episodeId");int index=server.optInt("_index");int token=generation;AnimeApi.fresh(AnimeApi.resolve(animeId,episodeId,index),(value,error)->{if(token!=generation||isFinishing())return;wait.dismiss();if(error!=null){alert(error);return;}String url=value.optString("url");if(url.isEmpty()){alert("لم يُرجع السيرفر رابطاً صالحاً.");return;}remember(anime,episode);JSONObject source=new JSONObject();put(source,"url",url);put(source,"type",mediaType(url));put(source,"quality",value.optString("quality",server.optString("quality")));String title=anime.optString("title")+" - الحلقة "+page.optInt("episodeNumber");Media.open(this,source,title,download);});
    }
    private String mediaType(String url){String lower=url.toLowerCase(Locale.ROOT);if(lower.contains(".m3u8"))return "m3u8";if(lower.contains(".mpd"))return "mpd";if(lower.matches(".*\\.(mp4|mkv|webm)(\\?.*)?$" )||lower.contains("/api/file/")||lower.contains("/get_video?"))return "mp4";return "webm";}

    private void showLibrary(boolean history){navigation();JSONArray values=library(history);if(values.length()==0){note(content,history?"سجل المشاهدة فارغ.":"لم تضف أعمالاً إلى المفضلة بعد.");return;}Cards.grid(this,content,values,false,this::openDetail);}
    private JSONArray library(boolean history){String key=history?"awr_anime_history":"awr_anime_favorites";try{return new JSONArray(getSharedPreferences("awr_anime",MODE_PRIVATE).getString(key,"[]"));}catch(JSONException e){return new JSONArray();}}
    private void saveLibrary(boolean history,JSONArray values){getSharedPreferences("awr_anime",MODE_PRIVATE).edit().putString(history?"awr_anime_history":"awr_anime_favorites",values.toString()).apply();}
    private boolean isFavorite(String id){JSONArray list=library(false);for(int i=0;i<list.length();i++)if(id.equals(list.optJSONObject(i)==null?"":list.optJSONObject(i).optString("id")))return true;return false;}
    private void toggleFavorite(JSONObject item){String id=item.optString("id");JSONArray current=library(false),next=new JSONArray();boolean removed=false;for(int i=0;i<current.length();i++){JSONObject old=current.optJSONObject(i);if(old!=null&&id.equals(old.optString("id"))){removed=true;continue;}if(old!=null)next.put(old);}if(!removed)next.put(AnimeApi.card(item));saveLibrary(false,next);Toast.makeText(this,removed?"أزيل من المفضلة":"أضيف إلى المفضلة",Toast.LENGTH_SHORT).show();}
    private void remember(JSONObject anime,JSONObject episode){JSONObject item=AnimeApi.card(anime);put(item,"episodeId",page.optString("episodeId"));put(item,"episodeName","الحلقة "+page.optInt("episodeNumber"));put(item,"watchedAt",System.currentTimeMillis());JSONArray current=library(true),next=new JSONArray();next.put(item);for(int i=0;i<current.length()&&next.length()<80;i++){JSONObject old=current.optJSONObject(i);if(old!=null&&!(item.optString("id").equals(old.optString("id"))&&item.optString("episodeId").equals(old.optString("episodeId"))))next.put(old);}saveLibrary(true,next);}

    private void showNews(){navigation();LinearLayout list=Ui.column(this);content.addView(list);load("news",list,value->{JSONArray news=AnimeApi.items(value);if(news.length()==0){note(list,"لا توجد أخبار متاحة حالياً.");return;}for(int i=0;i<news.length();i++){JSONObject item=news.optJSONObject(i);if(item==null)continue;LinearLayout row=Ui.row(this);row.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));Ui.clickable(row,Ui.surface(this),12);String image=AnimeApi.first(item,"image","thumb_link","thumb_uri");row.addView(Ui.image(this,image,9),new LinearLayout.LayoutParams(Ui.dp(this,116),Ui.dp(this,76)));TextView title=Ui.text(this,AnimeApi.first(item,"title","name"),15,true);title.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),0);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));String link=AnimeApi.first(item,"url","news_link");String animeId=AnimeApi.first(item,"animeId","anime_id");row.setOnClickListener(v->{if(!animeId.isEmpty()){JSONObject card=new JSONObject();put(card,"id",animeId);put(card,"title",AnimeApi.first(item,"title"));put(card,"image",image);openDetail(card);}else if(!link.isEmpty())openExternal(link);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(this,9);list.addView(row,lp);}});
    }
    private void openExternal(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){alert("تعذر فتح الرابط.");}}
    private void alert(String message){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setMessage(message).setPositiveButton("حسناً",null).show();}
}
