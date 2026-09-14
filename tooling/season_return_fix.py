from pathlib import Path

p = Path('native/src/awr/witcher/DramaActivity.java')
s = p.read_text(encoding='utf-8')

old = 'for(int i=0;i<seasons.length();i++){\n                JSONObject season=seasons.optJSONObject(i);'
new = 'int selectedSeason=Math.max(0,Math.min(seasons.length()-1,page.optInt("selectedSeason",0)));\n            for(int i=0;i<seasons.length();i++){\n                final int seasonIndex=i;JSONObject season=seasons.optJSONObject(i);'
assert old in s, 'season loop marker not found'
s = s.replace(old, new, 1)

old = 'TextView head=Ui.button(this,Api.label(season)+"  ·  "+episodes.length()+" حلقة",v->list.setVisibility(list.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=Ui.dp(this,12);content.addView(head,hp);content.addView(list);if(i>0)list.setVisibility(View.GONE);'
new = 'TextView head=Ui.button(this,Api.label(season)+"  ·  "+episodes.length()+" حلقة",v->{boolean opening=list.getVisibility()!=View.VISIBLE;if(opening)put(page,"selectedSeason",seasonIndex);list.setVisibility(opening?View.VISIBLE:View.GONE);});LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=Ui.dp(this,12);content.addView(head,hp);content.addView(list);if(i!=selectedSeason)list.setVisibility(View.GONE);'
assert old in s, 'season visibility marker not found'
s = s.replace(old, new, 1)

old = 'row.setOnClickListener(v->sources(episode,true,false));Ui.Icon download=icon("download");download.setContentDescription("تنزيل "+Api.label(episode));row.addView(download,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));download.setOnClickListener(v->sources(episode,true,true));'
new = 'row.setOnClickListener(v->{put(page,"selectedSeason",seasonIndex);sources(episode,true,false);});Ui.Icon download=icon("download");download.setContentDescription("تنزيل "+Api.label(episode));row.addView(download,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));download.setOnClickListener(v->{put(page,"selectedSeason",seasonIndex);sources(episode,true,true);});'
assert old in s, 'episode click marker not found'
s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')
print('Applied selected-season return fix')
