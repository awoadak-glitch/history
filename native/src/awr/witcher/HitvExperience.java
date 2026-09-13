package awr.witcher;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Self-contained HiTV experience hosted by the existing activity, so no protected host lifecycle is modified. */
final class HitvExperience {
    private HitvExperience(){}
    static final int BG=0xff080b14,SURFACE=0xff121827,SURFACE2=0xff1a2235,ACCENT=0xff7c5cff,TEXT=0xfff7f7fb,MUTED=0xff9ca6bd;

    static void open(Context context){
        Context x=context;while(x instanceof ContextWrapper&&!(x instanceof Activity))x=((ContextWrapper)x).getBaseContext();
        if(!(x instanceof Activity)){Toast.makeText(context,"تعذر فتح HiTV من هذه الشاشة.",Toast.LENGTH_SHORT).show();return;}
        new Screen((Activity)x).show();
    }

    static final class Item {
        String id="",name="بدون عنوان",vertical="",horizontal="",intro="",update="",area="",genre="";int category=1,episodes=1;double score;
    }
    static final class Source { String url,label,type; Source(String u,String l,String t){url=u;label=l;type=t;} }
    static final class State { String kind="home",title="HiTV",query="";Item item; State(String k){kind=k;} }
    static final class Section { String title;ArrayList<Item> items=new ArrayList<>(); Section(String t){title=t;} }

