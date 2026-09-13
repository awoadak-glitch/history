package awr.witcher;

import android.app.Activity;
import android.content.*;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;

/** Inflated by the original activity_home.xml: no hook into protected lifecycle code. */
public final class WitcherTabs extends LinearLayout {
    public interface Listener { void select(int tab); }
    public static final String[] LABELS={"الأنمي","المسلسلات","الأفلام","القنوات","HiTV"};
    private static final String[] ICONS={"anime","series","movies","channels","hitv"};
    private int selected;
    private Listener listener;
    public WitcherTabs(Context c){this(c,null);}
    public WitcherTabs(Context c, AttributeSet a){
        super(c,a);setOrientation(HORIZONTAL);setLayoutDirection(LAYOUT_DIRECTION_RTL);setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(Ui.surface(c));setElevation(Ui.dp(c,10));setMinimumHeight(Ui.dp(c,64));
        listener = tab -> {
            if(tab==0)return;
            if(tab==4){HitvExperience.open(c);return;}
            Intent intent=new Intent(c,DramaActivity.class).putExtra("tab",tab);
            Context test=c;while(test instanceof ContextWrapper && !(test instanceof Activity))test=((ContextWrapper)test).getBaseContext();
            if(!(test instanceof Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
        };
        render();
    }
    public void bind(int active, Listener l){selected=active;listener=l;render();}
    private void render(){
        removeAllViews();Context c=getContext();
        for(int i=0;i<LABELS.length;i++){
            final int index=i;boolean active=i==selected;
            LinearLayout item=Ui.column(c);item.setGravity(Gravity.CENTER);item.setPadding(0,Ui.dp(c,6),0,Ui.dp(c,5));
            Ui.clickable(item,active?0x18eec60a:0x00000000,16);item.setSelected(active);item.setContentDescription(LABELS[i]);
            item.addView(new Ui.Icon(c,ICONS[i],active?Ui.ACCENT:Ui.muted(c)),new LayoutParams(Ui.dp(c,23),Ui.dp(c,23)));
            TextView label=Ui.text(c,LABELS[i],index==4?11:12,active);label.setTextColor(active?Ui.ACCENT:Ui.muted(c));label.setGravity(Gravity.CENTER);
            LayoutParams t=new LayoutParams(-1,-2);t.topMargin=Ui.dp(c,3);item.addView(label,t);
            LayoutParams lp=new LayoutParams(0,Ui.dp(c,56),1);lp.setMargins(Ui.dp(c,2),Ui.dp(c,4),Ui.dp(c,2),Ui.dp(c,4));addView(item,lp);
            item.setOnClickListener(v->{
                if(index==4){if(selected!=4)HitvExperience.open(c);return;}
                if(index!=selected && listener!=null)listener.select(index);
            });
        }
    }
}
