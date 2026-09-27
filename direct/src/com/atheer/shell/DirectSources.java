package com.atheer.shell;

import android.app.Activity;
import android.content.Intent;

/** Opens the directly compiled Source World activity in the current application process. */
public final class DirectSources {
    public static final String MAIN="com.drama.mp4.ui.main.MainActivity";
    public static final String ACTIVITY="awr.witcher.AnimeActivity";
    private DirectSources() {}

    public static void open(Activity activity) {
        Intent intent=new Intent(activity,awr.witcher.AnimeActivity.class);
        activity.startActivity(intent);
    }
}