    static final class Screen {
        final Activity a;final Handler handler=new Handler(Looper.getMainLooper());final ArrayDeque<State> back=new ArrayDeque<>();
        Dialog dialog;LinearLayout root,content;ScrollView scroll;State state=new State("home");int generation,homeType;Runnable searchTask;
        Screen(Activity activity){a=activity;}
        void show(){
            dialog=new Dialog(a,android.R.style.Theme_Black_NoTitleBar_Fullscreen);dialog.setOnDismissListener(d->{generation++;handler.removeCallbacksAndMessages(null);});render();dialog.show();Window w=dialog.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(BG);w.setNavigationBarColor(BG);}}
        void render(){
            generation++;if(searchTask!=null)handler.removeCallbacks(searchTask);root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.setBackgroundColor(BG);
            root.addView(topBar(),new LinearLayout.LayoutParams(-1,Ui.dp(a,62)));
            scroll=new ScrollView(a);scroll.setFillViewport(true);content=new LinearLayout(a);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(Ui.dp(a,14),0,Ui.dp(a,14),Ui.dp(a,24));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            root.addView(bottomBar(),new LinearLayout.LayoutParams(-1,Ui.dp(a,62)));if(dialog!=null)dialog.setContentView(root);
            if("search".equals(state.kind))renderSearch();else if("detail".equals(state.kind))renderDetail();else renderHome();
        }
        View topBar(){
            LinearLayout bar=new LinearLayout(a);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(Ui.dp(a,8),0,Ui.dp(a,8),0);bar.setBackgroundColor(BG);
            TextView backButton=iconButton(back.isEmpty()?"×":"‹");backButton.setContentDescription(back.isEmpty()?"إغلاق HiTV":"رجوع");backButton.setOnClickListener(v->goBack());bar.addView(backButton,new LinearLayout.LayoutParams(Ui.dp(a,48),-1));
            TextView title=text(state.title==null?"HiTV":state.title,21,true);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
            TextView logo=text("HiTV",17,true);logo.setTextColor(ACCENT);logo.setGravity(Gravity.CENTER);bar.addView(logo,new LinearLayout.LayoutParams(Ui.dp(a,62),-1));
            TextView search=iconButton("⌕");search.setContentDescription("بحث HiTV");search.setOnClickListener(v->{if(!"search".equals(state.kind)){State n=new State("search");n.title="بحث HiTV";navigate(n);}});bar.addView(search,new LinearLayout.LayoutParams(Ui.dp(a,48),-1));return bar;
        }
        View bottomBar(){
            LinearLayout bar=new LinearLayout(a);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER);bar.setBackgroundColor(SURFACE);bar.setPadding(Ui.dp(a,8),Ui.dp(a,5),Ui.dp(a,8),Ui.dp(a,5));
            TextView home=nav("⌂\nالرئيسية",!"search".equals(state.kind));home.setOnClickListener(v->{back.clear();state=new State("home");render();});bar.addView(home,new LinearLayout.LayoutParams(0,-1,1));
            TextView search=nav("⌕\nبحث","search".equals(state.kind));search.setOnClickListener(v->{if(!"search".equals(state.kind)){State n=new State("search");n.title="بحث HiTV";navigate(n);}});bar.addView(search,new LinearLayout.LayoutParams(0,-1,1));
            TextView badge=nav("▶\nHiTV",true);bar.addView(badge,new LinearLayout.LayoutParams(0,-1,1));return bar;
        }
        TextView nav(String s,boolean active){TextView v=text(s,12,true);v.setGravity(Gravity.CENTER);v.setTextColor(active?ACCENT:MUTED);return v;}
        TextView iconButton(String s){TextView v=text(s,30,false);v.setGravity(Gravity.CENTER);v.setTextColor(TEXT);v.setBackground(ripple(SURFACE2,24));return v;}
        TextView text(String s,int size,boolean bold){TextView v=new TextView(a);v.setText(s);v.setTextColor(TEXT);v.setTextSize(size);v.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);return v;}
        android.graphics.drawable.Drawable ripple(int color,float radius){android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(color);bg.setCornerRadius(Ui.dp(a,radius));return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x337c5cff),bg,null);}
        void goBack(){if(back.isEmpty()){dialog.dismiss();return;}state=back.removeLast();render();}
        void navigate(State next){back.addLast(state);state=next;render();}
        void loading(){ProgressBar p=new ProgressBar(a);p.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(a,38),Ui.dp(a,38));lp.gravity=Gravity.CENTER;lp.topMargin=lp.bottomMargin=Ui.dp(a,44);content.addView(p,lp);}
        void message(String s){TextView t=text(s,15,false);t.setTextColor(MUTED);t.setGravity(Gravity.CENTER);t.setPadding(Ui.dp(a,12),Ui.dp(a,32),Ui.dp(a,12),Ui.dp(a,32));content.addView(t,new LinearLayout.LayoutParams(-1,-2));}
        TextView button(String s){TextView b=text(s,15,true);b.setGravity(Gravity.CENTER);b.setPadding(Ui.dp(a,15),Ui.dp(a,11),Ui.dp(a,15),Ui.dp(a,11));b.setBackground(ripple(SURFACE2,14));return b;}
        void heading(String s){TextView h=text(s,20,true);h.setPadding(Ui.dp(a,2),Ui.dp(a,20),0,Ui.dp(a,10));content.addView(h);}

        void renderHome(){
            LinearLayout chips=new LinearLayout(a);chips.setOrientation(LinearLayout.HORIZONTAL);chips.setGravity(Gravity.CENTER);String[] names={"الكل","غربي","كوري"};for(int i=0;i<3;i++){final int n=i;TextView b=button(names[i]);b.setTextColor(i==homeType?Color.WHITE:MUTED);b.setBackground(ripple(i==homeType?ACCENT:SURFACE2,18));b.setOnClickListener(v->{homeType=n;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(a,44),1);lp.setMargins(Ui.dp(a,4),0,Ui.dp(a,4),0);chips.addView(b,lp);}content.addView(chips);
            final int token=generation;loading();HitvApi.get("/cms/web/hitv/homePage/album/page",HitvApi.params("sequence",0,"type",homeType),(value,error)->{
                if(token!=generation||dialog==null||!dialog.isShowing())return;content.removeViews(1,content.getChildCount()-1);
                if(error!=null){message(error);TextView retry=button("إعادة المحاولة");retry.setOnClickListener(v->render());content.addView(retry);return;}
                ArrayList<Section> sections=sections(value);ArrayList<Item> flat=items(value);if(sections.isEmpty()&&!flat.isEmpty()){Section s=new Section("مختارات HiTV");s.items.addAll(flat);sections.add(s);}if(sections.isEmpty()){message("لم يصل محتوى من HiTV حالياً.");return;}
                Item hero=null;for(Section s:sections)if(!s.items.isEmpty()){hero=s.items.get(0);break;}if(hero!=null)addHero(hero);
                for(Section s:sections){if(s.items.isEmpty())continue;addRail(s.title,s.items);}
            });
        }
        void addHero(Item item){
            FrameLayout box=new FrameLayout(a);box.setBackgroundColor(SURFACE);ImageView image=Ui.image(a,item.horizontal.isEmpty()?item.vertical:item.horizontal,18);box.addView(image,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout shade=new LinearLayout(a);shade.setOrientation(LinearLayout.VERTICAL);shade.setGravity(Gravity.BOTTOM);shade.setPadding(Ui.dp(a,18),Ui.dp(a,12),Ui.dp(a,18),Ui.dp(a,16));shade.setBackgroundColor(0x55000000);TextView tag=text("HiTV  •  "+(item.category==0?"فيلم":"مسلسل"),12,true);tag.setTextColor(0xffd5cbff);shade.addView(tag);TextView title=text(item.name,24,true);title.setMaxLines(2);shade.addView(title);if(!item.update.isEmpty()){TextView u=text(item.update,13,false);u.setTextColor(0xffe0e3eb);shade.addView(u);}box.addView(shade,new FrameLayout.LayoutParams(-1,-1));box.setOnClickListener(v->openDetail(item));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(a,220));lp.topMargin=Ui.dp(a,14);content.addView(box,lp);
        }
        void addRail(String title,ArrayList<Item> list){heading(title==null||title.trim().isEmpty()?"HiTV":title);HorizontalScrollView sc=new HorizontalScrollView(a);sc.setHorizontalScrollBarEnabled(false);LinearLayout rail=new LinearLayout(a);rail.setOrientation(LinearLayout.HORIZONTAL);rail.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);for(int i=0;i<list.size()&&i<24;i++){Item item=list.get(i);LinearLayout card=card(item,Ui.dp(a,126),Ui.dp(a,190));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(Ui.dp(a,126),Ui.dp(a,238));cp.setMargins(Ui.dp(a,5),0,Ui.dp(a,5),0);rail.addView(card,cp);}sc.addView(rail,new HorizontalScrollView.LayoutParams(-2,-2));content.addView(sc,new LinearLayout.LayoutParams(-1,Ui.dp(a,244)));}
        LinearLayout card(Item item,int w,int imageH){LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);card.setBackground(ripple(SURFACE,14));ImageView image=Ui.image(a,item.vertical,14);card.addView(image,new LinearLayout.LayoutParams(-1,imageH));TextView name=text(item.name,13,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(Ui.dp(a,7),Ui.dp(a,5),Ui.dp(a,7),0);card.addView(name,new LinearLayout.LayoutParams(-1,0,1));card.setOnClickListener(v->openDetail(item));return card;}
        void openDetail(Item item){State n=new State("detail");n.item=item;n.title=item.name;navigate(n);}

        void renderSearch(){
            EditText field=new EditText(a);field.setSingleLine(true);field.setHint("ابحث في HiTV عن فيلم أو مسلسل");field.setHintTextColor(MUTED);field.setTextColor(TEXT);field.setTextSize(16);field.setPadding(Ui.dp(a,16),0,Ui.dp(a,16),0);field.setBackground(ripple(SURFACE,16));field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);field.setText(state.query);content.addView(field,new LinearLayout.LayoutParams(-1,Ui.dp(a,54)));LinearLayout results=new LinearLayout(a);results.setOrientation(LinearLayout.VERTICAL);content.addView(results);
            if(state.query.trim().isEmpty()){TextView hint=text("اكتب اسم العمل. البحث متصل مباشرة بمصدر HiTV.",14,false);hint.setTextColor(MUTED);hint.setGravity(Gravity.CENTER);hint.setPadding(0,Ui.dp(a,40),0,Ui.dp(a,20));results.addView(hint);}else search(state.query,results,++generation);
            field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int before,int count){String q=s.toString().trim();state.query=q;if(searchTask!=null)handler.removeCallbacks(searchTask);results.removeAllViews();if(q.isEmpty())return;int token=++generation;searchTask=()->search(q,results,token);handler.postDelayed(searchTask,420);}});
            field.setOnEditorActionListener((v,action,event)->{String q=field.getText().toString().trim();state.query=q;if(searchTask!=null)handler.removeCallbacks(searchTask);results.removeAllViews();int token=++generation;if(!q.isEmpty())search(q,results,token);((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);return true;});
        }
        void search(String query,LinearLayout results,int token){
            ProgressBar p=new ProgressBar(a);p.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(Ui.dp(a,34),Ui.dp(a,34));pl.gravity=Gravity.CENTER;pl.topMargin=Ui.dp(a,26);results.addView(p,pl);
            HitvApi.post("/cms/web/hitv/movieDrama/searchWithKeyWord",HitvApi.params("size",50,"searchKeyWord",query),(value,error)->{
                if(token!=generation||!"search".equals(state.kind))return;results.removeAllViews();if(error!=null){TextView m=text(error,14,false);m.setTextColor(MUTED);m.setGravity(Gravity.CENTER);results.addView(m);return;}ArrayList<Item> list=items(value);if(list.isEmpty()){TextView m=text("لا توجد نتائج لهذا الاسم في HiTV.",15,false);m.setTextColor(MUTED);m.setGravity(Gravity.CENTER);m.setPadding(0,Ui.dp(a,30),0,0);results.addView(m);return;}TextView h=text("نتائج البحث  ·  "+list.size(),18,true);h.setPadding(0,Ui.dp(a,18),0,Ui.dp(a,12));results.addView(h);addGrid(results,list);
            });
        }
        void addGrid(LinearLayout parent,ArrayList<Item> list){
            int width=a.getResources().getDisplayMetrics().widthPixels-Ui.dp(a,36),gap=Ui.dp(a,8),cw=(width-gap*2)/3;LinearLayout row=null;
            for(int i=0;i<list.size()&&i<60;i++){if(i%3==0){row=new LinearLayout(a);row.setOrientation(LinearLayout.HORIZONTAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);parent.addView(row,new LinearLayout.LayoutParams(-1,Ui.dp(a,214)));}LinearLayout card=card(list.get(i),cw,Ui.dp(a,158));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(a,208),1);lp.setMargins(gap/2,0,gap/2,Ui.dp(a,6));row.addView(card,lp);}
        }

        void renderDetail(){
            Item initial=state.item;if(initial==null){message("تعذر فتح هذا العمل.");return;}final int token=generation;loading();HitvApi.get("/cms/web/hitv/movieDrama/detail",HitvApi.params("id",initial.id,"category",initial.category),(value,error)->{
                if(token!=generation||!"detail".equals(state.kind))return;content.removeAllViews();Item item=initial;if(error==null){ArrayList<Item> found=items(value);if(!found.isEmpty()){Item richer=found.get(0);item=merge(initial,richer);}else if(value instanceof JSONObject)item=merge(initial,normalize((JSONObject)value));}state.item=item;drawDetail(item);
            });
        }
        void drawDetail(Item item){
            ImageView cover=Ui.image(a,item.horizontal.isEmpty()?item.vertical:item.horizontal,18);content.addView(cover,new LinearLayout.LayoutParams(-1,Ui.dp(a,235)));TextView name=text(item.name,25,true);name.setPadding(0,Ui.dp(a,16),0,Ui.dp(a,6));content.addView(name);ArrayList<String> meta=new ArrayList<>();if(item.score>0)meta.add(String.format(Locale.ROOT,"★ %.1f",item.score));if(!item.area.isEmpty())meta.add(item.area);if(!item.genre.isEmpty())meta.add(item.genre);if(!item.update.isEmpty())meta.add(item.update);if(!meta.isEmpty()){TextView m=text(TextUtils.join("  •  ",meta),13,false);m.setTextColor(MUTED);content.addView(m);}if(!item.intro.isEmpty()){TextView intro=text(item.intro,15,false);intro.setTextColor(0xffd8dce7);intro.setLineSpacing(Ui.dp(a,4),1f);intro.setPadding(0,Ui.dp(a,14),0,Ui.dp(a,8));content.addView(intro);}
            TextView play=button(item.category==1&&item.episodes>1?"▶  اختر حلقة للمشاهدة":"▶  تشغيل عبر مشغل HiTV");play.setTextColor(Color.WHITE);play.setBackground(ripple(ACCENT,16));play.setOnClickListener(v->{if(item.category==1&&item.episodes>1)showEpisodes(item);else play(item,1);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(a,54));lp.topMargin=Ui.dp(a,18);content.addView(play,lp);
            if(item.category==1&&item.episodes>1)showEpisodeButtons(item,Math.min(item.episodes,240));
        }
        void showEpisodes(Item item){scroll.post(()->scroll.smoothScrollTo(0,Math.max(0,content.getHeight()-a.getResources().getDisplayMetrics().heightPixels/2)));}
        void showEpisodeButtons(Item item,int count){
            TextView head=text("الحلقات  ·  "+count,20,true);head.setPadding(0,Ui.dp(a,24),0,Ui.dp(a,10));content.addView(head);GridLayout grid=new GridLayout(a);grid.setColumnCount(5);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);grid.setUseDefaultMargins(false);
            int gap=Ui.dp(a,4);for(int i=1;i<=count;i++){final int ep=i;TextView b=button(Integer.toString(i));b.setOnClickListener(v->play(item,ep));GridLayout.LayoutParams gp=new GridLayout.LayoutParams();gp.width=0;gp.height=Ui.dp(a,46);gp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1,1f);gp.setMargins(gap,gap,gap,gap);grid.addView(b,gp);}content.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        }
        void play(Item item,int episode){
            final int token=generation;ProgressDialog wait=new ProgressDialog(a);wait.setMessage("جاري تجهيز تشغيل HiTV…");wait.setCancelable(true);wait.show();HitvApi.get("/cms/web/hitv/movieDrama/getPlayInfo",HitvApi.params("id",item.id,"category",item.category,"seriesNo",episode),(value,error)->{
                if(wait.isShowing())wait.dismiss();if(token!=generation||dialog==null||!dialog.isShowing())return;if(error!=null){alert(error);return;}ArrayList<Source> list=sources(value);if(list.isEmpty()){alert("مصدر HiTV لم يُرجع رابط تشغيل متاحاً لهذه الحلقة.");return;}chooseSource(item.name+(item.category==1?" · الحلقة "+episode:""),list);
            });
        }
        void chooseSource(String title,ArrayList<Source> list){
            if(list.size()==1){Source s=list.get(0);HitvPlayer.play(a,title,s.url,s.type);return;}String[] labels=new String[list.size()];for(int i=0;i<list.size();i++)labels[i]=list.get(i).label==null||list.get(i).label.trim().isEmpty()?"جودة "+(i+1):list.get(i).label;new AlertDialog.Builder(a).setTitle("اختر جودة HiTV").setItems(labels,(d,i)->{Source s=list.get(i);HitvPlayer.play(a,title,s.url,s.type);}).show();
        }
        void alert(String s){if(!a.isFinishing()&&!a.isDestroyed())new AlertDialog.Builder(a).setMessage(s).setPositiveButton("حسناً",null).show();}
    }

    static Item merge(Item a,Item b){if(b==null)return a;Item r=new Item();r.id=pick(b.id,a.id);r.name=pick(b.name,a.name);r.vertical=pick(b.vertical,a.vertical);r.horizontal=pick(b.horizontal,a.horizontal);r.intro=pick(b.intro,a.intro);r.update=pick(b.update,a.update);r.area=pick(b.area,a.area);r.genre=pick(b.genre,a.genre);r.category=b.category;r.episodes=Math.max(a.episodes,b.episodes);r.score=b.score>0?b.score:a.score;return r;}
    static String pick(String a,String b){return a!=null&&!a.trim().isEmpty()&&!"بدون عنوان".equals(a)?a:b;}
    static String first(JSONObject o,String... keys){for(String k:keys){Object v=o.opt(k);if(v instanceof String&&!((String)v).trim().isEmpty())return ((String)v).trim();if(v instanceof Number)return String.valueOf(v);}return "";}
    static Item normalize(JSONObject o){
        Item x=new Item();x.id=first(o,"id","contentId","relatedId","relationId");String name=first(o,"name","title");if(!name.isEmpty())x.name=name;x.vertical=first(o,"coverVerticalUrl","imageUrl","cover","bannerUrl");x.horizontal=first(o,"coverHorizontalUrl","bannerUrl","cover","imageUrl","coverVerticalUrl");x.intro=first(o,"introduction","description","summary");x.update=first(o,"updateInfo");x.score=parseDouble(first(o,"score"));
        Object cat=o.opt("domainType");if(cat==null||cat==JSONObject.NULL)cat=o.opt("category");String c=String.valueOf(cat);x.category=("0".equals(c)||"movie".equalsIgnoreCase(c))?0:1;Object area=o.opt("area");if(area instanceof JSONObject)x.area=first((JSONObject)area,"name");else if(area instanceof String)x.area=(String)area;Object tag=o.opt("categoryTag");if(tag instanceof JSONObject)x.genre=first((JSONObject)tag,"name");else if(tag instanceof String)x.genre=(String)tag;
        JSONArray eps=o.optJSONArray("episodeVo");if(eps==null)eps=o.optJSONArray("episodeList");if(eps!=null&&eps.length()>0)x.episodes=eps.length();else{x.episodes=Math.max(1,o.optInt("episodeCount",1));if(x.episodes==1){Matcher m=Pattern.compile("(\\d+)").matcher(x.update);if(m.find())try{x.episodes=Math.max(1,Integer.parseInt(m.group(1)));}catch(Exception ignored){}}}return x;
    }
    static double parseDouble(String s){try{return Double.parseDouble(s);}catch(Exception e){return 0;}}
    static ArrayList<Item> items(Object value){LinkedHashMap<String,Item> map=new LinkedHashMap<>();collectItems(value,0,map);return new ArrayList<>(map.values());}
    static void collectItems(Object value,int depth,LinkedHashMap<String,Item> out){
        if(value==null||value==JSONObject.NULL||depth>8||out.size()>300)return;if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)collectItems(a.opt(i),depth+1,out);return;}if(!(value instanceof JSONObject))return;JSONObject o=(JSONObject)value;Item item=normalize(o);if(!item.id.isEmpty()&&(!item.name.equals("بدون عنوان")||!item.vertical.isEmpty()))out.put(item.category+":"+item.id,item);Iterator<String> keys=o.keys();while(keys.hasNext()){Object next=o.opt(keys.next());if(next instanceof JSONObject||next instanceof JSONArray)collectItems(next,depth+1,out);}
    }
    static ArrayList<Section> sections(Object value){ArrayList<Section> out=new ArrayList<>();collectSections(value,0,out,new HashSet<String>());return out;}
    static void collectSections(Object value,int depth,ArrayList<Section> out,HashSet<String> seen){
        if(value==null||value==JSONObject.NULL||depth>7||out.size()>30)return;if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)collectSections(a.opt(i),depth+1,out,seen);return;}if(!(value instanceof JSONObject))return;JSONObject o=(JSONObject)value;JSONArray list=o.optJSONArray("recommendContentVOList");if(list==null)list=o.optJSONArray("contentList");if(list==null)list=o.optJSONArray("items");String title=first(o,"homeSectionName","sectionName","name","title");if(list!=null&&list.length()>0){ArrayList<Item> its=items(list);if(!its.isEmpty()){String key=(title.isEmpty()?"HiTV":title)+":"+its.get(0).id;if(seen.add(key)){Section s=new Section(title.isEmpty()?"مختارات HiTV":title);s.items.addAll(its);out.add(s);}}}Iterator<String> keys=o.keys();while(keys.hasNext()){Object next=o.opt(keys.next());if(next instanceof JSONObject||next instanceof JSONArray)collectSections(next,depth+1,out,seen);}
    }
    static ArrayList<Source> sources(Object value){LinkedHashMap<String,Source> out=new LinkedHashMap<>();collectSources(value,0,out);return new ArrayList<>(out.values());}
    static void collectSources(Object value,int depth,LinkedHashMap<String,Source> out){
        if(value==null||value==JSONObject.NULL||depth>8||out.size()>30)return;if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)collectSources(a.opt(i),depth+1,out);return;}if(!(value instanceof JSONObject))return;JSONObject o=(JSONObject)value;String url=first(o,"url","playUrl","mediaUrl","file","videoUrl");if(url.startsWith("https://")||url.startsWith("http://")){String lower=url.toLowerCase(Locale.ROOT);if(!lower.matches(".*\\.(jpg|jpeg|png|webp|gif|vtt|srt|ass)(\\?.*)?$")){String label=first(o,"label","name","definition","quality","resolution");if(label.isEmpty())label="Auto";String type=first(o,"type","format","mimeType");out.put(url,new Source(url,label,type));}}Iterator<String> keys=o.keys();while(keys.hasNext()){Object next=o.opt(keys.next());if(next instanceof JSONObject||next instanceof JSONArray)collectSources(next,depth+1,out);}
    }
}
