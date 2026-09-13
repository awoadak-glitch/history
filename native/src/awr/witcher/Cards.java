package awr.witcher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Poster, live-channel and hero compositions sized in dp; data always comes from the API. */
public final class Cards {
    public interface Select { void open(JSONObject item); }
    private Cards(){}
    public static View poster(Activity c,JSONObject item,boolean channel,int width,Select select){
        LinearLayout card=Ui.column(c);card.setPadding(Ui.dp(c,4),Ui.dp(c,4),Ui.dp(c,4),Ui.dp(c,10));
        int height=channel?Math.round(width*0.57f):Math.round(width*1.43f);
        FrameLayout frame=new FrameLayout(c);
        frame.addView(Ui.image(c,item.optString("image",item.optString("cover")),8),new FrameLayout.LayoutParams(-1,-1));
        String quality=item.optString("label",item.optString("quality"));
        if(quality.isEmpty() && channel)quality="مباشر";
        if(!quality.isEmpty()){
            TextView badge=Ui.text(c,quality,10,true);badge.setTextColor(Color.BLACK);badge.setBackground(Ui.rounded(c,Ui.ACCENT,5));
            badge.setPadding(Ui.dp(c,6),Ui.dp(c,2),Ui.dp(c,6),Ui.dp(c,2));
            FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);bp.setMargins(Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,5),0);frame.addView(badge,bp);
        }
        card.addView(frame,new LinearLayout.LayoutParams(-1,height));
        TextView title=Ui.text(c,Api.label(item),13,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=Ui.dp(c,7);card.addView(title,tp);
        String year=item.optString("year");
        if(!year.isEmpty()){TextView info=Ui.text(c,year,11,false);info.setTextColor(Ui.muted(c));card.addView(info);}
        card.setContentDescription(Api.label(item));card.setOnClickListener(v->select.open(item));return card;
    }
    public static void grid(Activity c,LinearLayout parent,JSONArray items,boolean channels,Select select){
        int available=c.getResources().getDisplayMetrics().widthPixels-Ui.dp(c,24);
        int columns=channels?2:(available/Ui.dp(c,110));columns=Math.max(2,Math.min(5,columns));
        int width=available/columns;
        LinearLayout row=null;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            if(i%columns==0){row=Ui.row(c);row.setGravity(Gravity.TOP);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            row.addView(poster(c,item,channels,width,select),new LinearLayout.LayoutParams(width,-2));
        }
    }
    public static void rail(Activity c,LinearLayout parent,String title,JSONArray items,Select select){
        if(items.length()==0)return;
        TextView heading=Ui.text(c,title,19,true);heading.setPadding(Ui.dp(c,4),Ui.dp(c,20),Ui.dp(c,4),Ui.dp(c,10));parent.addView(heading);
        HorizontalScrollView scroll=new HorizontalScrollView(c);scroll.setHorizontalScrollBarEnabled(false);scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout row=Ui.row(c);row.setGravity(Gravity.TOP);int width=Ui.dp(c,120);
        for(int i=0;i<items.length();i++){
            JSONObject o=items.optJSONObject(i);if(o!=null)row.addView(poster(c,o,Api.channel(o),width,select),new LinearLayout.LayoutParams(width,-2));
        }
        scroll.addView(row);parent.addView(scroll);
    }
    public static View hero(Activity c,JSONArray items,Select select){
        LinearLayout block=Ui.column(c);ViewFlipper pages=new ViewFlipper(c);
        pages.setBackground(Ui.rounded(c,Ui.surface(c),12));pages.setClipToOutline(true);
        pages.setInAnimation(c,android.R.anim.fade_in);pages.setOutAnimation(c,android.R.anim.fade_out);
        LinearLayout dots=Ui.row(c);dots.setGravity(Gravity.CENTER);final int count=Math.min(6,items.length());
        for(int i=0;i<count;i++){
            JSONObject item=items.optJSONObject(i);FrameLayout frame=new FrameLayout(c);
            String cover=item.optString("cover");if(cover.isEmpty())cover=item.optString("image");
            frame.addView(Ui.image(c,cover,12),new FrameLayout.LayoutParams(-1,-1));
            View shade=new View(c);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xe8000000}));frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));
            TextView title=Ui.text(c,Api.label(item),24,true);title.setTextColor(Color.WHITE);title.setMaxLines(2);title.setPadding(Ui.dp(c,16),Ui.dp(c,12),Ui.dp(c,16),Ui.dp(c,16));
            frame.addView(title,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));frame.setOnClickListener(v->select.open(item));pages.addView(frame);
            final int index=i;TextView dot=Ui.text(c,"●",11,false);dot.setGravity(Gravity.CENTER);dot.setTextColor(i==0?Ui.ACCENT:Ui.muted(c));
            dots.addView(dot,new LinearLayout.LayoutParams(Ui.dp(c,26),Ui.dp(c,28)));
            dot.setOnClickListener(v->{pages.setDisplayedChild(index);for(int j=0;j<count;j++)((TextView)dots.getChildAt(j)).setTextColor(j==index?Ui.ACCENT:Ui.muted(c));});
        }
        block.addView(pages,new LinearLayout.LayoutParams(-1,Ui.dp(c,220)));block.addView(dots);return block;
    }
}
