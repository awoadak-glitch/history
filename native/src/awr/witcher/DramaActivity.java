package awr.witcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** First checkpoint: tab navigation, before content integration. */
public final class DramaActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        int tab=getIntent().getIntExtra("tab",1);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.bg(this));
        TextView title=Ui.text(this,WitcherTabs.LABELS[tab],23,true);title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,0,1));
        WitcherTabs tabs=new WitcherTabs(this);tabs.bind(tab,next->{if(next==0)finish();else{getIntent().putExtra("tab",next);recreate();}});
        root.addView(tabs,new LinearLayout.LayoutParams(-1,Ui.dp(this,64)));setContentView(root);
    }
}
