package awr.witcher;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Native content screens alongside Anime Witcher's unmodified home activity. */
public final class DramaActivity extends Activity {
    private final ArrayDeque<JSONObject> history=new ArrayDeque<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JSONObject page;
    private LinearLayout root,content;
    private ScrollView scroll;
    private int generation,searchGeneration,tab;
    private Runnable searchTask;
    private interface Result {void show(Object value);}
    private static void put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException e){throw new IllegalArgumentException(e);}}
    private static JSONObject make(String kind){JSONObject o=new JSONObject();put(o,"kind",kind);return o;}

    @Override protected void attachBaseContext(Context base){
        Configuration cfg=new Configuration(base.getResources().getConfiguration());
        String mode=base.getSharedPreferences("my_pref",0).getString("default_appearance","");
        if(mode.contains("ليلي"))cfg.uiMode=(cfg.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_YES;
        else if(mode.contains("نهاري"))cfg.uiMode=(cfg.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_NO;
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        tab=Math.max(1,Math.min(3,getIntent().getIntExtra("tab",1)));
        if(state!=null){tab=state.getInt("tab",tab);try{page=new JSONObject(state.getString("page","{}"));JSONArray a=new JSONArray(state.getString("back","[]"));for(int i=0;i<a.length();i++)history.addLast(a.getJSONObject(i));}catch(Exception ignored){}}
        if(page==null||page.length()==0)page=make("catalog");render();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        if(scroll!=null)put(page,"scroll",scroll.getScrollY());state.putString("page",page.toString());state.putInt("tab",tab);
        JSONArray back=new JSONArray();for(JSONObject p:history)back.put(p);state.putString("back",back.toString());super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed(){if(history.isEmpty()){finish();return;}page=history.removeLast();render();}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private void navigate(JSONObject next){
        if(scroll!=null)put(page,"scroll",scroll.getScrollY());if(history.size()>=12)history.removeFirst();history.addLast(page);page=next;
        View focus=getCurrentFocus();if(focus!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);render();
    }
    private Ui.Icon icon(String name){Ui.Icon v=new Ui.Icon(this,name,Ui.textColor(this));v.setPadding(Ui.dp(this,11),Ui.dp(this,11),Ui.dp(this,11),Ui.dp(this,11));Ui.clickable(v,0,24);return v;}
    private void render(){
        generation++;searchGeneration++;if(searchTask!=null)handler.removeCallbacks(searchTask);
        String kind=page.optString("kind","catalog");getWindow().setStatusBarColor(Ui.bg(this));getWindow().setNavigationBarColor(Ui.surface(this));
        root=Ui.column(this);root.setBackgroundColor(Ui.bg(this));LinearLayout bar=Ui.row(this);bar.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);
        Ui.Icon back=icon("back");back.setContentDescription("رجوع");back.setOnClickListener(v->onBackPressed());bar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));
        TextView title=Ui.text(this,page.optString("title",WitcherTabs.LABELS[tab]),20,true);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        Ui.Icon search=icon("search");search.setContentDescription("بحث في عالم الدراما");search.setOnClickListener(v->{JSONObject n=make("search");put(n,"title","البحث");navigate(n);});bar.addView(search,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));root.addView(bar,new LinearLayout.LayoutParams(-1,Ui.dp(this,56)));
        scroll=new ScrollView(this);scroll.setFillViewport(true);content=Ui.column(this);content.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),Ui.dp(this,24));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(kind.equals("catalog")||kind.equals("search")){WitcherTabs tabs=new WitcherTabs(this);tabs.bind(tab,next->{if(next==0){finish();return;}tab=next;history.clear();page=make("catalog");render();});root.addView(tabs,new LinearLayout.LayoutParams(-1,Ui.dp(this,64)));}
        setContentView(root);switch(kind){case "search":showSearch();break;case "detail":showDetail();break;case "seasons":showSeasons();break;case "sources":showSources();break;default:showCatalog();}
    }
    private void message(LinearLayout parent,String s){TextView t=Ui.text(this,s,15,false);t.setGravity(Gravity.CENTER);t.setTextColor(Ui.muted(this));t.setPadding(Ui.dp(this,12),Ui.dp(this,30),Ui.dp(this,12),Ui.dp(this,30));parent.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    private void loading(LinearLayout parent){ProgressBar b=new ProgressBar(this);b.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,32),Ui.dp(this,32));lp.gravity=Gravity.CENTER;lp.topMargin=lp.bottomMargin=Ui.dp(this,28);parent.addView(b,lp);}
    private void heading(LinearLayout parent,String s){TextView t=Ui.text(this,s,20,true);t.setPadding(Ui.dp(this,4),Ui.dp(this,18),0,Ui.dp(this,12));parent.addView(t);}
    private void load(String route,LinearLayout target,Result result){
        final int token=generation;loading(target);Api.get(route,(value,error)->{
            if(token!=generation||isFinishing()||isDestroyed())return;target.removeAllViews();
            if(error!=null){message(target,error);target.addView(Ui.button(this,"إعادة المحاولة",v->{target.removeAllViews();load(route,target,result);}));}
            else{result.show(value);ScrollView s=scroll;int y=page.optInt("scroll");if(y>0)s.post(()->s.scrollTo(0,y));}
        });
    }
    private void markChannels(JSONArray a){for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)put(o,"_channel",true);}}
    private void showCatalog(){
        LinearLayout filters=Ui.row(this);filters.addView(Ui.button(this,page.optString("categoryTitle","جميع التصنيفات"),v->chooseCategory()),new LinearLayout.LayoutParams(0,-2,1));
        if(tab!=3){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,90),-2);lp.setMarginStart(Ui.dp(this,8));filters.addView(Ui.button(this,"ترتيب",v->chooseOrder()),lp);}content.addView(filters);LinearLayout results=Ui.column(this);content.addView(results);catalogPage(results,0);
    }
    private void catalogPage(LinearLayout target,int number){
        LinearLayout batch=Ui.column(this);target.addView(batch);final int selected=tab;
        load(Api.list(tab,page.optInt("category"),page.optString("order","created"),number),batch,value->{
            JSONArray list=Api.array(value);if(selected==3)markChannels(list);if(list.length()==0){if(number==0)message(batch,"لا يوجد محتوى في هذا القسم حالياً.");return;}
            if(number==0&&selected!=3&&page.optInt("category")==0){batch.addView(Cards.hero(this,list,this::detail));heading(batch,selected==1?"أحدث المسلسلات":"أحدث الأفلام");}Cards.grid(this,batch,list,selected==3,this::detail);
            TextView more=Ui.button(this,"عرض المزيد",v->{});batch.addView(more,new LinearLayout.LayoutParams(-1,-2));more.setOnClickListener(v->{batch.removeView(more);catalogPage(target,number+1);});
        });
    }
    private void chooseOrder(){String[] titles={"الأحدث إضافة","الأعلى تقييماً","تقييم IMDb","الاسم","السنة","الأكثر مشاهدة"},values={"created","rating","imdb","title","year","views"};new AlertDialog.Builder(this).setTitle("ترتيب المحتوى").setItems(titles,(d,i)->{put(page,"order",values[i]);put(page,"scroll",0);render();}).show();}
    private void chooseCategory(){
        final int token=generation;Api.get(tab==3?"category/all/":"genre/all/",(value,error)->{
            if(token!=generation||isFinishing())return;if(error!=null){Toast.makeText(this,error,Toast.LENGTH_LONG).show();return;}JSONArray list=Api.array(value);String[] names=new String[list.length()+1];names[0]="جميع التصنيفات";for(int i=0;i<list.length();i++)names[i+1]=Api.label(list.optJSONObject(i));
            new AlertDialog.Builder(this).setTitle("التصنيفات").setItems(names,(d,i)->{put(page,"category",i==0?0:list.optJSONObject(i-1).optInt("id"));put(page,"categoryTitle",names[i]);put(page,"scroll",0);render();}).show();
        });
    }
    private void showSearch(){
        EditText field=new EditText(this);field.setSingleLine(true);field.setTextSize(16);field.setTextColor(Ui.textColor(this));field.setHintTextColor(Ui.muted(this));field.setHint("ابحث عن فيلم أو مسلسل أو قناة");field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);field.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),0);field.setBackground(Ui.rounded(this,Ui.surface(this),12));content.addView(field,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));LinearLayout results=Ui.column(this);content.addView(results);
        String query=page.optString("query");field.setText(query);if(query.trim().isEmpty())message(results,"اكتب اسم العمل للبحث في مصادر عالم الدراما.");else searchPage(results,query,0,++searchGeneration);
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){
            if(searchTask!=null)handler.removeCallbacks(searchTask);String q=s.toString().trim();put(page,"query",q);int token=++searchGeneration;results.removeAllViews();if(q.isEmpty()){message(results,"اكتب اسم العمل للبحث.");return;}searchTask=()->searchPage(results,q,0,token);handler.postDelayed(searchTask,450);
        }});
        field.setOnEditorActionListener((v,action,event)->{if(searchTask!=null)handler.removeCallbacks(searchTask);results.removeAllViews();String q=field.getText().toString().trim();if(!q.isEmpty())searchPage(results,q,0,++searchGeneration);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);return true;});
    }
    private void searchPage(LinearLayout target,String query,int number,int token){
        final int screen=generation;LinearLayout batch=Ui.column(this);target.addView(batch);loading(batch);Api.get(Api.search(query,number),(value,error)->{
            if(token!=searchGeneration||screen!=generation||isFinishing()||isDestroyed())return;batch.removeAllViews();if(error!=null){message(batch,error);batch.addView(Ui.button(this,"إعادة المحاولة",v->{target.removeView(batch);searchPage(target,query,number,token);}));return;}
            JSONObject data=Api.object(value);JSONArray posters=Api.array(data.optJSONArray("posters")),channels=Api.array(data.optJSONArray("channels"));markChannels(channels);if(posters.length()+channels.length()==0){if(number==0)message(batch,"لم نعثر على نتائج لهذا الاسم.");return;}
            if(posters.length()>0){heading(batch,"الأفلام والمسلسلات");Cards.grid(this,batch,posters,false,this::detail);}if(channels.length()>0){heading(batch,"القنوات");Cards.grid(this,batch,channels,true,this::detail);}
            TextView more=Ui.button(this,"نتائج إضافية",v->{});batch.addView(more);more.setOnClickListener(v->{batch.removeView(more);searchPage(target,query,number+1,token);});
        });
    }
    private void detail(JSONObject item){JSONObject n=make("detail");put(n,"item",item);put(n,"title",Api.label(item));navigate(n);}
    private void showDetail(){JSONObject initial=page.optJSONObject("item");if(initial==null){message(content,"تعذر فتح هذا العمل.");return;}load(Api.detail(Api.channel(initial),initial.optInt("id")),content,value->{JSONObject item=Api.object(value);if(Api.channel(initial))put(item,"_channel",true);put(page,"item",item);renderDetail(item);});}
    private void renderDetail(JSONObject item){
        boolean channel=Api.channel(item),series=Api.series(item);String cover=item.optString("cover");if(cover.isEmpty())cover=item.optString("image");content.addView(Ui.image(this,cover,12),new LinearLayout.LayoutParams(-1,Ui.dp(this,channel?210:245)));heading(content,Api.label(item));
        ArrayList<String> meta=new ArrayList<>();for(String key:new String[]{"year","duration","imdb"}){String s=item.optString(key);if(!s.isEmpty()&&!s.equals("null")&&!s.equals("0"))meta.add((key.equals("imdb")?"IMDb  ":"")+s);}TextView info=Ui.text(this,TextUtils.join("  •  ",meta),13,false);info.setTextColor(Ui.muted(this));content.addView(info);
        JSONArray genres=item.optJSONArray("genres");if(genres!=null){ArrayList<String> names=new ArrayList<>();for(int i=0;i<genres.length();i++){JSONObject o=genres.optJSONObject(i);if(o!=null)names.add(Api.label(o));}TextView tags=Ui.text(this,TextUtils.join("  ·  ",names),13,false);tags.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,6));content.addView(tags);}
        TextView watch=Ui.button(this,series?"عرض الحلقات":"مشاهدة",v->{if(series){JSONObject n=make("seasons");put(n,"item",item);put(n,"title",Api.label(item));navigate(n);}else sources(item,false,false);});Ui.clickable(watch,Ui.ACCENT,12);watch.setTextColor(Color.BLACK);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,Ui.dp(this,52));wp.topMargin=Ui.dp(this,18);wp.bottomMargin=Ui.dp(this,12);content.addView(watch,wp);
        if(!series&&!channel)content.addView(Ui.button(this,"تنزيل",v->sources(item,false,true)));
        String description=item.optString("description");if(!description.isEmpty()){heading(content,"القصة");TextView summary=Ui.text(this,android.text.Html.fromHtml(description).toString(),16,false);summary.setLineSpacing(Ui.dp(this,5),1);content.addView(summary);}
    }
    private void showSeasons(){
        JSONObject show=page.optJSONObject("item");load("season/by/serie/"+show.optInt("id")+"/",content,value->{JSONArray seasons=Api.array(value);if(seasons.length()==0){message(content,"لم تُضف حلقات لهذا العمل بعد.");return;}
            for(int i=0;i<seasons.length();i++){
                JSONObject season=seasons.optJSONObject(i);if(season==null)continue;JSONArray episodes=Api.array(season.optJSONArray("episodes"));LinearLayout list=Ui.column(this);TextView head=Ui.button(this,Api.label(season)+"  ·  "+episodes.length()+" حلقة",v->list.setVisibility(list.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=Ui.dp(this,12);content.addView(head,hp);content.addView(list);if(i>0)list.setVisibility(View.GONE);
                for(int j=0;j<episodes.length();j++){
                    JSONObject episode=episodes.optJSONObject(j);if(episode==null)continue;if(!episode.has("playas"))put(episode,"playas",show.optString("playas"));if(!episode.has("downloadas"))put(episode,"downloadas",show.optString("downloadas"));
                    LinearLayout row=Ui.row(this);row.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));row.addView(Ui.image(this,episode.optString("image",show.optString("image")),7),new LinearLayout.LayoutParams(Ui.dp(this,92),Ui.dp(this,60)));TextView title=Ui.text(this,Api.label(episode),15,true);title.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),0);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->sources(episode,true,false));Ui.Icon download=icon("download");download.setContentDescription("تنزيل "+Api.label(episode));row.addView(download,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));download.setOnClickListener(v->sources(episode,true,true));list.addView(row);
                }
            }
        });
    }
    private void restricted(){new AlertDialog.Builder(this).setMessage("هذا المصدر يتطلب حساباً أو اشتراكاً في عالم الدراما. ربط الحساب غير متاح في هذه النسخة بعد.").setPositiveButton("حسناً",null).show();}
    private void sources(JSONObject item,boolean episode,boolean download){if(!Api.publicAccess(item.optString(download?"downloadas":"playas"))){restricted();return;}JSONObject n=make("sources");put(n,"item",item);put(n,"episode",episode);put(n,"download",download);put(n,"title",Api.label(item));navigate(n);}
    private void showSources(){
        JSONObject item=page.optJSONObject("item");boolean download=page.optBoolean("download");if(!Api.publicAccess(item.optString(download?"downloadas":"playas"))){restricted();return;}
        LinearLayout mode=Ui.row(this);for(int i=0;i<(Api.channel(item)?1:2);i++){final boolean next=i==1;TextView b=Ui.button(this,next?"سيرفرات التنزيل":"سيرفرات المشاهدة",v->{put(page,"download",next);put(page,"scroll",0);render();});if(download==next)b.setTextColor(Ui.ACCENT);mode.addView(b,new LinearLayout.LayoutParams(0,-2,1));}content.addView(mode);LinearLayout servers=Ui.column(this);content.addView(servers);
        if(Api.channel(item))serverRows(servers,Api.array(item.optJSONArray("sources")),item,download);else load(Api.sources(page.optBoolean("episode"),item.optInt("id")),servers,value->serverRows(servers,Api.array(value),item,download));
    }
    private void serverRows(LinearLayout parent,JSONArray servers,JSONObject item,boolean download){
        int shown=0;for(int i=0;i<servers.length();i++){
            JSONObject source=servers.optJSONObject(i);if(source==null)continue;String kind=source.optString("kind");if(!kind.equals("both")&&!kind.equals(download?"download":"play"))continue;shown++;
            LinearLayout row=Ui.row(this);row.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));Ui.clickable(row,Ui.surface(this),10);TextView number=Ui.text(this,String.format(Locale.ROOT,"%02d",shown),16,true);number.setTextColor(Ui.ACCENT);number.setGravity(Gravity.CENTER);row.addView(number,new LinearLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,42)));
            LinearLayout labels=Ui.column(this);labels.addView(Ui.text(this,source.optString("title","سيرفر "+shown),16,true));ArrayList<String> parts=new ArrayList<>();for(String key:new String[]{"type","quality","size"}){String s=source.optString(key);if(!s.isEmpty()&&!s.equals("null"))parts.add(s);}TextView sub=Ui.text(this,TextUtils.join("  ·  ",parts),12,false);sub.setTextColor(Ui.muted(this));labels.addView(sub);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));row.addView(new Ui.Icon(this,download?"download":"play",Ui.ACCENT),new LinearLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22)));
            row.setOnClickListener(v->{if(!Api.publicAccess(source.optString("premium"))){restricted();return;}Media.open(this,source,Api.label(item),download);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(this,10);parent.addView(row,lp);
        }if(shown==0)message(parent,"لا توجد سيرفرات "+(download?"تنزيل":"مشاهدة")+" متاحة لهذا العمل حالياً.");
    }
}
