package com.atheer.shell;

import android.app.Activity;
import android.content.Intent;

/** Opens the directly compiled Source World activity in the current application process. */
public final class DirectSources {
    public static final String MAIN="com.drama.mp4.ui.main.MainActivity";
    public static final String ACTIVITY="awr.witcher.DramaActivity";
    private DirectSources() {}

    public static void open(Activity activity) {
        Intent intent=new Intent(activity,awr.witcher.DramaActivity.class);
        intent.putExtra("tab",1);
        activity.startActivity(intent);
    }
}
